package ctt.metrics;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import ctt.Configuration;
import ctt.Main;
import ctt.ResultsWriter;
import ctt.Utilities;
import ctt.coverage.CoverageAnalyser;
import ctt.types.ClassDepthPair;
import ctt.types.ClassLevelMetrics;
import ctt.types.scores.clazz.AugmentedClassScoresTensor;
import ctt.types.scores.clazz.ClassScoresTensor;
import ctt.types.scores.clazz.PureClassScoresTensor;
import ctt.types.EvaluationMetrics;
import ctt.types.FunctionLevelMetrics;
import ctt.types.HitSpectrum;
import ctt.types.Technique;
import ctt.types.TestClassToClassTensors;
import ctt.types.TestCollection;
import ctt.types.TestToFunctionScores;
import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.skb.interfaces.transformers.textformat.TextAlignment;
import org.apache.commons.io.FileUtils;
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
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ctt.Utilities.createIfAbsent;

/**
 * Created by RRGWhite on 02/07/2019
 */
public class ClassLevelMetricsProvider {
  private Configuration config;

  // Map of methods to the tests that executed them
  private Map<String, Set<ClassDepthPair>> nonTestClassMap = null;

  // Map of tests to the methods that they executed
  private Map<String, Set<ClassDepthPair>> testClassMap = null;

  // -- OPTIONAL FIELDS --
  // Coverage data - this is optional! Check for null before using.
  private Table<String, String,
      Map<CoverageAnalyser.CounterType, CoverageAnalyser.CoverageStat>> coverageData = null;
  // -- END OPTIONAL FIELDS --

  public ClassLevelMetricsProvider(Configuration config) {
    this.config = config;
  }

  // Returns metrics table
  public ClassLevelMetrics calculateClassLevelMetrics(TestCollection testCollection,
                                                      FunctionLevelMetrics functionLevelMetrics,
                                                      boolean verbose) {

    Table<String, String, Map<Technique, Double>> classLevelRelevanceTable =
        computeRelevanceTable(testCollection);

    PureClassScoresTensor pureClassScoresTensor = new PureClassScoresTensor(config, classLevelRelevanceTable,
        true);

    if (verbose) {
      printClassToClassScores(pureClassScoresTensor);
    }

    /*ResultsWriter.writeOutClassLevelTraceabilityScores(config, pureClassScoresTensor,
        Main.ScoreType.PURE);*/

    Table<Technique, String, List<String>> traceabilityPredictions =
        computeTraceabilityPredictions(pureClassScoresTensor, Main.ScoreType.PURE);

    if (verbose) {
      printPredictedClassToClassLinks(traceabilityPredictions);
    }

    ResultsWriter.writeOutClassLevelTraceabilityPredictions(config, traceabilityPredictions,
        Main.ScoreType.PURE);

    Table<Technique, String, EvaluationMetrics> evaluationMetricsTable =
        computeEvaluationMetrics(traceabilityPredictions, Main.ScoreType.PURE);

    ClassLevelMetrics classLevelMetrics = new ClassLevelMetrics(pureClassScoresTensor,
        evaluationMetricsTable);

    return classLevelMetrics;
  }

  public ClassLevelMetrics augmentClassLevelMetrics(ClassLevelMetrics classLevelMetrics,
                                                    FunctionLevelMetrics functionLevelMetrics,
                                                    boolean verbose) {

    // Keys: test class, non-test Class | Value: test-to-function matrix
    Table<String, String, TestClassToClassTensors> testClassToClassTensors =
        computeTestClassToClassTensors(functionLevelMetrics,
            classLevelMetrics.getClassScoresTensor());

    AugmentedClassScoresTensor augmentedClassScoresTensor = new AugmentedClassScoresTensor(config,
        classLevelMetrics.getClassScoresTensor(), testClassToClassTensors,
        true);

    if (verbose) {
      printClassToClassScores(augmentedClassScoresTensor);
    }

    /*ResultsWriter.writeOutClassLevelTraceabilityScores(config, augmentedClassScoresTensor,
        Main.ScoreType.AUGMENTED);*/

    Table<Technique, String, List<String>> traceabilityPredictions =
        computeTraceabilityPredictions(augmentedClassScoresTensor, Main.ScoreType.AUGMENTED);

    if (verbose) {
      printPredictedClassToClassLinks(traceabilityPredictions);
    }

    ResultsWriter.writeOutClassLevelTraceabilityPredictions(config, traceabilityPredictions,
        Main.ScoreType.AUGMENTED);

    Table<Technique, String, EvaluationMetrics> evaluationMetricsTable =
        computeEvaluationMetrics(traceabilityPredictions, Main.ScoreType.AUGMENTED);

    ClassLevelMetrics augmentedClassLevelMetrics = new ClassLevelMetrics(augmentedClassScoresTensor,
        evaluationMetricsTable);

    return augmentedClassLevelMetrics;
  }

