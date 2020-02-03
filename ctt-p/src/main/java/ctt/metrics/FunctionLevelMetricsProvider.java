package ctt.metrics;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import ctt.Configuration;
import ctt.Main;
import ctt.ResultsWriter;
import ctt.SpectraParser;
import ctt.Utilities;
import ctt.coverage.CoverageAnalyser;
import ctt.types.ClassLevelMetrics;
import ctt.types.EvaluationMetrics;
import ctt.types.FunctionLevelMetrics;
import ctt.types.HitSpectrum;
import ctt.types.MethodDepthPair;
import ctt.types.scores.method.AugmentedMethodScoresTensor;
import ctt.types.scores.method.MethodScoresTensor;
import ctt.types.MethodValuePair;
import ctt.types.Technique;
import ctt.types.TestCollection;
import ctt.types.scores.method.PureMethodScoresTensor;
import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.skb.interfaces.transformers.textformat.TextAlignment;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.commons.text.similarity.LongestCommonSubsequence;
import org.apache.commons.text.similarity.SimilarityScore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ctt.Utilities.createIfAbsent;

/**
 * Created by RRGWhite on 11/07/2019
 */
public class FunctionLevelMetricsProvider {
  // Whether to use Naming Conventions as ground truth when no ground truth data is available.
  private static final boolean CONFIG_NAMING_CONVENTIONS_AS_GROUND_TRUTH = false;
  private Configuration config;
  // Map of methods to the tests that executed them
  private Map<String, Set<MethodDepthPair>> methodMap = null;

  // Map of tests to the methods that they executed
  private Map<String, Set<MethodDepthPair>> testsMap = null;

  // -- OPTIONAL FIELDS --
  // Coverage data - this is optional! Check for null before using.
  private Table<String, String, Map<CoverageAnalyser.CounterType, CoverageAnalyser.CoverageStat>> coverageData = null;
  // -- END OPTIONAL FIELDS --

  public FunctionLevelMetricsProvider(Configuration config) {
    this.config = config;
  }

