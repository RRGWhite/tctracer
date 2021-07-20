package ctt;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import ctt.types.Technique;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * This class represents all configurable parameters
 *
 * - Threshold data
 */
public class Configuration {

  public enum AugmentationType {TECHNIQUE_SCORE, COMBINED_SCORE, TFIDF, CONTAINS}
  public enum ScoreCombinationMethod {SUM, PRODUCT, AVERAGE}
  public enum Level {METHOD, CLASS}
  public enum ArtefactType {FUNCTION, TEST}
  public enum Platform {WINDOWS, UBUNTU}
  private String corpusPath = "corpus";
  private String feedForwardNetworkScript = "tctracer_ffn.py";
  private String mlDir = "ml";
  private Platform platform =
      System.getProperty("os.name").toLowerCase().contains("windows") ? Platform.WINDOWS :
          Platform.UBUNTU;
  private String pythonCommand = platform.equals(Platform.WINDOWS) ? "python" : "python3";

  private Configuration() {
  }

  private Map<String, String> projectBaseDirs = Maps
      .newHashMap(ImmutableMap.<String, String>builder()
          .put("commons-lang", corpusPath + "/commons-lang")
          .put("jfreechart", corpusPath + "/jfreechart")
          .put("commons-io", corpusPath + "/commons-io")
          .put("argouml", corpusPath + "/argouml-v-30-1")
          .put("apache-ant", corpusPath + "/apache-ant")
          .put("dependency-finder", corpusPath + "/dependency-finder")
          .put("ccollections", corpusPath + "/ccollections")
          .put("barbecue", corpusPath + "/barbecue")
          .put("gcviewer", corpusPath + "/gcviewer")
          .put("terrier-core", corpusPath + "/terrier-core")
          .put("joda-time", corpusPath + "/joda-time")
          .put("gson", corpusPath + "/gson/gson")
          .build()
      );

  private Map<String, String> projectSrcDirs = Maps
      .newHashMap(ImmutableMap.<String, String>builder()
          .put("commons-lang", projectBaseDirs.get("commons-lang") + "/src/main")
          .put("jfreechart", projectBaseDirs.get("jfreechart") + "/source")
          .put("commons-io", projectBaseDirs.get("commons-io") + "/src/main")
          .put("apache-ant", projectBaseDirs.get("apache-ant") + "/src/main")
          .put("ccollections", projectBaseDirs.get("ccollections") + "/src")
          .put("barbecue", projectBaseDirs.get("barbecue") + "/src/main")
          .put("gcviewer", projectBaseDirs.get("gcviewer") + "/src/main")
          .put("terrier-core", projectBaseDirs.get("terrier-core") + "/src/core")
          .put("joda-time", projectBaseDirs.get("joda-time") + "/src/main/java")
          .put("gson", projectBaseDirs.get("gson") + "/src/main")
          .build()
      );

  private Map<String, String> projectTestSrcDirs = Maps
      .newHashMap(ImmutableMap.<String, String>builder()
          .put("commons-lang", projectBaseDirs.get("commons-lang") + "/src/test")
          .put("jfreechart", projectBaseDirs.get("jfreechart") + "/tests")
          .put("commons-io", projectBaseDirs.get("commons-io") + "/src/test")
          .put("apache-ant", projectBaseDirs.get("apache-ant") + "/src/tests")
          .put("ccollections", projectBaseDirs.get("ccollections") + "/src")
          .put("barbecue", projectBaseDirs.get("barbecue") + "/src/test")
          .put("gcviewer", projectBaseDirs.get("gcviewer") + "/src/test")
          .put("terrier-core", projectBaseDirs.get("terrier-core") + "/src/test")
          .put("joda-time", projectBaseDirs.get("joda-time") + "/src/test/java")
          .put("gson", projectBaseDirs.get("gson") + "/src/test")
          .build()
      );

  private List<String> projects;

  private boolean parseCttLogs = false;

  private List<String> functionLevelEvaluationProjects = Arrays.asList(
      "commons-lang", "jfreechart", "commons-io", "barbecue", "gcviewer", "terrier-core",
      "joda-time", "gson");