  private void initMaps(TestCollection testCollection) {
    // Map of method to the tests that execute the method
    nonTestClassMap = new HashMap<>();

    // Map of test to the methods that the test executes
    testClassMap = new HashMap<>();

    // Populate map of method-to-invoking-tests and test-to-invoked-methods
    for (HitSpectrum testHitSpectrum : testCollection.tests) {
      for (Map.Entry<String, Integer> hitSetEntry : testHitSpectrum.hitSet.entrySet()) {
        if (testHitSpectrum.cls == null || testHitSpectrum.cls.equals("null")
            || testHitSpectrum.test == null || testHitSpectrum.test.equals("null")
            || hitSetEntry.getKey() == null || hitSetEntry.getKey().equals("null")
            || hitSetEntry.getValue() == null || hitSetEntry.getValue().equals("null")) {
          Utilities.logger.debug("DEUGGING NULL METHODS");
          continue;
        }

        String invokedNonTestClassFqn = Utilities.getClassFqnFromMethodFqn(hitSetEntry.getKey());
        if (Utilities.getClassNameFromFqn(invokedNonTestClassFqn).toLowerCase().endsWith("test")) {
          //Utilities.logger.debug("skipping non test class becuase it is a test class");
          continue;
        }

        int callDepth = hitSetEntry.getValue();
        String testClassFqn = testHitSpectrum.cls;
        Set<ClassDepthPair> testList = nonTestClassMap.computeIfAbsent(invokedNonTestClassFqn,
            k -> new HashSet<>());

        ClassDepthPair existingClassDepthPair = getExistingClassDepthPair(testList, testClassFqn);
        if (existingClassDepthPair == null) {
          testList.add(new ClassDepthPair(testClassFqn, callDepth));
        } else if (existingClassDepthPair.getCallDepth() > callDepth) {
          testList.remove(existingClassDepthPair);
          testList.add(new ClassDepthPair(testClassFqn, callDepth));
        }


        Set<ClassDepthPair> nonTestClassList = testClassMap.computeIfAbsent(testClassFqn,
            k -> new HashSet<>());

        existingClassDepthPair = getExistingClassDepthPair(nonTestClassList,
            invokedNonTestClassFqn);
        if (existingClassDepthPair == null) {
          nonTestClassList.add(new ClassDepthPair(invokedNonTestClassFqn, callDepth));
        } else if (existingClassDepthPair.getCallDepth() > callDepth) {
          nonTestClassList.remove(existingClassDepthPair);
          nonTestClassList.add(new ClassDepthPair(invokedNonTestClassFqn, callDepth));
        }
      }
    }
  }

  private ClassDepthPair getExistingClassDepthPair(Set<ClassDepthPair> classDepthPairs,
                                                   String fqn) {
    for (ClassDepthPair classDepthPair : classDepthPairs) {
      if (classDepthPair.getClassName().equals(fqn)) {
        return classDepthPair;
      }
    }

    return null;
  }

