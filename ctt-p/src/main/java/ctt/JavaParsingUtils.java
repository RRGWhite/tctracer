package ctt;

import com.github.javaparser.JavaParser;
import com.github.javaparser.JavaToken;
import com.github.javaparser.Token;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import ctt.types.FunctionalityMethod;
import ctt.types.MethodCallExprDepthPair;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public abstract class JavaParsingUtils {

  private static ArrayList<MethodCallExprDepthPair> methodCallExprs = new ArrayList<>();
  private static ArrayList<ClassOrInterfaceDeclaration> classDecls = new ArrayList<>();
  //private static ArrayList<ClassOrInterfaceDeclaration> ancestorClassDecls = new ArrayList<>();
  private static CombinedTypeSolver combinedTypeSolver;
  private static ArrayList<MethodDeclaration> foundCalledMethods = new ArrayList<>();

  /*public static CompilationUnit mergeCompilationUnits(CompilationUnit cu1, CompilationUnit cu2) {
    CompilationUnit mergedCompilationUnit = mergeCompilationUnitImports(cu1, cu2);

    for (TypeDeclaration classDecl : cu2.getTypes()) {
      for (Object object : classDecl.getMembers().toArray()) {
        if (object instanceof MethodDeclaration) {
          MethodDeclaration methodDecl = (MethodDeclaration) object;
          insertOrOverwriteMethod(methodDecl, testClass);
        } else {
          testClass.addMember(((BodyDeclaration) object).clone());
        }
      }
    }
  }*/

  public static CompilationUnit parseCompilationUnit(String filePath) {
    File compilationUnitFile = new File(filePath);

    CompilationUnit cu = null;
    /*if (Config.cacheParsedCompilationUnits) {
      cu = getCachedCompilationUnit(compilationUnitFile);
    }*/

    if (cu == null) {
      if (!JavaParser.getStaticConfiguration().getSymbolResolver().isPresent()) {
        // Set up a minimal type solver that only looks at the classes used to run this sample.
        combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());

        // Configure JavaParser to use type resolution
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combinedTypeSolver);
        JavaParser.getStaticConfiguration().setSymbolResolver(symbolSolver);
      }

      try {
        Charset charset = Charset.forName("UTF-8");
        cu = JavaParser.parse(new File(filePath), charset);

        /*if (Config.cacheParsedCompilationUnits) {
          addCompilationUnitToCache(compilationUnitFile, cu);
        }*/
      } catch (Exception e) {
        Logger.get().logAndPrintLn("Error parsing compilation unit for file: " + filePath);
        //ExceptionHandler.handleCaughtThrowable(e, false);
      }
    }
    return cu;
  }

  public static CompilationUnit mergeCompilationUnitImports(CompilationUnit cu1,
                                                            CompilationUnit cu2) {
    CompilationUnit mergedCompilationUnit = cu1.clone();
    for (ImportDeclaration importDeclaration : cu2.getImports()) {
      if (!mergedCompilationUnit.getImports().contains(importDeclaration)) {
        mergedCompilationUnit.addImport(importDeclaration);
      }
    }

    return mergedCompilationUnit;
  }

  public static String getPackageStringFromPackageDeclaration(String packageDeclaration) {
    String path =
        packageDeclaration.replace("package ", "").replace(";", "")
            .replace("\n", "").trim();

    return path;
  }

  public static String getFilePathFromPackageDeclaration(String packageDeclaration) {
    String path =
        getPackageStringFromPackageDeclaration(packageDeclaration).replace(".", "/").trim();

    return path;
  }

  /*public static CompilationUnit getCompilationUnitForFunction(FunctionalityMethod function,
                                                              ProjectDetails projectDetails) {
    String sourceFileName = function.getClassName() + ".java";
    if (function.getClassName().contains("$")) {
      sourceFileName = function.getClassName().split("\\$")[0] + ".java";
    }

    File compilationUnitFile =
        new File(projectDetails.getSourcePath() + "/" +
            getFilePathFromPackageDeclaration(function.getMethodPackage()) + "/" + sourceFileName);

    CompilationUnit compilationUnit = parseCompilationUnit(compilationUnitFile.getPath());
    return compilationUnit;
  }*/

  public static ClassOrInterfaceDeclaration getInnerClassByName(
      ClassOrInterfaceDeclaration containingClass, String innerClassName) {
    for (Node child : containingClass.getChildNodes()) {
      if (child instanceof ClassOrInterfaceDeclaration) {
        ClassOrInterfaceDeclaration innerClass = (ClassOrInterfaceDeclaration) child;
        if (innerClass.getNameAsString().equals(innerClassName)) {
          return innerClass;
        }
      }
    }

    return null;
  }

  public static ArrayList<Statement> getStatementsAtLine(int lineNumber, CompilationUnit cu) {
    ArrayList<Statement> statementsAtLine = new ArrayList<>();
    boolean lineFound = false;

    for (TypeDeclaration typeDeclaration : cu.getTypes()) {
      if (typeDeclaration instanceof ClassOrInterfaceDeclaration) {
        ClassOrInterfaceDeclaration classDeclaration =
            (ClassOrInterfaceDeclaration) typeDeclaration;

        for (MethodDeclaration methodDeclaration : classDeclaration.getMethods()) {
          for (Statement methodStatement : methodDeclaration.getBody().get().getStatements()) {
            if (methodStatement.getBegin().get().line == lineNumber) {
              lineFound = true;
              statementsAtLine.add(methodStatement);
            }
          }

          if (lineFound) {
            return statementsAtLine;
          }
        }
      }
    }

    return statementsAtLine;
  }

  /*public static MethodCallExpr getMethodCallExprFromStatement(Statement startingStatement) {
    Statement statement = startingStatement;
    while (!(statement.isExpressionStmt() &&
        statement.asExpressionStmt().getExpression().isMethodCallExpr())) {
      List<Node> childNodes = statement.getChildNodes();
      for (Node node : childNodes) {

      }
    }
  }*/

  public static ArrayList<MethodCallExprDepthPair> getMethodCallExprsFromStatement(
      Statement startingStatement) {
    methodCallExprs = new ArrayList<>();
    int depth = 0;
    Statement statement = startingStatement;
    findMethodCallExprChildren(statement, ++depth);
    return methodCallExprs;
  }

  public static void findMethodCallExprChildren(Node node, int depth) {
    if (node == null) {
      return;
    }

    if (node instanceof MethodCallExpr) {
      methodCallExprs.add(new MethodCallExprDepthPair((MethodCallExpr) node, depth));
    }

    List<Node> childNodes = node.getChildNodes();
    for (Node childNode : childNodes) {
      findMethodCallExprChildren(childNode, ++depth);
    }
  }

  public static MethodDeclaration getMethodDeclFromClass(ClassOrInterfaceDeclaration classDecl,
                                                         String methodName,
                                                         ArrayList<ResolvedType> parameterTypes) {
    MethodDeclaration foundMethodDecl = null;
    for (MethodDeclaration methodDeclaration : classDecl.getMethods()) {
      if (methodDeclaration.getNameAsString().equals(methodName)) {
        ArrayList<ResolvedType> methodDeclarationParameterTypes = new ArrayList<>();
        if (parameterTypes.size() == methodDeclarationParameterTypes.size()) {
          for (int i = 0; i < parameterTypes.size(); ++i) {
            if (!parameterTypes.get(i).equals(
                methodDeclaration.getParameter(i).getType().resolve())) {
              break;
            }
          }

          foundMethodDecl = methodDeclaration;
        }
      }
    }

    return foundMethodDecl;
  }

  public static MethodDeclaration getMethodDeclFromClass(ClassOrInterfaceDeclaration classDecl,
                                                         String methodName,
                                                         int numParameters) {
    MethodDeclaration foundMethodDecl = null;
    for (MethodDeclaration methodDeclaration : classDecl.getMethods()) {
      if (methodDeclaration.getNameAsString().equals(methodName)) {
        if (methodDeclaration.getParameters().size() == numParameters) {
          foundMethodDecl = methodDeclaration;
        }
      }
    }

    return foundMethodDecl;
  }

  /*public static CanonicalFormAssert normaliseAssertToCanonicalForm(Statement assertStmt) {
    Statement normalisedStatement = null;
    if (assertStmt.isExpressionStmt()
        && assertStmt.asExpressionStmt().getExpression().isMethodCallExpr()) {
      MethodCallExpr assertMethodCallExpr =
          (MethodCallExpr) assertStmt.asExpressionStmt().getExpression().asMethodCallExpr();
      String assertCallName = assertMethodCallExpr.getName().asString();
      int numArgs = assertMethodCallExpr.getArguments().size();
      //assertMethodCallExpr.getArgument(0).calculateResolvedType().
    }
    //TODO: implement
    return null;
  }*/

  public static void insertOrOverwriteMethod(MethodDeclaration methodDecl,
                                             ClassOrInterfaceDeclaration classDecl) {
    for (MethodDeclaration existingMethodDecl : classDecl.getMethods()) {
      if (existingMethodDecl.getDeclarationAsString().equals(methodDecl.getDeclarationAsString())) {
        classDecl.replace(existingMethodDecl, methodDecl);
        return;
      }
    }

    classDecl.addMember(methodDecl);
  }

  public static void insertOrOverwriteField(FieldDeclaration fieldDecl,
                                            ClassOrInterfaceDeclaration classDecl) {
    for (FieldDeclaration existingFieldDecl : classDecl.getFields()) {
      if (existingFieldDecl.toString().equals(fieldDecl.toString())) {
        classDecl.replace(existingFieldDecl, fieldDecl);
        return;
      }
    }

    classDecl.addMember(fieldDecl);
  }

  public static ArrayList<MethodCallExprDepthPair> getMethodCallExprsInFunction(
      MethodDeclaration caller) {
    ArrayList<MethodCallExprDepthPair> methodCallExprs = new ArrayList<>();
    if (caller.getBody().isPresent()) {
      for (Statement statement : caller.getBody().get().getStatements()) {
        methodCallExprs.addAll(JavaParsingUtils.getMethodCallExprsFromStatement(statement));
      }
    }

    return methodCallExprs;
  }

  /*public static ArrayList<String> getNamesOfMethodCalledByFunction(MethodDeclaration caller) {
    ArrayList<String> methodNamesCalledByFunction = new ArrayList<>();
    ArrayList<MethodCallExpr> methodCallExprs = getMethodCallExprsInFunction(caller);
    for (MethodCallExpr methodCallExpr : methodCallExprs) {
      String calledMethodName = methodCallExpr.getName().asString();
      if (!methodNamesCalledByFunction.contains(calledMethodName)) {
        methodNamesCalledByFunction.add(calledMethodName);
      }
    }

    return methodNamesCalledByFunction;
  }*/

  public static boolean methodIsATest(MethodDeclaration method) {
    for (AnnotationExpr annotationExpr : method.getAnnotations()) {
      if (annotationExpr.getName().asString().contains("Test")) {
        return true;
      }
    }

    if (method.getNameAsString().toLowerCase().startsWith("test")) {
      return true;
    }

    return false;
  }

  public static boolean methodIsATestClassSetupOrTeardownMethod(MethodDeclaration method) {
    for (AnnotationExpr annotationExpr : method.getAnnotations()) {
      if (annotationExpr.getName().asString().contains("Before")
          || annotationExpr.getName().asString().contains("After")) {
        return true;
      }
    }

    return false;
  }

  public static ArrayList<ClassOrInterfaceDeclaration> getAllClassDeclsFromCompilationUnit(
      CompilationUnit cu) {
    classDecls = new ArrayList<>();
    if (cu == null){
      return classDecls;
    }

    for (TypeDeclaration<?> typeDeclaration : cu.getTypes()) {
      if (typeDeclaration.isClassOrInterfaceDeclaration()) {
        ClassOrInterfaceDeclaration classOrInterfaceDeclaration = typeDeclaration.asClassOrInterfaceDeclaration();
        findClassDeclChildren(classOrInterfaceDeclaration);
      }
    }
    return classDecls;
  }

  public static void findClassDeclChildren(ClassOrInterfaceDeclaration classDecl) {
    if (classDecl == null) {
      return;
    }

    classDecls.add(classDecl);

    ArrayList<ClassOrInterfaceDeclaration> innerClassDecls = new ArrayList<>();
    for (BodyDeclaration<?> bodyDeclaration : classDecl.getMembers()) {
      if (bodyDeclaration.isClassOrInterfaceDeclaration()) {
        ClassOrInterfaceDeclaration innerClass = bodyDeclaration.asClassOrInterfaceDeclaration();
        findClassDeclChildren(innerClass);
      }
    }
  }

  /*public static boolean methodDirectlyCallsMethod(MethodDeclaration caller,
                                                  MethodDeclaration callee) {
    ArrayList<MethodCallExpr> methodCallExprsInCaller = getMethodCallExprsInFunction(caller);
    for (MethodCallExpr methodCallExpr : methodCallExprsInCaller) {
      if (methodIsTheTargetOfMethodCallExpr(callee, methodCallExpr)) {
        return true;
      }
    }

    return false;
  }*/

  /*public static boolean methodIsReachableFromMethodInClass(MethodDeclaration callee,
                                                           MethodDeclaration caller,
                                                           ClassOrInterfaceDeclaration classDecl) {
    foundCalledMethods = new ArrayList<>();
    searchForCalledMethodsInClass(caller, classDecl);
    for (MethodDeclaration methodDeclaration : foundCalledMethods) {
      if (methodDeclaration.getDeclarationAsString(true, true, true)
          .equals(callee.getDeclarationAsString(true, true, true))) {
        return true;
      }
    }
    return false;
  }

  public static ArrayList<MethodDeclaration> getAllMethodsReachableFromMethodInClass(MethodDeclaration caller,
                                                                ClassOrInterfaceDeclaration classDecl) {
    foundCalledMethods = new ArrayList<>();
    searchForCalledMethodsInClass(caller, classDecl);
    return foundCalledMethods;
  }*/

  /*public static ArrayList<MethodDeclaration> searchForCalledMethodsInClass(MethodDeclaration
  caller,
                                                                           ClassOrInterfaceDeclaration classDecl) {
    ArrayList<MethodDeclaration> directlyCalledMethods =
          getAllDirectlyCalledMethodsByMethodInClass(caller,
              classDecl);

      for (MethodDeclaration directlyCalledMethod : directlyCalledMethods) {
        if (!foundCalledMethods.contains(caller)) {
          foundCalledMethods.add(directlyCalledMethod);
          foundCalledMethods.addAll(searchForCalledMethodsInClass(directlyCalledMethod, classDecl));
        }
      }

    return directlyCalledMethods;
  }*/

  public static boolean methodIsTheTargetOfMethodCallExpr(MethodDeclaration callee,
                                                          MethodCallExpr methodCallExpr) {
      try {
        JavaParserMethodDeclaration javaParserMethodDeclaration =
            (JavaParserMethodDeclaration) methodCallExpr.resolveInvokedMethod();
        if (javaParserMethodDeclaration.getWrappedNode().toString().equals(callee.toString())) {
          return true;
        }
      } catch (Exception e) {
        if (methodCallExpr.getName().asString().equals(callee.getName().asString())
            && methodCallExpr.getArguments().size() == callee.getParameters().size()) {
          return true;
        }
      }

    return false;
  }

  /*public static ArrayList<MethodDeclaration> getAllDirectlyCalledMethodsByMethodInClass(
      MethodDeclaration caller, ClassOrInterfaceDeclaration classDecl) {
    ArrayList<MethodDeclaration> calledMethods = new ArrayList<>();
    ArrayList<MethodCallExpr> methodCallExprsInCaller = getMethodCallExprsInFunction(caller);
    for (MethodCallExpr methodCallExpr : methodCallExprsInCaller) {
      for (MethodDeclaration methodDeclaration : classDecl.getMethods()) {
        if (methodIsTheTargetOfMethodCallExpr(methodDeclaration, methodCallExpr)) {
          if (!calledMethods.contains(methodDeclaration)) {
            calledMethods.add(methodDeclaration);
          }
        }
      }
    }

    return calledMethods;
  }

  public static boolean methodDirectlyCallsMethod(MethodDeclaration caller,
                                                  FunctionalityMethod callee) {
    ArrayList<MethodCallExpr> methodCallExprsInCaller = getMethodCallExprsInFunction(caller);
    for (MethodCallExpr methodCallExpr : methodCallExprsInCaller) {
      try {
        JavaParserMethodDeclaration javaParserMethodDeclaration =
            (JavaParserMethodDeclaration) methodCallExpr.resolveInvokedMethod();
        if (javaParserMethodDeclaration.getWrappedNode().toString().equals(callee.toString())) {
          return true;
        }
      } catch (Exception e) {
        if (methodCallExpr.getName().asString().equals(callee.getName())
            && methodCallExpr.getArguments().size() == callee.getParams().size()) {
          return true;
        }
      }
    }

    return false;
  }*/

  public static CompilationUnit migrateImportsAcrossCompilationUnits(CompilationUnit source,
                                                                     CompilationUnit destination) {
    for (ImportDeclaration importDeclaration : source.getImports()) {
      if (!destination.getImports().contains(importDeclaration)) {
        destination.addImport(importDeclaration);
      }
    }

    return destination;
  }

  /*public static ClassOrInterfaceDeclaration migrateFieldsAcrossClasses(
      ClassOrInterfaceDeclaration source,
      ClassOrInterfaceDeclaration destination) {
    for (FieldDeclaration fieldDeclaration : source.getFields()) {
      boolean foundInDestination = false;
      for (FieldDeclaration destinationFieldDeclaration : source.getFields()) {
        if (fieldDeclaration.toString().equals(destinationFieldDeclaration.toString())) {
          foundInDestination = true;
        }
      }

      if (!foundInDestination) {
        destination.addMember(fieldDeclaration.clone());
      }
    }

    return destination;
  }*/

  public static boolean fieldsShareAVariableName(FieldDeclaration field1, FieldDeclaration field2) {
    boolean matchedAVariable = false;
    for (VariableDeclarator field1VariableDeclarator : field1.getVariables()) {
      for (VariableDeclarator field2VariableDeclarator : field2.getVariables()) {
        if (field1VariableDeclarator.getName().asString().equals(
            field2VariableDeclarator.getName().asString())) {
          matchedAVariable = true;
        }
      }
    }

    return matchedAVariable;
  }

  public static ClassOrInterfaceDeclaration migrateFieldsAndInitializersAcrossClasses(
      ClassOrInterfaceDeclaration source, ClassOrInterfaceDeclaration destination) {
    for (BodyDeclaration declaration : source.getMembers()) {
      boolean foundInDestination = false;
      if (declaration instanceof FieldDeclaration) {
        FieldDeclaration fieldDeclaration = (FieldDeclaration) declaration;
        for (BodyDeclaration destinationDeclaration : destination.getMembers()) {
          if (destinationDeclaration instanceof FieldDeclaration) {
            FieldDeclaration destinationFieldDeclaration = (FieldDeclaration) destinationDeclaration;
            if (fieldsShareAVariableName(fieldDeclaration, destinationFieldDeclaration)) {
              foundInDestination = true;
              break;
            }
          }
        }
      } else if (declaration instanceof InitializerDeclaration) {
        for (BodyDeclaration destinationDeclaration : destination.getMembers()) {
          if (destinationDeclaration instanceof InitializerDeclaration) {
            if (declaration.toString().equals(destinationDeclaration.toString())) {
              foundInDestination = true;
              break;
            }
          }
        }
      } else {
        continue;
      }

      if (!foundInDestination) {
        destination.addMember(declaration.clone());
      }
    }

    return destination;
  }

  public static String getOuterMostClassFqn(String innerClassFqn) {
    String[] fqnComponents = innerClassFqn.split("\\.");
    String outerMostClassFqn = "";
    for (int i = 0; i < fqnComponents.length; ++i) {
      String fqnComponent = fqnComponents[i];
      outerMostClassFqn += fqnComponent;

      if (fqnComponent.substring(0,1).matches("[A-Z]")) {
        break;
      }

      outerMostClassFqn += ".";
    }

    return outerMostClassFqn;
  }

  public static ArrayList<JavaToken> getTokenListForMethodSource(String methodSrc,
                                                                 boolean stripWhitespace) {
    try {
      BodyDeclaration<?> bodyDeclaration = JavaParser.parseBodyDeclaration(methodSrc);
      ArrayList<JavaToken> methodTokens = new ArrayList<>();
      bodyDeclaration.getTokenRange().get().forEach((token) -> {
        if (stripWhitespace) {
          if (!token.getCategory().isComment()
              && !token.getCategory().isWhitespace()) {
            methodTokens.add(token);
          }
        } else {
          methodTokens.add(token);
        }
      });
      return methodTokens;
    } catch (Exception e) {
      ExceptionHandler.handleCaughtThrowable(e, false);
      return null;
    }
  }

  public static String removeJavaDocComment(String src) {
    String formattedSrc = src;
    int endOfJavaDocPosition = src.lastIndexOf("*/");
    if (endOfJavaDocPosition != -1) {
      int subStrStart = endOfJavaDocPosition + 2;
      formattedSrc = src.substring(subStrStart).trim();
    }
    return formattedSrc;
  }

  /*public static ClassOrInterfaceDeclaration migrateInitializersAcrossClasses
  (ClassOrInterfaceDeclaration source, ClassOrInterfaceDeclaration destination) {
    for (BodyDeclaration declaration : source.getMembers()) {
      boolean foundInDestination = false;
      for (FieldDeclaration destinationFieldDeclaration : source.getFields()) {
        if (fieldDeclaration.toString().equals(destinationFieldDeclaration.toString())) {
          foundInDestination = true;
        }
      }

      if (!foundInDestination) {
        destination.addMember(fieldDeclaration.clone());
      }
    }

    return destination;
  }*/

  /*public static boolean methodDirectlyOrIndirectlyCallsMethod(MethodDeclaration caller,
                                                              MethodDeclaration callee) {
    ArrayList<MethodCallExpr> methodCallExprsInCaller = getMethodCallExprsInFunction(caller);
    for (MethodCallExpr methodCallExpr : methodCallExprsInCaller) {
      try {
        JavaParserMethodDeclaration javaParserMethodDeclaration =
            (JavaParserMethodDeclaration) methodCallExpr.resolveInvokedMethod();
        if (javaParserMethodDeclaration.getWrappedNode().toString().equals(callee.toString())) {
          return true;
        }
      } catch (Exception e) {
        if (methodCallExpr.getName().asString().equals(callee.getName())
            && methodCallExpr.getArguments().size() == callee.getParameters().size()) {
          return true;
        }
      }
    }

    return false;
  }

  public static boolean methodDirectlyOrIndirectlyCallsMethod(MethodDeclaration caller,
                                                  FunctionalityMethod callee) {
    ArrayList<MethodCallExpr> methodCallExprsInCaller = getMethodCallExprsInFunction(caller);
    for (MethodCallExpr methodCallExpr : methodCallExprsInCaller) {
      try {
        JavaParserMethodDeclaration javaParserMethodDeclaration =
            (JavaParserMethodDeclaration) methodCallExpr.resolveInvokedMethod();
        if (javaParserMethodDeclaration.getWrappedNode().toString().equals(callee.toString())) {
          return true;
        }
      } catch (Exception e) {
        if (methodCallExpr.getName().asString().equals(callee.getName())
            && methodCallExpr.getArguments().size() == callee.getParams().size()) {
          return true;
        }
      }
    }

    return false;
  }*/

  /*private static CompilationUnit getCachedCompilationUnit(File file) {
    for (CachedCompilationUnit cachedCompilationUnit : compilationUnitCache) {
      if (file.getPath().equals(cachedCompilationUnit.getFile().getPath())
          && file.lastModified() == cachedCompilationUnit.getModifiedTimestamp()) {
        return cachedCompilationUnit.getCompilationUnit();
      }
    }

    return null;
  }

  private static void addCompilationUnitToCache(File file, CompilationUnit compilationUnit) {
    CachedCompilationUnit newCachedCompilationUnit = new CachedCompilationUnit(file,
        file.lastModified(), compilationUnit);
    compilationUnitCache.add(newCachedCompilationUnit);
  }*/

 /* public static ArrayList<ClassOrInterfaceDeclaration> getAncestorClassDecls(
      ClassOrInterfaceDeclaration startingClass) {
    ancestorClassDecls = new ArrayList<>();
    searchForAncestorClasses(startingClass);
  }

  private static void searchForAncestorClasses(ClassOrInterfaceDeclaration startingClass) {
    for (ClassOrInterfaceType type : startingClass.getExtendedTypes()) {
      ResolvedType typeDeclaration = JavaParserFacade.get(combinedTypeSolver).convertToUsage(type);
      ResolvedReferenceTypeDeclaration resolvedReferenceTypeDeclaration =
          typeDeclaration.asReferenceType().getTypeDeclaration();
      resolvedReferenceTypeDeclaration.get
      ClassOrInterfaceDeclaration ancestorClassDecl = (ClassOrInterfaceDeclaration) resolvedReferenceTypeDeclaration;
      if (resolvedReferenceTypeDeclaration instanceof ClassOrInterfaceDeclaration) {

      }
      JavaParserFacade.get(combinedTypeSolver).
      JavaParserFacade.get(combinedTypeSolver).getTypeDeclaration(typeDeclaration);
      if (typeDeclaration. instanceof JavaParserClassDeclaration) {

      }
    }
  }*/
}