  private List<String> classLevelEvaluationProjects = Arrays.asList(
      "apache-ant", "argouml", "dependency-finder", "commons-lang", "commons-io", "jfreechart");

  private boolean singleProject;

  // Whether or not to discount based on coverage.
  // 100% coverage = full score. 90% coverage = score * 0.9.
  private boolean applyCoverageDiscount = false;

  // The discount factor to apply based on call depth.
  // Discounted score = score * (discountFactor^callDepth)
  // Set to 1 to disable discounting. Set to 0 to only consider top-level methods.
  private double callDepthDiscountFactor = 0.5;

  // All techniques to compute metrics for.
  // Do not include ground truth technique in this list!
  private Technique[] methodLevelTechniqueList = {
      Technique.NC,
      Technique.NCC,
      Technique.LCS_U_N,
      Technique.LCS_B_N,
      Technique.LEVENSHTEIN_N,
      Technique.LCBA,
      Technique.TARANTULA,
      Technique.TFIDF,
      Technique.STATIC_NC,
      Technique.STATIC_NCC,
      Technique.STATIC_LCS_U_N,
      Technique.STATIC_LCS_B_N,
      Technique.STATIC_LEVENSHTEIN_N,
      //Technique.STATIC_LCBA,
      Technique.COMBINED,
      Technique.COMBINED_FFN
  };

  private Technique[] classLevelTechniqueList = {
      Technique.NC_CLASS,
      Technique.NCC_CLASS,
      Technique.LCS_U_N_CLASS,
      Technique.LCS_B_N_CLASS,
      Technique.LEVENSHTEIN_N_CLASS,
      Technique.LCBA_CLASS,
      Technique.TARANTULA_CLASS,
      Technique.TFIDF_CLASS,
      Technique.STATIC_NC_CLASS,
      Technique.STATIC_NCC_CLASS,
      Technique.STATIC_LCS_U_N_CLASS,
      Technique.STATIC_LCS_B_N_CLASS,
      Technique.STATIC_LEVENSHTEIN_N_CLASS,
      //Technique.STATIC_LCBA_CLASS,
      Technique.COMBINED_CLASS,
      Technique.COMBINED_CLASS_FFN
  };

  private Technique[] multiLevelTechniqueList = {
      Technique.NC_MULTI,
      Technique.NCC_MULTI,
      Technique.LCS_U_N_MULTI,
      Technique.LCS_B_N_MULTI,
      Technique.LEVENSHTEIN_N_MULTI,
      Technique.LCBA_MULTI,
      Technique.TARANTULA_MULTI,
      Technique.TFIDF_MULTI,
      Technique.STATIC_NC_MULTI,
      Technique.STATIC_NCC_MULTI,
      Technique.STATIC_LCS_U_N_MULTI,
      Technique.STATIC_LCS_B_N_MULTI,
      Technique.STATIC_LEVENSHTEIN_N_MULTI,
      //Technique.STATIC_LCBA_MULTI,
      Technique.COMBINED_MULTI,
      Technique.COMBINED_MULTI_FFN
  };

  // All techniques listed here will be normalized.
  private Technique[] techniquesToNormalize = {
      Technique.LCS_U_N,
      Technique.LCS_B_N,
      Technique.LCS_U_N_CLASS,
      Technique.LCS_B_N_CLASS,
      Technique.LCS_U_N_MULTI,
      Technique.LCS_B_N_MULTI,
      Technique.LEVENSHTEIN_N,
      Technique.LEVENSHTEIN_N_CLASS,
      Technique.LEVENSHTEIN_N_MULTI,
      Technique.TARANTULA,
      Technique.TARANTULA_CLASS,
      Technique.TARANTULA_MULTI,
      Technique.TFIDF,
      Technique.TFIDF_CLASS,
      Technique.TFIDF_MULTI,
      Technique.COMBINED,
      Technique.COMBINED_CLASS,
      Technique.COMBINED_MULTI,
      Technique.STATIC_LCS_U_N,
      Technique.STATIC_LCS_B_N,
      Technique.STATIC_LCS_U_N_CLASS,
      Technique.STATIC_LCS_B_N_CLASS,
      Technique.STATIC_LCS_U_N_MULTI,
      Technique.STATIC_LCS_B_N_MULTI,
      Technique.STATIC_LEVENSHTEIN_N,
      Technique.STATIC_LEVENSHTEIN_N_CLASS,
      Technique.STATIC_LEVENSHTEIN_N_MULTI
  };