  private Table<String, String, Map<Technique, Double>> computeRelevanceTable(
      TestCollection testCollection) {
    Utilities.logger.info("Constructing class level relevance table");
    initMaps(testCollection);

    // Suspiciousness values
    // Keys: Test, Method, Map<Technique, Value>
    Table<String, String, Map<Technique, Double>> relevanceTable = HashBasedTable.create();

    // Method-to-test
    // For each method, calculate suspiciousness value of that method for every test.
    for (Map.Entry<String, Set<ClassDepthPair>> entry : nonTestClassMap.entrySet()) {
      String nonTestClassFqn = entry.getKey();
      Set<ClassDepthPair> testClassesExecutingNonTestClass = entry.getValue();

      // Suspiciousness values
      double passed = testClassesExecutingNonTestClass.size() - 1; // number of test classes that
      // executed the nonTestClassFqn
      double totalPassed = testClassMap.size() - 1; // total number of tests in the test suite

      // For each test that executes the nonTestClassFqn
      for (ClassDepthPair classDepthPair : testClassesExecutingNonTestClass) {
        String testClassFqn = classDepthPair.getClassName();
        Map<Technique, Double> valueMap = createIfAbsent(relevanceTable, testClassFqn,
            nonTestClassFqn, HashMap::new);

        // Tarantula
        double suspiciousness = 1.0 / (1.0 + passed / totalPassed);
        valueMap.put(Technique.FAULT_LOC_TARANTULA_CLASS,
            computeDiscountedScore(suspiciousness, testClassFqn, nonTestClassFqn,
                classDepthPair.getCallDepth()));
        //System.out.println("Suspiciousness for nonTestClassFqn " + nonTestClassFqn + " , testClassFqn: " + testClassFqn + " = " + suspiciousness);

        // tf-idf values
        // If we are here, we are looking at a testClassFqn that this nonTestClassFqn is executed by. All other cells get a default value of 0 (handles the 'otherwise' case).
        double tf1 = 1.0; // binary
        double tf2 = 1.0 / testClassMap.get(testClassFqn).size();
        double tf3 = Math.log(1.0 + 1.0 / testClassMap.get(testClassFqn)
            .size()); // the more methods this testClassFqn tests, the lower this value.

        double idf1 = (double) testClassesExecutingNonTestClass.size() / testClassMap.size();
        double idf2 = Math.log((double) testClassMap.size() / testClassesExecutingNonTestClass.size());

        double tfidf_11 = tf1 * idf1;
        double tfidf_12 = tf1 * idf2;
        double tfidf_21 = tf2 * idf1;
        double tfidf_22 = tf2 * idf2;
        double tfidf_31 = tf3 * idf1;
        double tfidf_32 = tf3 * idf2;

        valueMap.put(Technique.IR_TFIDF_11_CLASS,
            computeDiscountedScore(tfidf_11, testClassFqn, nonTestClassFqn, classDepthPair.getCallDepth()));
        valueMap.put(Technique.IR_TFIDF_12_CLASS,
            computeDiscountedScore(tfidf_12, testClassFqn, nonTestClassFqn, classDepthPair.getCallDepth()));
        valueMap.put(Technique.IR_TFIDF_21_CLASS,
            computeDiscountedScore(tfidf_21, testClassFqn, nonTestClassFqn, classDepthPair.getCallDepth()));
        valueMap.put(Technique.IR_TFIDF_22_CLASS,
            computeDiscountedScore(tfidf_22, testClassFqn, nonTestClassFqn, classDepthPair.getCallDepth()));
        valueMap.put(Technique.IR_TFIDF_31_CLASS,
            computeDiscountedScore(tfidf_31, testClassFqn, nonTestClassFqn, classDepthPair.getCallDepth()));
        valueMap.put(Technique.IR_TFIDF_32_CLASS,
            computeDiscountedScore(tfidf_32, testClassFqn, nonTestClassFqn, classDepthPair.getCallDepth()));
      }
    }

    // Test-to-method
    // Name similarity
    // For each test
    for (Map.Entry<String, Set<ClassDepthPair>> entry : testClassMap.entrySet()) {
      String testClassFqn = entry.getKey();
      /*int idx_test_openParen = testClassFqn.lastIndexOf('(');
      if (idx_test_openParen == -1) {
        throw new Error("Invalid testClassFqn name: " + testClassFqn);
      }*/
      /*String testClassName = testClassFqn
          .substring(testClassFqn.lastIndexOf('.', idx_test_openParen) + 1, idx_test_openParen)
          .toLowerCase();*/

      String testClassName = Utilities.getClassNameFromFqn(testClassFqn);

      // Remove 'testClassFqn' from the testClassFqn name
      if (testClassName.contains("testClassFqn")) {
        testClassName = testClassName.replace("testClassFqn", "");
      }
      testClassName = testClassName.toLowerCase().replace("test", "");

      Set<ClassDepthPair> methodsExecutedByTest = entry.getValue();

      for (ClassDepthPair classDepthPair : methodsExecutedByTest) {
        String nonTestClassFqn = classDepthPair.getClassName();
        /*int idx_method_openParen = nonTestClassFqn.lastIndexOf('(');
        if (idx_method_openParen == -1) {
          //throw new Error("Invalid nonTestClassFqn name: " + nonTestClassFqn);
          Utilities.logger.error("SpectraParser#computeRelevanceTable: Invalid nonTestClassFqn name: " +
              nonTestClassFqn);
          continue;
        }*/
        /*String nonTestClassName = nonTestClassFqn
            .substring(nonTestClassFqn.lastIndexOf('.', idx_method_openParen) + 1, idx_method_openParen)
            .toLowerCase();*/
        // Lower distance = more similar

        String nonTestClassName = Utilities.getClassNameFromFqn(nonTestClassFqn);
        nonTestClassName = nonTestClassName.toLowerCase().replace("test", "");

        // Longest Common Subsequence
        SimilarityScore<Integer> longestCommonSubsequence = new LongestCommonSubsequence();
        int similarityScore = longestCommonSubsequence.apply(testClassName, nonTestClassName);
        double score_longestCommonSubsequence =
            (double) similarityScore / Math.max(testClassName.length(), nonTestClassName.length());
        double score_longestCommonSubsequenceFuzzy =
            (double) similarityScore / nonTestClassName.length();

        // logger.info("testClassName {}, nonTestClassName {}, distance = {}, score = {}", testClassName, nonTestClassName, distance, score_longestCommonSubsequence);
        // System.out.printf("Similarity distance between %s and %s is: %d %n", testClassName, nonTestClassName, similarityScore);

        // Levenshtein Distance
        SimilarityScore<Integer> levenshteinDistance = new LevenshteinDistance();
        int distance = levenshteinDistance.apply(testClassName, nonTestClassName);
        double score_levenshtein =
            1.0 - ((double) distance / Math.max(testClassName.length(), nonTestClassName.length()));
        // logger.info("testClassName {}, nonTestClassName {}, distance = {}, score = {}", testClassName, nonTestClassName, distance, score_levenshtein);

        // Test contains nonTestClassFqn name
        boolean contains = testClassName.contains(nonTestClassName);

        Map<Technique, Double> valueMap = createIfAbsent(relevanceTable, testClassFqn,
            nonTestClassFqn, HashMap::new);
        /*String ncTestClassName = testClassName.toLowerCase().replace("test", "");
        String ncNonTestClassName = nonTestClassName.toLowerCase();*/
        boolean ncMatchFound = testClassName.equals(nonTestClassName);
        /*if (ncMatchFound) {
          Utilities.logger.debug("Class level NC match");
        }*/
        valueMap.put(Technique.NC_CLASS, computeDiscountedScore(ncMatchFound ? 1.0 : 0.0, testClassFqn,
            nonTestClassFqn,
                classDepthPair.getCallDepth()));
        valueMap.put(Technique.NS_COMMON_SUBSEQ_CLASS,
            computeDiscountedScore(score_longestCommonSubsequence, testClassFqn, nonTestClassFqn,
                classDepthPair.getCallDepth()));
        valueMap.put(Technique.NS_COMMON_SUBSEQ_N_CLASS,
            computeDiscountedScore(score_longestCommonSubsequence, testClassFqn, nonTestClassFqn,
                classDepthPair.getCallDepth()));
        valueMap.put(Technique.NS_COMMON_SUBSEQ_FUZ_CLASS,
            computeDiscountedScore(score_longestCommonSubsequenceFuzzy, testClassFqn,
                nonTestClassFqn, classDepthPair.getCallDepth()));
        valueMap.put(Technique.NS_COMMON_SUBSEQ_FUZ_N_CLASS,
            computeDiscountedScore(score_longestCommonSubsequenceFuzzy, testClassFqn,
                nonTestClassFqn, classDepthPair.getCallDepth()));
        valueMap.put(Technique.NS_LEVENSHTEIN_CLASS,
            computeDiscountedScore(score_levenshtein, testClassFqn, nonTestClassFqn,
                classDepthPair.getCallDepth()));
        valueMap.put(Technique.NS_LEVENSHTEIN_N_CLASS,
            computeDiscountedScore(score_levenshtein, testClassFqn, nonTestClassFqn,
                classDepthPair.getCallDepth()));
        valueMap.put(Technique.NS_CONTAINS_CLASS,
            computeDiscountedScore(contains ? 1.0 : 0.0, testClassFqn, nonTestClassFqn,
                classDepthPair.getCallDepth()));

        // Coverage
        /*if (coverageData != null) {
          double coverageScore = CoverageAnalyser.getCoverageScore(coverageData, testClassFqn, nonTestClassFqn);
          valueMap.put(Technique.COVERAGE,
              computeDiscountedScore(coverageScore, testClassFqn, nonTestClassFqn, classDepthPair.getCallDepth()));
        }*/
      }
    }

    for (HitSpectrum testHitSpectrum : testCollection.tests) {
      String testClassFqn = testHitSpectrum.cls;

      // Ground Truth
      for (String method : testHitSpectrum.groundTruth) {
        String nonTestClassFqn = Utilities.getClassFqnFromMethodFqn(method);
        Map<Technique, Double> valueMap = createIfAbsent(relevanceTable, testClassFqn, nonTestClassFqn,
            HashMap::new);
        valueMap.put(Technique.GROUND_TRUTH_CLASS, 1.0);
      }

      // LCBA
      //int numCallsBeforeAssert = testHitSpectrum.callsBeforeAssert.size();
      for (String method : testHitSpectrum.callsBeforeAssert) {
        String nonTestClassFqn = Utilities.getClassFqnFromMethodFqn(method);
        Map<Technique, Double> valueMap = createIfAbsent(relevanceTable, testClassFqn, nonTestClassFqn,
            HashMap::new);

        // Each method in callsBeforeAssert has 1.0 relevance
        valueMap.put(Technique.LAST_CALL_BEFORE_ASSERT_CLASS, 1.0);
      }
    }

    Utilities.logger.info("Class level relevance table constructed");
    return relevanceTable;
  }