  public FunctionLevelMetrics calculateFunctionLevelMetrics(TestCollection testCollection,
                                                            boolean verbose) {
    // Map of method to the tests that execute the method
    methodMap = new HashMap<>();

    // Map of test to the methods that the test executes
    testsMap = new HashMap<>();

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

        String invokedMethodFqn = hitSetEntry.getKey();
        if (Utilities.getClassNameFromFqn(
            Utilities.getClassFqnFromMethodFqn(invokedMethodFqn)).toLowerCase().endsWith("test")) {
          //Utilities.logger.debug("skipping function as its from a test class");
          continue;
        }

        if (invokedMethodFqn.contains("TEST START")) {
          //Utilities.logger.debug("Debugging TEST START function");
          continue;
        }

        int callDepth = hitSetEntry.getValue();
        String testFqn = testHitSpectrum.cls + "." + testHitSpectrum.test;
        Set<MethodDepthPair> testList = methodMap
            .computeIfAbsent(invokedMethodFqn, k -> new HashSet<>());
        testList.add(new MethodDepthPair(testFqn, callDepth));

        Set<MethodDepthPair> methodList = testsMap.computeIfAbsent(testFqn, k -> new HashSet<>());
        methodList.add(new MethodDepthPair(invokedMethodFqn, callDepth));
      }
    }

    if (verbose) {
      System.out.println("Map built");
    }

    // Calculate suspiciousness values using techniques
    // Keys: Test, Method, Map<Technique, Value>
    Table<String, String, Map<Technique, Double>> relevanceTable = computeRelevanceTable(
        testCollection);
    if (verbose) {
      System.out.println("Suspiciousness calculated");
    }

    MethodScoresTensor methodScoresTensor = new PureMethodScoresTensor(config, relevanceTable,
        true);

    /*ResultsWriter.writeOutMethodLevelTraceabilityScores(config, methodScoresTensor,
        Main.ScoreType.PURE);*/

    // Aggregate the results
    // Keys: Test, Technique, Ranked list of method candidates
    Table<String, Technique, SortedSet<MethodValuePair>> aggregatedResults =
        computeAggregatedResults(config, relevanceTable, methodScoresTensor);

    if (verbose) {
      System.out.println("Aggregated results computed");
      // Pretty-print aggregated results table
      printAggregatedResults(Utilities.getTechniques(config, Configuration.Level.METHOD,
          Main.ScoreType.PURE), aggregatedResults, null);
    }

    // Build candidate set
    // Keys: Test, Technique, Candidate set of methods
    Table<String, Technique, SortedSet<MethodValuePair>> candidateTable = buildCandidateSetTable(
        config, aggregatedResults);

    if (verbose) {
      System.out.println("Candidate set computed");
    }

    //ResultsWriter.writeOutMethodLevelTraceabilityPredictions(config, candidateTable);

    Map<String, SortedSet<MethodValuePair>> groundTruthMap =
        candidateTable.column(Technique.GROUND_TRUTH);
    ResultsWriter.writeOutMethodLevelGroundTruth(config, groundTruthMap);

    // Compute evaluation metrics for each test
    // Keys: Technique, Test, Evaluation Metrics (true positives, etc)
    Table<Technique, String, EvaluationMetrics> metricTable = computeEvaluationMetrics(config,
        candidateTable, Main.ScoreType.PURE);

    if (verbose) {
      System.out.println("Evaluation metrics computed");
      System.out.println("======= EVALUATION METRICS =======");
      // Pretty-print evaluation metrics table
      printEvaluationMetrics(Utilities.getTechniques(config, Configuration.Level.METHOD,
          Main.ScoreType.PURE), aggregatedResults, metricTable);
    }

    // Compute evaluation metrics for each technique
    // Keys: Technique, Metric, Value
    Table<Technique, SpectraParser.Metric, Double> techniqueMetrics =
        TechniqueMetricsProvider.computeTechniqueMetrics(metricTable);

    if (verbose) {
      System.out.println("Technique metrics computed");
      // Pretty-print technique metrics
      TechniqueMetricsProvider.printTechniqueMetrics(Utilities.getTechniques(config, Configuration.Level.METHOD,
          Main.ScoreType.PURE), techniqueMetrics);
    }

    ResultsWriter.writeOutMethodLevelTraceabilityPredictions(config, candidateTable,
        Main.ScoreType.PURE);

    return new FunctionLevelMetrics(methodScoresTensor, relevanceTable, aggregatedResults,
        candidateTable,
        metricTable);
  }

  // Returns metrics table
  public FunctionLevelMetrics augmentFunctionLevelMetrics(
      FunctionLevelMetrics functionLevelMetrics, ClassLevelMetrics classLevelMetrics,
      boolean verbose) {

    MethodScoresTensor augmentedMethodScoresTensor = new AugmentedMethodScoresTensor(config,
        functionLevelMetrics.getRelevanceTable(), functionLevelMetrics.getMethodScoresTensor(),
        classLevelMetrics.getClassScoresTensor(), true);

    // Aggregate the results
    // Keys: Test, Technique, Ranked list of method candidates
    Table<String, Technique, SortedSet<MethodValuePair>> aggregatedResults =
        computeAggregatedResults(config, functionLevelMetrics.getRelevanceTable(),
            augmentedMethodScoresTensor);

    if (verbose) {
      System.out.println("Aggregated results computed");
      // Pretty-print aggregated results table
      printAggregatedResults(Utilities.getTechniques(config, Configuration.Level.METHOD,
          Main.ScoreType.AUGMENTED), aggregatedResults, null);
    }

    // Build candidate set
    // Keys: Test, Technique, Candidate set of methods
    Table<String, Technique, SortedSet<MethodValuePair>> candidateTable = buildCandidateSetTable(
        config, aggregatedResults);

    if (verbose) {
      System.out.println("Candidate set computed");
    }

    //ResultsWriter.writeOutMethodLevelTraceabilityPredictions(config, candidateTable);

    // Compute evaluation metrics for each test
    // Keys: Technique, Test, Evaluation Metrics (true positives, etc)
    Table<Technique, String, EvaluationMetrics> metricTable = computeEvaluationMetrics(config,
        candidateTable, Main.ScoreType.AUGMENTED);

    if (verbose) {
      System.out.println("Evaluation metrics computed");
      System.out.println("======= EVALUATION METRICS =======");
      // Pretty-print evaluation metrics table
      printEvaluationMetrics(Utilities.getTechniques(config, Configuration.Level.METHOD,
          Main.ScoreType.AUGMENTED), aggregatedResults, metricTable);
    }

    // Compute evaluation metrics for each technique
    // Keys: Technique, Metric, Value
    Table<Technique, SpectraParser.Metric, Double> techniqueMetrics =
        TechniqueMetricsProvider.computeTechniqueMetrics(metricTable);

    if (verbose) {
      System.out.println("Technique metrics computed");
      // Pretty-print technique metrics
      TechniqueMetricsProvider.printTechniqueMetrics(Utilities.getTechniques(config,
          Configuration.Level.METHOD, Main.ScoreType.AUGMENTED), techniqueMetrics);
    }

    ResultsWriter.writeOutMethodLevelTraceabilityPredictions(config, candidateTable,
        Main.ScoreType.AUGMENTED);

    return new FunctionLevelMetrics(augmentedMethodScoresTensor, null,
        aggregatedResults, candidateTable, metricTable);
  }

  private Table<String, String, Map<Technique, Double>> computeRelevanceTable(
      TestCollection testCollection) {
    Utilities.logger.info("Constructing method level relevance table");
    // Suspiciousness values
    // Keys: Test, Method, Map<Technique, Value>
    Table<String, String, Map<Technique, Double>> relevanceTable = HashBasedTable.create();

    // Method-to-test
    // For each method, calculate suspiciousness value of that method for every test.
    for (Map.Entry<String, Set<MethodDepthPair>> entry : methodMap.entrySet()) {
      String method = entry.getKey();
      Set<MethodDepthPair> testsExecutingMethod = entry.getValue();

      // Suspiciousness values
      double passed = testsExecutingMethod.size() - 1; // number of tests that executed the method
      double totalPassed =
          testCollection.tests.size() - 1; // total number of tests in the test suite

      // For each test that executes the method
      for (MethodDepthPair testDepthPair : testsExecutingMethod) {
        String test = testDepthPair.getMethodName();
        Map<Technique, Double> valueMap = createIfAbsent(relevanceTable, test, method,
            HashMap::new);

        // Tarantula
        double suspiciousness = 1.0 / (1.0 + passed / totalPassed);
        valueMap.put(Technique.FAULT_LOC_TARANTULA,
            computeDiscountedScore(suspiciousness, test, method, testDepthPair.getCallDepth()));
        //System.out.println("Suspiciousness for method " + method + " , test: " + test + " = " + suspiciousness);

        // tf-idf values
        // If we are here, we are looking at a test that this method is executed by. All other cells get a default value of 0 (handles the 'otherwise' case).
        double tf1 = 1.0; // binary
        double tf2 = 1.0 / testsMap.get(test).size();
        double tf3 = Math.log(1.0 + 1.0 / testsMap.get(test)
            .size()); // the more methods this test tests, the lower this value.

        double idf1 = (double) testsExecutingMethod.size() / testCollection.tests.size();
        double idf2 = Math.log((double) testCollection.tests.size() / testsExecutingMethod.size());

        double tfidf_11 = tf1 * idf1;
        double tfidf_12 = tf1 * idf2;
        double tfidf_21 = tf2 * idf1;
        double tfidf_22 = tf2 * idf2;
        double tfidf_31 = tf3 * idf1;
        double tfidf_32 = tf3 * idf2;

        valueMap.put(Technique.IR_TFIDF_11,
            computeDiscountedScore(tfidf_11, test, method, testDepthPair.getCallDepth()));
        valueMap.put(Technique.IR_TFIDF_12,
            computeDiscountedScore(tfidf_12, test, method, testDepthPair.getCallDepth()));
        valueMap.put(Technique.IR_TFIDF_21,
            computeDiscountedScore(tfidf_21, test, method, testDepthPair.getCallDepth()));
        valueMap.put(Technique.IR_TFIDF_22,
            computeDiscountedScore(tfidf_22, test, method, testDepthPair.getCallDepth()));
        valueMap.put(Technique.IR_TFIDF_31,
            computeDiscountedScore(tfidf_31, test, method, testDepthPair.getCallDepth()));
        valueMap.put(Technique.IR_TFIDF_32,
            computeDiscountedScore(tfidf_32, test, method, testDepthPair.getCallDepth()));
      }
    }

    // Test-to-method
    // Name similarity
    // For each test
    for (Map.Entry<String, Set<MethodDepthPair>> entry : testsMap.entrySet()) {
      String test = entry.getKey();
      int idx_test_openParen = test.lastIndexOf('(');
      if (idx_test_openParen == -1) {
        System.out.println("Invalid test name: " + test);
        continue;
      }
      String testName = test
          .substring(test.lastIndexOf('.', idx_test_openParen) + 1, idx_test_openParen)
          .toLowerCase();

      // Remove 'test' from the test name
      if (testName.contains("test")) {
        testName = testName.replace("test", "");
      }

      Set<MethodDepthPair> methodsExecutedByTest = entry.getValue();

      for (MethodDepthPair methodDepthPair : methodsExecutedByTest) {
        String method = methodDepthPair.getMethodName();
        int idx_method_openParen = method.lastIndexOf('(');
        if (idx_method_openParen == -1) {
          //throw new Error("Invalid method name: " + method);
          Utilities.logger.error("SpectraParser#computeRelevanceTable: Invalid method name: " +
              method);
          continue;
        }
        String methodName = method
            .substring(method.lastIndexOf('.', idx_method_openParen) + 1, idx_method_openParen)
            .toLowerCase();
        // Lower distance = more similar

        // Longest Common Subsequence
        SimilarityScore<Integer> longestCommonSubsequence = new LongestCommonSubsequence();
        int similarityScore = longestCommonSubsequence.apply(testName, methodName);
        double score_longestCommonSubsequence =
            (double) similarityScore / Math.max(testName.length(), methodName.length());
        double score_longestCommonSubsequenceFuzzy = (double) similarityScore / methodName.length();

        // logger.info("testName {}, methodName {}, distance = {}, score = {}", testName, methodName, distance, score_longestCommonSubsequence);
        // System.out.printf("Similarity distance between %s and %s is: %d %n", testName, methodName, similarityScore);

        // Levenshtein Distance
        SimilarityScore<Integer> levenshteinDistance = new LevenshteinDistance();
        int distance = levenshteinDistance.apply(testName, methodName);
        double score_levenshtein =
            1.0 - ((double) distance / Math.max(testName.length(), methodName.length()));
        // logger.info("testName {}, methodName {}, distance = {}, score = {}", testName, methodName, distance, score_levenshtein);

        // Test contains method name
        boolean contains = testName.contains(methodName);

        Map<Technique, Double> valueMap = createIfAbsent(relevanceTable, test, method,
            HashMap::new);
        valueMap.put(Technique.NC,
            computeDiscountedScore(testName.equals(methodName) ? 1.0 : 0.0, test, method,
                methodDepthPair.getCallDepth()));
        valueMap.put(Technique.NS_COMMON_SUBSEQ,
            computeDiscountedScore(score_longestCommonSubsequence, test, method,
                methodDepthPair.getCallDepth()));
        valueMap.put(Technique.NS_COMMON_SUBSEQ_N,
            computeDiscountedScore(score_longestCommonSubsequence, test, method,
                methodDepthPair.getCallDepth()));
        valueMap.put(Technique.NS_COMMON_SUBSEQ_FUZ,
            computeDiscountedScore(score_longestCommonSubsequenceFuzzy, test, method,
                methodDepthPair.getCallDepth()));
        valueMap.put(Technique.NS_COMMON_SUBSEQ_FUZ_N,
            computeDiscountedScore(score_longestCommonSubsequenceFuzzy, test, method,
                methodDepthPair.getCallDepth()));
        valueMap.put(Technique.NS_LEVENSHTEIN,
            computeDiscountedScore(score_levenshtein, test, method, methodDepthPair.getCallDepth()));
        valueMap.put(Technique.NS_LEVENSHTEIN_N,
            computeDiscountedScore(score_levenshtein, test, method, methodDepthPair.getCallDepth()));
        valueMap.put(Technique.NS_CONTAINS,
            computeDiscountedScore(contains ? 1.0 : 0.0, test, method, methodDepthPair.getCallDepth()));

        // Coverage
        if (coverageData != null) {
          double coverageScore = CoverageAnalyser.getCoverageScore(coverageData, test, method);
          valueMap.put(Technique.COVERAGE,
              computeDiscountedScore(coverageScore, test, method, methodDepthPair.getCallDepth()));
        }
      }
    }

    for (HitSpectrum testHitSpectrum : testCollection.tests) {
      String testName = testHitSpectrum.cls + "." + testHitSpectrum.test;

      // Ground Truth
      for (String method : testHitSpectrum.groundTruth) {
        Map<Technique, Double> valueMap = createIfAbsent(relevanceTable, testName, method,
            HashMap::new);
        valueMap.put(Technique.GROUND_TRUTH, 1.0);
      }

      // LCBA
      //int numCallsBeforeAssert = testHitSpectrum.callsBeforeAssert.size();
      for (String method : testHitSpectrum.callsBeforeAssert) {
        Map<Technique, Double> valueMap = createIfAbsent(relevanceTable, testName, method,
            HashMap::new);

        // Each method in callsBeforeAssert has 1.0 relevance
        valueMap.put(Technique.LAST_CALL_BEFORE_ASSERT, 1.0);
      }
    }

    // COMBINED
    /*for (Table.Cell<String, String, Map<Technique, Double>> relevanceTableCell :
        relevanceTable.cellSet()) {
      double combinedScore =
          (config.getScoreCombinationMethod().equals(Configuration.ScoreCombinationMethod.PRODUCT))
              ? 1.0 : 0.0;
      Map<Technique, Double> techniqueScores = relevanceTableCell.getValue();
      for (Technique technique : config.getMethodLevelTechniqueList()) {
        if (technique.equals(Technique.COMBINED)) {
          continue;
        }

        double techniqueScore = techniqueScores.get(technique) == null ? 0 : techniqueScores.get(technique);

        switch (config.getScoreCombinationMethod()) {
          case AVERAGE:
          case SUM:
            if (config.isUseWeightedCombination()) {
              combinedScore +=
                  (techniqueScore * config.getMethodLevelTechniqueWeights().get(technique));
            } else {
              combinedScore += techniqueScore;
            }
            break;
          case PRODUCT:
            if (config.isUseWeightedCombination()) {
              if (techniqueScore == 0) {
                combinedScore *= config.getWeightingZeroPenalty();
                continue;
              }

              combinedScore *=
                  (techniqueScore * config.getMethodLevelTechniqueWeights().get(technique));
            } else {
              combinedScore *= techniqueScore;
            }
            break;
        }
      }

      double score = combinedScore;
      if (config.getScoreCombinationMethod().equals(Configuration.ScoreCombinationMethod.AVERAGE)) {
          score = config.getMethodLevelTechniqueList().length == 0 ? 0.0 :
              combinedScore / (double) config.getMethodLevelTechniqueList().length;
      }

      relevanceTableCell.getValue().put(Technique.COMBINED, score);
    }*/

    Utilities.logger.info("Method level relevance table constructed");
    return relevanceTable;
  }

  private static Table<String, Technique, SortedSet<MethodValuePair>> computeAggregatedResults(
      Configuration config, Table<String, String, Map<Technique, Double>> relevanceTable,
      MethodScoresTensor methodScoresTensor) {
    Utilities.logger.info("Constructing method level aggregated results table");
    // Build a table for each technique and each test with a ranked list inside each cell
    // Keys: Test, Technique, Ranked list of method candidates
    Table<String, Technique, SortedSet<MethodValuePair>> aggregatedResults = HashBasedTable
        .create();

    Set<Table.Cell<String, String, Map<Technique, Double>>> cells = relevanceTable.cellSet();
    for (Table.Cell<String, String, Map<Technique, Double>> cell : cells) {
      String testFqn = cell.getRowKey();
      String functionFqn = cell.getColumnKey();
      Map<Technique, Double> value = methodScoresTensor.getAllScoresForTestFunctionPair(testFqn,
          functionFqn);
      //Map<Technique, Double> value = cell.getValue();

      if (value != null) {
        for (Map.Entry<Technique, Double> entry : value.entrySet()) {
          Technique technique = entry.getKey();
          Double score = entry.getValue();

          SortedSet<MethodValuePair> methodSet = createIfAbsent(aggregatedResults, testFqn, technique,
              TreeSet::new);
          methodSet.add(new MethodValuePair(functionFqn, score));
        }

        Double groundTruth = cell.getValue().get(Technique.GROUND_TRUTH);
        if (groundTruth != null) {
          SortedSet<MethodValuePair> methodSet = createIfAbsent(
            aggregatedResults, testFqn, Technique.GROUND_TRUTH, TreeSet::new);
          methodSet.add(new MethodValuePair(functionFqn, groundTruth));
        }
      }
    }

    // Normalize values
    /*boolean normalize = true;
    if (normalize) {
      for (Technique techniqueToNormalize : config.getTechniquesToNormalize()) {
        Map<String, SortedSet<MethodValuePair>> testAndMethodMap = aggregatedResults
            .column(techniqueToNormalize);
        for (SortedSet<MethodValuePair> methods : testAndMethodMap.values()) {
          double maxValue = methods.first().getValue();
          for (MethodValuePair method : methods) {
            double newValue = (maxValue != 0 ? method.getValue() / maxValue : 0);
            method.setValue(newValue);
          }
        }
      }
    }*/

    Utilities.logger.info("Method level aggregated results table constructed");
    return aggregatedResults;
  }

  private static Table<String, Technique, SortedSet<MethodValuePair>> buildCandidateSetTable(
      Configuration config,
      Table<String, Technique, SortedSet<MethodValuePair>> aggregatedResults) {
    Utilities.logger.info("Constructing method traceability predictions");
    // Keys: Test, Technique, Candidate set of methods
    Table<String, Technique, SortedSet<MethodValuePair>> candidateTable = HashBasedTable.create();

    Set<Table.Cell<String, Technique, SortedSet<MethodValuePair>>> aggregatedCells = aggregatedResults
        .cellSet();
    for (Table.Cell<String, Technique, SortedSet<MethodValuePair>> cell : aggregatedCells) {
      String test = cell.getRowKey();
      Technique technique = cell.getColumnKey();
      SortedSet<MethodValuePair> methods = cell.getValue();

      // All cells must be populated even if it was empty in the aggregated results.
      // Empty in aggregated results = empty set.
      SortedSet<MethodValuePair> candidateSet = createIfAbsent(candidateTable, test, technique,
          TreeSet::new);
      if (methods != null) {
        for (MethodValuePair methodValuePair : methods) {
          if (methodValuePair.getValue() >= config.getThresholdData().getOrDefault(technique,
              0.0)) {
            candidateSet.add(methodValuePair);
          }
        }

        /*if (technique.equals(Technique.FAULT_LOC_TARANTULA)) {
          Utilities.logger.debug("DEBUGGING TARANTULA MAP");
        }*/
      }
    }

    Utilities.logger.info("Method traceability predictions constructed");
    return candidateTable;
  }

  // NOTE: This drops entries without ground truth data unless configured to use naming conventions as ground truth.
  private static Table<Technique, String, EvaluationMetrics> computeEvaluationMetrics(
      Configuration config, Table<String, Technique, SortedSet<MethodValuePair>> candidateTable,
      Main.ScoreType scoreType) {
    // Keys: Technique, Test, Evaluation Metrics (true positives, etc)
    Table<Technique, String, EvaluationMetrics> metricTable = HashBasedTable.create();

    //HashMap<String, SortedSet<MethodValuePair>> groundTruthMap = new HashMap<>();

    // For each test, get the ground truth answer, then measure the other techniques against the ground truth answer.
    for (Map.Entry<String, Map<Technique, SortedSet<MethodValuePair>>> testEntry : candidateTable
        .rowMap().entrySet()) {
      String test = testEntry.getKey();

      // Obtain ground truth data
      Map<Technique, SortedSet<MethodValuePair>> techniqueMap = testEntry.getValue();
      SortedSet<MethodValuePair> groundTruthPairSet = techniqueMap.get(Technique.GROUND_TRUTH);
      //groundTruthMap.put(test, groundTruthPairSet);

      if (groundTruthPairSet == null && CONFIG_NAMING_CONVENTIONS_AS_GROUND_TRUTH) {
        // If configured to do so, in the absence of ground truth, use Naming Conventions - Contains candidate set
        groundTruthPairSet = techniqueMap.get(Technique.NS_CONTAINS);
      }

      if (groundTruthPairSet != null) {
        Set<String> groundTruthSet = new HashSet<>();
        for (MethodValuePair methodValuePair : groundTruthPairSet) {
          groundTruthSet.add(methodValuePair.getMethod());
        }

        // For every technique
        for (Technique technique : Utilities.getTechniques(config, Configuration.Level.METHOD,
            scoreType)) {
          SortedSet<MethodValuePair> candidateMethodSet = candidateTable.get(test, technique);
          if (candidateMethodSet == null) {
            candidateMethodSet = new TreeSet<>(); // if no entry in the candidate table for the given test and technique, treat as empty set.
          }

          // System.out.printf("Test %s, technique %s, map size %d \n", test, technique, techniqueMap.size());

          Set<MethodValuePair> truePositiveSet = new HashSet<>(); // present here = relevant & in candidate set

          // Compute the intersection of relevant and retrieved
          int truePositives = 0;
          int falsePositives = 0; // elements in the candidate set that are not in the ground truth
          //double totalPrecision = 0.0; // for calculating average precision
          double avgPrecisionNumerator = 0.0;
          int listIdx = 0;
          for (MethodValuePair methodValuePair : candidateMethodSet) {
            listIdx++;
            if (groundTruthSet.contains(methodValuePair.getMethod())) {
              truePositives++;
              truePositiveSet.add(methodValuePair);
              avgPrecisionNumerator += Utilities.functionLevelPrecisionAtK(candidateMethodSet,
                  groundTruthSet, listIdx);
            } else {
              falsePositives++;
            }
            //totalPrecision += EvaluationMetrics.computePrecision(truePositives, falsePositives);
          }

          double avgPrecisionDenominator = groundTruthSet.size();
          double averagePrecision = (avgPrecisionDenominator == 0) ? 0 :
              avgPrecisionNumerator / avgPrecisionDenominator;

          /*if (technique.equals(Technique.NS_CONTAINS)) {
            Utilities.logger.debug("DEBUGGING MAP");
          }*/

          int falseNegatives = groundTruthPairSet.size()
              - truePositives; // elements in the ground truth set that were not in the candidate set

          // Compute bpref
          double bpref = 0.0;
          double bprefParameter = 0; // 0 or 10
          int numRelevant = groundTruthPairSet.size();
          for (MethodValuePair truePositiveMethod : truePositiveSet) {
            SortedSet<MethodValuePair> retrievedMethodsAboveR = candidateMethodSet
                .headSet(truePositiveMethod); // exclusive of truePositiveMethod
            int retrievedNonRelevantAboveR = 0;
            for (MethodValuePair methodAboveR : retrievedMethodsAboveR) {
              if (!truePositiveSet.contains(methodAboveR)) {
                retrievedNonRelevantAboveR++;
                if (retrievedNonRelevantAboveR >= numRelevant) {
                  break;
                }
              }
            }
            bpref += 1 - ((double) retrievedNonRelevantAboveR / (bprefParameter + numRelevant));
          }
          bpref *= 1.0 / numRelevant;

          metricTable.put(technique, test,
              new EvaluationMetrics(truePositives, falsePositives, falseNegatives, bpref,
                  averagePrecision));
        }
      } else {
        // Most tests won't have explicitly annotated ground truth data. OK to ignore.
        // System.out.printf("No ground truth data for test %s.%n", test);
      }
    }
    //ResultsWriter.writeOutMethodLevelGroundTruth(groundTruthMap);
    return metricTable;
  }

  public static void printAggregatedResults(Technique[] techniqueOrder,
                                            Table<String, Technique, SortedSet<MethodValuePair>> aggregatedResults,
                                            List<String> testFilter) {
    List<Technique> techniqueOrderWithGroundTruth = Stream
        .concat(Stream.of(Technique.GROUND_TRUTH), Arrays.stream(techniqueOrder))
        .collect(Collectors.toList());

    AsciiTable at = new AsciiTable();
    at.addRule();
    at.addRow(Stream
        .concat(Stream.of("Test"), techniqueOrderWithGroundTruth.stream().map(Technique::toString))
        .collect(Collectors.toList()));
    at.addRule();

    for (String test : aggregatedResults.rowKeySet()) {
      // If a test filter is provided, only print entries that match those in the filter
      if (testFilter != null && !testFilter.contains(test)) {
        continue;
      }
      List<String> rowStrings = new ArrayList<>();
      rowStrings.add(test);

      for (Technique technique : techniqueOrderWithGroundTruth) {
        StringBuilder sb = new StringBuilder("");
        SortedSet<MethodValuePair> methods = aggregatedResults.get(test, technique);
        if (methods != null) {
          for (MethodValuePair method : methods) {
            sb.append(String.format("%.4f: %s<br />", method.getValue(), method.getMethod()));
          }
        }
        rowStrings.add(sb.toString());
      }

      at.addRow(rowStrings);
      at.addRule();
    }

    at.setTextAlignment(TextAlignment.LEFT);
    String renderedTable = at.render(50 * techniqueOrder.length);
    System.out.println(renderedTable);
  }

  // Pass null as second argument to display only tests with ground truth
  public static void printEvaluationMetrics(Technique[] techniqueOrder,
                                            Table<String, Technique, SortedSet<MethodValuePair>> aggregatedResults,
                                            Table<Technique, String, EvaluationMetrics> metricTable) {
    AsciiTable at = new AsciiTable();
    at.addRule();
    at.addRow(
        Stream.concat(Stream.of("Test"), Arrays.stream(techniqueOrder).map(Technique::toString))
            .collect(Collectors.toList()));
    at.addRule();

    // Using aggregated results instead of metricTable so that tests without ground truth are also shown (as empty rows)
    Set<String> tests =
        (aggregatedResults != null) ? aggregatedResults.rowKeySet() : metricTable.columnKeySet();

    for (String test : tests) {
      List<String> rowStrings = new ArrayList<>();
      rowStrings.add(test);

      for (Technique technique : techniqueOrder) {
        StringBuilder sb = new StringBuilder("");
        EvaluationMetrics metrics = metricTable.get(technique, test);
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
    String renderedTable = at.render(35 * techniqueOrder.length);
    System.out.println(renderedTable);
  }

  // Table<String, String, Map<CounterType, CoverageStat>> coverageData
  private double computeDiscountedScore(double score, String testName, String methodName,
                                        int callDepth) {
    // Discount by coverage
    if (coverageData != null) {
      double coverageScore = CoverageAnalyser.getCoverageScore(coverageData, testName, methodName);
      // System.out.printf("coverageScore for test %s and method %s is %f %n", testName, methodName, coverageScore);
      score *= coverageScore;
    }

    // Discount by call depth
    double discountedScore = score * Math.pow(config.getCallDepthDiscountFactor(), callDepth);
    return discountedScore;
  }
}