  private Technique[] methodLevelCombinedScoreComponents = {
      Technique.NC,
      Technique.NCC,
      Technique.LCS_U_N,
      Technique.LCS_B_N,
      Technique.LEVENSHTEIN_N,
      Technique.LCBA,
      Technique.TARANTULA,
      Technique.TFIDF,
      Technique.STATIC_NC,
      Technique.STATIC_NCC,
      Technique.STATIC_LCS_U_N,
      Technique.STATIC_LCS_B_N,
      Technique.STATIC_LEVENSHTEIN_N,
      //Technique.STATIC_LCBA
  };

  private Technique[] classLevelCombinedScoreComponents = {
      Technique.NC_CLASS,
      Technique.NCC_CLASS,
      Technique.LCS_U_N_CLASS,
      Technique.LCS_B_N_CLASS,
      Technique.LEVENSHTEIN_N_CLASS,
      Technique.LCBA_CLASS,
      Technique.TARANTULA_CLASS,
      Technique.TFIDF_CLASS,
      Technique.STATIC_NC_CLASS,
      Technique.STATIC_NCC_CLASS,
      Technique.STATIC_LCS_U_N_CLASS,
      Technique.STATIC_LCS_B_N_CLASS,
      Technique.STATIC_LEVENSHTEIN_N_CLASS,
      //Technique.STATIC_LCBA_CLASS
  };

  //Precision-based weights
  /*private Map<Technique, Double> methodLevelTechniqueWeights = Maps
      .newHashMap(ImmutableMap.<Technique, Double>builder()
          .put(Technique.NC, 1.000)
          .put(Technique.NCC, 0.9206)
          .put(Technique.LCS_U_N, 0.5408)
          .put(Technique.LCS_B_N, 0.5250)
          .put(Technique.LEVENSHTEIN_N, 0.7484)
          .put(Technique.LCBA, 0.5858)
          .put(Technique.TARANTULA, 0.5336)
          .put(Technique.TFIDF, 0.6321)
          .put(Technique.STATIC_NC, 0.9000)
          .put(Technique.STATIC_NCC, 0.3321)
          .put(Technique.STATIC_LCS_U_N, 0.2366)
          .put(Technique.STATIC_LCS_B_N, 0.2921)
          .put(Technique.STATIC_LEVENSHTEIN_N, 0.2986)
          //.put(Technique.STATIC_LCBA, 0.73)
          .put(Technique.COMBINED, 1.0)
          .put(Technique.COMBINED_FFN, 1.0)
          .build()
      );

  private Map<Technique, Double> classLevelTechniqueWeights = Maps
      .newHashMap(ImmutableMap.<Technique, Double>builder()
          .put(Technique.NC_CLASS, 1.000)
          .put(Technique.NCC_CLASS, 0.8735)
          .put(Technique.LCS_U_N_CLASS, 0.6831)
          .put(Technique.LCS_B_N_CLASS, 0.6068)
          .put(Technique.LEVENSHTEIN_N_CLASS, 0.9818)
          .put(Technique.LCBA_CLASS, 0.4437)
          .put(Technique.TARANTULA_CLASS, 0.6417)
          .put(Technique.TFIDF_CLASS, 0.5829)
          .put(Technique.STATIC_NC_CLASS, 1.000)
          .put(Technique.STATIC_NCC_CLASS, 0.8024)
          .put(Technique.STATIC_LCS_U_N_CLASS, 0.6866)
          .put(Technique.STATIC_LCS_B_N_CLASS, 0.9475)
          .put(Technique.STATIC_LEVENSHTEIN_N_CLASS, 0.9589)
          //.put(Technique.STATIC_LCBA_CLASS, 0.73)
          .put(Technique.COMBINED_CLASS, 1.000)
          .put(Technique.COMBINED_CLASS_FFN, 1.000)
          .build()
      );*/