  private Table<String, String, TestClassToClassTensors> computeTestClassToClassTensors(
      FunctionLevelMetrics functionLevelMetrics,
      ClassScoresTensor pureClassScoresTensor) {
    Utilities.logger.info("Constructing test class to class tensors");

    Table<String, String, ArrayList<TestToFunctionScores>> allTestToFunctionScores =
        HashBasedTable.create();

    for (Cell<String, String, Map<Technique, Double>> cell :
        functionLevelMetrics.getRelevanceTable().cellSet()) {
      String testFqn = cell.getRowKey();
      String testClassFqn = Utilities.getClassFqnFromMethodFqn(cell.getRowKey());
      String functionFqn = cell.getColumnKey();
      String nonTestClassFqn = Utilities.getClassFqnFromMethodFqn(cell.getColumnKey());

      if (testFqn == null || testFqn.isEmpty() || testFqn.equals("null")
          || testClassFqn == null || testClassFqn.isEmpty() || testClassFqn.equals("null")
          || functionFqn == null || functionFqn.isEmpty() || functionFqn.equals("null")
          || nonTestClassFqn == null || nonTestClassFqn.isEmpty() || nonTestClassFqn.equals("null")) {
        /*Utilities.logger.debug("NULL OR EMPTY testFqn, testClassFq, functionFqn, or " +
            "nonTestClassFqn");*/
        continue;
      }

      ArrayList<TestToFunctionScores> testToFunctionScores = allTestToFunctionScores.get(
          testClassFqn, nonTestClassFqn);
      if (testToFunctionScores == null) {
        testToFunctionScores = new ArrayList<>();
        allTestToFunctionScores.put(testClassFqn, nonTestClassFqn, testToFunctionScores);
      }

      allTestToFunctionScores.get(testClassFqn, nonTestClassFqn).add(
          new TestToFunctionScores(testFqn, functionFqn,
              functionLevelMetrics.getMethodScoresTensor().getAllScoresForTestFunctionPair(
                  testFqn, functionFqn)));
    }

    Table<String, String, TestClassToClassTensors> testClassToClassTensors =
        HashBasedTable.create();

    for (Cell<String, String, ArrayList<TestToFunctionScores>> cell :
        allTestToFunctionScores.cellSet()) {
      if (cell.getRowKey() == null || cell.getColumnKey() == null) {
        System.out.println("DEBUGGING NULLS IN testClassToClassTensors");
      }

      testClassToClassTensors.put(cell.getRowKey(), cell.getColumnKey(),
          new TestClassToClassTensors(config, cell.getRowKey(), cell.getColumnKey(),
              cell.getValue(),
              pureClassScoresTensor.getAllScoresForTestClassNonTestClassPair(cell.getRowKey(),
                  cell.getColumnKey())));
    }

    Utilities.logger.info("Test class to class tensors constructed");
    return testClassToClassTensors;
  }

