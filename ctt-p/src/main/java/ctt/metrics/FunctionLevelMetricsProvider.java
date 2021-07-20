package ctt.metrics;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import ctt.Configuration;
import ctt.Logger;
import ctt.Main;
import ctt.ResultsWriter;
import ctt.SpectraParser;
import ctt.Utilities;
import ctt.coverage.CoverageAnalyser;
import ctt.ml.MLConnector;
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
import net.ericaro.neoitertools.generators.primitives.IntegerGenerator;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.commons.text.similarity.LongestCommonSubsequence;
import org.apache.commons.text.similarity.SimilarityScore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
            || hitSetEntry.getValue() == null || hitSetEntry.getValue().equals("null")
            || hitSetEntry.getKey().equals(">>> TEST START <<< |")) {
          Utilities.logger.debug("DEUGGING NULL METHODS:\n" + hitSetEntry.getKey() + ":" + hitSetEntry.getValue());
          continue;
        }

        String invokedMethodFqn = Utilities.removePackagesFromFqnParamTypes(hitSetEntry.getKey());
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

    if (config.isRunCombinedScoreOptimisationExperiment()) {
      runCombinedScoreOptimisationExperiment(relevanceTable, true);
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

    //@TODO: Use aggregatedResults to calculate Borda Count here
    Map<String, String> bordaCountWinnersTable = computeBordaCountWinners(
        config, aggregatedResults);

    // Build candidate set
    // Keys: Test, Technique, Candidate set of methods
    Table<String, Technique, SortedSet<MethodValuePair>> candidateTable = buildCandidateSetTable(
        config, aggregatedResults);

    ResultsWriter.writeOutMethodLevelTraceabilityPredictions(config, candidateTable,
        Main.ScoreType.PURE);
    ResultsWriter.writeOutCombinedFalsePositives(config, methodScoresTensor, Main.ScoreType.PURE,
        candidateTable);

    if (verbose) {
      System.out.println("Candidate set computed");
    }

    Map<String, SortedSet<MethodValuePair>> groundTruthMap = candidateTable.column(
        Technique.GROUND_TRUTH);
    ResultsWriter.writeOutMethodLevelGroundTruth(config, groundTruthMap);
    ResultsWriter.writeOutMethodLevelGroundTruthScores(config, methodScoresTensor,
        Main.ScoreType.PURE, groundTruthMap);

    // Compute evaluation metrics for each test
    // Keys: Technique, Test, Evaluation Metrics (true positives, etc)
    Table<Technique, String, EvaluationMetrics> metricTable = computeEvaluationMetrics(config,
        candidateTable, Main.ScoreType.PURE);

    EvaluationMetrics bordaCountEvaluationMetrics = computeBordaCountEvaluationMetrics(config,
        candidateTable, bordaCountWinnersTable, Main.ScoreType.PURE);

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

    return new FunctionLevelMetrics(methodScoresTensor, relevanceTable, aggregatedResults,
        candidateTable,
        metricTable);
  }

  // Returns metrics table
  public FunctionLevelMetrics augmentFunctionLevelMetrics(FunctionLevelMetrics functionLevelMetrics,
                                                          ClassLevelMetrics classLevelMetrics,
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
      if (method.contains("<init>")) {
        method = Utilities.replaceInitWithConstructorName(method);
      }

      Set<MethodDepthPair> testsExecutingMethod = entry.getValue();

      // Suspiciousness values
      double passed = testsExecutingMethod.size() - 1; // number of tests that executed the method
      double totalPassed =
          testCollection.tests.size() - 1; // total number of tests in the test suite

      // For each test that executes the method
      for (MethodDepthPair testDepthPair : testsExecutingMethod) {
        String test = testDepthPair.getMethodName();

        Map<Technique, Double> valueMap = createIfAbsent(relevanceTable,
            Utilities.removePackagesFromFqnParamTypes(test),
            Utilities.removePackagesFromFqnParamTypes(method),
            HashMap::new);

        // Tarantula
        double suspiciousness = 1.0 / (1.0 + passed / totalPassed);
        valueMap.put(Technique.TARANTULA,
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
        double tfidf = tf3 * idf2;

        valueMap.put(Technique.TFIDF,
            computeDiscountedScore(tfidf, test, method, testDepthPair.getCallDepth()));
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
        testName = testName
            .replace("testcase", "")
            .replace("test", "");
      }

      Set<MethodDepthPair> methodsExecutedByTest = entry.getValue();

      for (MethodDepthPair methodDepthPair : methodsExecutedByTest) {
        String method = methodDepthPair.getMethodName();
        if (method.contains("<init>")) {
          method = Utilities.replaceInitWithConstructorName(method);
        }

        /*if (method.contains("SegmentedTimeline") && test.contains("testMs2SegmentedTimeline")) {
          System.out.println("DEBUGGING NEW CONSTRUCTOR NAME COMPARISON");
        }*/

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

        Map<Technique, Double> valueMap = createIfAbsent(relevanceTable,
            Utilities.removePackagesFromFqnParamTypes(test),
            Utilities.removePackagesFromFqnParamTypes(method),
            HashMap::new);
        valueMap.put(Technique.NC,
            computeDiscountedScore(testName.equals(methodName) ? 1.0 : 0.0, test, method,
                methodDepthPair.getCallDepth()));
        valueMap.put(Technique.LCS_B_N,
            computeDiscountedScore(score_longestCommonSubsequence, test, method,
                methodDepthPair.getCallDepth()));
        valueMap.put(Technique.LCS_U_N,
            computeDiscountedScore(score_longestCommonSubsequenceFuzzy, test, method,
                methodDepthPair.getCallDepth()));
        valueMap.put(Technique.LEVENSHTEIN_N,
            computeDiscountedScore(score_levenshtein, test, method, methodDepthPair.getCallDepth()));
        valueMap.put(Technique.NCC,
            computeDiscountedScore(contains ? 1.0 : 0.0, test, method, methodDepthPair.getCallDepth()));
      }
    }

    for (HitSpectrum testHitSpectrum : testCollection.tests) {
      String testNameFqn = testHitSpectrum.cls + "." + testHitSpectrum.test;

      // Ground Truth
      for (String methodFqn : testHitSpectrum.groundTruth) {
        if (methodFqn.contains("<init>")) {
          methodFqn = Utilities.replaceInitWithConstructorName(methodFqn);
        }

        String normalisedMethodFqn = Utilities.removePackagesFromFqnParamTypes(methodFqn);

        Map<Technique, Double> valueMap = createIfAbsent(relevanceTable, testNameFqn,
            normalisedMethodFqn, HashMap::new);
        valueMap.put(Technique.GROUND_TRUTH, 1.0);
      }

      // LCBA
      for (String methodFqn : testHitSpectrum.callsBeforeAssert) {
        if (methodFqn.contains("<init>")) {
          methodFqn = Utilities.replaceInitWithConstructorName(methodFqn);
        }

        String normalisedMethodFqn = Utilities.removePackagesFromFqnParamTypes(methodFqn);

        Map<Technique, Double> valueMap = createIfAbsent(relevanceTable, testNameFqn,
            normalisedMethodFqn, HashMap::new);

        // Each methodFqn in callsBeforeAssert has 1.0 relevance
        valueMap.put(Technique.LCBA, 1.0);
      }
    }

    StaticTechniquesProvider staticTechniquesProvider = new StaticTechniquesProvider(config, true);
    relevanceTable =
        staticTechniquesProvider.computeMethodLevelScores(relevanceTable);

    if (Arrays.asList(config.getMethodLevelTechniqueList()).contains(Technique.COMBINED_FFN)) {
      MLConnector mlConnector = new MLConnector(config, relevanceTable, Configuration.Level.METHOD);
      if (!mlConnector.isMethodLevelModelTrained()) {
        mlConnector.train();
      }

      if (mlConnector.isMethodLevelModelTrained()) {
        relevanceTable = mlConnector.addFFNScores();
      }
    }

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
          methodSet.add(new MethodValuePair(Utilities.removePackagesFromFqnParamTypes(functionFqn),
              groundTruth));
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

  public static Map<String, String> computeBordaCountWinners(
      Configuration config,
      Table<String, Technique, SortedSet<MethodValuePair>> aggregatedResults) {
    Utilities.logger.info("Computing borda count winners");
    // Keys: Test, Technique, Candidate set of methods
    Map<String, String> winnersMap = new HashMap<>();

    /*Set<Table.Cell<String, Technique, SortedSet<MethodValuePair>>> aggregatedCells =
        aggregatedResults.cellSet();*/

    for (Map.Entry<String, Map<Technique, SortedSet<MethodValuePair>>> entry :
        aggregatedResults.rowMap().entrySet()) {
      String test = entry.getKey();
      Map<String, Integer> methodScores = new LinkedHashMap<>();
      for (Map.Entry<Technique, SortedSet<MethodValuePair>> techniqueEntry :
          entry.getValue().entrySet()) {
        Technique technique = techniqueEntry.getKey();

        //Skip binary scored techniques
        if (technique.equals(Technique.LCBA) ||
            technique.equals(Technique.NC) ||
            technique.equals(Technique.NCC) ||
            technique.equals(Technique.STATIC_LCBA) ||
            technique.equals(Technique.STATIC_NC) ||
            technique.equals(Technique.STATIC_NCC)) {
          continue;
        }

        List<MethodValuePair> rankedMethodValuePairs =
            new ArrayList<MethodValuePair>( techniqueEntry.getValue() );
        Collections.sort(rankedMethodValuePairs, new Comparator<MethodValuePair>(){
          public int compare(MethodValuePair i1, MethodValuePair i2) {
            Double d1 = i1.getValue();
            Double d2 = i2.getValue();
            return d1.compareTo(d2);
          }
        });

        Collections.reverse(rankedMethodValuePairs);

        int position = 1;
        for (MethodValuePair methodValuePair : rankedMethodValuePairs) {
          int methodScore = techniqueEntry.getValue().size() - position;
          methodScores.putIfAbsent(methodValuePair.getMethod(), 0);
          methodScores.put(methodValuePair.getMethod(),
              methodScores.get(methodValuePair.getMethod()) + methodScore);
          position++;
        }
      }

      List<Map.Entry<String, Integer>> entries = new ArrayList<>( methodScores.entrySet() );
      Collections.sort(entries, new Comparator<Map.Entry<String,Integer>>(){
        public int compare(Map.Entry<String,Integer> i1, Map.Entry<String,Integer> i2) {
          return i2.getValue().compareTo(i1.getValue());
        }
      });

      String winningFunction = entries.get(0).getKey();
      winnersMap.put(test, winningFunction);
    }

    Utilities.logger.info("Computed borda count winners: " + winnersMap);
    return winnersMap;
  }

  public static Table<String, Technique, SortedSet<MethodValuePair>> buildCandidateSetTable(
      Configuration config,
      Table<String, Technique, SortedSet<MethodValuePair>> aggregatedResults) {
    Utilities.logger.info("Constructing method traceability predictions");
    // Keys: Test, Technique, Candidate set of methods
    Table<String, Technique, SortedSet<MethodValuePair>> candidateTable = HashBasedTable.create();

    Set<Table.Cell<String, Technique, SortedSet<MethodValuePair>>> aggregatedCells = aggregatedResults
        .cellSet();

    HashMap<Integer, Integer> predictedDepths = new HashMap<>();
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
            int depth = Utilities.getfunctionDepth(test, methodValuePair.getMethod());
            if (technique.equals(Technique.COMBINED)) {
              predictedDepths.putIfAbsent(depth, 0);
              predictedDepths.put(depth, predictedDepths.get(depth) + 1);
            }
          }
        }
      }
    }

    Utilities.logger.info("Predicted depths for " + config.getProjects().get(0) + ":");
    Utilities.logger.info(predictedDepths.toString());

    Utilities.logger.info("Method traceability predictions constructed");
    return candidateTable;
  }

  // NOTE: This drops entries without ground truth data unless configured to use naming conventions as ground truth.
  public static EvaluationMetrics computeBordaCountEvaluationMetrics(
      Configuration config, Table<String, Technique, SortedSet<MethodValuePair>> candidateTable,
      Map<String, String> winnersMap, Main.ScoreType scoreType) {
    // For each test, get the ground truth answer, then measure the other techniques against the ground truth answer.

    double bpref = 0.0;
    int truePositives = 0;
    int falsePositives = 0;
    int falseNegatives = 0;
    double avgPrecisionDenominator = 0.0;
    double avgPrecisionNumerator = 0.0;

    for (Map.Entry<String, Map<Technique, SortedSet<MethodValuePair>>> testEntry : candidateTable
        .rowMap().entrySet()) {
      String test = testEntry.getKey();

      if (test.contains("ScatterPlotTest.testReplaceDataset")) {
        System.out.println("Debugging new ground truth format");
      }

      // Obtain ground truth data
      Map<Technique, SortedSet<MethodValuePair>> techniqueMap = testEntry.getValue();
      SortedSet<MethodValuePair> groundTruthPairSet = techniqueMap.get(Technique.GROUND_TRUTH);
      //groundTruthMap.put(test, groundTruthPairSet);

      if (groundTruthPairSet == null && CONFIG_NAMING_CONVENTIONS_AS_GROUND_TRUTH) {
        // If configured to do so, in the absence of ground truth, use Naming Conventions - Contains candidate set
        groundTruthPairSet = techniqueMap.get(Technique.NCC);
      }

      if (groundTruthPairSet != null) {
        Set<String> groundTruthSet = new HashSet<>();
        for (MethodValuePair methodValuePair : groundTruthPairSet) {
          groundTruthSet.add(methodValuePair.getMethod());
        }

        String bordaWinningMethod = winnersMap.get(test);
        int listIdx = 0;
        listIdx++;
        if (groundTruthSet.contains(bordaWinningMethod)) {
          truePositives++;
          SortedSet<MethodValuePair> methodValueSet = new TreeSet<>();
          methodValueSet.add(new MethodValuePair(bordaWinningMethod, 1.0));
          avgPrecisionNumerator += Utilities.functionLevelPrecisionAtK(methodValueSet,
              groundTruthSet, listIdx);
          falseNegatives += groundTruthPairSet.size() - 1;
        } else {
          falsePositives++;
          falseNegatives += groundTruthPairSet.size();
        }

        avgPrecisionDenominator += groundTruthSet.size();
      } else {
        // Most tests won't have explicitly annotated ground truth data. OK to ignore.
        // System.out.printf("No ground truth data for test %s.%n", test);
      }
    }

    double averagePrecision = (avgPrecisionDenominator == 0) ? 0 :
        avgPrecisionNumerator / avgPrecisionDenominator;

    Logger.get().logAndPrintLn("Borda Count - true positives: " + truePositives);
    Logger.get().logAndPrintLn("Borda Count - false positives: " + falsePositives);
    Logger.get().logAndPrintLn("Borda Count - false negatives: " + falseNegatives);

    double precision = EvaluationMetrics
        .computePrecision(truePositives, falsePositives);
    double recall = EvaluationMetrics.computeRecall(truePositives, falseNegatives);
    double fScore = EvaluationMetrics.computeFScore(precision, recall);

    Logger.get().logAndPrintLn("Borda Count - precision: " + precision);
    Logger.get().logAndPrintLn("Borda Count - recall: " + recall);
    Logger.get().logAndPrintLn("Borda Count - fScore: " + fScore);

    return new EvaluationMetrics(truePositives, falsePositives, falseNegatives, bpref,
        averagePrecision);
  }

  // NOTE: This drops entries without ground truth data unless configured to use naming conventions as ground truth.
  public static Table<Technique, String, EvaluationMetrics> computeEvaluationMetrics(
      Configuration config, Table<String, Technique, SortedSet<MethodValuePair>> candidateTable,
      Main.ScoreType scoreType) {
    // Keys: Technique, Test, Evaluation Metrics (true positives, etc)
    Table<Technique, String, EvaluationMetrics> metricTable = HashBasedTable.create();

    //HashMap<String, SortedSet<MethodValuePair>> groundTruthMap = new HashMap<>();

    HashMap<Integer, Integer> truePositiveDepths = new HashMap<>();
    // For each test, get the ground truth answer, then measure the other techniques against the ground truth answer.
    for (Map.Entry<String, Map<Technique, SortedSet<MethodValuePair>>> testEntry : candidateTable
        .rowMap().entrySet()) {
      String test = testEntry.getKey();

      if (test.contains("ScatterPlotTest.testReplaceDataset")) {
        System.out.println("Debugging new ground truth format");
      }

      // Obtain ground truth data
      Map<Technique, SortedSet<MethodValuePair>> techniqueMap = testEntry.getValue();
      SortedSet<MethodValuePair> groundTruthPairSet = techniqueMap.get(Technique.GROUND_TRUTH);
      //groundTruthMap.put(test, groundTruthPairSet);

      if (groundTruthPairSet == null && CONFIG_NAMING_CONVENTIONS_AS_GROUND_TRUTH) {
        // If configured to do so, in the absence of ground truth, use Naming Conventions - Contains candidate set
        groundTruthPairSet = techniqueMap.get(Technique.NCC);
      }

      if (groundTruthPairSet != null) {
        Set<String> groundTruthSet = new HashSet<>();
        for (MethodValuePair methodValuePair : groundTruthPairSet) {
          groundTruthSet.add(methodValuePair.getMethod());
        }

        // For every technique
        for (Technique technique : Utilities.getTechniques(config, Configuration.Level.METHOD,
            scoreType)) {

          /*if (technique.equals(Technique.STATIC_LCBA)) {
            Logger.get().logAndPrintLn("Debugging static method level matching");
          }*/

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

              if (technique.equals(Technique.COMBINED)) {
                Integer depth = Utilities.getGroundTruthDepth(test, methodValuePair.getMethod());
                truePositiveDepths.putIfAbsent(depth, 0);
                truePositiveDepths.put(depth, truePositiveDepths.get(depth) + 1);
              }
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

          /*Logger.get().logAndPrintLn("Test: " + test);
          Logger.get().logAndPrintLn("True Positives: " + truePositives);
          Logger.get().logAndPrintLn("False Positives: " + falsePositives);
          Logger.get().logAndPrintLn("False Negatives: " + falseNegatives);*/
          /*if (falseNegatives > 0 && technique.equals(Technique.STATIC_LCS_B_N)) {
            Logger.get().logAndPrintLn("DEBUGGING MISSING RECALL");
            Logger.get().logAndPrintLn("STATIC_LCS_B_N missing pair for test: " + test);
          }*/

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

    Utilities.logger.info("True Positive depths for " + config.getProjects().get(0) + ":");
    Utilities.logger.info(truePositiveDepths.toString());

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

  private void runCombinedScoreOptimisationExperiment(Table<String, String, Map<Technique,
      Double>> relevanceTable, boolean normalisedWithinTestByTechnique) {
    for (Technique technique : config.getMethodLevelTechniqueList()) {
      MethodScoresTensor methodScoresTensor = new PureMethodScoresTensor(config, relevanceTable,
          normalisedWithinTestByTechnique);

      // Aggregate the results
      // Keys: Test, Technique, Ranked list of method candidates
      Table<String, Technique, SortedSet<MethodValuePair>> aggregatedResults =
          computeAggregatedResults(config, relevanceTable, methodScoresTensor);

      // Build candidate set
      // Keys: Test, Technique, Candidate set of methods
      Table<String, Technique, SortedSet<MethodValuePair>> candidateTable = buildCandidateSetTable(
          config, aggregatedResults);

      Map<String, SortedSet<MethodValuePair>> groundTruthMap =
          candidateTable.column(Technique.GROUND_TRUTH);
      ResultsWriter.writeOutMethodLevelGroundTruth(config, groundTruthMap);

      // Compute evaluation metrics for each test
      // Keys: Technique, Test, Evaluation Metrics (true positives, etc)
      Table<Technique, String, EvaluationMetrics> metricTable = computeEvaluationMetrics(config,
          candidateTable, Main.ScoreType.PURE);

      printFunctionLevelMetricsSummary(config, metricTable, Main.ScoreType.PURE);
    }
  }

  public static void printFunctionLevelMetricsSummary(Configuration config,
                                                       Table<Technique, String, EvaluationMetrics> functionLevelMetricsTable, Main.ScoreType scoreType) {
    // System.out.println("======= AGGREGATED TECHNIQUE EVALUATION METRICS =======");
    // SpectraParser.printEvaluationMetrics(config.getTechniqueList(), null, metricsTable);

    config.printConfiguration();

    System.out.println("======= TECHNIQUE METRICS =======");
    TechniqueMetricsProvider.printTechniqueMetrics(Utilities.getTechniques(config,
        Configuration.Level.METHOD, scoreType),
        TechniqueMetricsProvider.computeTechniqueMetrics(functionLevelMetricsTable));

    System.out.println("======= TECHNIQUE METRICS DATA =======");
    TechniqueMetricsProvider.printTechniqueMetricsData(Utilities.getTechniques(config,
        Configuration.Level.METHOD, scoreType),
        TechniqueMetricsProvider.computeTechniqueMetrics(functionLevelMetricsTable));

    int testCount = functionLevelMetricsTable.columnKeySet().size();
    System.out.printf("Total number of tests: %d %n", testCount);
  }
}
