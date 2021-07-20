package ctt.experiments;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import ctt.Configuration;
import ctt.SpectraParser;
import ctt.SpectraParser.Metric;
import ctt.coverage.CoverageAnalyser.CounterType;
import ctt.coverage.CoverageAnalyser.CoverageStat;
import ctt.metrics.TechniqueMetricsProvider;
import ctt.types.CollectedComputedMetrics;
import ctt.types.EvaluationMetrics;
import ctt.types.HitSpectrum;
import ctt.types.Technique;
import ctt.types.TestCollection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Exhaustive, multi-objective search for best pair of threshold and call depth discount for each
 * technique. Maximises F-Score.
 */
public class OptimisationExperiment implements IExperiment {

  private static final Logger logger = LogManager.getLogger(OptimisationExperiment.class.getName());

  // Configuration
  private static final double MAX_THRESHOLD = 1.0;
  private static final double MIN_THRESHOLD = 0.0;
  // private static final double MIN_THRESHOLD = 0.9; // debug-only: for faster testing
  private static final double STEP_THRESHOLD = 0.05;

  private static final double MAX_CALL_DEPTH_DISCOUNT = 1.0;
  private static final double MIN_CALL_DEPTH_DISCOUNT = 0.30;
  // private static final double MIN_CALL_DEPTH_DISCOUNT = 0.98; // debug-only: for faster testing
  private static final double STEP_CALL_DEPTH_DISCOUNT = 0.02;

  // For setting fixed values
  // private static final double MAX_CALL_DEPTH_DISCOUNT = 0.75; // debug: for setting fixed values
  // private static final double MIN_CALL_DEPTH_DISCOUNT = 0.75; // debug: for setting fixed values

  private Technique[] techniqueList = {
      Technique.LCS_B_N,
      Technique.LCS_U_N,
      Technique.LEVENSHTEIN_N,
      Technique.TARANTULA,
      Technique.TFIDF,
      Technique.COMBINED
  };

  private static Metric[] metricsToRecord = {
      Metric.PRECISION,
      Metric.RECALL,
      Metric.F_SCORE,
      Metric.MAP,
      //Metric.BPREF,
      SpectraParser.Metric.TRUE_POSITIVES,
      SpectraParser.Metric.FALSE_POSITIVES,
      SpectraParser.Metric.FALSE_NEGATIVES
  };

  class ValueSet {

    double threshold;
    double callDepthDiscount;
    boolean scaleByCoverage;

    public ValueSet(double threshold, double callDepthDiscount, boolean scaleByCoverage) {
      this.threshold = threshold;
      this.callDepthDiscount = callDepthDiscount;
      this.scaleByCoverage = scaleByCoverage;
    }
  }

  // Keys: Technique, ValueSet | Value: Map<Metric, Metric Value>
  private Table<Technique, ValueSet, Map<Metric, Double>> results;

  // Keys: Technique, ValueSet | Value: Map<Metric, Metric Value>
  private Table<Technique, ValueSet, Map<Metric, Double>> evaluationResults;

  // Run without a test (evaluation) set.
  public void run(Configuration config, TestCollection testCollection,
      Table<String, String, Map<CounterType, CoverageStat>> coverageData) {
    this.runOn(Collections.singletonList(testCollection), coverageData, 0);
  }