  private Table<Technique, String, EvaluationMetrics> computeEvaluationMetrics(
      Table<Technique, String, List<String>> traceabilityPredictions, Main.ScoreType scoreType) {
    Table<Technique, String, EvaluationMetrics> evaluationMetrics = HashBasedTable.create();
    Map<String, List<String>> oracleLinks = new HashMap<>();
    for (String project : config.getProjects()) {
      oracleLinks.putAll(getOracleLinksForProject(project));
    }

    for (Cell<Technique, String, List<String>> cell : traceabilityPredictions.cellSet()) {
      Technique technique = cell.getRowKey();
      String testClassFqn = cell.getColumnKey();
      List<String> predictedTestedClasses = cell.getValue();
      String oracleFormatTestClassName = Utilities.getClassNameFromFqn(testClassFqn).toLowerCase();
      List<String> oracleTestedClasses = oracleLinks.get(oracleFormatTestClassName);
      if (oracleTestedClasses == null || oracleTestedClasses.size() == 0) {
        continue;
      }

      /*if (testClassFqn.equals("org.apache.commons.io.input.XmlStreamReaderUtilitiesTest")) {
        Utilities.logger.debug("Debugging negative number of false negatives");
      }*/

      ArrayList<String> matchedClasses = new ArrayList<>();

      int truePositives = 0;
      int falsePositives = 0; // elements in the candidate set that are not in the ground truth
      //double totalPrecision = 0.0; // for calculating average precision
      double avgPrecisionNumerator = 0.0;
      int listIdx = 0;
      for (String predictedTestedClass : predictedTestedClasses) {
        listIdx++;
        String oracleFormatPredictedTestedClass = Utilities.getClassNameFromFqn(
            predictedTestedClass).toLowerCase();
        if (oracleTestedClasses.contains(oracleFormatPredictedTestedClass)
            && !matchedClasses.contains(oracleFormatPredictedTestedClass)) {
          truePositives++;
          avgPrecisionNumerator += Utilities.classLevelPrecisionAtK(predictedTestedClasses,
              oracleTestedClasses, listIdx);
          matchedClasses.add(oracleFormatPredictedTestedClass);
        } else {
          if (technique.equals(Technique.NC_CLASS)) {
            Utilities.logger.debug("Debugging an NC false positive");
          }
          falsePositives++;
        }
        //totalPrecision += EvaluationMetrics.computePrecision(truePositives, falsePositives);
      }

      double avgPrecisionDenominator = Math.max(oracleTestedClasses.size(), 0);
      double averagePrecision = (avgPrecisionDenominator == 0) ? 0 :
          avgPrecisionNumerator / avgPrecisionDenominator;

      int falseNegatives = oracleTestedClasses.size() - truePositives;
      if (falseNegatives < 0) {
        Utilities.logger.debug("Debugging negative number of false negatives");
      }

      //WHAT IS BPREF?
      double bpref = 0.0;

      evaluationMetrics.put(technique, testClassFqn, new EvaluationMetrics(truePositives,
          falsePositives, falseNegatives, bpref, averagePrecision));
    }

    /*if (Utilities.allProjectsHaveClassLevelEvaluation(config)) {
      printEvaluationMetrics(config, evaluationMetrics, scoreType);
    }*/

    return evaluationMetrics;
  }

