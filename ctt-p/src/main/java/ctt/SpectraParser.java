package ctt;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.gson.Gson;
import ctt.coverage.CoverageAnalyser;
import ctt.coverage.CoverageAnalyser.CounterType;
import ctt.coverage.CoverageAnalyser.CoverageStat;
import ctt.types.HitSpectrum;
import ctt.types.TestCollection;
import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.skb.interfaces.transformers.textformat.TextAlignment;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.commons.text.similarity.LongestCommonSubsequence;
import org.apache.commons.text.similarity.SimilarityScore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ctt.Utilities.createIfAbsent;

/**
 * Reads JSON hit sets into memory to perform analysis
 */
public class SpectraParser {
    private static final Logger logger = LogManager.getLogger(SpectraParser.class.getName());

    // Configuration
    private Configuration config = null;
    private static final boolean CONFIG_NAMING_CONVENTIONS_AS_GROUND_TRUTH = false; // Whether to use Naming Conventions as ground truth when no ground truth data is available.

    // -- OPTIONAL FIELDS --
    // Coverage data - this is optional! Check for null before using.
    private Table<String, String, Map<CounterType, CoverageStat>> coverageData = null;
    // -- END OPTIONAL FIELDS --

    // Map of methods to the tests that executed them
    private Map<String, Set<MethodDepthPair>> methodMap = null;

    // Map of tests to the methods that they executed
    private Map<String, Set<MethodDepthPair>> testsMap = null;

    // Keys: Test, Method | Value: Map<Technique, Relevance Value>
    private Table<String, String, Map<Technique, Double>> relevanceTable = null;

    // Keys: Test, Technique | Value: Sorted set of method candidates (highest relevance first)
    private Table<String, Technique, SortedSet<MethodValuePair>> aggregatedResults = null;

    // Keys: Test, Technique | Value: Sorted candidate set
    private Table<String, Technique, SortedSet<MethodValuePair>> candidateTable = null;

    // Keys: Technique, Test | Value: Evaluation Metrics (true positives, etc)
    private Table<Technique, String, EvaluationMetrics> metricTable = null;

    public SpectraParser(Configuration config) {
        this(config, null);
    }

    public SpectraParser(Configuration config, Table<String, String, Map<CounterType, CoverageStat>> coverageData) {
        this.config = config;
        this.coverageData = coverageData;
    }

    public static Table<Technique, String, EvaluationMetrics> parseTestCollections(Configuration config,
                                                                                   List<TestCollection> testCollections,
                                                                                   Table<String, String, Map<CounterType, CoverageStat>> coverageData) {
        // Parse on each test collection and aggregate metrics.
        Table<Technique, String, EvaluationMetrics> aggregatedMetricsTable = HashBasedTable.create();
        for (TestCollection testCollection : testCollections) {
            SpectraParser parser = new SpectraParser(config, coverageData);
            Table<Technique, String, EvaluationMetrics> metricsTable = parser.parseTestCollection(testCollection);
            aggregatedMetricsTable.putAll(metricsTable);
        }
        return aggregatedMetricsTable;
    }

    public Table<String, Technique, SortedSet<MethodValuePair>> getAggregatedResults() {
        return aggregatedResults;
    }

    public enum Metric {
        PRECISION ("Precision"),
        RECALL    ("Recall"),
        F_SCORE   ("F-Score"),
        MAP       ("MAP"),
        BPREF     ("Bpref");

        private final String text;
        Metric(String text) { this.text = text; }
        @Override public String toString() { return text; }
    }

    // Similarity metrics for name similarity
    enum SimilarityMetrics {
        CONTAINS, // Method name is contained in the test name
        LEVENSHTEIN_DISTANCE,
        LONGEST_COMMON_SUBSEQUENCE,
        COSINE_DISTANCE,
    }

    static class MethodDepthPair {
        public String methodName;
        public int callDepth;

        public MethodDepthPair(String methodName, int callDepth) {
            this.methodName = methodName;
            this.callDepth = callDepth;
        }
    }

    // Represents a method and its traceability value
    static class MethodValuePair implements Comparable<MethodValuePair> {
        public String method;
        public double value;

        public MethodValuePair(String method, double value) {
            this.method = method;
            this.value = value;
        }

