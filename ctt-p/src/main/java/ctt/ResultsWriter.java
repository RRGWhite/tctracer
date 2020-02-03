package ctt;

import com.google.common.collect.Table;
import ctt.SpectraParser.Metric;
import ctt.types.MethodValuePair;
import ctt.types.Technique;
import ctt.types.scores.clazz.ClassScoresTensor;
import ctt.types.scores.method.MethodScoresTensor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;

/**
 * Created by RRGWhite on 03/07/2019
 */
public abstract class ResultsWriter {

  public static void writeOutConfig(Configuration config) {
    String fileName = "results/run-configs/" + Utilities.programStartTimeStamp + ".cfg";
    Utilities.writeStringToFile(config.toString(), fileName, false);
  }

  public static void writeOutMethodLevelTraceabilityScores(
      Configuration config, MethodScoresTensor methodScoresTensor, Main.ScoreType scoreType) {
    String csvHeader = "test-class,tested-class,score\n";
    for (Technique technique : Utilities.getTechniques(config, Configuration.Level.CLASS, scoreType)) {
      String fileName = "results/test-to-function/" + Utilities.programStartTimeStamp + "/" +
          Utilities.programStartTimeStamp + "-test-to-function-scores-" + technique + ".csv";
      Utilities.writeStringToFile(csvHeader, fileName, false);

      for (String testFqn : methodScoresTensor.getTestsFqns()) {
        for (String functionFqn : methodScoresTensor.getFunctionFqns()) {
          String rowString = testFqn + "," + functionFqn +
              "," + methodScoresTensor.getSingleScoreForTestFunctionPair(testFqn, functionFqn,
              technique) + "\n";
          Utilities.writeStringToFile(rowString, fileName, true);
        }
      }
    }
  }

  public static void writeOutClassLevelTraceabilityScores(
      Configuration config, ClassScoresTensor classScoresTensor, Main.ScoreType scoreType) {
    String csvHeader = "test-class,tested-class,score\n";
    for (Technique technique : Utilities.getTechniques(config, Configuration.Level.CLASS, scoreType)) {
      String fileName = "results/class-to-class/" + Utilities.programStartTimeStamp + "/" +
          Utilities.programStartTimeStamp + "-class-to-class-scores-" + technique + ".csv";
      Utilities.writeStringToFile(csvHeader, fileName, false);

      for (String testClassFqn : classScoresTensor.getTestClassFqns()) {
        for (String nonTestClassFqn : classScoresTensor.getNonTestClassFqns()) {
          String rowString = testClassFqn + "," + nonTestClassFqn + "," +
              classScoresTensor.getSingleScoreForTestClassNonTestClassPair(testClassFqn, nonTestClassFqn, technique) + "\n";
          Utilities.writeStringToFile(rowString, fileName, true);
        }
      }
    }
  }

  public static void writeOutMethodLevelTraceabilityPredictions(Configuration config,
      Table<String, Technique, SortedSet<MethodValuePair>> candidateTable,
                                                                Main.ScoreType scoreType) {
    String csvHeader = "test,tested-method\n";
    String projects = Utilities.getProjectsString(config);
    String rq = scoreType == Main.ScoreType.PURE ? "rq1" : "rq4";
    for (Entry<Technique, Map<String, SortedSet<MethodValuePair>>> col :
        candidateTable.columnMap().entrySet()) {
      Technique technique = col.getKey();
      String fileName = "results/test-to-function/" + Utilities.programStartTimeStamp + "/" +
          Utilities.programStartTimeStamp + "-" + projects + "-test-to-function-predictions" +
          "-" + technique.toString().toLowerCase() + "-" + rq + ".csv";
      Utilities.writeStringToFile(csvHeader, fileName, false);

      String file2Name = "../../artefacts/method-level-predicted-links/" + projects + "-test-to-function-predictions" +
          "-" + technique.toString().toLowerCase() + "-" + rq + ".csv";
      Utilities.writeStringToFile(csvHeader, file2Name, false);

      for (Entry<String, SortedSet<MethodValuePair>> cell : col.getValue().entrySet()) {
        for (MethodValuePair functionScorePair : cell.getValue()) {
          if (functionScorePair != null && config.getThresholdData().get(technique) != null
              && functionScorePair.getValue() >= config.getThresholdData().get(technique)) {
            String rowString = "\"" + cell.getKey() + "\",\"" + functionScorePair.getMethod() +
                "\"\n";
            Utilities.writeStringToFile(rowString, fileName, true);
            Utilities.writeStringToFile(rowString, file2Name, true);
          }
        }
      }
    }
  }