  // Table<String, String, Map<CounterType, CoverageStat>> coverageData
  private double computeDiscountedScore(double score, String testClassFqn, String nonTestClassFqn,
                                        int callDepth) {
    // Discount by coverage
    if (coverageData != null) {
      double coverageScore = CoverageAnalyser.getCoverageScore(coverageData, testClassFqn, nonTestClassFqn);
      // System.out.printf("coverageScore for test %s and method %s is %f %n", testClassFqn, nonTestClassFqn, coverageScore);
      score *= coverageScore;
    }

    // Discount by call depth
    double discountedScore = score * Math.pow(config.getCallDepthDiscountFactor(), callDepth);
    return discountedScore;
  }

  private void printClassToClassScores(ClassScoresTensor classScoresTensor) {
    Utilities.logger.info("Class-level traceability scores\n");
    for (Technique technique : classScoresTensor.getTechniques()) {
      Utilities.logger.info(technique + "\n");
      for (String testClassFqn : classScoresTensor.getTestClassFqns()) {
        for (String nonTestClassFqn : classScoresTensor.getNonTestClassFqns()) {
          Utilities.logger.info(testClassFqn + "\t:\t" + nonTestClassFqn + "\t=\t" +
              classScoresTensor.getSingleScoreForTestClassNonTestClassPair(testClassFqn,
                  nonTestClassFqn, technique));
        }
      }
      Utilities.logger.info("\n");
    }
  }

