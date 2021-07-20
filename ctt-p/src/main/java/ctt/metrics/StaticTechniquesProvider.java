package ctt.metrics;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.google.common.collect.Table;
import ctt.Configuration;
import ctt.ExceptionHandler;
import ctt.FileSystemHandler;
import ctt.JavaParsingUtils;
import ctt.Logger;
import ctt.MethodParser;
import ctt.RWUtilities;
import ctt.Utilities;
import ctt.types.FunctionalityMethod;
import ctt.types.Method;
import ctt.types.MethodCallExprDepthPair;
import ctt.types.Technique;
import ctt.types.TestMethod;
import ctt.types.scores.clazz.StaticClassScoresMapEntry;
import ctt.types.scores.method.StaticMethodScoresMapEntry;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.commons.text.similarity.LongestCommonSubsequence;
import org.apache.commons.text.similarity.SimilarityScore;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static ctt.Utilities.createIfAbsent;

/**
 * Created by RRGWhite on 15/10/2020
 */
public class StaticTechniquesProvider {
  //private static StaticTechniquesProvider instance;
  private static HashMap<Technique, HashSet<StaticMethodScoresMapEntry>> testToFunctionScores;
  private static HashMap<Technique, HashSet<StaticClassScoresMapEntry>> testClassToTestedClassScores;
  private Configuration config;
  private boolean useScoreCaching;
  private ArrayList<FunctionalityMethod> functionalityList;
  private ArrayList<TestMethod> testList;
  private String projectName;
  private String sourcesPath;
  private String testSourcesPath;
  private String parsedMethodsDirPath;
  private Integer numPairsComputed;
  private Integer numPairsSkipped;
  private Integer lastReportedNumPairsComputed;

  /*public static StaticTechniquesProvider get(Configuration config, boolean useScoreCaching) {
    if (instance == null) {
      instance = new StaticTechniquesProvider(config, useScoreCaching);
    }

    return instance;
  }*/

  public StaticTechniquesProvider(Configuration config, boolean useScoreCaching) {
    this.config = config;
    this.useScoreCaching = useScoreCaching;

    functionalityList = new ArrayList<>();
    testList = new ArrayList<>();

    projectName = config.getProjects().get(0);
    sourcesPath = config.getProjectSrcDirs().get(projectName);
    testSourcesPath = config.getProjectTestSrcDirs().get(projectName);
    parsedMethodsDirPath = config.getCorpusPath() + "/" + projectName + "/parsed-methods";

    numPairsComputed = 0;
    numPairsSkipped = 0;
    lastReportedNumPairsComputed = 0;

    fillFunctionalityAndTestLists();
  }

  private void fillFunctionalityAndTestLists() {
    String existingFunctionalityListFilepath = parsedMethodsDirPath + "/functionality.list";
    File existingFunctionalityList = new File(existingFunctionalityListFilepath);
    if (existingFunctionalityList.exists()) {
      Logger.get().logAndPrintLn("Loading " + projectName + " functions from disk");
      functionalityList =
          (ArrayList<FunctionalityMethod>) FileSystemHandler.deserializeObject(existingFunctionalityList);
    } else {
      //Read all functions for project
      functionalityList = Utilities.readMethodsFromFiles(sourcesPath,
          Configuration.ArtefactType.FUNCTION, true);
      FileSystemHandler.serializeObject(functionalityList, existingFunctionalityList);
    }

    String existingTestListFilepath = parsedMethodsDirPath + "/test.list";
    File existingTestList = new File(existingTestListFilepath);
    if (existingTestList.exists()) {
      Logger.get().logAndPrintLn("Loading " + projectName + " tests from disk");
      testList = (ArrayList<TestMethod>) FileSystemHandler.deserializeObject(existingTestList);
    } else {
      //Read all tests for project
      testList = Utilities.readMethodsFromFiles(testSourcesPath, Configuration.ArtefactType.TEST,
          true);
      FileSystemHandler.serializeObject(testList, existingTestList);
    }

    Logger.get().logAndPrintLn(projectName + " Functionality methods: "
        + functionalityList.size());
    Logger.get().logAndPrintLn(projectName + " Total Test methods: " +
        testList.size());
  }