        @Override
        public int compareTo(MethodValuePair other) {
            int valueCompare = Double.compare(this.value, other.value);
            if (valueCompare != 0) {
                return -valueCompare; // negating to reverse order
            } else {
                return method.compareTo(other.method); // String comparison
            }
        }
    }

    // Table<String, String, Map<CounterType, CoverageStat>> coverageData
    private double computeDiscountedScore(double score, String testName, String methodName, int callDepth) {
        // Discount by coverage
        if (coverageData != null) {
            double coverageScore = CoverageAnalyser.getCoverageScore(coverageData, testName, methodName);
            // System.out.printf("coverageScore for test %s and method %s is %f %n", testName, methodName, coverageScore);
            score *= coverageScore;
        }

        // Discount by call depth
        return score * Math.pow(config.getCallDepthDiscountFactor(), callDepth);
    }

    public Table<Technique, String, EvaluationMetrics> parse(File file, boolean verbose) throws FileNotFoundException {
        TestCollection testCollection = parseJSONFile(file);
        if (verbose) System.out.println("JSON parse finished");
        return parseTestCollection(testCollection, verbose);
    }

    public static TestCollection parseJSONFile(File file) throws FileNotFoundException {
        BufferedReader br = new BufferedReader(new FileReader(file));
        Gson gson = new Gson();
        return gson.fromJson(br, TestCollection.class);
    }

    public Table<Technique, String, EvaluationMetrics> parseTestCollection(TestCollection testCollection) {
        return parseTestCollection(testCollection, false);
    }

    // Returns metrics table
    public Table<Technique, String, EvaluationMetrics> parseTestCollection(TestCollection testCollection, boolean verbose) {
        // Map of method to the tests that execute the method
        methodMap = new HashMap<>();

        // Map of test to the methods that the test executes
        testsMap = new HashMap<>();

        // Populate map of method-to-invoking-tests and test-to-invoked-methods
        for (HitSpectrum testHitSpectrum : testCollection.tests) {
            for (Map.Entry<String, Integer> hitSetEntry : testHitSpectrum.hitSet.entrySet()) {
                String invokedMethod = hitSetEntry.getKey();
                int callDepth = hitSetEntry.getValue();
                String testName = testHitSpectrum.cls + "." + testHitSpectrum.func;
                Set<MethodDepthPair> testList = methodMap.computeIfAbsent(invokedMethod, k -> new HashSet<>());
                testList.add(new MethodDepthPair(testName, callDepth));

                Set<MethodDepthPair> methodList = testsMap.computeIfAbsent(testName, k -> new HashSet<>());
                methodList.add(new MethodDepthPair(invokedMethod, callDepth));
            }
        }

        if (verbose) System.out.println("Map built");

        // Calculate suspiciousness values using techniques
        // Keys: Test, Method, Map<Technique, Value>
        relevanceTable = computeRelevanceTable(testCollection);
        if (verbose) System.out.println("Suspiciousness calculated");

        // Aggregate the results
        // Keys: Test, Technique, Ranked list of method candidates
        aggregatedResults = computeAggregatedResults(config, relevanceTable);

        if (verbose) {
            System.out.println("Aggregated results computed");
            // Pretty-print aggregated results table
            printAggregatedResults(config.getTechniqueList(), aggregatedResults, null);
        }

        // Build candidate set
        // Keys: Test, Technique, Candidate set of methods
        candidateTable = buildCandidateSetTable(config, aggregatedResults);

        if (verbose) {
            System.out.println("Candidate set computed");
        }

        // Compute evaluation metrics for each test
        // Keys: Technique, Test, Evaluation Metrics (true positives, etc)
        metricTable = computeEvaluationMetrics(config, candidateTable);

        if (verbose) {
            System.out.println("Evaluation metrics computed");
            System.out.println("======= EVALUATION METRICS =======");
            // Pretty-print evaluation metrics table
            printEvaluationMetrics(config.getTechniqueList(), aggregatedResults, metricTable);
        }

        // Compute evaluation metrics for each technique
        // Keys: Technique, Metric, Value
        Table<Technique, Metric, Double> techniqueMetrics = computeTechniqueMetrics(metricTable);

        if (verbose) {
            System.out.println("Technique metrics computed");
            // Pretty-print technique metrics
            printTechniqueMetrics(config.getTechniqueList(), techniqueMetrics);
        }

        return metricTable;
    }

