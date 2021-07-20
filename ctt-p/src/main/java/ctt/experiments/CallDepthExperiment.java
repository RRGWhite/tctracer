package ctt.experiments;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import ctt.Configuration;
import ctt.Main;
import ctt.SpectraParser;
import ctt.SpectraParser.Metric;
import ctt.Utilities;
import ctt.coverage.CoverageAnalyser.CounterType;
import ctt.coverage.CoverageAnalyser.CoverageStat;
import ctt.metrics.TechniqueMetricsProvider;
import ctt.types.CollectedComputedMetrics;
import ctt.types.Technique;
import ctt.types.TestCollection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CallDepthExperiment implements IExperiment {

  private static final Logger logger = LogManager.getLogger(CallDepthExperiment.class.getName());

  private static Metric[] metricsToRecord = {
      Metric.PRECISION,
      Metric.RECALL,
      Metric.MAP,
      Metric.F_SCORE,
      SpectraParser.Metric.TRUE_POSITIVES,
      SpectraParser.Metric.FALSE_POSITIVES,
      SpectraParser.Metric.FALSE_NEGATIVES
  };

  private static final double MAX_CALL_DEPTH_DISCOUNT = 1.0;
  private static final double MIN_CALL_DEPTH_DISCOUNT = 0.00;
  private static final double STEP_CALL_DEPTH_DISCOUNT = 0.02;

  // Keys: Technique, Metric | Value: Map<threshold value, metric value>
  private Table<Technique, Metric, Map<Double, Double>> results;

  // Keys: Technique, Threshold | Value: Map<Metric, Metric Value>
  private Table<Technique, Double, Map<Metric, Double>> resultsCSV;

  public void run(Configuration config, TestCollection testCollection,
      Table<String, String, Map<CounterType, CoverageStat>> coverageData) {
    this.runOn(Collections.singletonList(testCollection), coverageData);
  }

  public void runOn(List<TestCollection> testCollections,
      Table<String, String, Map<CounterType, CoverageStat>> coverageData) {
    results = HashBasedTable.create();
    resultsCSV = HashBasedTable.create();

    for (double callDepthDiscount = MAX_CALL_DEPTH_DISCOUNT;
        callDepthDiscount >= MIN_CALL_DEPTH_DISCOUNT;
        callDepthDiscount -= STEP_CALL_DEPTH_DISCOUNT) {
      // Round value
      callDepthDiscount = Math.round(callDepthDiscount * 100.0) / 100.0;

      Configuration config = new Configuration.Builder()
          .setCallDepthDiscountFactor(callDepthDiscount)
          // .setTechniqueList(techniqueList)
          .build();

      double variedValue = config.getCallDepthDiscountFactor();
      logger.info("Starting experiment for call depth discount factor {}.", variedValue);
      ArrayList<CollectedComputedMetrics> collectedComputedMetrics =
          SpectraParser.parseTestCollections(config, testCollections, coverageData, false);

      Table<Technique, Metric, Double> techniqueMetrics = HashBasedTable.create();
      for (CollectedComputedMetrics computedMetrics : collectedComputedMetrics) {
        techniqueMetrics.putAll(TechniqueMetricsProvider.computeTechniqueMetrics(
            computedMetrics.getFunctionLevelMetrics().getMetricTable()));
      }

      for (Metric metric : metricsToRecord) {
        Map<Technique, Double> techniqueValueMap = techniqueMetrics.column(metric);
        for (Map.Entry<Technique, Double> techniqueEntry : techniqueValueMap.entrySet()) {
          Technique technique = techniqueEntry.getKey();
          Map<Double, Double> valueMap = Utilities
              .createIfAbsent(results, technique, metric, LinkedHashMap::new);
          valueMap.put(variedValue, techniqueEntry.getValue());
          resultsCSV.put(technique, variedValue, techniqueMetrics.row(technique));
        }
      }
    }
  }

  public void printSummary() {
    printSummaryRaw();
    printSummaryCSV();
  }

  private void printSummaryRaw() {
    for (Technique technique : results.rowKeySet()) {
      System.out.println("Technique: " + technique);
      for (Metric metric : metricsToRecord) {
        System.out.println("  Metric: " + metric);
        Map<Double, Double> thresholdValueMap = results.get(technique, metric);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Double, Double> entry : thresholdValueMap.entrySet()) {
          sb.append(String.format("(%.2f,%.2f)", entry.getKey(), entry.getValue() * 100));
        }
        System.out.println("    Data: " + sb.toString());
      }
    }
  }

  private void printSummaryCSV() {
    for (Technique technique : resultsCSV.rowKeySet()) {
      System.out.println("Technique: " + technique);

      // Print header
      List<String> headerStrings = new ArrayList<>();
      headerStrings.add("call_depth_factor");
      for (Metric metric : metricsToRecord) {
        headerStrings.add(metric.toString());
      }
      System.out.println(String.join(",", headerStrings));

      // Print data
      for (Double threshold : resultsCSV.row(technique).keySet()) {
        List<String> rowStrings = new ArrayList<>();
        rowStrings.add(String.format("%.2f", threshold));
        Map<Metric, Double> metricMap = resultsCSV.get(technique, threshold);
        for (Metric metric : metricsToRecord) {
          double value = metricMap.get(metric);
          rowStrings.add(String.format("%.2f", value * 100));
        }
        System.out.println(String.join(",", rowStrings));
      }

    }
  }
}
