package ctt.ml;

import com.google.common.collect.Table;
import ctt.Configuration;
import ctt.FileSystemHandler;
import ctt.Logger;
import ctt.ProcessHandler;
import ctt.types.Technique;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * Created by RRGWhite on 05/11/2020
 */
public class MLConnector {
  private final Configuration config;
  private final Table<String, String, Map<Technique, Double>> relevanceTable;
  private boolean methodLevelModelTrained;
  private boolean classLevelModelTrained;
  private final int maxTrainSetSize;
  private final int maxTestSetSize;
  private final File dataDir;
  private final File outDir;
  private final File trainDataFile;
  private final File testDataFile;
  private final File inferDir;
  private final File inferInputFile;
  private final File inferOutputFile;
  private final Configuration.Level level;

  public MLConnector(Configuration config,
                     Table<String, String, Map<Technique, Double>> relevanceTable,
                     Configuration.Level level) {
    this.config = config;
    this.relevanceTable = relevanceTable;

    methodLevelModelTrained = true;
    classLevelModelTrained = true;
    maxTrainSetSize = 100000;
    maxTestSetSize = 50;

    String projectName = config.getProjects().get(0);

    dataDir = new File(config.getMlDir() + "/data/" + projectName);
    FileSystemHandler.recreateAFolder(dataDir.getAbsolutePath());

    outDir = new File(config.getMlDir() + "/out/" + level.toString().toLowerCase());

    trainDataFile = new File(dataDir + "/train-" +
        level.toString().toLowerCase() + ".csv");
    testDataFile = new File(dataDir + "/test-" +
        level.toString().toLowerCase() + ".csv");

    inferDir = new File(config.getMlDir() + "/infer/" + projectName);
    FileSystemHandler.recreateAFolder(inferDir.getAbsolutePath());

    inferInputFile = new File(inferDir +
        "/input-" + level.toString().toLowerCase() + ".csv");
    inferOutputFile = new File(inferDir +
        "/output-" + level.toString().toLowerCase() + ".csv");

    this.level = level;
  }

  public void train() {
    createTrainingData();
    String command = config.getPythonCommand() + " " + config.getFeedForwardNetworkScript() +
        " --train_data_file=" + trainDataFile.getAbsolutePath() +
        " --test_data_file=" + testDataFile.getAbsolutePath() +
        " --out_dir=" + outDir.getAbsolutePath();

    ArrayList<String> ffnOutput = ProcessHandler.executeCommand(command,
        new File(config.getMlDir()), new File("cli-output/process-output.txt"));

    if (ffnOutput.get(ffnOutput.size() - 1).contains("trained")) {
      if (level.equals(Configuration.Level.METHOD)) {
        methodLevelModelTrained = true;
      } else {
        classLevelModelTrained = true;
      }
    } else {
      if (level.equals(Configuration.Level.METHOD)) {
        methodLevelModelTrained = false;
      } else {
        classLevelModelTrained = false;
      }
    }
  }

  public ArrayList<String> infer() {
    File[] files = outDir.listFiles();
    Arrays.sort(files);
    File checkpointDir = files[files.length - 1];

    String command = config.getPythonCommand() + " " + config.getFeedForwardNetworkScript() +
        " --inference_input_file=" + inferInputFile.getAbsolutePath() +
        " --inference_output_file=" + inferOutputFile.getAbsolutePath() +
        " --ckpt=" + outDir.getAbsolutePath();

    ArrayList<String> cliOutput = ProcessHandler.executeCommand(command,
        new File(config.getMlDir()), new File("cli-output/process-output.txt"));

    /*Logger.get().logAndPrintLn("Output from infer:");
    for (String line : cliOutput) {
      Logger.get().logAndPrintLn(line);
    }*/

    ArrayList<String> predictionsOutput =
        FileSystemHandler.readLinesFromFile(inferOutputFile.getPath());

    if (!predictionsOutput.get(0).contains("predictions")) {
      Logger.get().logAndPrintLn("Error inferring over feed forward network");
      return null;
    }

    predictionsOutput.remove(0);

    return predictionsOutput;
  }