  //Even weighting
  private Map<Technique, Double> methodLevelTechniqueWeights = Maps
      .newHashMap(ImmutableMap.<Technique, Double>builder()
          .put(Technique.NC, 1.000)
          .put(Technique.NCC, 1.000)
          .put(Technique.LCS_U_N, 1.000)
          .put(Technique.LCS_B_N, 1.000)
          .put(Technique.LEVENSHTEIN_N, 1.000)
          .put(Technique.LCBA, 1.000)
          .put(Technique.TARANTULA, 1.000)
          .put(Technique.TFIDF, 1.000)
          .put(Technique.STATIC_NC, 1.000)
          .put(Technique.STATIC_NCC, 1.000)
          .put(Technique.STATIC_LCS_U_N, 1.000)
          .put(Technique.STATIC_LCS_B_N, 1.000)
          .put(Technique.STATIC_LEVENSHTEIN_N, 1.000)
          //.put(Technique.STATIC_LCBA, 0.73)
          .put(Technique.COMBINED, 1.000)
          .put(Technique.COMBINED_FFN, 1.000)
          .build()
      );

  private Map<Technique, Double> classLevelTechniqueWeights = Maps
      .newHashMap(ImmutableMap.<Technique, Double>builder()
          .put(Technique.NC_CLASS, 1.000)
          .put(Technique.NCC_CLASS, 1.000)
          .put(Technique.LCS_U_N_CLASS, 1.000)
          .put(Technique.LCS_B_N_CLASS, 1.000)
          .put(Technique.LEVENSHTEIN_N_CLASS, 1.000)
          .put(Technique.LCBA_CLASS, 1.000)
          .put(Technique.TARANTULA_CLASS, 1.000)
          .put(Technique.TFIDF_CLASS, 1.000)
          .put(Technique.STATIC_NC_CLASS, 1.000)
          .put(Technique.STATIC_NCC_CLASS, 1.000)
          .put(Technique.STATIC_LCS_U_N_CLASS, 1.000)
          .put(Technique.STATIC_LCS_B_N_CLASS, 1.000)
          .put(Technique.STATIC_LEVENSHTEIN_N_CLASS, 1.000)
          //.put(Technique.STATIC_LCBA_CLASS, 0.73)
          .put(Technique.COMBINED_CLASS, 1.000)
          .put(Technique.COMBINED_CLASS_FFN, 1.000)
          .build()
      );