  public static void writeOutClassLevelTraceabilityPredictions(Configuration config,
      Table<Technique, String, List<String>> traceabilityPredictions, Main.ScoreType scoreType) {
    String csvHeader = "test-class,tested-class\n";
    String projects = Utilities.getProjectsString(config);
    String rq = scoreType == Main.ScoreType.PURE ? "rq2" : "rq3";
    for (Entry<Technique, Map<String, List<String>>> row :
        traceabilityPredictions.rowMap().entrySet()) {
      String fileName = "results/class-to-class/" + Utilities.programStartTimeStamp + "/" +
          Utilities.programStartTimeStamp + "-" + projects + "-class-to-class-predictions-" +
          row.getKey().toString().toLowerCase() + "-" + rq + ".csv";
      Utilities.writeStringToFile(csvHeader, fileName, false);

      String file2Name = "../../artefacts/class-level-predicted-links/" + projects + "-class-to" +
          "-tested-class-predictions" + "-" + row.getKey().toString().toLowerCase() + "-" + rq +
          ".csv";
      Utilities.writeStringToFile(csvHeader, file2Name, false);

      for (Entry<String, List<String>> cell : row.getValue().entrySet()) {
        for (String testedClass : cell.getValue()) {
          if (testedClass != null) {
            String rowString = cell.getKey() + "," + testedClass + "\n";
            Utilities.writeStringToFile(rowString, fileName, true);
            Utilities.writeStringToFile(rowString, file2Name, true);
          }
        }
      }
    }
  }

  public static void writeOutFunctionLevelValidationSummary(Main.ScoreType scoreType,
                                                            Configuration config,
                                                            Table<Technique, Metric, Double> techniqueMetrics) {
    Metric[] metricOrder = {
        Metric.PRECISION,
        Metric.RECALL,
        Metric.MAP,
        Metric.F_SCORE
    };

    String fileName = "results/test-to-function-evaluation/" + Utilities.programStartTimeStamp +
        "/" + Utilities.programStartTimeStamp + "-" + Utilities.getProjectsString(config)  + "-"  +
        (config.isUseWeightedCombination() ? "weighted" : "unweighted") + "-" +
        config.getScoreCombinationMethod().toString().toLowerCase() +
        (scoreType.equals(Main.ScoreType.AUGMENTED) ? "-augmented" : "") + ".csv";

    String csvHeader = "technique,precision,recall,map,f1-score\n";
    Utilities.writeStringToFile(csvHeader, fileName, false);

    String dataString = "";
    for (Technique technique : Utilities.getTechniques(config, Configuration.Level.METHOD,
        scoreType)) {
      dataString += technique.toString() + ",";
      for (Metric metric : metricOrder) {
        dataString += String.format("%.4f", techniqueMetrics.get(technique, metric)) + ",";
      }
      dataString = dataString.substring(0, dataString.length() - 1) + "\n";
    }

    dataString += "\n" + config.toString() + "\n";
    Utilities.writeStringToFile(dataString, fileName, true);
  }

  public static void writeOutClassLevelValidationSummary(Main.ScoreType scoreType,
                                                         Configuration config,
                                                         Table<Technique, Metric, Double> techniqueMetrics) {
    Metric[] metricOrder = {
        Metric.PRECISION,
        Metric.RECALL,
        Metric.MAP,
        Metric.F_SCORE
    };

    Technique[] techniques = Utilities.getTechniques(config, Configuration.Level.CLASS, scoreType);

    String fileName = "results/class-to-class-evaluation/" + Utilities.programStartTimeStamp +
        "/" + Utilities.programStartTimeStamp + "-" + Utilities.getProjectsString(config) + "-"  +
        (config.isUseWeightedCombination() ? "weighted" : "unweighted") + "-" +
        config.getScoreCombinationMethod().toString().toLowerCase() +
        (scoreType.equals(Main.ScoreType.AUGMENTED) ? "-augmented" : "") + ".csv";

    String csvHeader = "technique,precision,recall,map,f1-score\n";
    Utilities.writeStringToFile(csvHeader, fileName, false);

    String dataString = "";
    for (Technique technique : techniques) {
      dataString += technique.toString() + ",";
      for (Metric metric : metricOrder) {
        dataString += String.format("%.4f", techniqueMetrics.get(technique, metric)) + ",";
      }
      dataString = dataString.substring(0, dataString.length() - 1) + "\n";
    }

    dataString += "\n" + config.toString() + "\n";
    Utilities.writeStringToFile(dataString, fileName, true);
  }

  public static void writeOutMethodLevelGroundTruth(Configuration config,
      Map<String, SortedSet<MethodValuePair>> groundTruthMap) {
    String projects = Utilities.getProjectsString(config);
    String fileName = "../../artefacts/method-level-ground-truth/" + projects +
        ".csv";

    String csvHeader = "project,test-fqn,tested-method-fqn\n";
    Utilities.writeStringToFile(csvHeader, fileName, false);

    String dataString = "";
    for (Entry<String, SortedSet<MethodValuePair>> entry : groundTruthMap.entrySet()) {
      for (MethodValuePair methodValuePair : entry.getValue()) {
        dataString += projects + ",\"" + entry.getKey() + "\",\"" +
            methodValuePair.getMethod() + "\"\n";
      }
    }

    Utilities.writeStringToFile(dataString, fileName, true);
  }
}