    private Table<String, String, Map<Technique, Double>> computeRelevanceTable(TestCollection testCollection) {
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
            double totalPassed = testCollection.tests.size() - 1; // total number of tests in the test suite

            // For each test that executes the method
            for (MethodDepthPair testDepthPair : testsExecutingMethod) {
                String test = testDepthPair.methodName;
                Map<Technique, Double> valueMap = createIfAbsent(relevanceTable, test, method, HashMap::new);

                // Tarantula
                double suspiciousness = 1.0 / (1.0 + passed/totalPassed);
                valueMap.put(Technique.FAULT_LOC_TARANTULA, computeDiscountedScore(suspiciousness, test, method, testDepthPair.callDepth));
                //System.out.println("Suspiciousness for method " + method + " , test: " + test + " = " + suspiciousness);

                // tf-idf values
                // If we are here, we are looking at a test that this method is executed by. All other cells get a default value of 0 (handles the 'otherwise' case).
                double tf1 = 1.0; // binary
                double tf2 = 1.0 / testsMap.get(test).size();
                double tf3 = Math.log(1.0 + 1.0 / testsMap.get(test).size()); // the more methods this test tests, the lower this value.

                double idf1 = (double) testsExecutingMethod.size() / testCollection.tests.size();
                double idf2 = Math.log((double) testCollection.tests.size() / testsExecutingMethod.size());

                double tfidf_11 = tf1 * idf1;
                double tfidf_12 = tf1 * idf2;
                double tfidf_21 = tf2 * idf1;
                double tfidf_22 = tf2 * idf2;
                double tfidf_31 = tf3 * idf1;
                double tfidf_32 = tf3 * idf2;

                valueMap.put(Technique.IR_TFIDF_11, computeDiscountedScore(tfidf_11, test, method, testDepthPair.callDepth));
                valueMap.put(Technique.IR_TFIDF_12, computeDiscountedScore(tfidf_12, test, method, testDepthPair.callDepth));
                valueMap.put(Technique.IR_TFIDF_21, computeDiscountedScore(tfidf_21, test, method, testDepthPair.callDepth));
                valueMap.put(Technique.IR_TFIDF_22, computeDiscountedScore(tfidf_22, test, method, testDepthPair.callDepth));
                valueMap.put(Technique.IR_TFIDF_31, computeDiscountedScore(tfidf_31, test, method, testDepthPair.callDepth));
                valueMap.put(Technique.IR_TFIDF_32, computeDiscountedScore(tfidf_32, test, method, testDepthPair.callDepth));
            }
        }