  // Relevance values must be at or above these thresholds for a method to be included in the candidate set.
  // If not present in this map, the default is no threshold (0.0).
  //SAVED OPTIMISED THRESHOLDS
  /*private Map<Technique, Double> thresholdData = Maps
      .newHashMap(ImmutableMap.<Technique, Double>builder()
          .put(Technique.NC, 1.00)
          .put(Technique.NC_CLASS, 1.00)
          .put(Technique.NC_MULTI, 1.00)
          .put(Technique.STATIC_NC, 1.00)
          .put(Technique.STATIC_NC_CLASS, 1.00)
          .put(Technique.STATIC_NC_MULTI, 1.00)
          .put(Technique.NCC, 1.00)
          .put(Technique.NCC_CLASS, 1.00)
          .put(Technique.NCC_MULTI, 1.00)
          .put(Technique.STATIC_NCC, 1.00)
          .put(Technique.STATIC_NCC_CLASS, 1.00)
          .put(Technique.STATIC_NCC_MULTI, 1.00)
          .put(Technique.LCS_B_N, 0.55)
          .put(Technique.LCS_B_N_CLASS, 0.55)
          .put(Technique.LCS_B_N_MULTI, 0.70)
          .put(Technique.STATIC_LCS_B_N, 1.0) // Set to  0.00001 to check for full recall
          .put(Technique.STATIC_LCS_B_N_CLASS, 1.0) // Set to  0.00001 to check for full recall
          .put(Technique.STATIC_LCS_B_N_MULTI, 1.0) // Set to  0.00001 to check for full recall
          .put(Technique.LCS_U_N, 0.75)
          .put(Technique.LCS_U_N_CLASS, 0.75)
          .put(Technique.LCS_U_N_MULTI, 0.85)
          .put(Technique.STATIC_LCS_U_N, 1.0)
          .put(Technique.STATIC_LCS_U_N_CLASS, 1.0)
          .put(Technique.STATIC_LCS_U_N_MULTI, 1.0)
          .put(Technique.LEVENSHTEIN_N, 0.95)
          .put(Technique.LEVENSHTEIN_N_CLASS, 0.95)
          .put(Technique.LEVENSHTEIN_N_MULTI, 0.95)
          .put(Technique.STATIC_LEVENSHTEIN_N, 0.995)
          .put(Technique.STATIC_LEVENSHTEIN_N_CLASS, 0.995)
          .put(Technique.STATIC_LEVENSHTEIN_N_MULTI, 0.995)
          .put(Technique.LCBA, 1.0)
          .put(Technique.LCBA_CLASS, 1.0)
          .put(Technique.LCBA_MULTI, 1.0)
          .put(Technique.STATIC_LCBA, 1.0)
          .put(Technique.STATIC_LCBA_CLASS, 1.0)
          .put(Technique.STATIC_LCBA_MULTI, 1.0)
          .put(Technique.TARANTULA, 0.995)
          .put(Technique.TARANTULA_CLASS, 0.995)
          .put(Technique.TARANTULA_MULTI, 0.999)
          .put(Technique.TFIDF, 0.90)
          .put(Technique.TFIDF_CLASS, 0.90)
          .put(Technique.TFIDF_MULTI, 0.95)
          .put(Technique.COMBINED, 0.80)
          .put(Technique.COMBINED_CLASS, 0.80)
          .put(Technique.COMBINED_MULTI, 0.95)
          .put(Technique.COMBINED_FFN, 0.90)
          .put(Technique.COMBINED_CLASS_FFN, 0.90)
          .put(Technique.COMBINED_MULTI_FFN, 0.95)
          .build()
      );*/

  private Map<Technique, Double> thresholdData = Maps
      .newHashMap(ImmutableMap.<Technique, Double>builder()
          .put(Technique.NC, 1.00)
          .put(Technique.NC_CLASS, 1.00)
          .put(Technique.NC_MULTI, 1.00)
          .put(Technique.STATIC_NC, 1.00)
          .put(Technique.STATIC_NC_CLASS, 1.00)
          .put(Technique.STATIC_NC_MULTI, 1.00)
          .put(Technique.NCC, 1.00)
          .put(Technique.NCC_CLASS, 1.00)
          .put(Technique.NCC_MULTI, 1.00)
          .put(Technique.STATIC_NCC, 1.00)
          .put(Technique.STATIC_NCC_CLASS, 1.00)
          .put(Technique.STATIC_NCC_MULTI, 1.00)
          .put(Technique.LCS_B_N, 0.55)
          .put(Technique.LCS_B_N_CLASS, 0.55)
          .put(Technique.LCS_B_N_MULTI, 0.70)
          .put(Technique.STATIC_LCS_B_N, 1.0) // Set to  0.00001 to check for full recall
          .put(Technique.STATIC_LCS_B_N_CLASS, 1.0) // Set to  0.00001 to check for full recall
          .put(Technique.STATIC_LCS_B_N_MULTI, 1.0) // Set to  0.00001 to check for full recall
          .put(Technique.LCS_U_N, 0.75)
          .put(Technique.LCS_U_N_CLASS, 0.75)
          .put(Technique.LCS_U_N_MULTI, 0.85)
          .put(Technique.STATIC_LCS_U_N, 1.0)
          .put(Technique.STATIC_LCS_U_N_CLASS, 1.0)
          .put(Technique.STATIC_LCS_U_N_MULTI, 1.0)
          .put(Technique.LEVENSHTEIN_N, 0.95)
          .put(Technique.LEVENSHTEIN_N_CLASS, 0.95)
          .put(Technique.LEVENSHTEIN_N_MULTI, 0.95)
          .put(Technique.STATIC_LEVENSHTEIN_N, 0.995)
          .put(Technique.STATIC_LEVENSHTEIN_N_CLASS, 0.995)
          .put(Technique.STATIC_LEVENSHTEIN_N_MULTI, 0.995)
          .put(Technique.LCBA, 1.0)
          .put(Technique.LCBA_CLASS, 1.0)
          .put(Technique.LCBA_MULTI, 1.0)
          .put(Technique.STATIC_LCBA, 1.0)
          .put(Technique.STATIC_LCBA_CLASS, 1.0)
          .put(Technique.STATIC_LCBA_MULTI, 1.0)
          .put(Technique.TARANTULA, 0.995)
          .put(Technique.TARANTULA_CLASS, 0.995)
          .put(Technique.TARANTULA_MULTI, 0.999)
          .put(Technique.TFIDF, 0.90)
          .put(Technique.TFIDF_CLASS, 0.90)
          .put(Technique.TFIDF_MULTI, 0.95)
          .put(Technique.COMBINED, 0.80)
          .put(Technique.COMBINED_CLASS, 0.80)
          .put(Technique.COMBINED_MULTI, 0.95)
          .put(Technique.COMBINED_FFN, 0.90)
          .put(Technique.COMBINED_CLASS_FFN, 0.90)
          .put(Technique.COMBINED_MULTI_FFN, 0.95)
          .build()
      );