  private Table<Technique, String, List<String>> computeTraceabilityPredictions(
      ClassScoresTensor classScoresTensor, Main.ScoreType scoreType) {
    Utilities.logger.info("Constructing class traceability predictions");
    Table<Technique, String, List<String>> predictions = HashBasedTable.create();

    Technique[] techniques = Utilities.getTechniques(config, Configuration.Level.CLASS, scoreType);

    for (Technique technique : techniques) {
      Double threshold = config.getThresholdData().get(technique);

      //Using a percentile threshold for combined class
      /*if (technique.equals(Technique.COMBINED_CLASS)) {
        threshold = classScoresTensor.getPercentileValueForTechnique(0.99,
            Technique.COMBINED_CLASS);
      }*/

      for (String testClassFqn : classScoresTensor.getTestClassFqns()) {
        if (testClassFqn != null && technique != null) {
          ArrayList<String> predictionsForClass = new ArrayList<>();
          if (threshold == null) {
            String predictedClass = getOneToOneTraceabilityPredictionForClass(
                testClassFqn, classScoresTensor.getScoresForTestClassForTechnique(testClassFqn,
                    technique));
            if (!predictedClass.equalsIgnoreCase("none")) {
              predictionsForClass.add(predictedClass);
            }
          } else {
            predictionsForClass.addAll(getOneToManyTraceabilityPredictionForClass(
                testClassFqn, classScoresTensor.getScoresForTestClassForTechnique(testClassFqn,
                    technique),
                config.getThresholdData().get(technique)));
          }

          predictions.put(technique, testClassFqn, predictionsForClass);
        }
      }
    }

    printPredictionsForGrouthTruth(predictions);

    Utilities.logger.info("Class traceability predictions constructed");
    return predictions;
  }

  private void printPredictionsForGrouthTruth(Table<Technique, String, List<String>> predictions) {
    Map<String, List<String>> combinedPredictions = predictions.row(Technique.COMBINED_CLASS);
    if (config.getTestClassesForGroundTruth() == null || combinedPredictions.size() == 0) {
      return;
    }

    for (String groundTruthTestClass : config.getTestClassesForGroundTruth()) {
      List<String> groundTruthClassPredictions = combinedPredictions.get(groundTruthTestClass);
      if (groundTruthClassPredictions == null) {
        groundTruthClassPredictions = new ArrayList<>();
      }

      System.out.println(groundTruthTestClass + "," + groundTruthClassPredictions);
    }
  }

  private String getOneToOneTraceabilityPredictionForClass(String testClass, Map<String, Double> classScores) {
    double bestScore = 0;
    String bestPrediction = "none";

    for (Entry<String, Double> entry : classScores.entrySet()) {
      if (entry.getValue() > bestScore && !entry.getKey().equalsIgnoreCase(testClass)) {
        bestScore = entry.getValue();
        bestPrediction = entry.getKey();
      }
    }

    return bestPrediction;
  }

  private ArrayList<String> getOneToManyTraceabilityPredictionForClass(
      String testClass, Map<String, Double> classScores, double threshold) {
    ArrayList<String> predictions = new ArrayList<>();

    for (Entry<String, Double> entry : classScores.entrySet()) {
      if (entry.getValue() >= threshold && !entry.getKey().equalsIgnoreCase(testClass)) {
        predictions.add(entry.getKey());
      }
    }

    if (predictions.size() == 0) {
      String predictedClass = getOneToOneTraceabilityPredictionForClass(testClass, classScores);
      if (!predictedClass.equalsIgnoreCase("none")) {
        predictions.add(predictedClass);
      }
    }

    return predictions;
  }

  private void printPredictedClassToClassLinks(
      Table<Technique, String, List<String>> traceabilityPredictions) {
    Utilities.logger.info("Class-level traceability predictions:\n");
    for (Entry<Technique, Map<String, List<String>>> row :
        traceabilityPredictions.rowMap().entrySet()) {
      Technique technique = row.getKey();
      Utilities.logger.info(technique + "\n");
      for (Entry<String, List<String>> cell : row.getValue().entrySet()) {
        for (String testedClass : cell.getValue()) {
          if (testedClass != null) {
            Utilities.logger.info(cell.getKey() + "\t:\t" + testedClass);
          }
        }
      }
      Utilities.logger.info("\n");
    }
  }

