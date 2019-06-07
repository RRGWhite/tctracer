package ctt;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import ctt.coverage.CoverageAnalyser;
import ctt.coverage.CoverageAnalyser.CounterType;
import ctt.coverage.CoverageAnalyser.CoverageStat;
import ctt.experiments.*;
import ctt.types.TestCollection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class.getName());

    private static boolean VERBOSE = false;

    enum RunType {
        SINGLE, SINGLE_ALL, EXPERIMENT, OPTIMISATION
    }

    public static void main(String[] args) throws Exception {
        // Set paths here as appropriate to point to the base directory of the projects under evaluation.
        String BASE_DIR_COMMONS_LANG = "projects/clang";
        String BASE_DIR_JFREECHART = "projects/jfreechart";
        String BASE_DIR_COMMONS_IO = "projects/commons-io";
        String[] allBaseDirs = {BASE_DIR_COMMONS_LANG, BASE_DIR_JFREECHART, BASE_DIR_COMMONS_IO};

        // Specify a single project for evaluation for run types that operate on single projects.
        String baseDir;
        // baseDir = BASE_DIR_COMMONS_LANG;
        // baseDir = BASE_DIR_JFREECHART;
        baseDir = BASE_DIR_COMMONS_IO;

        Path logInputPath = getLogsDirectory(baseDir);
        Path logOutputPath = getSpectrumDirectory(baseDir);
        Path coverageReportsPath = getCoverageReportsPath(baseDir);

        // Parse execution trace -> produce hit spectra.
        // Only needs to be done once per project - can be commented out once hit spectrum is generated.
        // logParse(logInputPath, logOutputPath);

        RunType runType = null;
        // runType = RunType.SINGLE;
        runType = RunType.SINGLE_ALL;
        // runType = RunType.EXPERIMENT;
        // runType = RunType.OPTIMISATION;

        if (runType == RunType.SINGLE) {
            // Run single configuration
            Configuration config = new Configuration.Builder()
                    // .setThresholdValue(0.9)
                    .setCallDepthDiscountFactor(0.5)
                    // .setApplyCoverageDiscount(true)
                    // .setTechniqueList(Presets.TECHNIQUES_NAME_SIMILARITY)
                    // .setTechniqueList(Presets.TECHNIQUES_OTHER)
                    .build();
            spectrumParse(config, logOutputPath, coverageReportsPath);

        } else if (runType == RunType.SINGLE_ALL) {
            Configuration config = new Configuration.Builder()
                    .setCallDepthDiscountFactor(0.5)
                    // .setTechniqueList(Presets.TECHNIQUES_NAME_SIMILARITY)
                    // .setTechniqueList(Presets.TECHNIQUES_OTHER)
                    .build();

            List<TestCollection> testCollections = getTestCollections(allBaseDirs);
            Table<Technique, String, EvaluationMetrics> aggregatedMetricsTable = SpectraParser.parseTestCollections(config, testCollections, null);
            printSummary(config, aggregatedMetricsTable);

        } else if (runType == RunType.EXPERIMENT) {
            // Run an experiment
            Table<String, String, Map<CounterType, CoverageStat>> coverageData = null;

            // Uncomment to use coverage data.
            // coverageData = getCoverageData(coverageReportsPath);

            List<TestCollection> testCollections = getTestCollections(allBaseDirs); // Run on all packages
            // List<TestCollection> testCollections = Collections.singletonList(getProjectTestCollection(logOutputPath)); // Run on one package

            // Choose an experiment here
            ThresholdExperiment experiment = new ThresholdExperiment();
            // CallDepthExperiment experiment = new CallDepthExperiment();

            experiment.runOn(testCollections, coverageData);
            experiment.printSummary();

        } else if (runType == RunType.OPTIMISATION) {
            List<TestCollection> testCollections = getTestCollections(allBaseDirs);

            // Read coverage data
            Table<String, String, Map<CounterType, CoverageStat>> coverageData = null;
            coverageData = getCoverageData(getCoverageReportsPath(BASE_DIR_COMMONS_LANG));
            coverageData.putAll(getCoverageData(getCoverageReportsPath(BASE_DIR_JFREECHART)));
            coverageData.putAll(getCoverageData(getCoverageReportsPath(BASE_DIR_COMMONS_IO)));

            OptimisationExperiment experiment = new OptimisationExperiment();
            Set<String> testSet = experiment.runOn(testCollections, coverageData, 0.0);

            experiment.printSummary();
            logger.info("Test set size: {}", testSet.size());
            logger.info("Test set: {}", testSet.toString());

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

    private static List<TestCollection> getTestCollections(String[] basePaths) throws FileNotFoundException {
        List<TestCollection> testCollections = new ArrayList<>();
        for (String basePath : basePaths) {
            testCollections.add(getProjectTestCollection(getSpectrumDirectory(basePath)));
        }
        return testCollections;
    }

    public static void logParse(Path inputDirectory, Path outputDirectory) throws IOException {
        // Create the output directory if necessary
        File outputDir = outputDirectory.toFile();
        if (!outputDir.exists()) outputDir.mkdirs();

        File inputDir = inputDirectory.toFile();
        File[] files = inputDir.listFiles();
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            logger.info("[{}/{}] Parsing {}", i+1, files.length, file);
            if (file.isFile()) {
                File outputFile = outputDirectory.resolve(file.getName()).toFile();
                CTTSimpleLogParser.parse(file, outputFile);
            }
        }

        logger.info("{} logs parsed. Output directory: {}", files.length, outputDirectory);
    }

    // Performs analysis on entire project
    // One SpectraParser instance across entire project and all test classes
    private static void spectrumParse(Configuration config, Path inputDirectory, Path coverageReportsPath) throws FileNotFoundException {
        Table<String, String, Map<CounterType, CoverageStat>> coverageData = null;
        if (config.shouldApplyCoverageDiscount()) {
            coverageData = getCoverageData(coverageReportsPath);
        }

        SpectraParser parser = new SpectraParser(config, coverageData);
        TestCollection aggregatedTestCollection = getProjectTestCollection(inputDirectory);

        Table<Technique, String, EvaluationMetrics> metricsTable = parser.parseTestCollection(aggregatedTestCollection, VERBOSE);
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

        printSummary(config, metricsTable);
    }

    private static TestCollection getProjectTestCollection(Path inputDirectory) throws FileNotFoundException {
        TestCollection aggregatedTestCollection = new TestCollection();

        File inputDir = inputDirectory.toFile();
        File[] files = inputDir.listFiles();
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file.isFile()) {
                logger.info("[{}/{}] Analysing {}", i+1, files.length, file);
                TestCollection testCollection = SpectraParser.parseJSONFile(file);
                aggregatedTestCollection.tests.addAll(testCollection.tests);
                logger.info("Added {} tests to the collection.", testCollection.tests.size());
            }
        }
        logger.info("{} hit spectra parsed.", files.length);
        logger.info("Tests in aggregated collection: {}", aggregatedTestCollection.tests.size());
        return aggregatedTestCollection;
    }

    // Performs analysis on a test-suite (class) basis and aggregate the metrics
    // One SpectraParser instance for each class
    private static void spectrumParsePerClass(Configuration config, Path inputDirectory, Path coverageReportsPath) throws FileNotFoundException {
        Table<String, String, Map<CounterType, CoverageStat>> coverageData = null;
        if (config.shouldApplyCoverageDiscount()) {
            coverageData = getCoverageData(coverageReportsPath);
        }

        // Build one large table from all the input hit spectra and then analyse.
        Table<Technique, String, EvaluationMetrics> aggregatedMetricsTable = HashBasedTable.create();

        File inputDir = inputDirectory.toFile();
        File[] files = inputDir.listFiles();
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file.isFile()) {
                System.out.printf("[%d/%d] Analysing %s %n", i+1, files.length, file);
                SpectraParser parser = new SpectraParser(config, coverageData);
                Table<Technique, String, EvaluationMetrics> metricsTable = parser.parse(file, VERBOSE);
                System.out.printf("Number of tests: %d %n", metricsTable.columnKeySet().size());

                aggregatedMetricsTable.putAll(metricsTable);
            }
        }
        System.out.printf("%d hit spectra parsed. %n", files.length);

        printSummary(config, aggregatedMetricsTable);
    }

    private static void printSummary(Configuration config, Table<Technique, String, EvaluationMetrics> metricsTable) {
        // System.out.println("======= AGGREGATED TECHNIQUE EVALUATION METRICS =======");
        // SpectraParser.printEvaluationMetrics(config.getTechniqueList(), null, metricsTable);

        config.printConfiguration();

        System.out.println("======= TECHNIQUE METRICS =======");
        SpectraParser.printTechniqueMetrics(config.getTechniqueList(), SpectraParser.computeTechniqueMetrics(metricsTable));

        System.out.println("======= TECHNIQUE METRICS DATA =======");
        SpectraParser.printTechniqueMetricsData(config.getTechniqueList(), SpectraParser.computeTechniqueMetrics(metricsTable));

        int testCount = metricsTable.columnKeySet().size();
        System.out.printf("Total number of tests: %d %n", testCount);
    }

    private static Table<String, String, Map<CounterType, CoverageStat>> getCoverageData(Path coverageReportsPath) {
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