  private List<Technique> techniquesToAugment = Arrays.asList(
      Technique.NC,
      Technique.NCC,
      Technique.LCS_B_N,
      Technique.LCS_U_N,
      Technique.LEVENSHTEIN_N,
      Technique.TARANTULA,
      Technique.TFIDF,
      Technique.COMBINED,
      Technique.COMBINED_FFN);

  private boolean normaliseClassScore = true;
  private AugmentationType augmentationType = AugmentationType.COMBINED_SCORE;
  private ScoreCombinationMethod scoreCombinationMethod = ScoreCombinationMethod.AVERAGE;
  private boolean useTechniqueWeightingForCombinedScore = true;
  private boolean useTechniqueWeightingForAugmentation = false;
  private double weightingZeroPenalty = 0.01;
  private double percentileThreshold = 0.90;
  private double commonThresholdValue = -1.0; // Set if all threshold values are the same (except NS_CONTAINS)
  private boolean createClassLevelGroundTruthSample = false;
  private boolean autoExtractDeveloperLinks = false;
  private boolean runCombinedScoreOptimisationExperiment = false;

  // List of tests to inspect (print to standard out)
  private String[] testTrackList = {};

  private ArrayList<String> testClassesForGroundTruth;

  // Displays a human-readable form of all configuration settings.
  public void printConfiguration() {
    System.out.println("==== CONFIGURATION ====");
    System.out.println(toString());
    /*System.out.printf("Apply Coverage Discount: %s %n", applyCoverageDiscount);
    System.out.printf("Call Depth Discount Factor: %f %n", callDepthDiscountFactor);
    if (this.commonThresholdValue != -1.0) {
      System.out.printf("Threshold Value: %f %n", commonThresholdValue);
    }*/

  }

  @Override
  public String toString() {
    return "Configuration{" +
        "\nprojectBaseDirs=\"" + projectBaseDirs +
        "\",\nprojects=" + projects +
        ",\nsingleProject=" + singleProject +
        ",\napplyCoverageDiscount=" + applyCoverageDiscount +
        ",\ncallDepthDiscountFactor=" + callDepthDiscountFactor +
        ",\nmethodLevelTechniqueList=\"" + Arrays.toString(methodLevelTechniqueList) +
        "\",\nclassLevelTechniqueList=\"" + Arrays.toString(classLevelTechniqueList) +
        "\",\ntechniquesToNormalize=\"" + Arrays.toString(techniquesToNormalize) +
        "\",\nthresholdData=\"" + thresholdData +
        "\",\ntechniquesToAugment=\"" + techniquesToAugment +
        "\",\nnormaliseClassScore=" + normaliseClassScore +
        ",\naugmentationType=" + augmentationType +
        ",\ncommonThresholdValue=" + commonThresholdValue +
        ",\ntestTrackList=" + Arrays.toString(testTrackList) +
        ",\nnormaliseClassScore=" + normaliseClassScore +
        ",\ntestTrackaugmentationTypeList=" + augmentationType +
        ",\nscoreCombinationMethod=" + scoreCombinationMethod +
        ",\nuseWeightedCombination=" + useTechniqueWeightingForCombinedScore +
        ",\nweightingZeroPenalty=" + weightingZeroPenalty +
        '}';
  }