  public Table<String, String, Map<Technique, Double>> computeMethodLevelScores(
      Table<String, String, Map<Technique, Double>> relevanceTable) {
    testToFunctionScores = new HashMap<>();
    testClassToTestedClassScores = new HashMap<>();

    ExecutorService threadPool = Executors.newFixedThreadPool(16);

    HashSet<Technique> techniques = new HashSet<>();
    techniques.addAll(Arrays.asList(config.getMethodLevelTechniqueList().clone()));
    techniques.addAll(Arrays.asList(config.getClassLevelTechniqueList().clone()));
    for (Technique technique : techniques) {
      if (!technique.toString().toLowerCase().contains("static")) {
        continue;
      }

      if (technique.toString().toLowerCase().contains("class")) {
        testClassToTestedClassScores.put(technique, new HashSet<>());
      } else {
        testToFunctionScores.put(technique, new HashSet<>());
      }
    }

    File existingTestToFunctionScoresFile = new File(config.getProjectBaseDirs().get(projectName) +
        "/cached-scores/t-f-scores.set");
    File existingTestClassToTestedClassScoresFile =
        new File(config.getProjectBaseDirs().get(projectName) +
        "/cached-scores/tc-tc-scores.set");
    if (useScoreCaching
        && existingTestToFunctionScoresFile.exists()
        && existingTestClassToTestedClassScoresFile.exists()) {
      Logger.get().logAndPrintLn("Loading " + projectName +
          " scores from disk");
      testToFunctionScores = (HashMap<Technique, HashSet<StaticMethodScoresMapEntry>>)
          FileSystemHandler.deserializeObject(existingTestToFunctionScoresFile);
      testClassToTestedClassScores = (HashMap<Technique, HashSet<StaticClassScoresMapEntry>>)
          FileSystemHandler.deserializeObject(existingTestClassToTestedClassScoresFile);
    } else {
      Logger.get().logAndPrintLn("Computing static technique scores");
      //Logger.get().startShowingWorkingOutput();
      // generate pairwise comparisons of the method's vs. unit test's name
      long totalComparisons = (long) functionalityList.size() * testList.size();

      // go through all the combinations
      for (TestMethod testMethod : testList) {
        if (testMethod.getName().equals(testMethod.getClassName())) {
          continue;
        }

        threadPool.submit(() -> {
          try {
            for (FunctionalityMethod functionalityMethod : functionalityList) {
              //Filter interface method declarations
              if (!functionalityMethod.getSrc().contains("{")) {
                continue;
              }


              //Test method name formats
              String testFqn = convertFqnFormat(testMethod.getFullyQualifiedMethodName());
              String normalisedTestFqn = testFqn.substring(0,
                      testFqn.lastIndexOf('(')).toLowerCase()
                      .replace("testcase", "")
                      .replace("test", "");
              String normalisedTestName =
                  normalisedTestFqn.substring(normalisedTestFqn.lastIndexOf('.') + 1);

              //Test class name formats
              String testClassFqn = testMethod.getMethodPackage() + "." + testMethod.getClassName();
              String normalisedTestClassFqn = testClassFqn.toLowerCase()
                  .replace("testcase", "")
                  .replace("test", "");
              String normalisedTestClassName =
                  normalisedTestClassFqn.substring(normalisedTestClassFqn.lastIndexOf('.') + 1);

              //Function name formats
              String functionFqn = convertFqnFormat(functionalityMethod.getFullyQualifiedMethodName());
              String normalisedFunctionFqn =
                  functionFqn.substring(0,
                      functionFqn.lastIndexOf('(')).toLowerCase()
                      .replace("testcase", "")
                      .replace("test", "");
              String normalisedFunctionName =
                  normalisedFunctionFqn.substring(normalisedFunctionFqn.lastIndexOf('.') + 1);

              //Tested class name formats
              String testedClassFqn = functionalityMethod.getMethodPackage() + "." +
                  functionalityMethod.getClassName();
              String normalisedTestedClassFqn = testedClassFqn.toLowerCase()
                  .replace("testcase", "")
                  .replace("test", "");
              String normalisedTestedClassName =
                  normalisedTestedClassFqn.substring(normalisedTestedClassFqn.lastIndexOf('.') + 1);

              /*if (testFqn.equals(
              "org.apache.commons.io.serialization.ValidatingObjectInputStreamTest.ourTestClassAcceptedFirstWildcard()")) {
                Logger.get().logAndPrintLn("DEBUGGING MISSING RECALL");
              }*/

              /*if (functionFqn.equals(
                  "org.apache.commons.io.serialization.ValidatingObjectInputStream.accept(String[])")
                  || functionFqn.equals(
                      "org.apache.commons.io.serialization.ValidatingObjectInputStream.accept" +
                          "(String...)")
                  || functionFqn.equals(
                  "org.apache.commons.io.serialization.ValidatingObjectInputStream.accept" +
                      "(String)")) {
                Logger.get().logAndPrintLn("DEBUGGING MISSING RECALL");
              }*/

              /*if (testFqn.equals(
              "org.apache.commons.io.serialization.ValidatingObjectInputStreamTest.ourTestClassAcceptedFirstWildcard()")
              && functionFqn.equals(
              "org.apache.commons.io.serialization.ValidatingObjectInputStream.accept(String[])")) {
                Logger.get().logAndPrintLn("DEBUGGING MISSING RECALL");
              }*/

              double totalMethodScore = 0;
              double totalClassScore = 0;

              //Class NC
              double ncClassScore = normalisedTestClassFqn.equals(normalisedTestedClassFqn) ? 1 : 0;
              totalClassScore += ncClassScore;

              //Method NC
              double ncMethodScore = (normalisedTestFqn.equals(normalisedFunctionFqn)
              && ncClassScore == 1.0) ? 1 : 0;
              totalMethodScore += ncMethodScore;

              //Class NCC
              double nccClassScore =
                  (normalisedTestClassName.contains(normalisedTestedClassName) ? 1 : 0);
              totalClassScore += nccClassScore;

              //Method NCC
              double nccMethodScore = (normalisedTestName.contains(normalisedFunctionName)
              && normalisedTestClassName.contains(normalisedTestedClassName)) ? 1 : 0;
              totalMethodScore += nccMethodScore;

              /*if (nccMethodScore == 1.0) {
                Logger.get().logAndPrintLn("Method NCC of 1.0: " + testFqn + " : " +
                    functionFqn);
              }*/

              //Class LCS-B
              SimilarityScore<Integer> longestCommonSubsequence = new LongestCommonSubsequence();
              int similarityScore = longestCommonSubsequence.apply(normalisedTestClassName,
                  normalisedTestedClassName);
              double lcsBClassScore =
                  (double) similarityScore / Math.max(normalisedTestClassName.length(),
                      normalisedTestedClassName.length());
              totalClassScore += lcsBClassScore;

              //Class LCS-U
              double lcsUClassScore = (double) similarityScore / normalisedTestedClassName.length();
              totalClassScore += lcsUClassScore;

              //Method LCS-B
              longestCommonSubsequence = new LongestCommonSubsequence();
              similarityScore = longestCommonSubsequence.apply(normalisedTestFqn,
                  normalisedFunctionFqn);

              if (similarityScore == 0) {
                Logger.get().logAndPrintLn("DEBUG - THIS SHOULDNT HAPPEN");
              }

              double lcsBMethodScore =
                  (double) similarityScore / Math.max(normalisedTestFqn.length(),
                      normalisedFunctionFqn.length());
              totalMethodScore += lcsBMethodScore;

              if (lcsBMethodScore == 0 || lcsBMethodScore < 0.00001) {
                Logger.get().logAndPrintLn("DEBUG - THIS SHOULDNT HAPPEN");
              }

              //Method LCS-U
              double lcsUMethodScore = (double) similarityScore / normalisedFunctionFqn.length();
              totalMethodScore += lcsUMethodScore;

              //Class Levenshtein
              SimilarityScore<Integer> levenshteinDistance = new LevenshteinDistance();
              int distance = levenshteinDistance.apply(normalisedTestClassFqn,
                  normalisedTestedClassFqn);
              double levenClassScore =
                  1.0 - ((double) distance / Math.max(normalisedTestClassFqn.length(),
                      normalisedTestedClassFqn.length()));
              totalClassScore += levenClassScore;

              //Method Levenshtein
              levenshteinDistance = new LevenshteinDistance();
              distance = levenshteinDistance.apply(normalisedTestFqn, normalisedFunctionFqn);
              double levenMethodScore =1.0 - ((double) distance / Math.max(normalisedTestFqn.length(),
                  normalisedFunctionFqn.length()));
              totalMethodScore += levenMethodScore;

              //Method and Class LCBA
              HashSet<String> lastCalledFunctionNames = getLastCalledFunctionNames(testMethod);
              double lcbaMethodScore =
                  (lastCalledFunctionNames.contains(normalisedFunctionName) ? 1 : 0);
              totalMethodScore += lcbaMethodScore;

              double lcbaClassScore = lcbaMethodScore;
              totalClassScore += lcbaClassScore;

              double totalMethodLevelScoreThreshold = 2.0;
              double totalClassLevelScoreThreshold = 2.0;
              if (totalClassScore < totalClassLevelScoreThreshold
                  && totalMethodScore < totalMethodLevelScoreThreshold) {
                synchronized (numPairsSkipped) {
                  numPairsSkipped++;
                }
                continue;
              }

              if (totalMethodScore >= totalMethodLevelScoreThreshold) {
                /*Logger.get().logAndPrintLn("Method level results:\n" +
                    convertFqnFormat(testMethod.getFullyQualifiedMethodName()) + "\n" +
                    convertFqnFormat(functionalityMethod.getFullyQualifiedMethodName()) + "\n" +
                    Technique.STATIC_NC.toString() + " : " + ncMethodScore + "\n" +
                    Technique.STATIC_NCC.toString() + " : " + nccMethodScore + "\n" +
                    Technique.STATIC_LCS_B_N.toString() + " : " + lcsBMethodScore + "\n" +
                    Technique.STATIC_LCS_U_N.toString() + " : " + lcsUMethodScore + "\n" +
                    Technique.STATIC_LEVENSHTEIN_N.toString() + " : " + levenMethodScore + "\n" +
                    Technique.STATIC_LCBA.toString() + " : " + lcbaMethodScore + "\n\n");*/

                addToTestToFunctionScores(Technique.STATIC_NC,
                    new StaticMethodScoresMapEntry(testMethod, functionalityMethod,
                        ncMethodScore));

                addToTestToFunctionScores(Technique.STATIC_NCC,
                    new StaticMethodScoresMapEntry(testMethod, functionalityMethod, nccMethodScore));

                addToTestToFunctionScores(Technique.STATIC_LCS_B_N,
                    new StaticMethodScoresMapEntry(testMethod, functionalityMethod, lcsBMethodScore));

                addToTestToFunctionScores(Technique.STATIC_LCS_U_N,
                    new StaticMethodScoresMapEntry(testMethod, functionalityMethod, lcsUMethodScore));

                addToTestToFunctionScores(Technique.STATIC_LEVENSHTEIN_N,
                    new StaticMethodScoresMapEntry(testMethod, functionalityMethod, levenMethodScore));

                addToTestToFunctionScores(Technique.STATIC_LCBA,
                    new StaticMethodScoresMapEntry(testMethod, functionalityMethod, lcbaMethodScore));
              }

              if (totalClassScore >= totalClassLevelScoreThreshold) {
                addToTestClassToTestedClassScores(Technique.STATIC_NC_CLASS,
                    new StaticClassScoresMapEntry(testClassFqn, testedClassFqn, ncClassScore));

                addToTestClassToTestedClassScores(Technique.STATIC_NCC_CLASS,
                    new StaticClassScoresMapEntry(testClassFqn, testedClassFqn, nccClassScore));

                addToTestClassToTestedClassScores(Technique.STATIC_LCS_B_N_CLASS,
                    new StaticClassScoresMapEntry(testClassFqn, testedClassFqn, lcsBClassScore));

                addToTestClassToTestedClassScores(Technique.STATIC_LCS_U_N_CLASS,
                    new StaticClassScoresMapEntry(testClassFqn, testedClassFqn, lcsUClassScore));

                addToTestClassToTestedClassScores(Technique.STATIC_LEVENSHTEIN_N_CLASS,
                    new StaticClassScoresMapEntry(testClassFqn, testedClassFqn, levenClassScore));

                addToTestClassToTestedClassScores(Technique.STATIC_LCBA_CLASS,
                    new StaticClassScoresMapEntry(testClassFqn, testedClassFqn, lcbaClassScore));
              }
            }

            synchronized (numPairsComputed) {
              numPairsComputed += functionalityList.size();
              if (numPairsComputed >= lastReportedNumPairsComputed + 1000000) {
                float percentage = ((float) numPairsComputed / (float) totalComparisons) *
                    100.0f;

                Logger.get().logAndPrintLn((double) numPairsComputed / 1000000.0 + " M of "
                    + (double) totalComparisons / 1000000.0 + " M scores computed - " + percentage
                    + "%" + " - " + numPairsSkipped + " pairs completely skipped");
                lastReportedNumPairsComputed = numPairsComputed;
              }
            }
          } catch (Exception e) {
            Logger.get().logAndPrintLn("Thread pool thread encountered exception " +
                e.toString() + "\n");
            e.printStackTrace();
          }
        });
      }

      try {
        threadPool.shutdown();
        if (!threadPool.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS)) {
          Logger.get().logAndPrintLn("Timeout hit while waiting for threadpool termination in "
              + "SimilarityProvider.calculateSimilarityScoresForDir");
        }
      } catch (InterruptedException e) {
        ExceptionHandler.handleCaughtThrowable(e, false);
      }

      Logger.get().logAndPrintLn("Thread pool finished");

      if (useScoreCaching) {
        FileSystemHandler.serializeObject(testToFunctionScores, existingTestToFunctionScoresFile);
        FileSystemHandler.serializeObject(testClassToTestedClassScores,
            existingTestClassToTestedClassScoresFile);
        Logger.get().logAndPrintLn("Static scores cached");
      }
    }

