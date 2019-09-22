package ctt;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import ctt.types.EvaluationMetrics;
import ctt.types.FunctionLevelMetrics;
import ctt.types.MethodDepthPair;
import ctt.types.MethodValuePair;
import ctt.types.Technique;
import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.skb.interfaces.transformers.textformat.TextAlignment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Utilities {
  public static final Logger logger = LogManager.getLogger();
  public static String programStartTimeStamp = getCurrentTimestamp();

  // Utility function that implements a function similar to Java 8's computeIfAbsent for Guava tables
  public static <R, C, V> V createIfAbsent(Table<R, C, V> table, R rowKey, C columnKey,
      Supplier<? extends V> mappingFunction) {
    V value = table.get(rowKey, columnKey);
    if (value == null) {
      value = mappingFunction.get();
      table.put(rowKey, columnKey, value);
    }
    return value;
  }

  public static String getClassFqnFromMethodFqn(String methodFqn) {
    String[] splitMethodFqn = methodFqn.split("\\(")[0].split("\\.");
    String[] splitClassFqn = new String[splitMethodFqn.length - 1];

    for (int i = 0; i < (splitMethodFqn.length - 1); ++i) {
      splitClassFqn[i] = splitMethodFqn[i];
    }

    String classFqn = String.join(".", splitClassFqn);
    return classFqn;
  }

  public static String getClassNameFromFqn(String classFqn) {
    String[] splitFqn = classFqn.split("\\.");
    String className = splitFqn[splitFqn.length - 1];
    return className;
  }

  public static Technique getTechniqueForMethodLevel(Technique technique) {
    switch (technique) {

      case GROUND_TRUTH:
        return Technique.GROUND_TRUTH;
      case GROUND_TRUTH_CLASS:
        return Technique.GROUND_TRUTH;
      case NC:
        return Technique.NC;
      case NC_CLASS:
        return Technique.NC;
      case NC_MULTI:
        return Technique.NC;
      case NS_CONTAINS:
        return Technique.NS_CONTAINS;
      case NS_CONTAINS_CLASS:
        return Technique.NS_CONTAINS;
      case NS_CONTAINS_MULTI:
        return Technique.NS_CONTAINS;
      case NS_COMMON_SUBSEQ:
        return Technique.NS_COMMON_SUBSEQ;
      case NS_COMMON_SUBSEQ_CLASS:
        return Technique.NS_COMMON_SUBSEQ;
      case NS_COMMON_SUBSEQ_MULTI:
        return Technique.NS_COMMON_SUBSEQ;
      case NS_COMMON_SUBSEQ_FUZ:
        return Technique.NS_COMMON_SUBSEQ_FUZ;
      case NS_COMMON_SUBSEQ_FUZ_CLASS:
        return Technique.NS_COMMON_SUBSEQ_FUZ;
      case NS_COMMON_SUBSEQ_FUZ_MULTI:
        return Technique.NS_COMMON_SUBSEQ_FUZ;
      case NS_COMMON_SUBSEQ_N:
        return Technique.NS_COMMON_SUBSEQ_N;
      case NS_COMMON_SUBSEQ_N_CLASS:
        return Technique.NS_COMMON_SUBSEQ_N;
      case NS_COMMON_SUBSEQ_N_MULTI:
        return Technique.NS_COMMON_SUBSEQ_N;
      case NS_COMMON_SUBSEQ_FUZ_N:
        return Technique.NS_COMMON_SUBSEQ_FUZ_N;
      case NS_COMMON_SUBSEQ_FUZ_N_CLASS:
        return Technique.NS_COMMON_SUBSEQ_FUZ_N;
      case NS_COMMON_SUBSEQ_FUZ_N_MULTI:
        return Technique.NS_COMMON_SUBSEQ_FUZ_N;
      case NS_LEVENSHTEIN:
        return Technique.NS_LEVENSHTEIN;
      case NS_LEVENSHTEIN_CLASS:
        return Technique.NS_LEVENSHTEIN;
      case NS_LEVENSHTEIN_MULTI:
        return Technique.NS_LEVENSHTEIN;
      case NS_LEVENSHTEIN_N:
        return Technique.NS_LEVENSHTEIN_N;
      case NS_LEVENSHTEIN_N_CLASS:
        return Technique.NS_LEVENSHTEIN_N;
      case NS_LEVENSHTEIN_N_MULTI:
        return Technique.NS_LEVENSHTEIN_N;
      case LAST_CALL_BEFORE_ASSERT:
        return Technique.LAST_CALL_BEFORE_ASSERT;
      case LAST_CALL_BEFORE_ASSERT_CLASS:
        return Technique.LAST_CALL_BEFORE_ASSERT;
      case LAST_CALL_BEFORE_ASSERT_MULTI:
        return Technique.LAST_CALL_BEFORE_ASSERT;
      case FAULT_LOC_TARANTULA:
        return Technique.FAULT_LOC_TARANTULA;
      case FAULT_LOC_TARANTULA_CLASS:
        return Technique.FAULT_LOC_TARANTULA;
      case FAULT_LOC_TARANTULA_MULTI:
        return Technique.FAULT_LOC_TARANTULA;
      case FAULT_LOC_OCHIAI:
        return Technique.FAULT_LOC_OCHIAI;
      case IR_TFIDF_11:
        return Technique.IR_TFIDF_11;
      case IR_TFIDF_11_CLASS:
        return Technique.IR_TFIDF_11;
      case IR_TFIDF_11_MULTI:
        return Technique.IR_TFIDF_11;
      case IR_TFIDF_12:
        return Technique.IR_TFIDF_12;
      case IR_TFIDF_12_CLASS:
        return Technique.IR_TFIDF_12;
      case IR_TFIDF_12_MULTI:
        return Technique.IR_TFIDF_12;
      case IR_TFIDF_21:
        return Technique.IR_TFIDF_21;
      case IR_TFIDF_21_CLASS:
        return Technique.IR_TFIDF_21;
      case IR_TFIDF_21_MULTI:
        return Technique.IR_TFIDF_21;
      case IR_TFIDF_22:
        return Technique.IR_TFIDF_22;
      case IR_TFIDF_22_CLASS:
        return Technique.IR_TFIDF_22;
      case IR_TFIDF_22_MULTI:
        return Technique.IR_TFIDF_22;
      case IR_TFIDF_31:
        return Technique.IR_TFIDF_31;
      case IR_TFIDF_31_CLASS:
        return Technique.IR_TFIDF_31;
      case IR_TFIDF_31_MULTI:
        return Technique.IR_TFIDF_31;
      case IR_TFIDF_32:
        return Technique.IR_TFIDF_32;
      case IR_TFIDF_32_CLASS:
        return Technique.IR_TFIDF_32;
      case IR_TFIDF_32_MULTI:
        return Technique.IR_TFIDF_32;
      case COVERAGE:
        return Technique.COVERAGE;
      case COMBINED:
        return Technique.COMBINED;
      case COMBINED_CLASS:
        return Technique.COMBINED;
      case COMBINED_MULTI:
        return Technique.COMBINED;
    }

    return null;
  }

  public static Technique getTechniqueForClassLevel(Technique technique) {
    switch (technique) {

      case GROUND_TRUTH:
        return Technique.GROUND_TRUTH_CLASS;
      case GROUND_TRUTH_CLASS:
        return Technique.GROUND_TRUTH_CLASS;
      case NC:
        return Technique.NC_CLASS;
      case NC_CLASS:
        return Technique.NC_CLASS;
      case NC_MULTI:
        return Technique.NC_CLASS;
      case NS_CONTAINS:
        return Technique.NS_CONTAINS_CLASS;
      case NS_CONTAINS_CLASS:
        return Technique.NS_CONTAINS_CLASS;
      case NS_CONTAINS_MULTI:
        return Technique.NS_CONTAINS_CLASS;
      case NS_COMMON_SUBSEQ:
        return Technique.NS_COMMON_SUBSEQ_CLASS;
      case NS_COMMON_SUBSEQ_CLASS:
        return Technique.NS_COMMON_SUBSEQ_CLASS;
      case NS_COMMON_SUBSEQ_MULTI:
        return Technique.NS_COMMON_SUBSEQ_CLASS;
      case NS_COMMON_SUBSEQ_FUZ:
        return Technique.NS_COMMON_SUBSEQ_FUZ_CLASS;
      case NS_COMMON_SUBSEQ_FUZ_CLASS:
        return Technique.NS_COMMON_SUBSEQ_FUZ_CLASS;
      case NS_COMMON_SUBSEQ_FUZ_MULTI:
        return Technique.NS_COMMON_SUBSEQ_FUZ_CLASS;
      case NS_COMMON_SUBSEQ_N:
        return Technique.NS_COMMON_SUBSEQ_N_CLASS;
      case NS_COMMON_SUBSEQ_N_CLASS:
        return Technique.NS_COMMON_SUBSEQ_N_CLASS;
      case NS_COMMON_SUBSEQ_N_MULTI:
        return Technique.NS_COMMON_SUBSEQ_N_CLASS;
      case NS_COMMON_SUBSEQ_FUZ_N:
        return Technique.NS_COMMON_SUBSEQ_FUZ_N_CLASS;
      case NS_COMMON_SUBSEQ_FUZ_N_CLASS:
        return Technique.NS_COMMON_SUBSEQ_FUZ_N_CLASS;
      case NS_COMMON_SUBSEQ_FUZ_N_MULTI:
        return Technique.NS_COMMON_SUBSEQ_FUZ_N_CLASS;
      case NS_LEVENSHTEIN:
        return Technique.NS_LEVENSHTEIN_CLASS;
      case NS_LEVENSHTEIN_CLASS:
        return Technique.NS_LEVENSHTEIN_CLASS;
      case NS_LEVENSHTEIN_MULTI:
        return Technique.NS_LEVENSHTEIN_CLASS;
      case NS_LEVENSHTEIN_N:
        return Technique.NS_LEVENSHTEIN_N_CLASS;
      case NS_LEVENSHTEIN_N_CLASS:
        return Technique.NS_LEVENSHTEIN_N_CLASS;
      case NS_LEVENSHTEIN_N_MULTI:
        return Technique.NS_LEVENSHTEIN_N_CLASS;
      case LAST_CALL_BEFORE_ASSERT:
        return Technique.LAST_CALL_BEFORE_ASSERT_CLASS;
      case LAST_CALL_BEFORE_ASSERT_CLASS:
        return Technique.LAST_CALL_BEFORE_ASSERT_CLASS;
      case LAST_CALL_BEFORE_ASSERT_MULTI:
        return Technique.LAST_CALL_BEFORE_ASSERT_CLASS;
      case FAULT_LOC_TARANTULA:
        return Technique.FAULT_LOC_TARANTULA_CLASS;
      case FAULT_LOC_TARANTULA_CLASS:
        return Technique.FAULT_LOC_TARANTULA_CLASS;
      case FAULT_LOC_TARANTULA_MULTI:
        return Technique.FAULT_LOC_TARANTULA_CLASS;
      case IR_TFIDF_11:
        return Technique.IR_TFIDF_11_CLASS;
      case IR_TFIDF_11_CLASS:
        return Technique.IR_TFIDF_11_CLASS;
      case IR_TFIDF_11_MULTI:
        return Technique.IR_TFIDF_11_CLASS;
      case IR_TFIDF_12:
        return Technique.IR_TFIDF_12_CLASS;
      case IR_TFIDF_12_CLASS:
        return Technique.IR_TFIDF_12_CLASS;
      case IR_TFIDF_12_MULTI:
        return Technique.IR_TFIDF_12_CLASS;
      case IR_TFIDF_21:
        return Technique.IR_TFIDF_21_CLASS;
      case IR_TFIDF_21_CLASS:
        return Technique.IR_TFIDF_21_CLASS;
      case IR_TFIDF_21_MULTI:
        return Technique.IR_TFIDF_21_CLASS;
      case IR_TFIDF_22:
        return Technique.IR_TFIDF_22_CLASS;
      case IR_TFIDF_22_CLASS:
        return Technique.IR_TFIDF_22_CLASS;
      case IR_TFIDF_22_MULTI:
        return Technique.IR_TFIDF_22_CLASS;
      case IR_TFIDF_31:
        return Technique.IR_TFIDF_31_CLASS;
      case IR_TFIDF_31_CLASS:
        return Technique.IR_TFIDF_31_CLASS;
      case IR_TFIDF_31_MULTI:
        return Technique.IR_TFIDF_31_CLASS;
      case IR_TFIDF_32:
        return Technique.IR_TFIDF_32_CLASS;
      case IR_TFIDF_32_CLASS:
        return Technique.IR_TFIDF_32_CLASS;
      case IR_TFIDF_32_MULTI:
        return Technique.IR_TFIDF_32_CLASS;
      case COMBINED:
        return Technique.COMBINED_CLASS;
      case COMBINED_CLASS:
        return Technique.COMBINED_CLASS;
      case COMBINED_MULTI:
        return Technique.COMBINED_CLASS;
    }

    return null;
  }


  public static Technique[] getTechniques(Configuration config,
                                          Configuration.Level level,
                                          Main.ScoreType scoreType) {
    Technique[] techniques;
    switch (scoreType) {
      case PURE:
        switch (level) {
          case METHOD:
            techniques = config.getMethodLevelTechniqueList();
            break;
          case CLASS:
            techniques = config.getClassLevelTechniqueList();
            break;
          default:
            techniques = new Technique[0];
            break;
        }
        break;
      case AUGMENTED:
        techniques = config.getMultiLevelTechniqueList();
        break;
      default:
        techniques = new Technique[0];
        break;
    }

    return techniques;
  }


  public static String getCurrentTimestamp() {
    return new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
  }

  public static void handleCaughtThrowable(Throwable e, boolean fatal) {
    String callerClassName = Thread.currentThread().getStackTrace()[2].getClassName();
    String callerMethodName = Thread.currentThread().getStackTrace()[2].getMethodName();
    System.out.println("Exception caught in " + callerClassName + "." + callerMethodName
        + ": " + e);
    e.printStackTrace();
    if (fatal) {
      System.exit(-1);
    }
  }

  public static void writeStringToFile(String strToWrite, String dstFilePath, boolean append) {
    if (dstFilePath == null) {
      logger.error("FileSystemHandler.writeToDisk rejecting null destination file "
          + "path");
      return;
    }

    String[] splitDstFilePath = dstFilePath.replace("\\", "/").split("/");
    String[] splitNewDstFilePath = new String[splitDstFilePath.length - 1];
    for (int i = 0; i < splitNewDstFilePath.length; ++i) {
      splitNewDstFilePath[i] = splitDstFilePath[i];
    }

    String dstDirPath = String.join("/", splitNewDstFilePath);
    File dstDir = new File(dstDirPath);
    if (!dstDir.exists()) {
      createDirStructureForFile(dstFilePath);
      /*if (!dstDir.mkdir()) {
        logger.error("Couldn't make dir " + dstDir.getName());
      }*/
    }

    BufferedWriter bw = null;
    FileWriter fw = null;

    try {
      fw = new FileWriter(dstFilePath, append);
      bw = new BufferedWriter(fw);
      bw.write(strToWrite);
    } catch (Exception e) {
      handleCaughtThrowable(e, false);
    } finally {
      try {
        if (bw != null) {
          bw.close();
        }
        if (fw != null) {
          fw.close();
        }
      } catch (IOException e) {
        handleCaughtThrowable(e, false);
      }
    }
  }

  public static INDArray normaliseTensorByMaxValue(INDArray unnormalisedTensor) {
    double maxValue = unnormalisedTensor.maxNumber().doubleValue();
    if (maxValue > 0) {
      INDArray normalisedTensor = unnormalisedTensor.div(maxValue);
      return normalisedTensor;
    }
    return unnormalisedTensor;
  }

  public static String getProjectsString(Configuration config) {
    String projectsStr = "";
    for (String project : config.getProjects()) {
      projectsStr += project + "-";
    }
    projectsStr = projectsStr.substring(0, projectsStr.length() - 1);
    return projectsStr;
  }

  public static void createDirStructureForFile(String path) {
    try {
      String[] splitDstFilePath = path.replace("\\", "/").split("/");
      String[] splitNewDstFilePath = new String[splitDstFilePath.length - 1];
      for (int i = 0; i < splitNewDstFilePath.length; ++i) {
        splitNewDstFilePath[i] = splitDstFilePath[i];
      }

      String dstDirPath = String.join("/", splitNewDstFilePath);
      File dstDir = new File(dstDirPath);
      if (!dstDir.exists()) {
        if (!dstDir.mkdirs()) {
          logger.error("Couldn't make dirs for path" + path);
        }
      }
    } catch (Exception e) {
      handleCaughtThrowable(e, false);
    }
  }

  public static ArrayList<String> readLinesFromFile(String filePath) {
    ArrayList<String> lines = new ArrayList<>();
    File file = new File(filePath);
    if (file.exists()) {
      try {
        lines = (ArrayList<String>) Files.readAllLines(file.toPath());
      } catch (IOException e) {
        handleCaughtThrowable(e, false);
      }
    } else {
      logger.error(
          "File passed to FileSystemHandler.readLinesFromFile does not exist");
    }
    return lines;
  }

  public static boolean allProjectsHaveFunctionLevelEvaluation(Configuration config) {
    HashSet<String> projectsIntersection = Sets.newHashSet(config.getProjects());
    projectsIntersection.retainAll(Sets.newHashSet(config.getFunctionLevelEvaluationProjects()));
    return projectsIntersection.size() == config.getProjects().size();
  }

  public static boolean allProjectsHaveClassLevelEvaluation(Configuration config) {
    HashSet<String> projectsIntersection = Sets.newHashSet(config.getProjects());
    projectsIntersection.retainAll(Sets.newHashSet(config.getClassLevelEvaluationProjects()));
    return projectsIntersection.size() == config.getProjects().size();
  }

  public static Table<Technique, String, EvaluationMetrics> getEvaluationMetricsTable(
      Table<Technique, String, EvaluationMetrics> metricTable, Set<String> evaluationSet) {
    Table<Technique, String, EvaluationMetrics> evaluationMetricsTable = HashBasedTable.create();
    for (Table.Cell<Technique, String, EvaluationMetrics> cell : metricTable.cellSet()) {
      if (evaluationSet.contains(cell.getColumnKey())) {
        evaluationMetricsTable.put(cell.getRowKey(), cell.getColumnKey(), cell.getValue());
      }
    }
    return evaluationMetricsTable;
  }

  // Prints unnormalised results
  public void printTraceabilityMatrix(Map<String, Set<MethodDepthPair>> testsMap,
                                      Map<String, Set<MethodDepthPair>> methodsMap,
                                      Technique techniqueToPrint, List<String> testFilter,
                                      List<String> methodFilter,
                                      FunctionLevelMetrics functionLevelMetrics) {
    // Keys: Method, Test | Value: Traceability Value
    Table<String, String, Double> traceabilityMatrix = HashBasedTable.create();

    // Populate the traceability matrix
    if (testFilter != null) {
      for (String test : testFilter) {
        Set<MethodDepthPair> methodsExecutedByTest = testsMap.get(test);
        if (methodsExecutedByTest == null) {
          continue;
        }
        for (MethodDepthPair method : methodsExecutedByTest) {
          Map<Technique, Double> traceabilityMap =
              functionLevelMetrics.getRelevanceTable().get(test, method.getMethodName());
          double traceabilityValue = traceabilityMap.get(techniqueToPrint);
          traceabilityMatrix.put(method.getMethodName(), test, traceabilityValue);
        }
      }
    }
    if (methodFilter != null) {
      for (String method : methodFilter) {
        Set<MethodDepthPair> testsThatExecuteMethod = methodsMap.get(method);
        if (testsThatExecuteMethod == null) {
          continue;
        }
        for (MethodDepthPair test : testsThatExecuteMethod) {
          Map<Technique, Double> traceabilityMap =
              functionLevelMetrics.getRelevanceTable().get(test.getMethodName(), method);
          double traceabilityValue = traceabilityMap.get(techniqueToPrint);
          traceabilityMatrix.put(method, test.getMethodName(), traceabilityValue);
        }
      }
    }

    if (traceabilityMatrix.size() == 0) {
      logger.warn(
          "Traceabiltiy matrix is empty - check that requested test and method sets are non-empty.");
      return;
    }

    // Print the traceability matrix
    AsciiTable at = new AsciiTable();
    at.addRule();
    at.addRow(Stream.concat(Stream.of("Method \\ Test"), traceabilityMatrix.columnKeySet().stream())
        .collect(Collectors.toList()));
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
  public void printTestTraceabilityMatrix(Technique techniqueToPrint, List<String> testFilter,
                                          FunctionLevelMetrics functionLevelMetrics) {
    // Keys: Method, Test | Value: Traceability Value
    Table<String, String, Double> traceabilityMatrix = HashBasedTable.create();

    // Populate the traceability matrix
    if (testFilter != null) {
      for (String test : testFilter) {
        SortedSet<MethodValuePair> techniqueMethodSet =
            functionLevelMetrics.getAggregatedResults().get(test, techniqueToPrint);
        if (techniqueMethodSet == null) {
          continue;
        }

        for (MethodValuePair methodValuePair : techniqueMethodSet) {
          double traceabilityValue = methodValuePair.getValue();
          traceabilityMatrix.put(methodValuePair.getMethod(), test, traceabilityValue);
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
    at.addRow(Stream.concat(Stream.of("Method \\ Test"), traceabilityMatrix.columnKeySet().stream())
        .collect(Collectors.toList()));
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

  public static double functionLevelPrecisionAtK(SortedSet<MethodValuePair> candidateMethodSet,
                                                 Set<String> groundTruthSet, int k) {
    int listIdx = 0;
    int truePositives = 0;
    int falsePositives = 0;
    for (MethodValuePair methodValuePair : candidateMethodSet) {
      listIdx++;
      if (groundTruthSet.contains(methodValuePair.getMethod())) {
        truePositives++;
      } else {
        falsePositives++;
      }

      if (listIdx == k) {
        break;
      }
    }

    return EvaluationMetrics.computePrecision(truePositives, falsePositives);
  }

  public static double classLevelPrecisionAtK(List<String> predictedClasses,
                                              List<String> groundTruthSet, int k) {
    int listIdx = 0;
    int truePositives = 0;
    int falsePositives = 0;
    for (String prediction : predictedClasses) {
      listIdx++;
      String oracleFormatPredictedTestedClass = Utilities.getClassNameFromFqn(
          prediction).toLowerCase();
      if (groundTruthSet.contains(oracleFormatPredictedTestedClass)) {
        truePositives++;
      } else {
        falsePositives++;
      }

      if (listIdx == k) {
        break;
      }
    }

    return EvaluationMetrics.computePrecision(truePositives, falsePositives);
  }
}