  public Map<String, String> getProjectBaseDirs() {
    return projectBaseDirs;
  }

  public Map<String, String> getProjectSrcDirs() { return projectSrcDirs; }

  public Map<String, String> getProjectTestSrcDirs() {
    return projectTestSrcDirs;
  }

  public String[] getProjectBaseDirsForProjects() {
    String[] projectBaseDirsForProjects = new String[projects.size()];
    int i = 0;
    for (String project : projects) {
      projectBaseDirsForProjects[i++] = projectBaseDirs.get(project);
    }
    return projectBaseDirsForProjects;
  }

  /*public String getProjectBaseDirsForProject(String project) {
    Map<String, String> projectBaseDirsForProjects = new HashMap<>();
    for (String project : projects) {
      projectBaseDirsForProjects.put(project, projectBaseDirs.get(project));
    }
    return projectBaseDirs;
  }*/

  public List<String> getProjects() {
    return projects;
  }

  public boolean isParseCttLogs() {
    return parseCttLogs;
  }

  public void setProjects(List<String> projects) {
    this.projects = projects;
  }

  public List<String> getFunctionLevelEvaluationProjects() {
    return functionLevelEvaluationProjects;
  }

  public List<String> getClassLevelEvaluationProjects() {
    return classLevelEvaluationProjects;
  }

  public boolean isSingleProject() {
    return singleProject;
  }

  public void setSingleProject(boolean singleProject) {
    this.singleProject = singleProject;
  }
/*public String getCurrentProject() {
    return currentProject;
  }

  public void setCurrentProject(String currentProject) {
    this.currentProject = currentProject;
  }*/

  public boolean shouldApplyCoverageDiscount() {
    return applyCoverageDiscount;
  }

  public double getCallDepthDiscountFactor() {
    return callDepthDiscountFactor;
  }

  public Technique[] getMethodLevelTechniqueList() {
    return methodLevelTechniqueList;
  }

  public Technique[] getClassLevelTechniqueList() {
    return classLevelTechniqueList;
  }

  public Technique[] getMultiLevelTechniqueList() {
    return multiLevelTechniqueList;
  }

  public Technique[] getTechniquesToNormalize() {
    return techniquesToNormalize;
  }

  public boolean isNormaliseClassScore() {
    return normaliseClassScore;
  }

  public Map<Technique, Double> getMethodLevelTechniqueWeights() {
    return methodLevelTechniqueWeights;
  }

  public Map<Technique, Double> getClassLevelTechniqueWeights() {
    return classLevelTechniqueWeights;
  }

  public Map<Technique, Double> getThresholdData() {
    return thresholdData;
  }

  public List<Technique> getTechniquesToAugment() {
    return techniquesToAugment;
  }

  public AugmentationType getAugmentationType() {
    return augmentationType;
  }

  public ScoreCombinationMethod getScoreCombinationMethod() {
    return scoreCombinationMethod;
  }

  public boolean isUseTechniqueWeightingForCombinedScore() {
    return useTechniqueWeightingForCombinedScore;
  }

  public double getWeightingZeroPenalty() {
    return weightingZeroPenalty;
  }

  public double getCommonThresholdValue() {
    return commonThresholdValue;
  }