  // Pass null as second argument to display only tests with ground truth
  public static void printEvaluationMetrics(Configuration config,
                                            Table<Technique, String, EvaluationMetrics> evaluationMetricsTable,
                                            Main.ScoreType scoreType) {
    Technique[] techniquesArr = Utilities.getTechniques(config, Configuration.Level.CLASS, scoreType);

    AsciiTable at = new AsciiTable();
    at.addRule();
    at.addRow(
        Stream.concat(Stream.of("Test Class"),
            Arrays.stream(techniquesArr).map(Technique::toString))
            .collect(Collectors.toList()));
    at.addRule();

    // Using aggregated results instead of metricTable so that classes without ground truth are also shown (as empty rows)
    Set<String> classes = evaluationMetricsTable.columnKeySet();

    for (String clazz : classes) {
      List<String> rowStrings = new ArrayList<>();
      rowStrings.add(clazz);

      for (Technique technique : techniquesArr) {
        StringBuilder sb = new StringBuilder("");
        EvaluationMetrics metrics = evaluationMetricsTable.get(technique, clazz);
        if (metrics != null) {
          sb.append(String.format("True Positives: %d/%d<br />", metrics.truePositives,
              metrics.truePositives + metrics.falseNegatives));
          sb.append(String.format("False Positives: %d<br />", metrics.falsePositives));
          sb.append(String.format("False Negatives: %d<br />", metrics.falseNegatives));
          sb.append(String.format("Precision: %f<br />", metrics.getPrecision()));
          sb.append(String.format("Recall: %f<br />", metrics.getRecall()));
          sb.append(String.format("F-Score: %f<br />", metrics.getFScore()));
        }
        rowStrings.add(sb.toString());
      }

      at.addRow(rowStrings);
      at.addRule();
    }

    at.setTextAlignment(TextAlignment.LEFT);
    String renderedTable = at.render(35 * techniquesArr.length);
    System.out.println(renderedTable);
  }

  private Map<String, List<String>> extractDeveloperLinks(String project) {
    Map<String, List<String>> developerLinks = new HashMap<>();

    String baseDir;
    if (config.getProjects().get(0).equals("jfreechart")) {
      baseDir = config.getProjectBaseDirs().get(project) + "/tests";
    } else {
      baseDir = config.getProjectBaseDirs().get(project) + "/src/test";
    }

    String[] extensions = { "java" };
    List<File> listOfFiles = (List<File>) FileUtils.listFiles(new File(baseDir), extensions, true);

    for (File file : listOfFiles) {
      String testClassName = file.getName().replace(".java", "");
      try {
        for (String line : Utilities.readLinesFromFile(file.getPath())) {
          if (line.contains("@link") && !line.contains("#")) {
            if (developerLinks.get(testClassName) == null) {
              developerLinks.put(testClassName, new ArrayList<>());
            }

            String testedClassFqn = line.split("}")[0].split("@link")[1].trim();
            if (!testedClassFqn.endsWith("Test")
                && !developerLinks.get(testClassName).contains(testedClassFqn)) {
              developerLinks.get(testClassName).add(testedClassFqn);
            }
          }
        }
      } catch (Exception e) {
        Utilities.logger.debug("Error parsing file in OrganInserter.findTargetTestClass(): "
            + file);
      }
    }

    System.out.println("Developer provided oracle links for " + project);
    for (Entry<String, List<String>> entry : developerLinks.entrySet()) {
      for (String testedClass : entry.getValue()) {
        System.out.println(Utilities.getClassNameFromFqn(entry.getKey()).toLowerCase() + "," +
            Utilities.getClassNameFromFqn(testedClass).toLowerCase());
      }
    }

    return developerLinks;
  }

  private Map<String, List<String>> getOracleLinksForProject(String project) {
    Map<String, List<String>> oracleLinks = new HashMap<>();
    String oracleFilePath = getOracleLinksFilePathForProject(project);
    ArrayList<String> oracleFileLines = Utilities.readLinesFromFile(oracleFilePath);
    boolean firstLine = true;
    for (String csvLine : oracleFileLines) {
      if (firstLine) {
        firstLine = false;
        continue;
      }

      String[] splitCsvLine = csvLine.split(",");
      String testClassName = splitCsvLine[1];
      String testedClassName = splitCsvLine[2];

      if (testClassName.contains(".")) {
        testClassName = Utilities.getClassNameFromFqn(testClassName).toLowerCase();
      }

      if (testedClassName.contains(".")) {
        testedClassName = Utilities.getClassNameFromFqn(testedClassName).toLowerCase();
      }

      List<String> testedClassesForTestClass = oracleLinks.get(testClassName);
      if (testedClassesForTestClass == null) {
        testedClassesForTestClass = new ArrayList<>();
      }
      testedClassesForTestClass.add(testedClassName);
      oracleLinks.put(testClassName, testedClassesForTestClass);
    }

    if (oracleLinks.isEmpty() && config.isAutoExtractDeveloperLinks()) {
      oracleLinks = extractDeveloperLinks(project);
    }

    return oracleLinks;
  }

  private String getOracleLinksFilePathForProject(String project) {
    String oracleFilePath = config.getProjectBaseDirs().get(project) + "/" + project + "-oracle"
        + "-class-links.csv";
    return oracleFilePath;
  }
}
