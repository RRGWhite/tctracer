package ctt;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import ctt.types.EvaluationMetrics;
import ctt.types.FunctionLevelMetrics;
import ctt.types.HitSpectrum;
import ctt.types.Method;
import ctt.types.MethodDepthPair;
import ctt.types.MethodValuePair;
import ctt.types.Technique;
import ctt.types.TestCollection;
import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.skb.interfaces.transformers.textformat.TextAlignment;
import org.apache.commons.io.FileUtils;
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
import java.util.Date;
import java.util.HashMap;
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
  public static HashMap<String, ArrayList<MethodDepthPair>> groundTruthFunctionsToDepthMap = new HashMap<>();
  public static HashMap<String, ArrayList<MethodDepthPair>> allFunctionsToDepthMap = new HashMap<>();

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
      case STATIC_NC:
        return Technique.STATIC_NC;
      case STATIC_NC_CLASS:
        return Technique.STATIC_NC;
      case STATIC_NC_MULTI:
        return Technique.STATIC_NC;
      case NCC:
        return Technique.NCC;
      case NCC_CLASS:
        return Technique.NCC;
      case NCC_MULTI:
        return Technique.NCC;
      case STATIC_NCC:
        return Technique.STATIC_NCC;
      case STATIC_NCC_CLASS:
        return Technique.STATIC_NCC;
      case STATIC_NCC_MULTI:
        return Technique.STATIC_NCC;
      case LCS_B_N:
        return Technique.LCS_B_N;
      case LCS_B_N_CLASS:
        return Technique.LCS_B_N;
      case LCS_B_N_MULTI:
        return Technique.LCS_B_N;
      case STATIC_LCS_B_N:
        return Technique.STATIC_LCS_B_N;
      case STATIC_LCS_B_N_CLASS:
        return Technique.STATIC_LCS_B_N;
      case STATIC_LCS_B_N_MULTI:
        return Technique.STATIC_LCS_B_N;
      case LCS_U_N:
        return Technique.LCS_U_N;
      case LCS_U_N_CLASS:
        return Technique.LCS_U_N;
      case LCS_U_N_MULTI:
        return Technique.LCS_U_N;
      case STATIC_LCS_U_N:
        return Technique.STATIC_LCS_U_N;
      case STATIC_LCS_U_N_CLASS:
        return Technique.STATIC_LCS_U_N;
      case STATIC_LCS_U_N_MULTI:
        return Technique.STATIC_LCS_U_N;
      case LEVENSHTEIN_N:
        return Technique.LEVENSHTEIN_N;
      case LEVENSHTEIN_N_CLASS:
        return Technique.LEVENSHTEIN_N;
      case LEVENSHTEIN_N_MULTI:
        return Technique.LEVENSHTEIN_N;
      case STATIC_LEVENSHTEIN_N:
        return Technique.STATIC_LEVENSHTEIN_N;
      case STATIC_LEVENSHTEIN_N_CLASS:
        return Technique.STATIC_LEVENSHTEIN_N;
      case STATIC_LEVENSHTEIN_N_MULTI:
        return Technique.STATIC_LEVENSHTEIN_N;
      case LCBA:
        return Technique.LCBA;
      case LCBA_CLASS:
        return Technique.LCBA;
      case LCBA_MULTI:
        return Technique.LCBA;
      case STATIC_LCBA:
        return Technique.STATIC_LCBA;
      case STATIC_LCBA_CLASS:
        return Technique.STATIC_LCBA;
      case STATIC_LCBA_MULTI:
        return Technique.STATIC_LCBA;
      case TARANTULA:
        return Technique.TARANTULA;
      case TARANTULA_CLASS:
        return Technique.TARANTULA;
      case TARANTULA_MULTI:
        return Technique.TARANTULA;
      case TFIDF:
        return Technique.TFIDF;
      case TFIDF_CLASS:
        return Technique.TFIDF;
      case TFIDF_MULTI:
        return Technique.TFIDF;
      case COMBINED:
        return Technique.COMBINED;
      case COMBINED_CLASS:
        return Technique.COMBINED;
      case COMBINED_MULTI:
        return Technique.COMBINED;
      case COMBINED_FFN:
        return Technique.COMBINED_FFN;
      case COMBINED_CLASS_FFN:
        return Technique.COMBINED_FFN;
      case COMBINED_MULTI_FFN:
        return Technique.COMBINED_FFN;
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
      case STATIC_NC:
        return Technique.STATIC_NC_CLASS;
      case STATIC_NC_CLASS:
        return Technique.STATIC_NC_CLASS;
      case STATIC_NC_MULTI:
        return Technique.STATIC_NC_CLASS;
      case NCC:
        return Technique.NCC_CLASS;
      case NCC_CLASS:
        return Technique.NCC_CLASS;
      case NCC_MULTI:
        return Technique.NCC_CLASS;
      case STATIC_NCC:
        return Technique.STATIC_NCC_CLASS;
      case STATIC_NCC_CLASS:
        return Technique.STATIC_NCC_CLASS;
      case STATIC_NCC_MULTI:
        return Technique.STATIC_NCC_CLASS;
      case LCS_B_N:
        return Technique.LCS_B_N_CLASS;
      case LCS_B_N_CLASS:
        return Technique.LCS_B_N_CLASS;
      case LCS_B_N_MULTI:
        return Technique.LCS_B_N_CLASS;
      case STATIC_LCS_B_N:
        return Technique.STATIC_LCS_B_N_CLASS;
      case STATIC_LCS_B_N_CLASS:
        return Technique.STATIC_LCS_B_N_CLASS;
      case STATIC_LCS_B_N_MULTI:
        return Technique.STATIC_LCS_B_N_CLASS;
      case LCS_U_N:
        return Technique.LCS_U_N_CLASS;
      case LCS_U_N_CLASS:
        return Technique.LCS_U_N_CLASS;
      case LCS_U_N_MULTI:
        return Technique.LCS_U_N_CLASS;
      case STATIC_LCS_U_N:
        return Technique.STATIC_LCS_U_N_CLASS;
      case STATIC_LCS_U_N_CLASS:
        return Technique.STATIC_LCS_U_N_CLASS;
      case STATIC_LCS_U_N_MULTI:
        return Technique.STATIC_LCS_U_N_CLASS;
      case LEVENSHTEIN_N:
        return Technique.LEVENSHTEIN_N_CLASS;
      case LEVENSHTEIN_N_CLASS:
        return Technique.LEVENSHTEIN_N_CLASS;
      case LEVENSHTEIN_N_MULTI:
        return Technique.LEVENSHTEIN_N_CLASS;
      case STATIC_LEVENSHTEIN_N:
        return Technique.STATIC_LEVENSHTEIN_N_CLASS;
      case STATIC_LEVENSHTEIN_N_CLASS:
        return Technique.STATIC_LEVENSHTEIN_N_CLASS;
      case STATIC_LEVENSHTEIN_N_MULTI:
        return Technique.STATIC_LEVENSHTEIN_N_CLASS;
      case LCBA:
        return Technique.LCBA_CLASS;
      case LCBA_CLASS:
        return Technique.LCBA_CLASS;
      case LCBA_MULTI:
        return Technique.LCBA_CLASS;
      case STATIC_LCBA:
        return Technique.STATIC_LCBA_CLASS;
      case STATIC_LCBA_CLASS:
        return Technique.STATIC_LCBA_CLASS;
      case STATIC_LCBA_MULTI:
        return Technique.STATIC_LCBA_CLASS;
      case TARANTULA:
        return Technique.TARANTULA_CLASS;
      case TARANTULA_CLASS:
        return Technique.TARANTULA_CLASS;
      case TARANTULA_MULTI:
        return Technique.TARANTULA_CLASS;
      case TFIDF:
        return Technique.TFIDF_CLASS;
      case TFIDF_CLASS:
        return Technique.TFIDF_CLASS;
      case TFIDF_MULTI:
        return Technique.TFIDF_CLASS;
      case COMBINED:
        return Technique.COMBINED_CLASS;
      case COMBINED_CLASS:
        return Technique.COMBINED_CLASS;
      case COMBINED_MULTI:
        return Technique.COMBINED_CLASS;
      case COMBINED_FFN:
        return Technique.COMBINED_CLASS_FFN;
      case COMBINED_CLASS_FFN:
        return Technique.COMBINED_CLASS_FFN;
      case COMBINED_MULTI_FFN:
        return Technique.COMBINED_CLASS_FFN;
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
      logger.error("FileSystemHandler.writeToDisk rejecting null destination file path");
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

  public static <T extends Method> ArrayList<T> readMethodsFromFiles(String inputFolder,
                                                              Configuration.ArtefactType artefactType,
                                                              boolean filterOnClassNameForArtefactType) {
    //Logger.get().logAndPrint("Reading files from " + inputFolder + "...");
    //Logger.get().startShowingWorkingOutput();
    ArrayList<T> allMethodList = new ArrayList<>();

    File folder = new File(inputFolder);
    String[] extensions = {"java"};

    List<File> listOfFiles = (List<File>) FileUtils.listFiles(folder, extensions, true);
    for (File file : listOfFiles) {
      String filePath = file.getAbsolutePath();

      //Filter on class name in cases where src and test are mixed in the dir
      if (filterOnClassNameForArtefactType) {
        if (artefactType == Configuration.ArtefactType.TEST
            && !file.getName().toLowerCase().contains("test")) {
          continue;
        } else if (artefactType == Configuration.ArtefactType.FUNCTION
            && file.getName().toLowerCase().contains("test")){
          continue;
        }
      }

      // parse each file into method (if possible)
      MethodParser<T> methodParser = new MethodParser<>(filePath, inputFolder, artefactType);
      try {
        /*if (filePath.contains("RecordReaderDataSetiteratorTest.java")) {
          System.out.println("Debugging missed test methods");
        }*/
        ArrayList<T> methodList = methodParser.parseMethods();
        // add the extracted methods to the big list
        allMethodList.addAll(methodList);
      } catch (Exception e) {
        ExceptionHandler.handleCaughtThrowable(e, false);
      }
    }

    //Logger.get().stopShowingWorkingOutput();
    //Logger.get().logAndPrintLn("done");
    return allMethodList;
  }

  public static String removePackagesFromFqnParamTypes(String fqn) {
    String paramsString = getParamsStringFromFqn(fqn);

    //ArrayList<String> paramTypes = new ArrayList<>();

    String newParamString = "";
    if (!paramsString.isEmpty()) {
      paramsString = MethodParser.eraseGenericTypeArgs(paramsString);

      boolean first = true;
      String[] splitParamsString = paramsString.split(",");
      for (String param : splitParamsString) {
        String[] splitParamString = param.split("\\.");

        String paramType = splitParamString[splitParamString.length - 1];
        if (paramType.contains("$")) {
          paramType = paramType.split("\\$")[1];
        }

        paramType = paramType.trim();

        //paramTypes.add(paramType);

        if (first) {
          first = false;
          newParamString = newParamString + paramType;
        } else {
          newParamString = newParamString + ", " + paramType;
        }
      }
    }

    int idx = fqn.indexOf('(');
    if (idx < 1) {
      return fqn;
    }

    String newFqn = fqn.substring(0, idx);
    newFqn = newFqn + "(" + newParamString + ")";
    return newFqn;
  }

  public static String getParamsStringFromFqn(String fqn) {
    try {
      String paramsString = fqn.substring( fqn.indexOf( '(' ) );
      paramsString = paramsString.replace( "(", "" ).replace( ")", "" );
      return paramsString;
    } catch (Exception e) {
      return fqn;
    }
  }

  public static void printGroundTruthDepths(TestCollection testCollection) {
    groundTruthFunctionsToDepthMap = new HashMap<>();
    allFunctionsToDepthMap = new HashMap<>();
    ctt.Logger.get().logAndPrintLn("***   Start ground truth depths   ***\n");
    for (HitSpectrum hitSpectrum : testCollection.tests) {
      for (Map.Entry<String, Integer> hits : hitSpectrum.hitSet.entrySet()) {
        String functionName = removePackagesFromFqnParamTypes(hits.getKey());
        if (functionName.contains("<init>")) {
          functionName = replaceInitWithConstructorName(functionName);
        }

        allFunctionsToDepthMap.putIfAbsent(hitSpectrum.getTestName(), new ArrayList<>());
        allFunctionsToDepthMap.get(hitSpectrum.getTestName()).add(new MethodDepthPair(
            functionName, hits.getValue()));
      }

      for (String groundTruthStr : hitSpectrum.groundTruth) {
        if (groundTruthStr.contains("<init>")) {
          groundTruthStr = replaceInitWithConstructorName(groundTruthStr);
        }

        for (String hitMethod : hitSpectrum.hitSet.keySet()) {
          String hitMethodComparator = hitMethod;
          if (hitMethodComparator.contains("<init>")) {
            hitMethodComparator = replaceInitWithConstructorName(hitMethodComparator);
          }

          if (removePackagesFromFqnParamTypes(groundTruthStr).equals(
              removePackagesFromFqnParamTypes(hitMethodComparator))) {
            Integer callDepth = hitSpectrum.hitSet.get(hitMethod);
            if (callDepth != null) {
              groundTruthFunctionsToDepthMap.putIfAbsent(hitSpectrum.getTestName(), new ArrayList<>());
              groundTruthFunctionsToDepthMap.get(hitSpectrum.getTestName()).add(new MethodDepthPair(
                  removePackagesFromFqnParamTypes(groundTruthStr), callDepth));
              ctt.Logger.get().logAndPrintLn(groundTruthStr + " : " + callDepth);
            } else {
              System.out.println("check this");
            }
          }
        }
      }
    }
    ctt.Logger.get().logAndPrintLn("***   End ground truth depths   ***\n");
  }

  public static int getGroundTruthDepth(String test, String function) {
    ArrayList<MethodDepthPair> depthPairs = groundTruthFunctionsToDepthMap.get(test);
    if (depthPairs != null) {
      for (MethodDepthPair depthPair : depthPairs) {
        if (function.equals(depthPair.getMethodName())) {
          return depthPair.getCallDepth();
        }
      }
    }
    ctt.Logger.get().logAndPrintLn("Function: " + function + " not found in " +
          "groundTruthFunctionsToDepthMap returning default of -2");
    return -2;
  }

  public static int getfunctionDepth(String test, String function) {
    ArrayList<MethodDepthPair> depthPairs = allFunctionsToDepthMap.get(test);
    if (depthPairs != null) {
      for (MethodDepthPair depthPair : depthPairs) {
        if (function.equals(depthPair.getMethodName())) {
          return depthPair.getCallDepth();
        }
      }
    }
    //ctt.Logger.get().logAndPrintLn("Function: " + function + " not found in " +
    //"allFunctionsToDepthMap returning default of -2");
    return -2;
  }

  public static String replaceInitWithConstructorName(String method) {
    String classfqn = Utilities.getClassFqnFromMethodFqn(method);
    String className = classfqn.substring(classfqn.lastIndexOf('.') + 1);
    return method.replace("<init>", className);
  }
}
