package ctt;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import ctt.coverage.CoverageAnalyser;
import ctt.coverage.CoverageAnalyser.CounterType;
import ctt.coverage.CoverageAnalyser.CoverageStat;
import ctt.experiments.OptimisationExperiment;
import ctt.experiments.ThresholdExperiment;
import ctt.metrics.TechniqueMetricsProvider;
import ctt.types.CollectedComputedMetrics;
import ctt.types.EvaluationMetrics;
import ctt.types.HitSpectrum;
import ctt.types.Technique;
import ctt.types.TestCollection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.units.qual.A;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class Main {

  public enum ScoreType {PURE, AUGMENTED}

  private static final Logger logger = LogManager.getLogger(Main.class.getName());

  private static boolean VERBOSE = false;

  enum RunType {
    SINGLE, SINGLE_ALL, EXPERIMENT, OPTIMISATION
  }

  public static void main(String[] args) throws Exception {
    ArrayList<String> projects = new ArrayList<>();
//    projects.add("apache-ant");
    //projects.add("commons-io");
    //projects.add("commons-lang");
    //projects.add("jfreechart");
    projects.add("Test");
    //gatherLogs(projects, true);
    //deleteLogs(projects);
    singleRun(projects);
    //experimentRun(projects);
  }

  private static void singleRun(ArrayList<String> projects) throws Exception {
    for (String project : projects) {
      // Run single configuration
      Configuration config = new Configuration.Builder()
          .setProjects(Arrays.asList(project))
          .setIsSingleProject(true)
          .setParseCttLogs(true)
          .build();

      // Parse execution trace -> produce hit spectra.
      // Only needs to be done once per project - can be commented out once hit spectrum is generated.
      if (config.isParseCttLogs()) {
        logParse(getLogsDirectory(config.getProjectBaseDirs().get(config.getProjects().get(0))),
            getSpectrumDirectory(config.getProjectBaseDirs().get(config.getProjects().get(0))));
      }

      spectrumParse(config,
          getSpectrumDirectory(config.getProjectBaseDirs().get(config.getProjects().get(0))),
          getCoverageReportsPath(config.getProjectBaseDirs().get(config.getProjects().get(0))));
    }
  }

  private static void singleAllRun(ArrayList<String> projects) throws Exception {
    Configuration config = new Configuration.Builder()
        .setCallDepthDiscountFactor(0.5)
        // .setTechniqueList(Presets.TECHNIQUES_NAME_SIMILARITY)
        // .setTechniqueList(Presets.TECHNIQUES_OTHER)
        .build();

    config.setProjects(Arrays.asList((String[]) config.getProjectBaseDirs().keySet().toArray()));

    List<TestCollection> testCollections =
        getTestCollections((String[]) config.getProjectBaseDirs().entrySet().toArray());
    ArrayList<CollectedComputedMetrics> aggregatedMetrics =
        SpectraParser.parseTestCollections(config, testCollections, null, false);

    // Build one large table from all the input hit spectra and then analyse.
    Table<Technique, String, EvaluationMetrics> aggregatedFunctionLevelMetricsTable =
        HashBasedTable.create();
    Table<Technique, String, EvaluationMetrics> aggregatedClassLevelMetricsTable =
        HashBasedTable.create();

    Table<Technique, String, EvaluationMetrics> aggregatedAugmentedFunctionLevelMetricsTable =
        HashBasedTable.create();
    Table<Technique, String, EvaluationMetrics> aggregatedAugmentedClassLevelMetricsTable =
        HashBasedTable.create();

    for (CollectedComputedMetrics metrics : aggregatedMetrics) {
      aggregatedFunctionLevelMetricsTable.putAll(
          metrics.getFunctionLevelMetrics().getMetricTable());
      aggregatedClassLevelMetricsTable.putAll(metrics.getClassLevelMetrics().getMetricTable());

      aggregatedAugmentedFunctionLevelMetricsTable.putAll(
          metrics.getAugmentedFunctionLevelMetrics().getMetricTable());
      aggregatedAugmentedClassLevelMetricsTable.putAll(
          metrics.getAugmentedClassLevelMetrics().getMetricTable());
    }

    if (Utilities.allProjectsHaveFunctionLevelEvaluation(config)) {
      printFunctionLevelMetricsSummary(config, aggregatedFunctionLevelMetricsTable,
          ScoreType.PURE);
      printFunctionLevelMetricsSummary(config, aggregatedAugmentedFunctionLevelMetricsTable,
          ScoreType.AUGMENTED);

      ResultsWriter.writeOutFunctionLevelValidationSummary(ScoreType.PURE, config,
          TechniqueMetricsProvider.computeTechniqueMetrics(aggregatedFunctionLevelMetricsTable));
      ResultsWriter.writeOutFunctionLevelValidationSummary(ScoreType.AUGMENTED, config,
          TechniqueMetricsProvider.computeTechniqueMetrics(
              aggregatedAugmentedFunctionLevelMetricsTable));
    }

    if (Utilities.allProjectsHaveClassLevelEvaluation(config)) {
      printClassLevelMetricsSummary(config, aggregatedClassLevelMetricsTable,
          ScoreType.PURE);
      printClassLevelMetricsSummary(config, aggregatedAugmentedClassLevelMetricsTable,
          ScoreType.AUGMENTED);

      ResultsWriter.writeOutClassLevelValidationSummary(ScoreType.PURE, config,
          TechniqueMetricsProvider.computeTechniqueMetrics(aggregatedClassLevelMetricsTable));
      ResultsWriter.writeOutClassLevelValidationSummary(ScoreType.AUGMENTED, config,
          TechniqueMetricsProvider.computeTechniqueMetrics(aggregatedAugmentedClassLevelMetricsTable));
    }
  }

  private static void experimentRun(ArrayList<String> projects) throws Exception {

    // Run an experiment
    Table<String, String, Map<CounterType, CoverageStat>> coverageData = null;

    // Uncomment to use coverage data.
    // coverageData = getCoverageData(coverageReportsPath);
    for (String project : projects) {
      Utilities.logger.info("Running experiment for " + project);

      Configuration config = new Configuration.Builder().build();
      config.setProjects(Arrays.asList(project));

      List<TestCollection> testCollections =
          getTestCollections(config.getProjectBaseDirsForProjects()); //
      // Run on all
      // packages
      // List<TestCollection> testCollections = Collections.singletonList(getProjectTestCollection(logOutputPath)); // Run on one package

      // Choose an experiment here
      ThresholdExperiment experiment = new ThresholdExperiment();
      // CallDepthExperiment experiment = new CallDepthExperiment();

      experiment.runOn(config, testCollections, coverageData);
      experiment.printSummary();
    }
  }

  private static void optimisationRun(ArrayList<String> projects) throws Exception {
    Configuration config = new Configuration.Builder().build();
    config.setProjects(Arrays.asList((String[]) config.getProjectBaseDirs().keySet().toArray()));

    List<TestCollection> testCollections =
        getTestCollections((String[]) config.getProjectBaseDirs().entrySet().toArray());

    // Read coverage data
    Table<String, String, Map<CounterType, CoverageStat>> coverageData = HashBasedTable.create();
    for (String baseDir : config.getProjectBaseDirs().values()) {
      coverageData.putAll(getCoverageData(getCoverageReportsPath(baseDir)));
    }
      /*coverageData = getCoverageData(getCoverageReportsPath(BASE_DIR_COMMONS_LANG));
      coverageData.putAll(getCoverageData(getCoverageReportsPath(BASE_DIR_JFREECHART)));
      coverageData.putAll(getCoverageData(getCoverageReportsPath(BASE_DIR_COMMONS_IO)));*/

    OptimisationExperiment experiment = new OptimisationExperiment();
    Set<String> testSet = experiment.runOn(testCollections, coverageData, 0.0);

    experiment.printSummary();
    logger.info("Test set size: {}", testSet.size());
    logger.info("Test set: {}", testSet.toString());
  }

  private static void gatherLogs(ArrayList<String> projects, boolean deleteCopiedFiles) {
    for (String project : projects) {
      Configuration config = new Configuration.Builder()
          .setProjects(Arrays.asList(project)).build();
      String cttLogDestDir = config.getProjectBaseDirs().get(project) + "/ctt_logs";

      Random random = new Random();
      String[] extensions = { "log" };
      List<File> listOfFiles = (List<File>) FileUtils.listFiles(
          new File(config.getProjectBaseDirs().get(project)), extensions, true);
      for (File file : listOfFiles) {
        if (file.getName().contains("init")) {
          if (!file.renameTo(new File(cttLogDestDir + "/" + "init-" +
              random.nextInt(99999) + ".log"))) {
            Utilities.logger.debug("init log file cant be renamed");
          }
          continue;
        }

        try {
          Files.copy(file.toPath(), new File(cttLogDestDir + "/" +
                  file.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
          if (deleteCopiedFiles) {
            Files.delete(file.toPath());
          }
        } catch (IOException e) {
          Utilities.handleCaughtThrowable(e, false);
        }
      }
    }
  }

  private static void deleteLogs(ArrayList<String> projects) {
    for (String project : projects) {
      Configuration config = new Configuration.Builder()
          .setProjects(Arrays.asList(project)).build();

      String[] extensions = { "log" };
      List<File> listOfFiles = (List<File>) FileUtils.listFiles(
          new File(config.getProjectBaseDirs().get(project)), extensions, true);
      for (File file : listOfFiles) {
        if (file.getPath().contains("ctt_spectrum")) {
          continue;
        }

        try {
          Utilities.logger.info("Deleting file: " + file.getCanonicalPath());
          Files.delete(file.toPath());
        } catch (IOException e) {
          Utilities.handleCaughtThrowable(e, false);
        }
      }
    }
  }

  private static Path getCoverageReportsPath(String baseDir) {
    return Paths.get(baseDir, "ctt/output");
  }

  private static Path getSpectrumDirectory(String baseDir) {
    return Paths.get(baseDir, "ctt_spectrum");
  }

  private static Path getLogsDirectory(String baseDir) {
    return Paths.get(baseDir, "ctt_logs");
  }

  private static List<TestCollection> getTestCollections(String[] basePaths)
      throws FileNotFoundException {
    List<TestCollection> testCollections = new ArrayList<>();
    for (String basePath : basePaths) {
      testCollections.add(getProjectTestCollection(getSpectrumDirectory(basePath)));
    }
    return testCollections;
  }

  public static void logParse(Path inputDirectory, Path outputDirectory) throws IOException {
    // Create the output directory if necessary
    File outputDir = outputDirectory.toFile();
    if (!outputDir.exists()) {
      outputDir.mkdirs();
    }

    File inputDir = inputDirectory.toFile();
    File[] files = inputDir.listFiles();
    for (int i = 0; i < files.length; i++) {
      File file = files[i];
      logger.info("[{}/{}] Parsing {}", i + 1, files.length, file);
      if (file.isFile()) {
        File outputFile = outputDirectory.resolve(file.getName()).toFile();
        CTTSimpleLogParser.parse(file, outputFile);
      }
    }

    logger.info("{} logs parsed. Output directory: {}", files.length, outputDirectory);
  }

  // Performs analysis on entire project
  // One SpectraParser instance across entire project and all test classes
  private static void spectrumParse(Configuration config, Path inputDirectory,
      Path coverageReportsPath) throws FileNotFoundException {
    Table<String, String, Map<CounterType, CoverageStat>> coverageData = null;
    if (config.shouldApplyCoverageDiscount()) {
      coverageData = getCoverageData(coverageReportsPath);
    }

    SpectraParser parser = new SpectraParser(config, coverageData);
    TestCollection aggregatedTestCollection = getProjectTestCollection(inputDirectory);

    if (config.isCreateClassLevelGroundTruthSample()) {
      ArrayList<String> testClasses = getTestClassesFromTestCollection(aggregatedTestCollection);
      config.setTestClassesForGroundTruth(randomlySelectTestClassesSample(testClasses));
    }

    CollectedComputedMetrics computedMetrics = parser.computeMetrics(aggregatedTestCollection,
        VERBOSE);
    logger.info("Analysis complete.");

    // To view un-normalised traceability scores for a method-test pair, uncomment this section.
    // parser.printTraceabilityMatrix(Technique.TECHNIQUE_NAME_HERE,
    //         Arrays.asList(
    //                 "TEST_NAME_HERE"
    //         ),
    //         Arrays.asList(
    //                 "METHOD_NAME_HERE"
    //         ));

    // To view normalised traceability scores for a single test, uncomment this section.
    // parser.printTestTraceabilityMatrix(Technique.TECHNIQUE_NAME_HERE,
    //         Arrays.asList(
    //                 "TEST_NAME_HERE"
    //         ));

    if (Utilities.allProjectsHaveFunctionLevelEvaluation(config)) {
      printFunctionLevelMetricsSummary(config,
          computedMetrics.getFunctionLevelMetrics().getMetricTable(),
          ScoreType.PURE);
      printFunctionLevelMetricsSummary(config,
          computedMetrics.getAugmentedFunctionLevelMetrics().getMetricTable(),
          ScoreType.AUGMENTED);

      ResultsWriter.writeOutFunctionLevelValidationSummary(ScoreType.PURE, config,
          TechniqueMetricsProvider.computeTechniqueMetrics(
              computedMetrics.getFunctionLevelMetrics().getMetricTable()));
      ResultsWriter.writeOutFunctionLevelValidationSummary(ScoreType.AUGMENTED, config,
          TechniqueMetricsProvider.computeTechniqueMetrics(
              computedMetrics.getAugmentedFunctionLevelMetrics().getMetricTable()));
    }

    if (Utilities.allProjectsHaveClassLevelEvaluation(config)) {
      printClassLevelMetricsSummary(config, computedMetrics.getClassLevelMetrics().getMetricTable(),
          ScoreType.PURE);
      printClassLevelMetricsSummary(config, computedMetrics.getAugmentedClassLevelMetrics().getMetricTable(),
          ScoreType.AUGMENTED);

      ResultsWriter.writeOutClassLevelValidationSummary(ScoreType.PURE, config,
          TechniqueMetricsProvider.computeTechniqueMetrics(
              computedMetrics.getClassLevelMetrics().getMetricTable()));
      ResultsWriter.writeOutClassLevelValidationSummary(ScoreType.AUGMENTED, config,
          TechniqueMetricsProvider.computeTechniqueMetrics(
              computedMetrics.getAugmentedClassLevelMetrics().getMetricTable()));
    }
  }

  private static ArrayList<String> getTestClassesFromTestCollection(TestCollection testCollection) {
    ArrayList<String> testClassFqns = new ArrayList<>();
    for (HitSpectrum hitSpectrum : testCollection.tests) {
      if (!testClassFqns.contains(hitSpectrum.cls)) {
        testClassFqns.add(hitSpectrum.cls);
      }
    }

    return testClassFqns;
  }

  private static ArrayList<String> randomlySelectTestClassesSample(ArrayList<String> testClasses) {
    ArrayList<String> selectedTestClassFqns = new ArrayList<>();
    int sampleSize = 20;

    Random random = new Random();
    for (int i = 0; i < sampleSize; ++i) {
      String testClass;
      do {
        int randIdx = random.nextInt(testClasses.size());
        testClass = testClasses.get(randIdx);
      } while (selectedTestClassFqns.contains(testClass));

      selectedTestClassFqns.add(testClass);
    }

    return selectedTestClassFqns;
  }

  private static TestCollection getProjectTestCollection(Path inputDirectory)
      throws FileNotFoundException {
    TestCollection aggregatedTestCollection = new TestCollection();

    int numSpectraParsed = 0;
    File inputDir = inputDirectory.toFile();
    File[] files = inputDir.listFiles();
    if (files != null) {
      for (int i = 0; i < files.length; i++) {
        File file = files[i];
        if (file.isFile()) {
          if (VERBOSE) {
            logger.info("[{}/{}] Analysing {}", i + 1, files.length, file);
          }

          TestCollection testCollection = SpectraParser.parseJSONFile(file);
          for (HitSpectrum testHitSpectrum : testCollection.tests) {
            for (Map.Entry<String, Integer> hitSetEntry : testHitSpectrum.hitSet.entrySet()) {
              if (testHitSpectrum.cls == null || testHitSpectrum.cls.equals("null")
                  || testHitSpectrum.test == null || testHitSpectrum.test.equals("null")
                  || hitSetEntry.getKey() == null || hitSetEntry.getKey().equals("null")
                  || hitSetEntry.getValue() == null || hitSetEntry.getValue().equals("null")) {
                Utilities.logger.debug("DEUGGING NULL METHODS");
              }
            }
          }

          aggregatedTestCollection.tests.addAll(testCollection.tests);

          if (VERBOSE) {
            logger.info("Added {} tests to the collection.", testCollection.tests.size());
          }

          numSpectraParsed++;
        }
      }
    }

    logger.info("{} hit spectra parsed.", numSpectraParsed);
    logger.info("Tests in aggregated collection: {}", aggregatedTestCollection.tests.size());
    return aggregatedTestCollection;
  }

  // Performs analysis on a test-suite (class) basis and aggregate the metrics
  // One SpectraParser instance for each class
  private static void spectrumParsePerClass(Configuration config, Path inputDirectory,
      Path coverageReportsPath) throws FileNotFoundException {
    Table<String, String, Map<CounterType, CoverageStat>> coverageData = null;
    if (config.shouldApplyCoverageDiscount()) {
      coverageData = getCoverageData(coverageReportsPath);
    }

    // Build one large table from all the input hit spectra and then analyse.
    Table<Technique, String, EvaluationMetrics> aggregatedFunctionLevelMetricsTable = HashBasedTable
        .create();
    Table<Technique, String, EvaluationMetrics> aggregatedClassLevelMetricsTable =
        HashBasedTable.create();

    Table<Technique, String, EvaluationMetrics> aggregatedAugmentedFunctionLevelMetricsTable =
        HashBasedTable.create();
    Table<Technique, String, EvaluationMetrics> aggregatedAugmentedClassLevelMetricsTable =
        HashBasedTable.create();

    File inputDir = inputDirectory.toFile();
    File[] files = inputDir.listFiles();
    for (int i = 0; i < files.length; i++) {
      File file = files[i];
      if (file.isFile()) {
        System.out.printf("[%d/%d] Analysing %s %n", i + 1, files.length, file);
        SpectraParser parser = new SpectraParser(config, coverageData);
        Table<Technique, String, EvaluationMetrics> functionLevelMetricsTable =
            parser.parse(file, VERBOSE).getFunctionLevelMetrics().getMetricTable();
        Table<Technique, String, EvaluationMetrics> classLevelMetricsTable =
            parser.parse(file, VERBOSE).getClassLevelMetrics().getMetricTable();

        Table<Technique, String, EvaluationMetrics> augmentedFunctionLevelMetricsTable =
            parser.parse(file, VERBOSE).getAugmentedFunctionLevelMetrics().getMetricTable();
        Table<Technique, String, EvaluationMetrics> augmentedClassLevelMetricsTable =
            parser.parse(file, VERBOSE).getAugmentedClassLevelMetrics().getMetricTable();

        System.out.printf("Number of tests: %d %n",
            functionLevelMetricsTable.columnKeySet().size());

        aggregatedFunctionLevelMetricsTable.putAll(functionLevelMetricsTable);
        aggregatedClassLevelMetricsTable.putAll(classLevelMetricsTable);

        aggregatedAugmentedFunctionLevelMetricsTable.putAll(augmentedFunctionLevelMetricsTable);
        aggregatedAugmentedClassLevelMetricsTable.putAll(augmentedClassLevelMetricsTable);
      }
    }
    System.out.printf("%d hit spectra parsed. %n", files.length);

    if (Utilities.allProjectsHaveFunctionLevelEvaluation(config)) {
      printFunctionLevelMetricsSummary(config, aggregatedFunctionLevelMetricsTable, ScoreType.PURE);
      printFunctionLevelMetricsSummary(config, aggregatedAugmentedFunctionLevelMetricsTable,
          ScoreType.AUGMENTED);

      ResultsWriter.writeOutFunctionLevelValidationSummary(ScoreType.PURE, config,
          TechniqueMetricsProvider.computeTechniqueMetrics(aggregatedFunctionLevelMetricsTable));
      ResultsWriter.writeOutFunctionLevelValidationSummary(ScoreType.AUGMENTED, config,
          TechniqueMetricsProvider.computeTechniqueMetrics(
              aggregatedAugmentedFunctionLevelMetricsTable));
    }

    if (Utilities.allProjectsHaveClassLevelEvaluation(config)) {
      printClassLevelMetricsSummary(config, aggregatedClassLevelMetricsTable, ScoreType.PURE);
      printClassLevelMetricsSummary(config, aggregatedAugmentedClassLevelMetricsTable,
          ScoreType.AUGMENTED);

      ResultsWriter.writeOutClassLevelValidationSummary(ScoreType.PURE, config,
          TechniqueMetricsProvider.computeTechniqueMetrics(aggregatedClassLevelMetricsTable));
      ResultsWriter.writeOutClassLevelValidationSummary(ScoreType.AUGMENTED, config,
          TechniqueMetricsProvider.computeTechniqueMetrics(aggregatedAugmentedClassLevelMetricsTable));
    }
  }

  private static void printFunctionLevelMetricsSummary(Configuration config,
      Table<Technique, String, EvaluationMetrics> functionLevelMetricsTable, ScoreType scoreType) {
    // System.out.println("======= AGGREGATED TECHNIQUE EVALUATION METRICS =======");
    // SpectraParser.printEvaluationMetrics(config.getTechniqueList(), null, metricsTable);

    config.printConfiguration();

    System.out.println("======= TECHNIQUE METRICS =======");
    TechniqueMetricsProvider.printTechniqueMetrics(Utilities.getTechniques(config,
        Configuration.Level.METHOD, scoreType),
        TechniqueMetricsProvider.computeTechniqueMetrics(functionLevelMetricsTable));

    System.out.println("======= TECHNIQUE METRICS DATA =======");
    TechniqueMetricsProvider.printTechniqueMetricsData(Utilities.getTechniques(config,
        Configuration.Level.METHOD, scoreType),
        TechniqueMetricsProvider.computeTechniqueMetrics(functionLevelMetricsTable));

    int testCount = functionLevelMetricsTable.columnKeySet().size();
    System.out.printf("Total number of tests: %d %n", testCount);
  }

  private static void printClassLevelMetricsSummary(Configuration config,
      Table<Technique, String, EvaluationMetrics> classLevelMetricsTable, ScoreType scoreType) {
    config.printConfiguration();

    System.out.println("======= TECHNIQUE METRICS =======");
    TechniqueMetricsProvider.printTechniqueMetrics(Utilities.getTechniques(config,
        Configuration.Level.CLASS, scoreType),
        TechniqueMetricsProvider.computeTechniqueMetrics(classLevelMetricsTable));
  }

  private static Table<String, String, Map<CounterType, CoverageStat>> getCoverageData(
      Path coverageReportsPath) {
    Table<String, String, Map<CounterType, CoverageStat>> coverageData = null;
    // If applying coverage discount, read coverage data into memory
    if (coverageReportsPath != null) {
      try {
        coverageData = CoverageAnalyser.analyseReports(coverageReportsPath);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return coverageData;
  }
}