  // Returns the set of test names in the test set.
  public Set<String> runOn(List<TestCollection> testCollections,
      Table<String, String, Map<CounterType, CoverageStat>> coverageData, double testPercent) {
    results = HashBasedTable.create();
    evaluationResults = HashBasedTable.create();

    // Generate test set
    Set<String> testSet = new HashSet<>();
    Set<String> allAnnotatedTests = new HashSet<>(); // all tests with ground truth annotations
    for (TestCollection testCollection : testCollections) {
      for (HitSpectrum hitSpectrum : testCollection.tests) {
        if (hitSpectrum.groundTruth.size() > 0) {
          String testName = hitSpectrum.getTestName();
          allAnnotatedTests.add(testName);
        }
      }
    }
    List<String> allAnnotatedTestsList = new ArrayList<>(allAnnotatedTests);
    Collections.shuffle(allAnnotatedTestsList, new Random(42));
    int numUsedForTestSet = (int) Math.floor(allAnnotatedTests.size() * testPercent);

    for (int i = 0; i < numUsedForTestSet; i++) {
      String testSetTest = allAnnotatedTestsList.get(i);
      testSet.add(testSetTest);
    }

    logger.info("Total number of annotated tests: {}", allAnnotatedTests.size());
    logger.info("Size of test set: {}", testSet.size());

    boolean coverageDataAvailable = coverageData != null;
    int coverageConfigurations = coverageDataAvailable ? 2 : 1;

    logger.info("Coverage data available: {}", coverageDataAvailable);

    // Generate configurations
    for (int i = 0; i < coverageConfigurations; i++) {
      Table<String, String, Map<CounterType, CoverageStat>> configConverageData =
          i == 0 ? null : coverageData;

      for (double threshold = MAX_THRESHOLD; threshold >= MIN_THRESHOLD;
          threshold -= STEP_THRESHOLD) {
        threshold = Math.round(threshold * 100.0) / 100.0; // Round value to 2 decimal places
        for (double callDepthDiscount = MAX_CALL_DEPTH_DISCOUNT;
            callDepthDiscount >= MIN_CALL_DEPTH_DISCOUNT;
            callDepthDiscount -= STEP_CALL_DEPTH_DISCOUNT) {
          callDepthDiscount =
              Math.round(callDepthDiscount * 100.0) / 100.0; // Round value to 2 decimal places

          Configuration config = new Configuration.Builder()
              .setThresholdValue(threshold)
              .setCallDepthDiscountFactor(callDepthDiscount)
              .setTechniqueList(techniqueList)
              .build();

          ValueSet variedValues = new ValueSet(threshold, callDepthDiscount,
              configConverageData != null);
          logger.info(
              "Starting experiment for threshold {}, call depth discount factor {}, coverage scaling: {}.",
              variedValues.threshold, variedValues.callDepthDiscount, variedValues.scaleByCoverage);

          ArrayList<CollectedComputedMetrics> collectedComputedMetrics =
              SpectraParser.parseTestCollections(config, testCollections, coverageData, false);

          // Train set metrics
          Table<Technique, String, EvaluationMetrics> aggregatedMetricsTable = HashBasedTable
              .create();
          for (CollectedComputedMetrics computedMetrics : collectedComputedMetrics) {
            aggregatedMetricsTable
                .putAll(computedMetrics.getFunctionLevelMetrics().getMetricTable());
          }

          // Test set metrics
          Table<Technique, String, EvaluationMetrics> testAggregatedMetricsTable = HashBasedTable
              .create();

          // Split data into train and test metrics
          for (String testInTestSet : testSet) {
            // Put in evaluation table
            for (Map.Entry<Technique, EvaluationMetrics> metricsEntry : aggregatedMetricsTable
                .column(testInTestSet).entrySet()) {
              testAggregatedMetricsTable
                  .put(metricsEntry.getKey(), testInTestSet, metricsEntry.getValue());
            }
            // Drop entries in the test set from the train table
            aggregatedMetricsTable.column(testInTestSet).clear();
          }

          saveResults(results, variedValues, aggregatedMetricsTable);
          saveResults(evaluationResults, variedValues, testAggregatedMetricsTable);
        }
      }
    }

    return testSet;
  }

  private static void saveResults(Table<Technique, ValueSet, Map<Metric, Double>> resultsTable,
      ValueSet variedValues, Table<Technique, String, EvaluationMetrics> aggregatedMetricsTable) {
    Table<Technique, Metric, Double> techniqueMetrics =
        TechniqueMetricsProvider.computeTechniqueMetrics(aggregatedMetricsTable);
    for (Metric metric : metricsToRecord) {
      Map<Technique, Double> techniqueValueMap = techniqueMetrics.column(metric);
      for (Map.Entry<Technique, Double> techniqueEntry : techniqueValueMap.entrySet()) {
        Technique technique = techniqueEntry.getKey();
        resultsTable.put(technique, variedValues, techniqueMetrics.row(technique));
      }
    }
  }

  public void printSummary() {
    // printSummaryRaw();
    System.out.println("=== TRAIN SET DATA ===");
    printSummaryCSV(results, "experiments/train_data");

    System.out.println("=== TEST SET DATA ===");
    printSummaryCSV(evaluationResults, "experiments/test_data");

    System.out.println("=== BEST PARAMETERS ===");
    findBestParameters(results);
    findBestParameters(evaluationResults);
  }

