package ctt;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import ctt.SpectraParser.Metric;
import ctt.types.EvaluationMetrics;
import ctt.types.MethodValuePair;
import ctt.types.Technique;
import ctt.types.scores.clazz.ClassScoresTensor;
import ctt.types.scores.clazz.PureClassScoresTensor;
import ctt.types.scores.method.MethodScoresTensor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

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
    for (Technique technique : Utilities.getTechniques(config, Configuration.Level.METHOD,
        scoreType)) {
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

  public static void writeOutMethodLevelGroundTruthScores(
      Configuration config, MethodScoresTensor methodScoresTensor, Main.ScoreType scoreType,
      Map<String, SortedSet<MethodValuePair>> groundTruthMap) {
    StringBuilder csvStringBuilder = new StringBuilder();
    csvStringBuilder.append("test;function");
    for (Technique technique : config.getMethodLevelTechniqueList()) {
      csvStringBuilder.append(";");
      csvStringBuilder.append(technique.toString().toLowerCase());
    }
    csvStringBuilder.append("\n");

    for (Entry<String, SortedSet<MethodValuePair>> entry : groundTruthMap.entrySet()) {
      String testFqn = entry.getKey();
      for (MethodValuePair mvp : entry.getValue()) {
        csvStringBuilder.append("\"");
        csvStringBuilder.append(testFqn);
        csvStringBuilder.append("\"");
        csvStringBuilder.append(";");
        csvStringBuilder.append("\"");
        csvStringBuilder.append(mvp.getMethod());
        csvStringBuilder.append("\"");

        for (Technique technique : config.getMethodLevelTechniqueList()) {
          double score = methodScoresTensor.getSingleScoreForTestFunctionPair(entry.getKey(),
              mvp.getMethod(), technique);
          csvStringBuilder.append(";");
          csvStringBuilder.append(score);
        }

        csvStringBuilder.append("\n");
      }
    }

    String projects = Utilities.getProjectsString(config);
    String fileName = "results/test-to-function/" + Utilities.programStartTimeStamp + "/" +
        Utilities.programStartTimeStamp + "-" + projects + "-test-to-function-" +
        scoreType.toString().toLowerCase() + "-ground-truth-scores.csv";
    Utilities.writeStringToFile(csvStringBuilder.toString(), fileName, false);
  }

  public static void writeOutMissedMethodLevelGroundTruthScores(
      Configuration config, MethodScoresTensor methodScoresTensor, Main.ScoreType scoreType,
      Map<String, SortedSet<MethodValuePair>> groundTruthMap) {

    HashSet<String> testsForMissedLinks = new HashSet<>();
    for (Entry<String, SortedSet<MethodValuePair>> entry : groundTruthMap.entrySet()) {
      String testFqn = entry.getKey();
      for (MethodValuePair mvp : entry.getValue()) {
        double combinedScore = methodScoresTensor.getSingleScoreForTestFunctionPair(entry.getKey(),
            mvp.getMethod(), Technique.COMBINED);

        if (combinedScore < config.getThresholdData().get(Technique.COMBINED)) {
          testsForMissedLinks.add(testFqn);
        }
      }
    }

    StringBuilder csvStringBuilder = new StringBuilder();
    csvStringBuilder.append("test;function");
    for (Technique technique : config.getMethodLevelTechniqueList()) {
      csvStringBuilder.append(";");
      csvStringBuilder.append(technique.toString().toLowerCase());
    }
    csvStringBuilder.append("\n");

    for (String testFqn : testsForMissedLinks) {
      //methodScoresTensor.getAllScoresForTestFunctionPair()
        for (Technique technique : config.getMethodLevelTechniqueList()) {
          csvStringBuilder.append(technique.toString().toLowerCase());
          csvStringBuilder.append("\n");

          Map<String, Double> scores = methodScoresTensor.getScoresForTestForTechnique(testFqn,
              technique);
          for (Entry<String, Double> scoreEntry : scores.entrySet()) {
            String functionFqn = scoreEntry.getKey();
            Double score = scoreEntry.getValue();

            /*boolean isGroundTruthLink = false;

            if ()*/
            csvStringBuilder.append("\"");
            csvStringBuilder.append(testFqn);
            csvStringBuilder.append("\"");
            csvStringBuilder.append(";");
            csvStringBuilder.append("\"");
            csvStringBuilder.append(functionFqn);
            csvStringBuilder.append("\"");
            csvStringBuilder.append(";");
            csvStringBuilder.append(score);
            csvStringBuilder.append("\n");
          }
        }

        csvStringBuilder.append("\n");
    }

    String projects = Utilities.getProjectsString(config);
    String fileName = "results/test-to-function/" + Utilities.programStartTimeStamp + "/" +
        Utilities.programStartTimeStamp + "-" + projects + "-test-to-function-" +
        scoreType.toString().toLowerCase() + "-missed-ground-truth-scores.csv";
    Utilities.writeStringToFile(csvStringBuilder.toString(), fileName, false);
  }

  public static void writeOutCombinedFalsePositives(
      Configuration config, MethodScoresTensor methodScoresTensor, Main.ScoreType scoreType,
      Table<String, Technique, SortedSet<MethodValuePair>> candidateTable) {

    HashMap<String, ArrayList<String>> falsePositivesMap = new HashMap<>();

    for (Map.Entry<String, Map<Technique, SortedSet<MethodValuePair>>> testEntry : candidateTable
        .rowMap().entrySet()) {
      String test = testEntry.getKey();
      falsePositivesMap.put(test, new ArrayList<>());

      Map<Technique, SortedSet<MethodValuePair>> techniqueMap = testEntry.getValue();
      SortedSet<MethodValuePair> groundTruthPairSet = techniqueMap.get(Technique.GROUND_TRUTH);

      if (groundTruthPairSet != null) {
        Set<String> groundTruthSet = new HashSet<>();
        for (MethodValuePair methodValuePair : groundTruthPairSet) {
          groundTruthSet.add(methodValuePair.getMethod());
        }

        Technique technique = Technique.COMBINED;
        SortedSet<MethodValuePair> candidateMethodSet = candidateTable.get(test, technique);
        if (candidateMethodSet == null) {
          candidateMethodSet = new TreeSet<>(); // if no entry in the candidate table for the given test and technique, treat as empty set.
        }

        for (MethodValuePair methodValuePair : candidateMethodSet) {
          if (!groundTruthSet.contains(methodValuePair.getMethod())) {
            falsePositivesMap.get(test).add(methodValuePair.getMethod());
          }
        }
      }
    }

    StringBuilder csvStringBuilder = new StringBuilder();
    csvStringBuilder.append("test;function");
    for (Technique technique : config.getMethodLevelTechniqueList()) {
      csvStringBuilder.append(";");
      csvStringBuilder.append(technique.toString().toLowerCase());
    }
    csvStringBuilder.append("\n");

    for (Entry<String, ArrayList<String>> entry : falsePositivesMap.entrySet()) {
      String testFqn = entry.getKey();
      for (String function : entry.getValue()) {
        csvStringBuilder.append("\"");
        csvStringBuilder.append(testFqn);
        csvStringBuilder.append("\"");
        csvStringBuilder.append(";");
        csvStringBuilder.append("\"");
        csvStringBuilder.append(function);
        csvStringBuilder.append("\"");

        for (Technique technique : config.getMethodLevelTechniqueList()) {
          double score = methodScoresTensor.getSingleScoreForTestFunctionPair(entry.getKey(),
              function, technique);
          csvStringBuilder.append(";");
          csvStringBuilder.append(score);
        }

        csvStringBuilder.append("\n");
      }
    }

    String projects = Utilities.getProjectsString(config);
    String fileName = "results/test-to-function/" + Utilities.programStartTimeStamp + "/" +
        Utilities.programStartTimeStamp + "-" + projects + "-test-to-function-" +
        scoreType.toString().toLowerCase() + "-false-positive-scores.csv";
    Utilities.writeStringToFile(csvStringBuilder.toString(), fileName, false);
  }

  public static void writeOutClassLevelTraceabilityScores(
      Configuration config, ClassScoresTensor classScoresTensor, Main.ScoreType scoreType) {
    String csvHeader = "test-class;tested-class,score\n";
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

  public static void writeOutClassLevelGroundTruthScores(
      Configuration config, ClassScoresTensor classScoresTensor, Main.ScoreType scoreType,
      Map<String, List<String>> groundTruthMap) {
    StringBuilder csvStringBuilder = new StringBuilder();
    csvStringBuilder.append("test-class,tested-class");
    for (Technique technique : config.getMethodLevelTechniqueList()) {
      csvStringBuilder.append(";");
      csvStringBuilder.append(technique.toString().toLowerCase());
    }
    csvStringBuilder.append("\n");

    for (Entry<String, List<String>> entry : groundTruthMap.entrySet()) {
      String testClassFqn = entry.getKey();
      for (String testedClassFqn : entry.getValue()) {
        csvStringBuilder.append("\"");
        csvStringBuilder.append(testClassFqn);
        csvStringBuilder.append("\"");
        csvStringBuilder.append(";");
        csvStringBuilder.append("\"");
        csvStringBuilder.append(testedClassFqn);
        csvStringBuilder.append("\"");

        for (Technique technique : config.getClassLevelTechniqueList()) {
          double score = classScoresTensor.getSingleScoreForTestClassNonTestClassPair(
              testClassFqn, testedClassFqn, technique);
          csvStringBuilder.append(";");
          csvStringBuilder.append(score);
        }

        csvStringBuilder.append("\n");
      }
    }

    String projects = Utilities.getProjectsString(config);
    String fileName = "results/class-to-class/" + Utilities.programStartTimeStamp + "/" +
        Utilities.programStartTimeStamp + "-" + projects + "-class-to-class-" +
        scoreType.toString().toLowerCase() + "-ground-truth-scores.csv";
    Utilities.writeStringToFile(csvStringBuilder.toString(), fileName, false);
  }

  public static void writeOutMethodLevelTraceabilityPredictions(Configuration config,
      Table<String, Technique, SortedSet<MethodValuePair>> candidateTable,
                                                                Main.ScoreType scoreType) {
    String csvHeader = "test,tested-method,score\n";
    String projects = Utilities.getProjectsString(config);
    String rq = scoreType == Main.ScoreType.PURE ? "rq1" : "rq4";
    for (Entry<Technique, Map<String, SortedSet<MethodValuePair>>> col :
        candidateTable.columnMap().entrySet()) {
      Technique technique = col.getKey();
      String fileName = "results/test-to-function/" + Utilities.programStartTimeStamp + "/" +
          Utilities.programStartTimeStamp + "-" + projects + "-test-to-function-predictions" +
          "-" + technique.toString().toLowerCase() + "-" + rq + ".csv";
      Utilities.writeStringToFile(csvHeader, fileName, false);

      /*String file2Name = "../../artefacts/method-level-predicted-links/" + projects + "-test-to"
          + "-function-predictions" +
          "-" + technique.toString().toLowerCase() + "-" + rq + ".csv";*/
      String file2Name = "results/for-relatest/method-level-predicted-links/" + projects + "-" +
          technique.toString().toLowerCase() + ".tct";
      Utilities.writeStringToFile(csvHeader, file2Name, false);

      for (Entry<String, SortedSet<MethodValuePair>> cell : col.getValue().entrySet()) {
        for (MethodValuePair functionScorePair : cell.getValue()) {
          if (functionScorePair != null && config.getThresholdData().get(technique) != null
              && functionScorePair.getValue() >= config.getThresholdData().get(technique)) {
            String file1RowString = "\"" + cell.getKey() + "\",\"" + functionScorePair.getMethod() +
                "\",\"" + functionScorePair.getValue() + "\"\n";
            Utilities.writeStringToFile(file1RowString, fileName, true);

            String file2RowString = "\"" + cell.getKey() + "\";\"" + functionScorePair.getMethod() +
                "\";\"" + functionScorePair.getValue() + "\"\n";
            Utilities.writeStringToFile(file2RowString, file2Name, true);
          }
        }
      }
    }
  }

  public static void writeOutClassLevelTraceabilityPredictions(Configuration config,
      Table<Technique, String, List<String>> traceabilityPredictions, Main.ScoreType scoreType,
                                                               ClassScoresTensor classScoresTensor) {
    String csvHeader = "test-class,tested-class,score\n";
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
            String rowString = cell.getKey() + "," + testedClass + "," +
                    classScoresTensor.getSingleScoreForTestClassNonTestClassPair(cell.getKey(),
                        testedClass, row.getKey()) + "\n";
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
        Metric.F_SCORE,
        SpectraParser.Metric.TRUE_POSITIVES,
        SpectraParser.Metric.FALSE_POSITIVES,
        SpectraParser.Metric.FALSE_NEGATIVES
    };

    String fileName = "results/test-to-function-evaluation/" + Utilities.programStartTimeStamp +
        "/" + Utilities.getCurrentTimestamp() + "-" + Utilities.getProjectsString(config)  + "-"  +
        (config.isUseTechniqueWeightingForCombinedScore() ? "weighted" : "unweighted") + "-" +
        config.getScoreCombinationMethod().toString().toLowerCase() +
        (scoreType.equals(Main.ScoreType.AUGMENTED) ? "-augmented" : "") + ".csv";

    String csvHeader = "technique,precision,recall,map,f1-score,true-positives,false-positives," +
        "false-negatives\n";
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
        Metric.F_SCORE,
        SpectraParser.Metric.TRUE_POSITIVES,
        SpectraParser.Metric.FALSE_POSITIVES,
        SpectraParser.Metric.FALSE_NEGATIVES
    };

    Technique[] techniques = Utilities.getTechniques(config, Configuration.Level.CLASS, scoreType);

    String fileName = "results/class-to-class-evaluation/" + Utilities.programStartTimeStamp +
        "/" + Utilities.getCurrentTimestamp() + "-" + Utilities.getProjectsString(config) + "-"  +
        (config.isUseTechniqueWeightingForCombinedScore() ? "weighted" : "unweighted") + "-" +
        config.getScoreCombinationMethod().toString().toLowerCase() +
        (scoreType.equals(Main.ScoreType.AUGMENTED) ? "-augmented" : "") + ".csv";

    String csvHeader = "technique,precision,recall,map,f1-score,true-positives,false-positives," +
        "false-negatives\n";
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