        // Test-to-method
        // Name similarity
        // For each test
        for (Map.Entry<String, Set<MethodDepthPair>> entry : testsMap.entrySet()) {
            String test = entry.getKey();
            int idx_test_openParen = test.lastIndexOf('(');
            if (idx_test_openParen == -1) throw new Error("Invalid test name: " + test);
            String testName = test.substring(test.lastIndexOf('.', idx_test_openParen) + 1, idx_test_openParen).toLowerCase();

            // Remove 'test' from the test name
            if (testName.contains("test")) {
                testName = testName.replace("test", "");
            }

            Set<MethodDepthPair> methodsExecutedByTest = entry.getValue();

            for (MethodDepthPair methodDepthPair : methodsExecutedByTest) {
                String method = methodDepthPair.methodName;
                int idx_method_openParen = method.lastIndexOf('(');
                if (idx_method_openParen == -1) throw new Error("Invalid method name: " + method);
                String methodName = method.substring(method.lastIndexOf('.', idx_method_openParen) + 1, idx_method_openParen).toLowerCase();
                // Lower distance = more similar

                // Longest Common Subsequence
                SimilarityScore<Integer> longestCommonSubsequence = new LongestCommonSubsequence();
                int similarityScore = longestCommonSubsequence.apply(testName, methodName);
                double score_longestCommonSubsequence = (double) similarityScore / Math.max(testName.length(), methodName.length());
                double score_longestCommonSubsequenceFuzzy = (double) similarityScore / methodName.length();

                // logger.info("testName {}, methodName {}, distance = {}, score = {}", testName, methodName, distance, score_longestCommonSubsequence);
                // System.out.printf("Similarity distance between %s and %s is: %d %n", testName, methodName, similarityScore);

                // Levenshtein Distance
                SimilarityScore<Integer> levenshteinDistance = new LevenshteinDistance();
                int distance = levenshteinDistance.apply(testName, methodName);
                double score_levenshtein = 1.0 - ((double) distance / Math.max(testName.length(), methodName.length()));
                // logger.info("testName {}, methodName {}, distance = {}, score = {}", testName, methodName, distance, score_levenshtein);

                // Test contains method name
                boolean contains = testName.contains(methodName);

                Map<Technique, Double> valueMap = createIfAbsent(relevanceTable, test, method, HashMap::new);
                valueMap.put(Technique.NS_COMMON_SUBSEQ, computeDiscountedScore(score_longestCommonSubsequence, test, method, methodDepthPair.callDepth));
                valueMap.put(Technique.NS_COMMON_SUBSEQ_FUZ, computeDiscountedScore(score_longestCommonSubsequenceFuzzy, test, method, methodDepthPair.callDepth));
                valueMap.put(Technique.NS_LEVENSHTEIN, computeDiscountedScore(score_levenshtein, test, method, methodDepthPair.callDepth));
                valueMap.put(Technique.NS_LEVENSHTEIN_N, computeDiscountedScore(score_levenshtein, test, method, methodDepthPair.callDepth));
                valueMap.put(Technique.NS_CONTAINS, computeDiscountedScore(contains ? 1.0 : 0.0, test, method, methodDepthPair.callDepth));

                // Coverage
                if (coverageData != null) {
                    double coverageScore = CoverageAnalyser.getCoverageScore(coverageData, test, method);
                    valueMap.put(Technique.COVERAGE, computeDiscountedScore(coverageScore, test, method, methodDepthPair.callDepth));
                }
            }
        }

