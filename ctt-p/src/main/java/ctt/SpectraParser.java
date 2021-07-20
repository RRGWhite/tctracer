package ctt;

import com.google.common.collect.Table;
import com.google.gson.Gson;
import ctt.coverage.CoverageAnalyser.CounterType;
import ctt.coverage.CoverageAnalyser.CoverageStat;
import ctt.metrics.ClassLevelMetricsProvider;
import ctt.metrics.FunctionLevelMetricsProvider;
import ctt.types.ClassLevelMetrics;
import ctt.types.CollectedComputedMetrics;
import ctt.types.FunctionLevelMetrics;
import ctt.types.TestCollection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads JSON hit sets into memory to perform analysis
 */
public class SpectraParser {

  private static final Logger logger = LogManager.getLogger(SpectraParser.class.getName());

  // Configuration
  private Configuration config = null;

  // -- OPTIONAL FIELDS --
  // Coverage data - this is optional! Check for null before using.
  private Table<String, String, Map<CounterType, CoverageStat>> coverageData = null;
  // -- END OPTIONAL FIELDS --

  public SpectraParser(Configuration config) {
    this(config, null);
  }

  public SpectraParser(Configuration config,
      Table<String, String, Map<CounterType, CoverageStat>> coverageData) {
    this.config = config;
    this.coverageData = coverageData;
  }

  public static ArrayList<CollectedComputedMetrics> parseTestCollections(Configuration config,
      List<TestCollection> testCollections, Table<String, String, Map<CounterType,
      CoverageStat>> coverageData, boolean verbose) {
    // Parse on each test collection and aggregate metrics.
    ArrayList<CollectedComputedMetrics> aggregatedMetrics = new ArrayList<>();
    for (TestCollection testCollection : testCollections) {
      SpectraParser parser = new SpectraParser(config, coverageData);
      CollectedComputedMetrics metrics = parser.computeMetrics(testCollection, verbose);
      aggregatedMetrics.add(metrics);
    }

    return aggregatedMetrics;
  }

  public enum Metric {
    PRECISION("Precision"),
    RECALL("Recall"),
    F_SCORE("F-Score"),
    MAP("MAP"),
    BPREF("Bpref"),
    AUPRC("AUPRC"),
    TRUE_POSITIVES("True Positives"),
    FALSE_POSITIVES("False Positives"),
    FALSE_NEGATIVES("False Negatives");

    private final String text;

    Metric(String text) {
      this.text = text;
    }

    @Override
    public String toString() {
      return text;
    }
  }

  // Similarity metrics for name similarity
  enum SimilarityMetrics {
    CONTAINS, // Method name is contained in the test name
    LEVENSHTEIN_DISTANCE,
    LONGEST_COMMON_SUBSEQUENCE,
    COSINE_DISTANCE,
  }

  public CollectedComputedMetrics parse(File file, boolean verbose) throws FileNotFoundException {
    TestCollection testCollection = parseJSONFile(file);
    if (verbose) {
      System.out.println("JSON parse finished");
    }
    return computeMetrics(testCollection, verbose);
  }

  public static TestCollection parseJSONFile(File file) throws FileNotFoundException {
    BufferedReader br = new BufferedReader(new FileReader(file));
    Gson gson = new Gson();
    try {
      return gson.fromJson( br, TestCollection.class );
    } catch (Exception e) {
      return null;
    }
  }

  public CollectedComputedMetrics computeMetrics(TestCollection testCollection, boolean verbose) {
    FunctionLevelMetricsProvider functionLevelMetricsProvider =
        new FunctionLevelMetricsProvider(config);

    FunctionLevelMetrics functionLevelMetrics =
        functionLevelMetricsProvider.calculateFunctionLevelMetrics(testCollection, verbose);

    ClassLevelMetricsProvider classLevelMetricsProvider =
        new ClassLevelMetricsProvider(config);

    ClassLevelMetrics classLevelMetrics =
        classLevelMetricsProvider.calculateClassLevelMetrics(testCollection,
        functionLevelMetrics, verbose);

    FunctionLevelMetrics augmentedFunctionLevelMetrics =
        functionLevelMetricsProvider.augmentFunctionLevelMetrics(functionLevelMetrics,
            classLevelMetrics, verbose);

    ClassLevelMetrics augmentedClassLevelMetrics =
        classLevelMetricsProvider.augmentClassLevelMetrics(classLevelMetrics,
            functionLevelMetrics, verbose);

    return new CollectedComputedMetrics(functionLevelMetrics, classLevelMetrics,
        augmentedFunctionLevelMetrics, augmentedClassLevelMetrics);
  }
}