  public void createTrainingData() {
    ArrayList<String> allTruePositiveExamples = new ArrayList<>();
    ArrayList<String> allFalsePositiveExamples = new ArrayList<>();
    for (Table.Cell<String, String, Map<Technique, Double>> cell : relevanceTable.cellSet()) {
      String example = "";
      if (level.equals(Configuration.Level.METHOD)) {
        //If example is in the ground truth or has NC score of 1 treat it as true positive
        if (cell.getValue() != null && (cell.getValue().get(Technique.GROUND_TRUTH) != null
            || (cell.getValue().get(Technique.NC) != null
                && cell.getValue().get(Technique.NC) == 1))) {
          example = convertCellToCsvString(config, cell) + ",1.0";
          allTruePositiveExamples.add(example);
          continue;
        }

        double scoresTotal = 0;
        for (Double score : cell.getValue().values()) {
          scoresTotal += score;
        }

        if (scoresTotal < 1.2) {
          example = convertCellToCsvString(config, cell) + ",0.0";
          allFalsePositiveExamples.add(example);
        }
      } else if (level.equals(Configuration.Level.CLASS)){
        //If example has NC score of 1 treat it as true positive
        if (cell.getValue() != null && ((cell.getValue().get(Technique.NC_CLASS) != null
                                        && cell.getValue().get(Technique.NC_CLASS) == 1))) {
          example = convertCellToCsvString(config, cell) + ",1.0";
          allTruePositiveExamples.add(example);
          continue;
        }

        double scoresTotal = 0;
        for (Double score : cell.getValue().values()) {
          scoresTotal += score;
        }

        if (scoresTotal < 1.2) {
          example = convertCellToCsvString(config, cell) + ",0.0";
          allFalsePositiveExamples.add(example);
        }
      }
    }

    ArrayList<String> filteredFalsePositiveExamples = new ArrayList<>();
    for (String example : allFalsePositiveExamples) {
      filteredFalsePositiveExamples.add(example);
      if (filteredFalsePositiveExamples.size() >= allTruePositiveExamples.size()) {
        break;
      }
    }

    ArrayList<String> allExamples = new ArrayList<>();
    allExamples.addAll(allTruePositiveExamples);
    allExamples.addAll(filteredFalsePositiveExamples);

    Collections.shuffle(allExamples);

    ArrayList<String> trainExamples = new ArrayList<>();
    ArrayList<String> testExamples = new ArrayList<>();

    //Split examples across test and train sets
    int idx = 0;
    while (idx < allExamples.size()) {
      if (idx >= maxTestSetSize) {
        break;
      }

      testExamples.add(allExamples.get(idx));
      idx++;
    }

    while (idx < allExamples.size()) {
      if (idx >= (maxTestSetSize + maxTrainSetSize)) {
        break;
      }

      trainExamples.add(allExamples.get(idx));
      idx++;
    }

    String headerString = getCSVHeaderString(false);

    Logger.get().logAndPrintLn("Generated " + trainExamples.size() + " train examples");
    Logger.get().logAndPrintLn("Generated " + testExamples.size() + " test examples");

    //Write examples to file
    writeExamplesToFile(headerString, testExamples, testDataFile);
    writeExamplesToFile(headerString, trainExamples, trainDataFile);
  }

  public String convertCellToCsvString(Configuration config,
                                       Table.Cell<String, String, Map<Technique, Double>> cell) {
    String example = "";
    boolean first = true;
    Technique[] techniqueArray;
    if (level.equals(Configuration.Level.METHOD)) {
      techniqueArray = config.getMethodLevelTechniqueList();
    } else {
      techniqueArray = config.getClassLevelTechniqueList();
    }

    for (Technique technique : techniqueArray) {
      if (technique.toString().toLowerCase().contains("combined")) {
        continue;
      }

      Double score = (cell.getValue().get(technique) != null) ?
          cell.getValue().get(technique) : new Double(0.0);

      if (first) {
        example = score.toString();
        first = false;
      } else {
        example = example + "," + score;
      }
    }

    return example;
  }

