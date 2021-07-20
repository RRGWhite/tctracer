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
import ctt.metrics.ClassLevelMetricsProvider;
import ctt.metrics.FunctionLevelMetricsProvider;
import ctt.metrics.TechniqueMetricsProvider;
import ctt.types.CollectedComputedMetrics;
import ctt.types.EvaluationMetrics;
import ctt.types.Method;
import ctt.types.PrecisionRecallPair;
import ctt.types.Technique;
import ctt.types.TestCollection;
import ctt.types.scores.PrecisionRecallCurve;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ThresholdExperiment implements IExperiment {

  private static final Logger logger = LogManager.getLogger(ThresholdExperiment.class.getName());
  private static Configuration.Level levelToTest = Configuration.Level.CLASS;

  private static Metric[] metricsToRecord = {
      Metric.PRECISION,
      Metric.RECALL,
      Metric.F_SCORE,
      Metric.MAP,
      //Metric.BPREF
      SpectraParser.Metric.TRUE_POSITIVES,
      SpectraParser.Metric.FALSE_POSITIVES,
      SpectraParser.Metric.FALSE_NEGATIVES
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

    ArrayList<CollectedComputedMetrics> collectedComputedMetrics =
        SpectraParser.parseTestCollections(config, testCollections, coverageData, false);

    HashMap<Technique, PrecisionRecallCurve> precisionRecallCurves = new HashMap<>();
    //ArrayList<PrecisionRecallCurve> precisionRecallCurves = new ArrayList<>();
    for (double threshold = MAX_VALUE; threshold >= MIN_VALUE; threshold -= STEP_VALUE) {
      threshold = Math.round(threshold * 1000.0) / 1000.0; // Round value to 3 dp

      config.setAllThresholdValues(threshold);

      logger.info("Starting experiment for threshold value {}.",
          config.getCommonThresholdValue());

      Table<Technique, Metric, Double> techniqueMetrics = HashBasedTable.create();
      for (CollectedComputedMetrics computedMetrics : collectedComputedMetrics) {
        if (levelToTest.equals(Configuration.Level.METHOD)) {
          if (Utilities.allProjectsHaveFunctionLevelEvaluation(config)) {
            Table<Technique, SpectraParser.Metric, Double> pureMetrics =
                TechniqueMetricsProvider.computeTechniqueMetrics(
                    FunctionLevelMetricsProvider.computeEvaluationMetrics(config,
                        FunctionLevelMetricsProvider.buildCandidateSetTable(config,
                            computedMetrics.getFunctionLevelMetrics().getAggregatedResults()),
                        Main.ScoreType.PURE));
            techniqueMetrics.putAll(pureMetrics);

            Table<Technique, SpectraParser.Metric, Double> augMetrics =
                TechniqueMetricsProvider.computeTechniqueMetrics(
                    FunctionLevelMetricsProvider.computeEvaluationMetrics(config,
                        FunctionLevelMetricsProvider.buildCandidateSetTable(config,
                            computedMetrics.getAugmentedFunctionLevelMetrics().getAggregatedResults()),
                        Main.ScoreType.AUGMENTED));
            techniqueMetrics.putAll(augMetrics);
          }
        } else if (levelToTest.equals(Configuration.Level.CLASS)) {
          if (Utilities.allProjectsHaveClassLevelEvaluation(config)) {
            Table<Technique, SpectraParser.Metric, Double> pureMetrics =
                TechniqueMetricsProvider.computeTechniqueMetrics(
                    ClassLevelMetricsProvider.computeEvaluationMetrics(config,
                        ClassLevelMetricsProvider.computeTraceabilityPredictions(config,
                            computedMetrics.getClassLevelMetrics().getClassScoresTensor(),
                            Main.ScoreType.PURE)));
            techniqueMetrics.putAll(pureMetrics);

            Table<Technique, SpectraParser.Metric, Double> augMetrics =
                TechniqueMetricsProvider.computeTechniqueMetrics(
                    ClassLevelMetricsProvider.computeEvaluationMetrics(config,
                        ClassLevelMetricsProvider.computeTraceabilityPredictions(config,
                            computedMetrics.getAugmentedClassLevelMetrics().getClassScoresTensor(),
                            Main.ScoreType.AUGMENTED)));
            techniqueMetrics.putAll(augMetrics);
          }
        }

        Map<Technique, Double> precisionForTechnique = techniqueMetrics.column(Metric.PRECISION);
        Map<Technique, Double> recallForTechnique = techniqueMetrics.column(Metric.RECALL);

        for (Map.Entry<Technique, Double> entry : precisionForTechnique.entrySet()) {
          Technique technique = entry.getKey();
          if (technique.equals(Technique.LCBA)
              || technique.equals(Technique.LCBA_CLASS)
              || technique.equals(Technique.LCBA_MULTI)
              || technique.equals(Technique.NC)
              || technique.equals(Technique.NC_CLASS)
              || technique.equals(Technique.NC_MULTI)
              || technique.equals(Technique.NCC)
              || technique.equals(Technique.NCC_CLASS)
              || technique.equals(Technique.NCC_MULTI)
              || technique.equals(Technique.STATIC_LCBA)
              || technique.equals(Technique.STATIC_LCBA_CLASS)
              || technique.equals(Technique.STATIC_LCBA_MULTI)
              || technique.equals(Technique.STATIC_NC)
              || technique.equals(Technique.STATIC_NC_CLASS)
              || technique.equals(Technique.STATIC_NC_MULTI)
              || technique.equals(Technique.STATIC_NCC)
              || technique.equals(Technique.STATIC_NCC_CLASS)
              || technique.equals(Technique.STATIC_NCC_MULTI)) {
            continue;
          }

          double precision = entry.getValue();
          double recall = recallForTechnique.get(technique);

          int apacheAntTotalTruePositives;
          int apacheAntTotalTrueNegatives;

          int commonsIoTotalTruePositives;
          int commonsIoTotalTrueNegatives;

          int commonsLangTotalTruePositives;
          int commonsLangTotalTrueNegatives;

          int jfreechartTotalTruePositives;
          int jfreechartTotalTrueNegatives;

          int gsonTotalTruePositives;
          int gsonTotalTrueNegatives;

          if (levelToTest.equals(Configuration.Level.CLASS)) {
            apacheAntTotalTruePositives = 79;
            apacheAntTotalTrueNegatives = 79000;

            commonsIoTotalTruePositives = 56;
            commonsIoTotalTrueNegatives = 56000;

            commonsLangTotalTruePositives = 85;
            commonsLangTotalTrueNegatives = 85000;

            jfreechartTotalTruePositives = 388;
            jfreechartTotalTrueNegatives = 388000;

            gsonTotalTruePositives = 0;
            gsonTotalTrueNegatives = 0;
          } else {
            apacheAntTotalTruePositives = 0;
            apacheAntTotalTrueNegatives = 0;

            commonsIoTotalTruePositives = 42;
            commonsIoTotalTrueNegatives = 42000;

            commonsLangTotalTruePositives = 78;
            commonsLangTotalTrueNegatives = 78000;

            jfreechartTotalTruePositives = 37;
            jfreechartTotalTrueNegatives = 37000;

            gsonTotalTruePositives = 55;
            gsonTotalTrueNegatives = 55000;
          }

          int totalTruePositives;
          int totalTrueNegatives;

          switch (config.getProjects().get(0)) {
            case "apache-ant":
              totalTruePositives = apacheAntTotalTruePositives;
              totalTrueNegatives = apacheAntTotalTrueNegatives;
              break;
            case "commons-io":
              totalTruePositives = commonsIoTotalTruePositives;
              totalTrueNegatives = commonsIoTotalTrueNegatives;
              break;
            case "commons-lang":
              totalTruePositives = commonsLangTotalTruePositives;
              totalTrueNegatives = commonsLangTotalTrueNegatives;
              break;
            case "jfreechart":
              totalTruePositives = jfreechartTotalTruePositives;
              totalTrueNegatives = jfreechartTotalTrueNegatives;
              break;
            case "gson":
              totalTruePositives = gsonTotalTruePositives;
              totalTrueNegatives = gsonTotalTrueNegatives;
              break;
            default:
              throw new IllegalStateException("project TP/FP not registered");
          }

          PrecisionRecallCurve precisionRecallCurve = precisionRecallCurves.get(technique);
          if (precisionRecallCurve == null) {
            precisionRecallCurve = new PrecisionRecallCurve(technique, new ArrayList<>(),
                totalTruePositives, totalTrueNegatives);
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

      ArrayList<String> toolOutput = ProcessHandler.executeCommand(cmdString,
          new File("./"), new File("cli-output/process-output.txt"));
      String aucStr = null;
      for (String line : toolOutput) {
        if (line.contains("Area Under the Curve for Precision - Recall")) {
          aucStr = line.split(" is ")[1];
        }
      }

      double auc = -1;
      if (aucStr != null) {
        auc = Double.parseDouble(aucStr);
      } else {
        System.out.println("Debugging AUC");
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
      if (technique.equals(Technique.LCBA)
          || technique.equals(Technique.LCBA_CLASS)
          || technique.equals(Technique.LCBA_MULTI)
          || technique.equals(Technique.NC)
          || technique.equals(Technique.NC_CLASS)
          || technique.equals(Technique.NC_MULTI)
          || technique.equals(Technique.NCC)
          || technique.equals(Technique.NCC_CLASS)
          || technique.equals(Technique.NCC_MULTI)
          || technique.equals(Technique.STATIC_LCBA)
          || technique.equals(Technique.STATIC_LCBA_CLASS)
          || technique.equals(Technique.STATIC_LCBA_MULTI)
          || technique.equals(Technique.STATIC_NC)
          || technique.equals(Technique.STATIC_NC_CLASS)
          || technique.equals(Technique.STATIC_NC_MULTI)
          || technique.equals(Technique.STATIC_NCC)
          || technique.equals(Technique.STATIC_NCC_CLASS)
          || technique.equals(Technique.STATIC_NCC_MULTI)) {
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