  // private void printSummaryRaw() {
  //     for (Technique technique : results.rowKeySet()) {
  //         System.out.println("Technique: " + technique);
  //         for (Metric metric : metricsToRecord) {
  //             System.out.println("  Metric: " + metric);
  //             Map<Double, Double> thresholdValueMap = results2.get(technique, metric);
  //             StringBuilder sb = new StringBuilder();
  //             for (Map.Entry<Double, Double> entry : thresholdValueMap.entrySet()) {
  //                 sb.append(String.format("(%.2f,%.2f)", entry.getKey(), entry.getValue() * 100));
  //             }
  //             System.out.println("    Data: " + sb.toString());
  //         }
  //     }
  // }

  private static void printSummaryCSV(Table<Technique, ValueSet, Map<Metric, Double>> results,
      String writeToFolder) {
    for (Technique technique : results.rowKeySet()) {
      StringBuilder sb = new StringBuilder();
      // System.out.println("Technique: " + technique);
      sb.append("Technique: " + technique);
      sb.append(System.lineSeparator());

      // Print header
      List<String> headerStrings = new ArrayList<>();
      headerStrings.add("threshold");
      headerStrings.add("call_depth_factor");
      headerStrings.add("scale_by_coverage");
      for (Metric metric : metricsToRecord) {
        headerStrings.add(metric.toString());
      }
      // System.out.println(String.join(",", headerStrings));
      sb.append(String.join(",", headerStrings));
      sb.append(System.lineSeparator());

      // Print data
      for (ValueSet variedValues : results.row(technique).keySet()) {
        List<String> rowStrings = new ArrayList<>();
        rowStrings.add(String.format("%.2f", variedValues.threshold));
        rowStrings.add(String.format("%.2f", variedValues.callDepthDiscount));
        rowStrings.add(variedValues.scaleByCoverage ? "1" : "0");
        Map<Metric, Double> metricMap = results.get(technique, variedValues);
        for (Metric metric : metricsToRecord) {
          double value = metricMap.get(metric);
          rowStrings.add(String.format("%.2f", value * 100));
        }
        // System.out.println(String.join(",", rowStrings));
        sb.append(String.join(",", rowStrings));
        sb.append(System.lineSeparator());
      }

      if (writeToFolder != null) {
        Path destinationPath = Paths.get(writeToFolder, technique.toString() + ".csv");
        try {
          Files.createDirectories(destinationPath.getParent());
        } catch (IOException e) {
          e.printStackTrace();
        }
        try (PrintWriter out = new PrintWriter(destinationPath.toString())) {
          out.println(sb.toString());
        } catch (FileNotFoundException e) {
          e.printStackTrace();
        }
      }

      System.out.print(sb.toString());
    }
  }

  // Find best parameters for each technique (maximise f-score)
  private static void findBestParameters(Table<Technique, ValueSet, Map<Metric, Double>> results) {
    System.out.println("=== BEST PARAMETERS TO MAXIMISE F-SCORE ===");
    for (Technique technique : results.rowKeySet()) {
      System.out.println("Technique: " + technique);

      Set<ValueSet> maxScoreSet = new HashSet<>();
      double maxFScore = -1.0;
      for (Map.Entry<ValueSet, Map<Metric, Double>> variedValuesEntry : results.row(technique)
          .entrySet()) {
        ValueSet variedValues = variedValuesEntry.getKey();
        Map<Metric, Double> metricValue = variedValuesEntry.getValue();
        double fScore = metricValue.get(Metric.F_SCORE);
        if (fScore == maxFScore) {
          // Same as best, add to max score set.
          maxScoreSet.add(variedValues);
        } else if (fScore > maxFScore) {
          // New best found
          maxScoreSet = new HashSet<>();
          maxScoreSet.add(variedValues);
          maxFScore = fScore;
        }
      }

      System.out.printf("  Best F-Score: %.4f %n", maxFScore);
      for (ValueSet valueSet : maxScoreSet) {
        System.out.printf("  Threshold: %.2f, Call Depth Discount: %.2f, Coverage: %b %n",
            valueSet.threshold, valueSet.callDepthDiscount, valueSet.scaleByCoverage);
      }
    }
  }
}