  // Sets the threshold value for all techniques except NS_CONTAINS, which is a binary (1/0) technique.
  // Range: 0-1
  public void setAllThresholdValues(double value) {
    for (Map.Entry<Technique, Double> thresholdEntry : thresholdData.entrySet()) {
      if (thresholdEntry.getKey() != Technique.NCC
          && thresholdEntry.getKey() != Technique.NCC_CLASS
          && thresholdEntry.getKey() != Technique.NCC_MULTI
          && thresholdEntry.getKey() != Technique.LCBA
          && thresholdEntry.getKey() != Technique.LCBA_CLASS
          && thresholdEntry.getKey() != Technique.LCBA_MULTI) {
        thresholdEntry.setValue(value);
      }
    }
    commonThresholdValue = value;
  }

  // Sets the threshold value for all techniques except NS_CONTAINS, which is a binary (1/0) technique.
  // Range: 0-1
  public void setAllClassLevelThresholdValues(double value) {
    for (Map.Entry<Technique, Double> thresholdEntry : thresholdData.entrySet()) {
      if (thresholdEntry.getKey() != Technique.NCC) {
        thresholdEntry.setValue(value);
      }
    }
    commonThresholdValue = value;
  }

  public String getCorpusPath() {
    return corpusPath;
  }

  public String getFeedForwardNetworkScript() {
    return feedForwardNetworkScript;
  }

  public String getMlDir() {
    return mlDir;
  }

  public String getPythonCommand() {
    return pythonCommand;
  }

  public String[] getTestTrackList() {
    return testTrackList;
  }

  public void setTestTrackList(String[] testTrackList) {
    this.testTrackList = testTrackList;
  }

  public boolean isCreateClassLevelGroundTruthSample() {
    return createClassLevelGroundTruthSample;
  }

  public ArrayList<String> getTestClassesForGroundTruth() {
    return testClassesForGroundTruth;
  }

  public void setTestClassesForGroundTruth(ArrayList<String> testClassesForGroundTruth) {
    this.testClassesForGroundTruth = testClassesForGroundTruth;
  }

  public boolean isAutoExtractDeveloperLinks() {
    return autoExtractDeveloperLinks;
  }

  public boolean isRunCombinedScoreOptimisationExperiment() {
    return runCombinedScoreOptimisationExperiment;
  }

  public boolean isUseTechniqueWeightingForAugmentation() {
    return useTechniqueWeightingForAugmentation;
  }

  public static class Builder {

    private Configuration config = new Configuration();

    public Builder setProjects(List<String> projects) {
      config.projects = projects;
      return this;
    }

    public Builder setParseCttLogs(boolean parseCttLogs) {
      config.parseCttLogs = parseCttLogs;
      return this;
    }
    /*public Builder setCurrentProject(String currentProject) {
      config.currentProject = currentProject;
      return this;
    }*/

    public Builder setIsSingleProject(boolean singleProject) {
      config.singleProject = singleProject;
      return this;
    }

    public Builder setApplyCoverageDiscount(boolean applyCoverageDiscount) {
      config.applyCoverageDiscount = applyCoverageDiscount;
      return this;
    }

    public Builder setCallDepthDiscountFactor(double callDepthDiscountFactor) {
      config.callDepthDiscountFactor = callDepthDiscountFactor;
      return this;
    }

    public Builder setTechniqueList(Technique[] techniqueList) {
      config.methodLevelTechniqueList = techniqueList;
      return this;
    }

    public Builder setTechniquesToNormalize(Technique[] techniquesToNormalize) {
      config.techniquesToNormalize = techniquesToNormalize;
      return this;
    }

    public Builder setThresholdData(Map<Technique, Double> thresholdData) {
      config.thresholdData = thresholdData;
      config.commonThresholdValue = -1;
      return this;
    }

    // Sets the threshold value for all techniques except NS_CONTAINS, which is a binary (1/0) technique.
    // Range: 0-1
    public Builder setThresholdValue(double value) {
      for (Map.Entry<Technique, Double> thresholdEntry : config.thresholdData.entrySet()) {
        if (thresholdEntry.getKey() != Technique.NCC) {
          thresholdEntry.setValue(value);
        }
      }
      config.commonThresholdValue = value;
      return this;
    }

    public Builder setTestTrackList(String[] testTrackList) {
      config.testTrackList = testTrackList;
      return this;
    }

    public Configuration build() {
      ResultsWriter.writeOutConfig(config);
      return config;
    }

  }
}