        for (HitSpectrum testHitSpectrum : testCollection.tests) {
            String testName = testHitSpectrum.cls + "." + testHitSpectrum.func;

            // Ground Truth
            for (String method : testHitSpectrum.groundTruth) {
                Map<Technique, Double> valueMap = createIfAbsent(relevanceTable, testName, method, HashMap::new);
                valueMap.put(Technique.GROUND_TRUTH, 1.0);
            }

            // LCBA
            int numCallsBeforeAssert = testHitSpectrum.callsBeforeAssert.size();
            for (String method : testHitSpectrum.callsBeforeAssert) {
                Map<Technique, Double> valueMap = createIfAbsent(relevanceTable, testName, method, HashMap::new);

                // Each method in callsBeforeAssert has 1.0 relevance
                valueMap.put(Technique.LAST_CALL_BEFORE_ASSERT, 1.0);
            }
        }
        return relevanceTable;
    }

    private static Table<String, Technique, SortedSet<MethodValuePair>> computeAggregatedResults(Configuration config, Table<String, String, Map<Technique, Double>> suspiciousnessValues) {
        // Build a table for each technique and each test with a ranked list inside each cell
        // Keys: Test, Technique, Ranked list of method candidates
        Table<String, Technique, SortedSet<MethodValuePair>> aggregatedResults = HashBasedTable.create();

        Set<Table.Cell<String, String, Map<Technique, Double>>> cells = suspiciousnessValues.cellSet();
        for (Table.Cell<String, String, Map<Technique, Double>> cell : cells) {
            String test = cell.getRowKey();
            String method = cell.getColumnKey();
            Map<Technique, Double> value = cell.getValue();

            if (value != null) {
                for (Map.Entry<Technique, Double> entry : value.entrySet()) {
                    Technique technique = entry.getKey();
                    Double suspiciousness = entry.getValue();

                    SortedSet<MethodValuePair> methodSet = createIfAbsent(aggregatedResults, test, technique, TreeSet::new);
                    methodSet.add(new MethodValuePair(method, suspiciousness));
                }
            }
        }

        // Normalize values
        boolean normalize = true;
        if (normalize) {
            for (Technique techniqueToNormalize : config.getTechniquesToNormalize()) {
                Map<String, SortedSet<MethodValuePair>> testAndMethodMap = aggregatedResults.column(techniqueToNormalize);
                for (SortedSet<MethodValuePair> methods : testAndMethodMap.values()) {
                    double maxValue = methods.first().value;
                    for (MethodValuePair method : methods) {
                        method.value = maxValue != 0 ? method.value / maxValue : 0;
                    }
                }
            }
        }
        return aggregatedResults;
    }

    private static Table<String, Technique, SortedSet<MethodValuePair>> buildCandidateSetTable(Configuration config, Table<String, Technique, SortedSet<MethodValuePair>> aggregatedResults) {
        // Keys: Test, Technique, Candidate set of methods
        Table<String, Technique, SortedSet<MethodValuePair>> candidateTable = HashBasedTable.create();

        Set<Table.Cell<String, Technique, SortedSet<MethodValuePair>>> aggregatedCells = aggregatedResults.cellSet();
        for (Table.Cell<String, Technique, SortedSet<MethodValuePair>> cell : aggregatedCells) {
            String test = cell.getRowKey();
            Technique technique = cell.getColumnKey();
            SortedSet<MethodValuePair> methods = cell.getValue();

            // All cells must be populated even if it was empty in the aggregated results.
            // Empty in aggregated results = empty set.
            SortedSet<MethodValuePair> candidateSet = createIfAbsent(candidateTable, test, technique, TreeSet::new);
            if (methods != null) {
                for (MethodValuePair methodValuePair : methods) {
                    if (methodValuePair.value >= config.getThresholdData().getOrDefault(technique, 0.0)) {
                        candidateSet.add(methodValuePair);
                    }
                }
            }
        }
        return candidateTable;
    }

    // NOTE: This drops entries without ground truth data unless configured to use naming conventions as ground truth.
    private static Table<Technique, String, EvaluationMetrics> computeEvaluationMetrics(Configuration config, Table<String, Technique, SortedSet<MethodValuePair>> candidateTable) {
        // Keys: Technique, Test, Evaluation Metrics (true positives, etc)
        Table<Technique, String, EvaluationMetrics> metricTable = HashBasedTable.create();

        // For each test, get the ground truth answer, then measure the other techniques against the ground truth answer.

        for (Map.Entry<String, Map<Technique, SortedSet<MethodValuePair>>> testEntry : candidateTable.rowMap().entrySet()) {
            String test = testEntry.getKey();

            // Obtain ground truth data
            Map<Technique, SortedSet<MethodValuePair>> techniqueMap = testEntry.getValue();
            SortedSet<MethodValuePair> groundTruthPairSet = techniqueMap.get(Technique.GROUND_TRUTH);

            if (groundTruthPairSet == null && CONFIG_NAMING_CONVENTIONS_AS_GROUND_TRUTH) {
                // If configured to do so, in the absence of ground truth, use Naming Conventions - Contains candidate set
                groundTruthPairSet = techniqueMap.get(Technique.NS_CONTAINS);
            }

            if (groundTruthPairSet != null) {
                Set<String> groundTruthSet = new HashSet<>();
                for (MethodValuePair methodValuePair : groundTruthPairSet) {
                    groundTruthSet.add(methodValuePair.method);
                }

                // For every technique
                for (Technique technique : config.getTechniqueList()) {
                    SortedSet<MethodValuePair> candidateMethodSet = candidateTable.get(test, technique);
                    if (candidateMethodSet == null) candidateMethodSet = new TreeSet<>(); // if no entry in the candidate table for the given test and technique, treat as empty set.

                    // System.out.printf("Test %s, technique %s, map size %d \n", test, technique, techniqueMap.size());

                    Set<MethodValuePair> truePositiveSet = new HashSet<>(); // present here = relevant & in candidate set

                    // Compute the intersection of relevant and retrieved
                    int truePositives = 0;
                    int falsePositives = 0; // elements in the candidate set that are not in the ground truth
                    double totalPrecision = 0.0; // for calculating average precision
                    for (MethodValuePair methodValuePair : candidateMethodSet) {
                        if (groundTruthSet.contains(methodValuePair.method)) {
                            truePositives++;
                            truePositiveSet.add(methodValuePair);
                        } else {
                            falsePositives++;
                        }
                        totalPrecision += EvaluationMetrics.computePrecision(truePositives, falsePositives);
                    }
                    double averagePrecision = candidateMethodSet.size() > 0 ? totalPrecision / candidateMethodSet.size() : 0;

                    int falseNegatives = groundTruthPairSet.size() - truePositives; // elements in the ground truth set that were not in the candidate set

                    // Compute bpref
                    double bpref = 0.0;
                    double bprefParameter = 0; // 0 or 10
                    int numRelevant = groundTruthPairSet.size();
                    for (MethodValuePair truePositiveMethod : truePositiveSet) {
                        SortedSet<MethodValuePair> retrievedMethodsAboveR = candidateMethodSet.headSet(truePositiveMethod); // exclusive of truePositiveMethod
                        int retrievedNonRelevantAboveR = 0;
                        for (MethodValuePair methodAboveR : retrievedMethodsAboveR) {
                            if (!truePositiveSet.contains(methodAboveR)) {
                                retrievedNonRelevantAboveR++;
                                if (retrievedNonRelevantAboveR >= numRelevant) break;
                            }
                        }
                        bpref += 1 - ((double) retrievedNonRelevantAboveR / (bprefParameter + numRelevant));
                    }
                    bpref *= 1.0 / numRelevant;

                    metricTable.put(technique, test, new EvaluationMetrics(truePositives, falsePositives, falseNegatives, bpref, averagePrecision));
                }
            } else {
                // Most tests won't have explicitly annotated ground truth data. OK to ignore.
                // System.out.printf("No ground truth data for test %s.%n", test);
            }
        }
        return metricTable;
    }

    public static Table<Technique, Metric, Double> computeTechniqueMetrics(Table<Technique, String, EvaluationMetrics> metricTable) {
        // Keys: Technique, Metric, Value
        Table<Technique, Metric, Double> techniqueMetrics = HashBasedTable.create();

        for (Map.Entry<Technique, Map<String, EvaluationMetrics>> techniqueEntry : metricTable.rowMap().entrySet()) {
            int numTests = techniqueEntry.getValue().size();
            int totalTruePositives = 0, totalFalsePositives = 0, totalFalseNegatives = 0;
            double totalBpref = 0.0, totalAveragePrecision = 0.0;
            Technique technique = techniqueEntry.getKey();

            for (EvaluationMetrics evaluationMetrics : techniqueEntry.getValue().values()) {
                totalTruePositives += evaluationMetrics.truePositives;
                totalFalsePositives += evaluationMetrics.falsePositives;
                totalFalseNegatives += evaluationMetrics.falseNegatives;
                totalBpref += evaluationMetrics.getBpref();
                totalAveragePrecision += evaluationMetrics.getAveragePrecision();
            }

            double precision = EvaluationMetrics.computePrecision(totalTruePositives, totalFalsePositives);
            double recall = EvaluationMetrics.computeRecall(totalTruePositives, totalFalseNegatives);
            double fScore = EvaluationMetrics.computeFScore(precision, recall);
            double meanAveragePrecision = totalAveragePrecision / numTests;
            double bpref = totalBpref / numTests;

            techniqueMetrics.put(technique, Metric.PRECISION, precision);
            techniqueMetrics.put(technique, Metric.RECALL, recall);
            techniqueMetrics.put(technique, Metric.F_SCORE, fScore);
            techniqueMetrics.put(technique, Metric.MAP, meanAveragePrecision);
            techniqueMetrics.put(technique, Metric.BPREF, bpref);

            // System.out.printf("%s:%n\tPrecision: %f | Recall: %f | F-Score: %f %n", technique.toString(), precision, recall, fScore);
        }
        return techniqueMetrics;
    }

    //======================
    // Utility Functions
    //======================
    public static Table<Technique, String, EvaluationMetrics> getEvaluationMetricsTable(Table<Technique, String, EvaluationMetrics> metricTable, Set<String> evaluationSet) {
        Table<Technique, String, EvaluationMetrics> evaluationMetricsTable = HashBasedTable.create();
        for (Table.Cell<Technique, String, EvaluationMetrics> cell : metricTable.cellSet()) {
            if (evaluationSet.contains(cell.getColumnKey())) {
                evaluationMetricsTable.put(cell.getRowKey(), cell.getColumnKey(), cell.getValue());
            }
        }
        return evaluationMetricsTable;
    }

    //======================
    // Output Functions
    //======================

    // Prints unnormalised results
    public void printTraceabilityMatrix(Technique techniqueToPrint, List<String> testFilter, List<String> methodFilter) {
        // Keys: Method, Test | Value: Traceability Value
        Table<String, String, Double> traceabilityMatrix = HashBasedTable.create();

        // Populate the traceability matrix
        if (testFilter != null) {
            for (String test : testFilter) {
                Set<MethodDepthPair> methodsExecutedByTest = this.testsMap.get(test);
                if (methodsExecutedByTest == null) continue;
                for (MethodDepthPair method : methodsExecutedByTest) {
                    Map<Technique, Double> traceabilityMap = this.relevanceTable.get(test, method.methodName);
                    double traceabilityValue = traceabilityMap.get(techniqueToPrint);
                    traceabilityMatrix.put(method.methodName, test, traceabilityValue);
                }
            }
        }
        if (methodFilter != null) {
            for (String method : methodFilter) {
                Set<MethodDepthPair> testsThatExecuteMethod = this.methodMap.get(method);
                if (testsThatExecuteMethod == null) continue;
                for (MethodDepthPair test : testsThatExecuteMethod) {
                    Map<Technique, Double> traceabilityMap = this.relevanceTable.get(test.methodName, method);
                    double traceabilityValue = traceabilityMap.get(techniqueToPrint);
                    traceabilityMatrix.put(method, test.methodName, traceabilityValue);
                }
            }
        }

        if (traceabilityMatrix.size() == 0) {
            logger.warn("Traceabiltiy matrix is empty - check that requested test and method sets are non-empty.");
            return;
        }

        // Print the traceability matrix
        AsciiTable at = new AsciiTable();
        at.addRule();
        at.addRow(Stream.concat(Stream.of("Method \\ Test"), traceabilityMatrix.columnKeySet().stream()).collect(Collectors.toList()));
        at.addRule();

        for (String method : traceabilityMatrix.rowKeySet()) {
            List<String> rowStrings = new ArrayList<>();
            rowStrings.add(method);

            for (String test : traceabilityMatrix.columnKeySet()) {
                rowStrings.add(String.format("%.4f", traceabilityMatrix.get(method, test)));
            }

            at.addRow(rowStrings);
            at.addRule();
        }

        at.setTextAlignment(TextAlignment.LEFT);
        String renderedTable = at.render(60 * traceabilityMatrix.columnKeySet().size());
        System.out.printf("=== Traceability Matrix for %s ===%n", techniqueToPrint.toString());
        System.out.println(renderedTable);
    }

    // Prints normalised results
    public void printTestTraceabilityMatrix(Technique techniqueToPrint, List<String> testFilter) {
        // Keys: Method, Test | Value: Traceability Value
        Table<String, String, Double> traceabilityMatrix = HashBasedTable.create();

        // Populate the traceability matrix
        if (testFilter != null) {
            for (String test : testFilter) {
                SortedSet<MethodValuePair> techniqueMethodSet = this.aggregatedResults.get(test, techniqueToPrint);
                if (techniqueMethodSet == null) continue;

                for (MethodValuePair methodValuePair : techniqueMethodSet) {
                    double traceabilityValue = methodValuePair.value;
                    traceabilityMatrix.put(methodValuePair.method, test, traceabilityValue);
                }
            }
        }

        if (traceabilityMatrix.size() == 0) {
            logger.warn("Traceabiltiy matrix is empty - check that requested test set is non-empty.");
            return;
        }

        // Print the traceability matrix
        AsciiTable at = new AsciiTable();
        at.addRule();
        at.addRow(Stream.concat(Stream.of("Method \\ Test"), traceabilityMatrix.columnKeySet().stream()).collect(Collectors.toList()));
        at.addRule();

        for (String method : traceabilityMatrix.rowKeySet()) {
            List<String> rowStrings = new ArrayList<>();
            rowStrings.add(method);

            for (String test : traceabilityMatrix.columnKeySet()) {
                rowStrings.add(String.format("%.4f", traceabilityMatrix.get(method, test)));
            }

            at.addRow(rowStrings);
            at.addRule();
        }

        at.setTextAlignment(TextAlignment.LEFT);
        String renderedTable = at.render(100 * traceabilityMatrix.columnKeySet().size());
        System.out.printf("=== Traceability Matrix for %s ===%n", techniqueToPrint.toString());
        System.out.println(renderedTable);
    }

    public static void printAggregatedResults(Technique[] techniqueOrder, Table<String, Technique, SortedSet<MethodValuePair>> aggregatedResults, List<String> testFilter) {
        List<Technique> techniqueOrderWithGroundTruth = Stream.concat(Stream.of(Technique.GROUND_TRUTH), Arrays.stream(techniqueOrder)).collect(Collectors.toList());

        AsciiTable at = new AsciiTable();
        at.addRule();
        at.addRow(Stream.concat(Stream.of("Test"), techniqueOrderWithGroundTruth.stream().map(Technique::toString)).collect(Collectors.toList()));
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
                        sb.append(String.format("%.4f: %s<br />", method.value, method.method));
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
        at.addRow(Stream.concat(Stream.of("Test"), Arrays.stream(techniqueOrder).map(Technique::toString)).collect(Collectors.toList()));
        at.addRule();

        // Using aggregated results instead of metricTable so that tests without ground truth are also shown (as empty rows)
        Set<String> tests = (aggregatedResults != null) ? aggregatedResults.rowKeySet() : metricTable.columnKeySet();

        for (String test : tests) {
            List<String> rowStrings = new ArrayList<>();
            rowStrings.add(test);

            for (Technique technique : techniqueOrder) {
                StringBuilder sb = new StringBuilder("");
                EvaluationMetrics metrics = metricTable.get(technique, test);
                if (metrics != null) {
                    sb.append(String.format("True Positives: %d/%d<br />", metrics.truePositives, metrics.truePositives + metrics.falseNegatives));
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

    public static void printTechniqueMetrics(Technique[] techniqueOrder, Table<Technique, Metric, Double> techniqueMetrics) {
        Metric[] metricOrder = {
                Metric.PRECISION,
                Metric.RECALL,
                Metric.MAP,
                Metric.F_SCORE,
                // Metric.BPREF,
        };

        AsciiTable at = new AsciiTable();
        at.addRule();
        at.addRow(Stream.concat(Stream.of("Technique \\ Metric"), Arrays.stream(metricOrder).map(Metric::toString)).collect(Collectors.toList()));
        at.addRule();

        for (Technique technique : techniqueOrder) {
            List<String> rowStrings = new ArrayList<>();
            rowStrings.add(technique.toString());
            for (Metric metric : metricOrder) {
                rowStrings.add(String.format("%.4f", techniqueMetrics.get(technique, metric)));
            }

            at.addRow(rowStrings);
            at.addRule();
        }

        at.setTextAlignment(TextAlignment.LEFT);
        String renderedTable = at.render(25 * metricOrder.length);
        System.out.println(renderedTable);
    }

    public static void printTechniqueMetricsData(Technique[] techniqueOrder, Table<Technique, Metric, Double> techniqueMetrics) {
        Metric[] metricOrder = {
                Metric.PRECISION,
                Metric.RECALL,
                Metric.MAP,
                Metric.F_SCORE,
                // Metric.BPREF,
        };

        StringBuilder sb = new StringBuilder();

        System.out.printf("Metrics [%d]: %s%n", metricOrder.length, Arrays.asList(metricOrder));
        System.out.printf("Techniques [%d]: %s%n", techniqueOrder.length, Arrays.asList(techniqueOrder));

        for (Metric metric : metricOrder) {
            List<String> rowStrings = new ArrayList<>();
            for (Technique technique : techniqueOrder) {
                double value = techniqueMetrics.get(technique, metric);
                rowStrings.add(String.format("%.1f\\%%", value * 100));
            }
            sb.append(String.join(" & ", rowStrings));
            sb.append(" \\\\");
            sb.append(System.lineSeparator());
        }

        System.out.println(sb.toString());
    }
}
