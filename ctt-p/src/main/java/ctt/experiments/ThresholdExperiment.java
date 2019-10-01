package ctt.experiments;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import ctt.Configuration;
import ctt.Main;
import ctt.ProcessHandler;
import ctt.SpectraParser;
import ctt.SpectraParser.Metric;
import ctt.Utilities;
import ctt.coverage.CoverageAnalyser.CounterType;
import ctt.coverage.CoverageAnalyser.CoverageStat;
import ctt.metrics.TechniqueMetricsProvider;
import ctt.types.CollectedComputedMetrics;
import ctt.types.PrecisionRecallPair;
import ctt.types.Technique;
import ctt.types.TestCollection;
import ctt.types.scores.PrecisionRecallCurve;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.plaf.multi.MultiListUI;
import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ThresholdExperiment implements IExperiment {

  private static final Logger logger = LogManager.getLogger(ThresholdExperiment.class.getName());

  private static Metric[] metricsToRecord = {
      Metric.PRECISION,
      Metric.RECALL,
      Metric.F_SCORE,
      Metric.MAP,
      Metric.BPREF
  };

  private static final double MAX_VALUE = 0.999;
  private static final double MIN_VALUE = 0.00;
  private static final double STEP_VALUE = 0.050;

  // Keys: Technique, Metric | Value: Map<threshold value, metric value>
  private Table<Technique, Metric, Map<Double, Double>> results;

  // Keys: Technique, Threshold | Value: Map<Metric, Metric Value>
  private Table<Technique, Double, Map<Metric, Double>> resultsCSV;

  public void run(Configuration config, TestCollection testCollection,
      Table<String, String, Map<CounterType, CoverageStat>> coverageData) {
    this.runOn(config, Collections.singletonList(testCollection), coverageData);
  }

  public void runOn(Configuration config, List<TestCollection> testCollections,
                    Table<String, String, Map<CounterType, CoverageStat>> coverageData) {
    results = HashBasedTable.create();
    resultsCSV = HashBasedTable.create();

    HashMap<Technique, PrecisionRecallCurve> precisionRecallCurves = new HashMap<>();
    //ArrayList<PrecisionRecallCurve> precisionRecallCurves = new ArrayList<>();
    for (double threshold = MAX_VALUE; threshold >= MIN_VALUE; threshold -= STEP_VALUE) {
      threshold = Math.round(threshold * 1000.0) / 1000.0; // Round value to 3 dp

      config.setAllThresholdValues(threshold);

      logger.info("Starting experiment for threshold value {}.",
          config.getCommonThresholdValue());
      ArrayList<CollectedComputedMetrics> collectedComputedMetrics =
          SpectraParser.parseTestCollections(config, testCollections, coverageData, false);

      Table<Technique, Metric, Double> techniqueMetrics = HashBasedTable.create();
      for (CollectedComputedMetrics computedMetrics : collectedComputedMetrics) {

        /*if (Utilities.allProjectsHaveFunctionLevelEvaluation(config)) {
          techniqueMetrics.putAll(TechniqueMetricsProvider.computeTechniqueMetrics(
              computedMetrics.getFunctionLevelMetrics().getMetricTable()));
          techniqueMetrics.putAll(TechniqueMetricsProvider.computeTechniqueMetrics(
              computedMetrics.getAugmentedFunctionLevelMetrics().getMetricTable()));
        }*/

        if (Utilities.allProjectsHaveClassLevelEvaluation(config)) {
          techniqueMetrics.putAll(TechniqueMetricsProvider.computeTechniqueMetrics(
              computedMetrics.getClassLevelMetrics().getMetricTable()));
          techniqueMetrics.putAll(TechniqueMetricsProvider.computeTechniqueMetrics(
              computedMetrics.getAugmentedClassLevelMetrics().getMetricTable()));
        }

        Map<Technique, Double> precisionForTechnique = techniqueMetrics.column(Metric.PRECISION);
        Map<Technique, Double> recallForTechnique = techniqueMetrics.column(Metric.RECALL);

        for (Map.Entry<Technique, Double> entry : precisionForTechnique.entrySet()) {
          Technique technique = entry.getKey();
          if (technique.equals(Technique.LAST_CALL_BEFORE_ASSERT)
              || technique.equals(Technique.LAST_CALL_BEFORE_ASSERT_CLASS)
              || technique.equals(Technique.LAST_CALL_BEFORE_ASSERT_MULTI)
              || technique.equals(Technique.NS_CONTAINS)
              || technique.equals(Technique.NS_CONTAINS_CLASS)
              || technique.equals(Technique.NS_CONTAINS_MULTI)) {
            continue;
          }

          double precision = entry.getValue();
          double recall = recallForTechnique.get(technique);

          PrecisionRecallCurve precisionRecallCurve = precisionRecallCurves.get(technique);
          if (precisionRecallCurve == null) {
            precisionRecallCurve = new PrecisionRecallCurve(technique, new ArrayList<>(),
                37, 37000);
          }

          precisionRecallCurve.getPrPoints().add(new PrecisionRecallPair(precision, recall));
          precisionRecallCurves.put(technique, precisionRecallCurve);
        }
      }

      for (Metric metric : metricsToRecord) {
        Map<Technique, Double> techniqueValueMap = techniqueMetrics.column(metric);
        for (Map.Entry<Technique, Double> techniqueEntry : techniqueValueMap.entrySet()) {
          Technique technique = techniqueEntry.getKey();

          Map<Double, Double> thresholdValueMap = Utilities.createIfAbsent(results, technique,
              metric, LinkedHashMap::new);
          thresholdValueMap.put(config.getCommonThresholdValue(), techniqueEntry.getValue());

          resultsCSV.put(technique, config.getCommonThresholdValue(),
              techniqueMetrics.row(technique));
        }
      }
    }

    getAUPRCResults(precisionRecallCurves);
  }

  public void printSummary() {
    //printSummaryRaw();
    printSummaryCSV();
  }

  private void getAUPRCResults(HashMap<Technique, PrecisionRecallCurve> precisionRecallCurves) {
    String toolJarPath = "tools/auc.jar";
    String inputFilePath = "tools/pr-input.txt";
    for (Map.Entry<Technique, PrecisionRecallCurve> entry :
        precisionRecallCurves.entrySet()) {
      Technique technique = entry.getKey();
      PrecisionRecallCurve precisionRecallCurve = entry.getValue();
      String input = "";
      for (PrecisionRecallPair prPoint : precisionRecallCurve.getPrPoints()) {
        input += prPoint.getRecall() + "\t" + prPoint.getPrecision() + "\n";
      }

      Utilities.writeStringToFile(input, inputFilePath, false);

      String cmdString = "java -jar " + toolJarPath + " " + inputFilePath + " PR " +
              precisionRecallCurve.getTotalTruePositives() + " " +
              precisionRecallCurve.getTotalTrueNegatives();

      ArrayList<String> toolOutput = ProcessHandler.executeCommand(cmdString, new File("./"));
      String aucStr = null;
      for (String line : toolOutput) {
        if (line.contains("Area Under the Curve for Precision - Recall")) {
          aucStr = line.split(" is ")[1];
        }
      }

      double auc = -1;
      if (aucStr != null) {
        auc = Double.parseDouble(aucStr);
      }

      /*if (technique.equals(Technique.COMBINED)) {
        Utilities.logger.debug("DEBUGGING INCONSISTENT COMBINED AUC SCORES");
      }*/

      Map<Metric, Double> aucMetric = new HashMap<>();
      aucMetric.put(Metric.AUPRC, auc);
      resultsCSV.put(technique, -1.0, aucMetric);
    }
  }

  private void printSummaryRaw() {
    for (Technique technique : results.rowKeySet()) {
      System.out.println("Technique: " + technique);
      for (Metric metric : metricsToRecord) {
        System.out.println("  Metric: " + metric);
        Map<Double, Double> thresholdValueMap = results.get(technique, metric);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Double, Double> entry : thresholdValueMap.entrySet()) {
          sb.append(String.format("(%.3f,%.3f)", entry.getKey(), entry.getValue() * 100));
        }
        System.out.println("    Data: " + sb.toString());
      }
    }
  }

  private void printSummaryCSV() {
    for (Technique technique : resultsCSV.rowKeySet()) {
      if (technique.equals(Technique.LAST_CALL_BEFORE_ASSERT)
          || technique.equals(Technique.LAST_CALL_BEFORE_ASSERT_CLASS)
          || technique.equals(Technique.LAST_CALL_BEFORE_ASSERT_MULTI)
          || technique.equals(Technique.NS_CONTAINS)
          || technique.equals(Technique.NS_CONTAINS_CLASS)
          || technique.equals(Technique.NS_CONTAINS_MULTI)) {
        continue;
      }

      System.out.println("Technique: " + technique);

      // Print header
      List<String> headerStrings = new ArrayList<>();
      headerStrings.add("threshold");
      for (Metric metric : metricsToRecord) {
        headerStrings.add(metric.toString());
      }
      System.out.println(String.join(",", headerStrings));

      // Print data
      for (Double threshold : resultsCSV.row(technique).keySet()) {
        if (threshold == -1.0) {
          continue;
        }

        List<String> rowStrings = new ArrayList<>();
        rowStrings.add(String.format("%.3f", threshold));
        Map<Metric, Double> metricMap = resultsCSV.get(technique, threshold);
        for (Metric metric : metricsToRecord) {
          double value = metricMap.get(metric);
          rowStrings.add(String.format("%.3f", value * 100));
        }
        System.out.println(String.join(",", rowStrings));
      }
      System.out.println("AUPRC: " + resultsCSV.get(technique, -1.0).get(Metric.AUPRC));
    }
  }
}