  public String getCSVHeaderString(boolean inferenceData) {
    String headerString = "";
    boolean first = true;
    Technique[] techniqueArray;
    if (level.equals(Configuration.Level.METHOD)) {
      techniqueArray = config.getMethodLevelTechniqueList();
    } else {
      techniqueArray = config.getClassLevelTechniqueList();
    }

    for (Technique technique : techniqueArray) {
      if (technique.toString().toLowerCase().contains("combined")) {
        continue;
      }

      if (first) {
        headerString = technique.toString();
        first = false;
      } else {
        headerString = headerString + "," + technique.toString();
      }
    }

    if (!inferenceData) {
      headerString = headerString + ",Label\n";
    } else {
      headerString = headerString + "\n";
    }

    return headerString;
  }

  public void writeExamplesToFile(String headerString, ArrayList<String> examples, File file) {
    StringBuilder strBldr = new StringBuilder();
    strBldr.append(headerString);
    for (String example : examples) {
      strBldr.append(example + "\n");
    }

    String testDataStr = strBldr.toString().trim();
    //testDataStr = testDataStr.substring(0, testDataStr.length() - 1);

    FileSystemHandler.writeToFile(file.getPath(), testDataStr, false);
  }

  public Table<String, String, Map<Technique, Double>> addFFNScores() {
    String headerString = getCSVHeaderString(true);
    FileSystemHandler.writeToFile(inferInputFile.getPath(), headerString, false);
    FileSystemHandler.writeToFile(inferOutputFile.getPath(), headerString, false);

    for (Table.Cell<String, String, Map<Technique, Double>> cell : relevanceTable.cellSet()) {
      addToInputDataFile(config, inferInputFile, cell.getValue());
    }

    ArrayList<String> scoreStrings = infer();
    int scoresIdx = 0;
    for (Table.Cell<String, String, Map<Technique, Double>> cell : relevanceTable.cellSet()) {
      String scoreString = scoreStrings.get(scoresIdx++);
      /*if (scoreString.equals("nan")) {
        scoreString = "0.0";
      }*/

      double score;
      try {
        score = Double.parseDouble(scoreString);
      } catch (NumberFormatException e) {
        score = 0.0;
      }

      Map<Technique, Double> valueMap = cell.getValue();

      if (level.equals(Configuration.Level.METHOD)) {
        valueMap.put(Technique.COMBINED_FFN, score);
      } else {
        valueMap.put(Technique.COMBINED_CLASS_FFN, score);
      }
    }

    return relevanceTable;
  }

  public void addToInputDataFile(Configuration config, File inputDataFile,
                                 Map<Technique, Double> valueMap) {
    StringBuilder inputDataBuilder = new StringBuilder();

    Technique[] techniqueArray;
    if (level.equals(Configuration.Level.METHOD)) {
      techniqueArray = config.getMethodLevelTechniqueList();
    } else {
      techniqueArray = config.getClassLevelTechniqueList();
    }

    boolean first = true;
    for (Technique technique : techniqueArray) {
      String scoreString;
      if (valueMap.get(technique) != null) {
        scoreString = "" + valueMap.get(technique);
      } else {
        scoreString = "0.0";
      }

      if (first) {
        inputDataBuilder.append(scoreString);
        first = false;
      } else {
        inputDataBuilder.append(",");
        inputDataBuilder.append(scoreString);
      }
    }

    inputDataBuilder.append("\n");
    FileSystemHandler.writeToFile(inputDataFile.getPath(), inputDataBuilder.toString(), true);
  }

  public boolean isMethodLevelModelTrained() {
    return methodLevelModelTrained;
  }

  public boolean isClassLevelModelTrained() {
    return classLevelModelTrained;
  }
}
