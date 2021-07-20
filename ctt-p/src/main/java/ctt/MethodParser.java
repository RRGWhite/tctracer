package ctt;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import ctt.types.Annotation;
import ctt.types.FunctionalityMethod;
import ctt.types.Method;
import ctt.types.TestMethod;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MethodParser<T extends Method> {
  private static String javaPackage;
  private static String javaClass;
  private final ArrayList<T> methodList;
  private final Configuration.ArtefactType artefactType;
  private String filePath;
  private String prefixToRemove;
  private boolean junitTestsOnly;
  private boolean includeNoAssertTests;

  public MethodParser(String filePath, String prefixToRemove,
                      Configuration.ArtefactType artefactType) {
    this.filePath = filePath;
    this.prefixToRemove = FileSystemHandler.normalisePathSeperators(prefixToRemove);
    this.artefactType = artefactType;
    methodList = new ArrayList<>();

    junitTestsOnly = true;
    includeNoAssertTests = true;
  }

  /***
   * Extract both methods and constructors
   * @return a list of methods & constructors
   */
  public ArrayList<T> parseMethods() {
    try {
      CompilationUnit cu;
      try (FileInputStream in = new FileInputStream(filePath)) {
        cu = JavaParser.parse(in);

        /*if (filePath.contains("WriterOutputStreamTest")) {
          Logger.get().logAndPrintLn("DEBUGGING MISSING TESTS");
        }*/

        // extract package and class name
        if (cu.getPackageDeclaration().isPresent()) {
          javaPackage = JavaParsingUtils.getPackageStringFromPackageDeclaration(
              cu.getPackageDeclaration().get().toString());
        } else {
          javaPackage = "unknown.package";
        }

        for (TypeDeclaration type : cu.getTypes()) {
          if (type instanceof ClassOrInterfaceDeclaration) {
            // getting class name
            ClassOrInterfaceDeclaration classDec = (ClassOrInterfaceDeclaration) type;
            javaClass = classDec.getName().asString();

          }
        }

        new MethodVisitor().visit(cu, null);
        new ConstructorVisitor().visit(cu, null);

      } catch (Throwable e) {
        ExceptionHandler.handleCaughtThrowable(e, false);
      }
    } catch (Exception e) {
      ExceptionHandler.handleCaughtThrowable(e, false);
    }

    /*if (filePath.contains("WriterOutputStreamTest")) {
      Logger.get().logAndPrintLn("DEBUGGING MISSING TESTS");
    }*/

    return methodList;
  }

  /***
   * Extract methods
   */
  private class MethodVisitor extends VoidVisitorAdapter {

    @Override
    public void visit(MethodDeclaration n, Object arg) {
      String enclosingClass;

      /*if (filePath.contains("WriterOutputStreamTest")) {
        Logger.get().logAndPrintLn("DEBUGGING MISSING TESTS");
      }*/

      if (n.getParentNode().get() instanceof ClassOrInterfaceDeclaration) {
        ClassOrInterfaceDeclaration classDec = (ClassOrInterfaceDeclaration) n.getParentNode().get();
        if (classDec.getParentNode().get() instanceof ClassOrInterfaceDeclaration) {
          ClassOrInterfaceDeclaration OuterclassDec = (ClassOrInterfaceDeclaration) classDec
              .getParentNode().get();
          enclosingClass = OuterclassDec.getName() + "$" + classDec.getName();
        } else if (classDec.getParentNode().get() instanceof EnumDeclaration) {
          EnumDeclaration OuterclassDec = (EnumDeclaration) classDec.getParentNode().get();
          enclosingClass = OuterclassDec.getName() + "$" + classDec.getName();
        } else {
          enclosingClass = classDec.getName().asString();
        }
      } else if (n.getParentNode().get() instanceof EnumDeclaration) {
        EnumDeclaration classDec = (EnumDeclaration) n.getParentNode().get();
        if (classDec.getParentNode().get() instanceof ClassOrInterfaceDeclaration) {
          ClassOrInterfaceDeclaration OuterclassDec = (ClassOrInterfaceDeclaration) classDec
              .getParentNode().get();
          enclosingClass = OuterclassDec.getName() + "$" + classDec.getName();
        } else if (classDec.getParentNode().get() instanceof EnumDeclaration) {
          EnumDeclaration OuterclassDec = (EnumDeclaration) classDec.getParentNode().get();
          enclosingClass = OuterclassDec.getName() + "$" + classDec.getName();
        } else {
          enclosingClass = classDec.getName().asString();
        }
      } else if (n.getParentNode().get() instanceof ObjectCreationExpr) {
        return;
      } else {
        enclosingClass = javaClass;
      }

      List<Parameter> parameterArrayList = n.getParameters();
      ArrayList<ctt.types.Parameter> paramsList = new ArrayList<>();
      for (Parameter p : parameterArrayList) {
        String paramTypeString = p.getType().toString();
        if (p.isVarArgs()) {
          paramTypeString = paramTypeString + "[]";
        }

        paramsList.add(
            new ctt.types.Parameter(paramTypeString, p.getNameAsString()));
      }

      String src = removeJavaDocComment(n.toString());
      if (artefactType == Configuration.ArtefactType.FUNCTION) {
        FunctionalityMethod m = new FunctionalityMethod(
            FileSystemHandler.normalisePathSeperators(filePath).replace(prefixToRemove, ""),
            javaPackage,
            enclosingClass,
            n.getName().asString(),
            src,
            n.getBegin().get().line,
            n.getEnd().get().line,
            paramsList,
            n.getDeclarationAsString(),
            extractAnnotations(n.getAnnotations()));
        methodList.add((T) m);
      } else if (artefactType == Configuration.ArtefactType.TEST) {
        if (n.getName().asString().equals("main")) {
          System.out.println("DEBUGGING NON-TEST TESTS");
        }

        TestMethod m = new TestMethod(
            FileSystemHandler.normalisePathSeperators(filePath).replace(prefixToRemove, ""),
            javaPackage,
            enclosingClass,
            n.getName().asString(),
            src,
            n.getBegin().get().line,
            n.getEnd().get().line,
            paramsList,
            n.getDeclarationAsString(),
            extractAnnotations(n.getAnnotations()));
        if (src.contains("@Test") || n.getName().asString().toLowerCase().startsWith("test") ||
            !junitTestsOnly){
          if (src.contains("assert") || includeNoAssertTests) {
            methodList.add((T) m);
          } /*else {
            Logger.get().logAndPrintLn("Throwing away link with no-assert test:\n" +
                m.getSrc());
          }*/
        } else {
          /*Logger.get().logAndPrintLn("Skipping test " + m.getFullyQualifiedMethodName() +
              " as it has no test annotation");*/
        }
      }

      super.visit(n, arg);
    }
  }

  /***
   * Extract constructors
   */
  private class ConstructorVisitor extends VoidVisitorAdapter {

    @Override
    public void visit(ConstructorDeclaration c, Object arg) {
      String enclosingClass;
      if (c.getParentNode().get() instanceof ClassOrInterfaceDeclaration) {
        ClassOrInterfaceDeclaration classDec = (ClassOrInterfaceDeclaration) c.getParentNode().get();
        enclosingClass = classDec.getName().asString();
      } else {
        enclosingClass = javaClass;
      }

      List<Parameter> parameterArrayList = c.getParameters();
      ArrayList<ctt.types.Parameter> paramsList = new ArrayList<>();
      for (Parameter p : parameterArrayList) {
        paramsList.add(
            new ctt.types.Parameter(
                p.getType().toString(),
                p.getNameAsString()));
      }

      if (artefactType == Configuration.ArtefactType.FUNCTION) {
        FunctionalityMethod m = new FunctionalityMethod(
            FileSystemHandler.normalisePathSeperators(filePath).replace(prefixToRemove, ""),
            javaPackage,
            enclosingClass,
            c.getName().asString(),
            //removeJavaDocComment(c.toStringWithoutComments()),
            removeJavaDocComment(c.toString()),
            c.getBegin().get().line,
            c.getEnd().get().line,
            paramsList,
            c.getDeclarationAsString(),
            extractAnnotations(c.getAnnotations()));
        methodList.add((T) m);
      } else if (artefactType == Configuration.ArtefactType.TEST) {
        //Ignore constructors for test classes
      }
      super.visit(c, arg);
    }
  }

  private List<Annotation> extractAnnotations(List<AnnotationExpr> annotationExprs) {
    List<Annotation> annotations = new ArrayList<>();
    for (AnnotationExpr an : annotationExprs) {
      String annoName = an.getName().asString();
      HashMap<String, String> pairs = new HashMap<>();
      if (an instanceof NormalAnnotationExpr) {
        NormalAnnotationExpr nane = (NormalAnnotationExpr) an;
        for (MemberValuePair pair : nane.getPairs()) {
          Expression value = pair.getValue();
          if (value instanceof StringLiteralExpr) {
            StringLiteralExpr sle = (StringLiteralExpr) value;
            pairs.put(pair.getName().asString(), sle.getValue());
          }
        }
      }
      annotations.add(new Annotation(annoName, pairs));
    }
    return annotations;
  }

  /**
   *
   * @param src
   * @return
   */
  private String removeJavaDocComment(String src) {
    String formattedSrc = src;
    int endOfJavaDocPosition = src.lastIndexOf("*/");
    if (endOfJavaDocPosition != -1) {
      int subStrStart = endOfJavaDocPosition + 2;
      formattedSrc = src.substring(subStrStart).trim();
    }
    return formattedSrc;
  }

  public static String eraseGenericTypeArgsFromParamType(String origParamType) {
      String newType = origParamType;
      int openingGenericTypeArgPos = origParamType.indexOf('<');
      if (openingGenericTypeArgPos != -1) {
        int closingGenericTypeArgPos = origParamType.lastIndexOf('>');
        newType = origParamType.substring(0, openingGenericTypeArgPos) + origParamType.substring
            (closingGenericTypeArgPos + 1);
      }
    return newType;
  }

  public static String eraseGenericTypeArgs(String fqn) {
    StringBuilder returnSb = new StringBuilder();
    int genericTypeParamsNestLevel = 0;
    for (int i = 0; i < fqn.length(); ++i) {
      char currentChar = fqn.charAt(i);
      if (currentChar == '<') {
        ++genericTypeParamsNestLevel;
      } else if (currentChar == '>') {
        --genericTypeParamsNestLevel;
      } else if (genericTypeParamsNestLevel == 0) {
        returnSb.append(currentChar);
      }
    }
    return returnSb.toString();
  }

  public static String normaliseSrc(String src) {
    String normalisedSrc = src.toLowerCase().trim();
    int indexOfCommentStart;
    while ((indexOfCommentStart = normalisedSrc.indexOf("//")) != -1) {
      int eolIndex = normalisedSrc.indexOf("\n", indexOfCommentStart);
      if (eolIndex == -1 || eolIndex < indexOfCommentStart) {
        System.out.println("DEBUGGING EOLINDEX OUT OF RANGE");
        break;
      } else {
        String commentString = normalisedSrc.substring(indexOfCommentStart, eolIndex);
        normalisedSrc = normalisedSrc.replace(commentString, "");
      }
    }

    normalisedSrc = normalisedSrc.replace("\r", "")
        .replace("\n", "")
        .replace(" ", "");

    return normalisedSrc;
  }
}