    for (Map.Entry<Technique, HashSet<StaticMethodScoresMapEntry>> entry :
        testToFunctionScores.entrySet()) {
      for (StaticMethodScoresMapEntry mapEntry : entry.getValue()) {
        boolean isConstructor = false;
        if (mapEntry.getFunctionalityMethod().getName().equals(
            mapEntry.getFunctionalityMethod().getClassName())) {
          isConstructor = true;
        }

        addMethodLevelScoreToRelevanceTable(relevanceTable, entry.getKey(), mapEntry.getTestMethod(),
            mapEntry.getFunctionalityMethod(), mapEntry.getScore(), isConstructor);
      }
    }

    return relevanceTable;
  }

  public Table<String, String, Map<Technique, Double>> computeClassLevelScores(Table<String, String,
      Map<Technique, Double>> relevanceTable) {
    for (Map.Entry<Technique, HashSet<StaticClassScoresMapEntry>> entry :
        testClassToTestedClassScores.entrySet()) {
      for (StaticClassScoresMapEntry mapEntry : entry.getValue()) {
        addClassLevelScoreToRelevanceTable(relevanceTable, entry.getKey(),
            mapEntry.getTestClassFqn(), mapEntry.getNonTestClassFqn(), mapEntry.getScore());
      }
    }

    return relevanceTable;
  }

  private HashSet<String> getLastCalledFunctionNames(TestMethod testMethod) {
    HashSet<String> lastCalledFunctionNames = new HashSet<>();
    try {
      MethodDeclaration jpMethod = JavaParser.parseBodyDeclaration(
          testMethod.getSrc()).asMethodDeclaration();

      NodeList<Statement> statements = jpMethod.getBody().get().getStatements();

      if (jpMethod == null) {
        return lastCalledFunctionNames;
      }

      String lastCalledfunctionName = null;
      for (Statement statement : statements) {
        boolean assignNextExpr = false;
        ArrayList<MethodCallExprDepthPair> methodCallExprs =
            getMethodCallExprsFromStatement(statement);
        for (MethodCallExprDepthPair callExprDepthPair : methodCallExprs) {
          String calledFunctionName = callExprDepthPair.getMethodCallExpr().getNameAsString();
          if (assignNextExpr) {
            lastCalledFunctionNames.add(calledFunctionName.toLowerCase());
            break;
          }

          boolean isAssertCall = calledFunctionName.toLowerCase().contains("assert");
          if (isAssertCall
              && methodCallExprs.size() == 1
              && lastCalledfunctionName != null) {
            lastCalledFunctionNames.add(lastCalledfunctionName.toLowerCase());
          } else if (!isAssertCall) {
            lastCalledfunctionName = calledFunctionName;
          } else if (methodCallExprs.size() > 1) {
            assignNextExpr = true;
          }
        }
      }
    } catch (Exception e) {
      /*Logger.get().logAndPrintLn("Caught exception in getLastCalledFunctionNames: \n");
      e.printStackTrace();*/
    }

    return lastCalledFunctionNames;
  }


  private ArrayList<MethodCallExprDepthPair> getMethodCallExprsFromStatement(
      Statement startingStatement) {
    ArrayList<MethodCallExprDepthPair> methodCallExprs = new ArrayList<>();
    int depth = 0;
    Statement statement = startingStatement;
    findMethodCallExprChildren(methodCallExprs, statement, ++depth);
    return methodCallExprs;
  }

  private void findMethodCallExprChildren(ArrayList<MethodCallExprDepthPair> methodCallExprs,
                                          Node node, int depth) {
    if (node == null) {
      return;
    }

    if (node instanceof MethodCallExpr) {
      methodCallExprs.add(new MethodCallExprDepthPair((MethodCallExpr) node, depth));
    }

    List<Node> childNodes = node.getChildNodes();
    for (Node childNode : childNodes) {
      findMethodCallExprChildren(methodCallExprs, childNode, ++depth);
    }
  }

  private synchronized void addToTestToFunctionScores(Technique technique,
                                                      StaticMethodScoresMapEntry entry) {
    if (testToFunctionScores.get(technique) != null) {
      testToFunctionScores.get(technique).add(entry);
    }
  }

  private synchronized void addToTestClassToTestedClassScores(Technique technique,
                                                      StaticClassScoresMapEntry entry) {
    if (testClassToTestedClassScores.get(technique) != null) {
      testClassToTestedClassScores.get(technique).add(entry);
    }
  }

  private synchronized void addMethodLevelScoreToRelevanceTable(
      Table<String, String, Map<Technique, Double>> relevanceTable, Technique technique,
      TestMethod test, FunctionalityMethod method, double score, boolean isConstructor){
    String convertedTestFqn = convertFqnFormat(test.getFullyQualifiedMethodName());
    String convertedFunctionFqn = convertFqnFormat(method.getFullyQualifiedMethodName());

    /*if (convertedFunctionFqn.contains("TaggedIOException")
        && convertedTestFqn.contains("testTaggedIOException")) {
      System.out.println("DEBUGGING NEW CONSTRUCTOR NAME COMPARISON");
    }*/

    /*if (isConstructor) {
      int openParenIdx = convertedFunctionFqn.lastIndexOf('(');
      String functionName = convertedFunctionFqn.substring(convertedFunctionFqn.lastIndexOf('.',
          openParenIdx) + 1, openParenIdx);

      convertedFunctionFqn = convertedFunctionFqn.replace(
          functionName + "(", "<init>(");
    }*/

    /*if (relevanceTable.get(convertedTestFqn, convertedFunctionFqn) != null
        && Utilities.getParamsStringFromFqn(convertedFunctionFqn).split(",").length > 1
        && relevanceTable.get(convertedTestFqn, convertedFunctionFqn).get(Technique.LCS_B_N) != null) {
      Logger.get().logAndPrintLn(
          "Matched multi-parameter method with existing non-static relevance table entry");
    }*/

    Map<Technique, Double> valueMap = createIfAbsent(relevanceTable, convertedTestFqn,
        convertedFunctionFqn, HashMap::new);
    valueMap.put(technique, score);

    /*if (technique.equals(Technique.STATIC_NC) && score == 1.0) {
      Logger.get().logAndPrintLn("Pair has static NC score of 1.0: " + convertedTestFqn +
          " : " + convertedFunctionFqn);
    }*/
  }

  private synchronized void addClassLevelScoreToRelevanceTable(Table<String, String,
      Map<Technique, Double>> relevanceTable,
                                                                  Technique technique,
                                                                  String testClassFqn,
                                                               String nonTestClassFqn,
                                                               double score){
    Map<Technique, Double> valueMap = createIfAbsent(relevanceTable, testClassFqn,
        nonTestClassFqn, HashMap::new);
    valueMap.put(technique, score);
  }

  private String convertFqnFormat(String methodModelFqn) {
    methodModelFqn = JavaParsingUtils.removeJavaDocComment(methodModelFqn);
    RWUtilities.RWMethod rwMethod = RWUtilities.parseRWMethodName(methodModelFqn);
    String convertedFqn = rwMethod.toString().replace(",", ", ");
    return convertedFqn;
    /*Class groundTruthClass = null;
    try {
      groundTruthClass = Class.forName(rwMethod.className);
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      return null;
    }

    Executable trueMethod = RWUtilities.getClassMethod(groundTruthClass, rwMethod.methodName, rwMethod.argTypes);

    if (trueMethod != null) {
      convertedFqn = RWUtilities.getMethodString(trueMethod);
    }

    return convertedFqn;*/
  }

  /*private boolean fqnsMatchCrossFormat(String parsedMethodFqn, String tcTracerFqn) {
    RWUtilities.RWMethod rwMethod = RWUtilities.parseRWMethodName(parsedMethodFqn);
    String convertedParsedMethodFqn = rwMethod.toString();

    String paramsString = tcTracerFqn.substring((tcTracerFqn.indexOf("(") + 1),
        tcTracerFqn.lastIndexOf(")"));
    String[] params = paramsString.split(",");
    String packageStrippedParams = "";
    boolean first = true;
    for (String param : params) {
      String packageStrippedParam = param.substring(param.lastIndexOf(".") + 1);
      if (first) {
        packageStrippedParams = packageStrippedParam;
        first = false;
      } else {
        packageStrippedParams = "," + packageStrippedParams;
      }
    }
    String converted

  }*/

  private boolean methodMatchesTcTracerString(Method method, String tctracerMethodFqn) {
    Logger.get().logAndPrintLn("Trying to match:\n" + method.getFullyQualifiedMethodName() +
    "\n" + tctracerMethodFqn);

    if (tctracerMethodFqn.indexOf('(') == -1) {
      System.out.println("tctracer method fqn has no params: " + tctracerMethodFqn);
      return false;
    }

    String paramsString = tctracerMethodFqn.substring(tctracerMethodFqn.indexOf('('));
    ArrayList<String> paramTypes = new ArrayList<>();
    paramsString = paramsString.replace("(", "").replace(")", "");

    String[] splitTcTracerString = tctracerMethodFqn.substring(0, tctracerMethodFqn.indexOf('('))
        .split("\\.");
    String methodName = splitTcTracerString[splitTcTracerString.length - 1];
    String className = splitTcTracerString[splitTcTracerString.length - 2];

    if (methodName.equals("<init>")) {
      methodName = className;
    }

    boolean methodNamesMatch = method.getName().equals(methodName);
    if (!methodNamesMatch) {
      return false;
    }

    if (className.contains("$")) {
      className = className.split("\\$")[1];
    }

    boolean classNamesMatch = method.getClassName().equals(className);
    if (!classNamesMatch) {
      return false;
    }

    if (!paramsString.isEmpty()) {
      /*while(paramsString.contains("<") && paramsString.contains(">")
          && (paramsString.indexOf("<") < paramsString.indexOf(">"))) {
        String typeParameterString = paramsString.substring(paramsString.indexOf("<"), paramsString.indexOf(">") + 1);
        paramsString = paramsString.replace(typeParameterString, "");
      }*/

      paramsString = MethodParser.eraseGenericTypeArgs(paramsString);

      String[] splitParamsString = paramsString.split(",");
      for (String param : splitParamsString) {
        String[] splitParamString = param.split("\\.");

        String paramType = splitParamString[splitParamString.length - 1];
        if (paramType.contains("$")) {
          paramType = paramType.split("\\$")[1];
        }

        paramTypes.add(paramType.trim());
      }
    }

    if (paramTypes.size() != method.getParams().size()) {
      return false;
    }

    for (int i = 0; i < paramTypes.size(); ++i) {
      String deTypeParameterisedType = MethodParser.eraseGenericTypeArgs(method.getParams().get(i).getType());
      /*while(deTypeParameterisedType.contains("<") && deTypeParameterisedType.contains(">")
            && (deTypeParameterisedType.indexOf("<") < deTypeParameterisedType.indexOf(">"))) {
        String typeParameterString = deTypeParameterisedType.substring(
            deTypeParameterisedType.indexOf("<"), deTypeParameterisedType.indexOf(">") + 1);
        deTypeParameterisedType = deTypeParameterisedType.replace(typeParameterString, "");
      }*/

      if (!paramTypes.get(i).equals(deTypeParameterisedType)) {
        return false;
      }
    }

    Logger.get().logAndPrintLn("Matched:\n" + method.getFullyQualifiedMethodName() +
        "\n" + tctracerMethodFqn);

    return true;
  }

  /*private boolean methodFqnMatchesTcTracerFqn(String methodFqn, String tctracerMethodFqn) {
    Logger.get().logAndPrintLn("Trying to match:\n" + methodFqn +
        "\n" + tctracerMethodFqn);

    if (tctracerMethodFqn.indexOf('(') == -1) {
      System.out.println("tctracer method fqn has no params: " + tctracerMethodFqn);
      return false;
    }

    String paramsString = tctracerMethodFqn.substring(tctracerMethodFqn.indexOf('('));
    ArrayList<String> paramTypes = new ArrayList<>();
    paramsString = paramsString.replace("(", "").replace(")", "");

    String[] splitTcTracerString = tctracerMethodFqn.substring(0, tctracerMethodFqn.indexOf('('))
        .split("\\.");
    String methodName = splitTcTracerString[splitTcTracerString.length - 1];
    String className = splitTcTracerString[splitTcTracerString.length - 2];

    if (methodName.equals("<init>")) {
      methodName = className;
    }

    boolean methodNamesMatch = method.getName().equals(methodName);
    if (!methodNamesMatch) {
      return false;
    }

    if (className.contains("$")) {
      className = className.split("\\$")[1];
    }

    boolean classNamesMatch = method.getClassName().equals(className);
    if (!classNamesMatch) {
      return false;
    }

    if (!paramsString.isEmpty()) {

      paramsString = MethodParser.eraseGenericTypeArgs(paramsString);

      String[] splitParamsString = paramsString.split(",");
      for (String param : splitParamsString) {
        String[] splitParamString = param.split("\\.");

        String paramType = splitParamString[splitParamString.length - 1];
        if (paramType.contains("$")) {
          paramType = paramType.split("\\$")[1];
        }

        paramTypes.add(paramType.trim());
      }
    }

    if (paramTypes.size() != method.getParams().size()) {
      return false;
    }

    for (int i = 0; i < paramTypes.size(); ++i) {
      String deTypeParameterisedType = MethodParser.eraseGenericTypeArgs(method.getParams().get(i).getType());
      if (!paramTypes.get(i).equals(deTypeParameterisedType)) {
        return false;
      }
    }

    Logger.get().logAndPrintLn("Matched:\n" + method.getFullyQualifiedMethodName() +
        "\n" + tctracerMethodFqn);

    return true;
  }*/
}
