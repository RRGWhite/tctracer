package ctt;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import ctt.types.Technique;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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

  private Configuration() {
  }

  private String corpusPath = "../corpus";

  private Map<String, String> projectBaseDirs = Maps
      .newHashMap(ImmutableMap.<String, String>builder()
          .put("commons-lang", corpusPath + "/commons-lang")
          .put("jfreechart", corpusPath + "/jfreechart")
          .put("commons-io", corpusPath + "/commons-io")
          .put("argouml", corpusPath + "/argouml-v-30-1")
          .put("apache-ant", corpusPath + "/apache-ant")
          .put("dependency-finder", corpusPath + "/dependency-finder")
          .build()
      );

  private List<String> projects;

  private boolean parseCttLogs = false;

  private List<String> functionLevelEvaluationProjects = Arrays.asList(
      "commons-lang", "jfreechart", "commons-io");

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
      Technique.NS_CONTAINS,
      Technique.NS_COMMON_SUBSEQ_FUZ_N,
      Technique.NS_COMMON_SUBSEQ_N,
      Technique.NS_LEVENSHTEIN_N,
      Technique.LAST_CALL_BEFORE_ASSERT,
      Technique.FAULT_LOC_TARANTULA,
      Technique.IR_TFIDF_32,
      Technique.COMBINED
  };

  /*private Technique[] methodLevelCombinedScoreComponents = {
      Technique.NC,
      Technique.NS_CONTAINS,
      Technique.NS_COMMON_SUBSEQ_FUZ,
      Technique.NS_COMMON_SUBSEQ_FUZ_N,
      Technique.NS_COMMON_SUBSEQ,
      Technique.NS_COMMON_SUBSEQ_N,
      Technique.NS_LEVENSHTEIN,
      Technique.NS_LEVENSHTEIN_N,
      Technique.LAST_CALL_BEFORE_ASSERT,
      Technique.FAULT_LOC_TARANTULA,
      // Technique.IR_TFIDF_11,
      // Technique.IR_TFIDF_12,
      // Technique.IR_TFIDF_21,
      // Technique.IR_TFIDF_22,
      // Technique.IR_TFIDF_31,
      Technique.IR_TFIDF_32,
      Technique.COMBINED
  };*/

  private Technique[] classLevelTechniqueList = {
      Technique.NC_CLASS,
      Technique.NS_CONTAINS_CLASS,
      Technique.NS_COMMON_SUBSEQ_FUZ_N_CLASS,
      Technique.NS_COMMON_SUBSEQ_N_CLASS,
      Technique.NS_LEVENSHTEIN_N_CLASS,
      Technique.LAST_CALL_BEFORE_ASSERT_CLASS,
      Technique.FAULT_LOC_TARANTULA_CLASS,
      Technique.IR_TFIDF_32_CLASS,
      Technique.COMBINED_CLASS
  };

  /*private Technique[] classLevelCombinedScoreComponents = {
      Technique.NC_CLASS,
      Technique.NS_CONTAINS_CLASS,
      Technique.NS_COMMON_SUBSEQ_FUZ_CLASS,
      Technique.NS_COMMON_SUBSEQ_CLASS,
      Technique.NS_LEVENSHTEIN_CLASS,
      Technique.NS_LEVENSHTEIN_N_CLASS,
      Technique.LAST_CALL_BEFORE_ASSERT_CLASS,
      Technique.FAULT_LOC_TARANTULA_CLASS,
      Technique.IR_TFIDF_32_CLASS,
      Technique.COMBINED_CLASS
  };*/

  private Technique[] multiLevelTechniqueList = {
      Technique.NC_MULTI,
      Technique.NS_CONTAINS_MULTI,
      Technique.NS_COMMON_SUBSEQ_FUZ_N_MULTI,
      Technique.NS_COMMON_SUBSEQ_N_MULTI,
      Technique.NS_LEVENSHTEIN_N_MULTI,
      Technique.LAST_CALL_BEFORE_ASSERT_MULTI,
      Technique.FAULT_LOC_TARANTULA_MULTI,
      Technique.IR_TFIDF_32_MULTI,
      Technique.COMBINED_MULTI
  };

  // All techniques listed here will be normalized.
  private Technique[] techniquesToNormalize = {
      Technique.NS_COMMON_SUBSEQ_FUZ_N,
      Technique.NS_COMMON_SUBSEQ_N,
      Technique.NS_COMMON_SUBSEQ_FUZ_N_CLASS,
      Technique.NS_COMMON_SUBSEQ_N_CLASS,
      Technique.NS_COMMON_SUBSEQ_FUZ_N_MULTI,
      Technique.NS_COMMON_SUBSEQ_N_MULTI,
      Technique.NS_LEVENSHTEIN_N,
      Technique.NS_LEVENSHTEIN_N_CLASS,
      Technique.NS_LEVENSHTEIN_N_MULTI,
      Technique.FAULT_LOC_TARANTULA,
      Technique.FAULT_LOC_TARANTULA_CLASS,
      Technique.FAULT_LOC_TARANTULA_MULTI,
      Technique.IR_TFIDF_11,
      Technique.IR_TFIDF_11_CLASS,
      Technique.IR_TFIDF_11_MULTI,
      Technique.IR_TFIDF_12,
      Technique.IR_TFIDF_12_CLASS,
      Technique.IR_TFIDF_12_MULTI,
      Technique.IR_TFIDF_21,
      Technique.IR_TFIDF_21_CLASS,
      Technique.IR_TFIDF_21_MULTI,
      Technique.IR_TFIDF_22,
      Technique.IR_TFIDF_22_CLASS,
      Technique.IR_TFIDF_22_MULTI,
      Technique.IR_TFIDF_31,
      Technique.IR_TFIDF_31_CLASS,
      Technique.IR_TFIDF_31_MULTI,
      Technique.IR_TFIDF_32,
      Technique.IR_TFIDF_32_CLASS,
      Technique.IR_TFIDF_32_MULTI,
      Technique.COVERAGE,
      Technique.COMBINED,
      Technique.COMBINED_CLASS,
      Technique.COMBINED_MULTI
  };

  private Map<Technique, Double> methodLevelTechniqueWeights = Maps
      .newHashMap(ImmutableMap.<Technique, Double>builder()
          .put(Technique.NC, 0.45)
          .put(Technique.NS_CONTAINS, 0.45)
          .put(Technique.NS_COMMON_SUBSEQ_FUZ, 0.64)
          .put(Technique.NS_COMMON_SUBSEQ, 0.64)
          .put(Technique.NS_LEVENSHTEIN, 0.68)
          .put(Technique.NS_LEVENSHTEIN_N, 0.68)
          .put(Technique.LAST_CALL_BEFORE_ASSERT, 0.73)
          .put(Technique.FAULT_LOC_TARANTULA, 0.73)
          .put(Technique.IR_TFIDF_32, 0.75)
          .put(Technique.COMBINED, 1.0)
          .build()
      );

  private Map<Technique, Double> classLevelTechniqueWeights = Maps
      .newHashMap(ImmutableMap.<Technique, Double>builder()
          .put(Technique.NC_CLASS, 0.85)
          .put(Technique.NS_CONTAINS_CLASS, 0.85)
          .put(Technique.NS_COMMON_SUBSEQ_FUZ_CLASS, 0.56)
          .put(Technique.NS_COMMON_SUBSEQ_CLASS, 0.66)
          .put(Technique.NS_LEVENSHTEIN_CLASS, 0.83)
          .put(Technique.NS_LEVENSHTEIN_N_CLASS, 0.83)
          .put(Technique.LAST_CALL_BEFORE_ASSERT_CLASS, 0.57)
          .put(Technique.FAULT_LOC_TARANTULA_CLASS, 0.43)
          .put(Technique.IR_TFIDF_32_CLASS, 0.36)
          .put(Technique.COMBINED_CLASS, 0.85)
          .build()
      );

  // Relevance values must be at or above these thresholds for a method to be included in the candidate set.
  // If not present in this map, the default is no threshold (0.0).
  /*private Map<Technique, Double> thresholdData = Maps
      .newHashMap(ImmutableMap.<Technique, Double>builder()
          .put(Technique.NS_CONTAINS, 1.00)
          .put(Technique.NS_COMMON_SUBSEQ, 0.45)
          .put(Technique.NS_COMMON_SUBSEQ_FUZ, 0.80)
          .put(Technique.NS_LEVENSHTEIN, 0.35)
          .put(Technique.NS_LEVENSHTEIN_N, 0.95)
          .put(Technique.FAULT_LOC_TARANTULA, 0.95)
          .put(Technique.FAULT_LOC_OCHIAI, 0.95)
          .put(Technique.IR_TFIDF_11, 0.95)
          .put(Technique.IR_TFIDF_12, 0.95)
          .put(Technique.IR_TFIDF_21, 0.95)
          .put(Technique.IR_TFIDF_22, 0.95)
          .put(Technique.IR_TFIDF_31, 0.95)
          .put(Technique.IR_TFIDF_32, 0.90)
          .put(Technique.COVERAGE, 0.95)
          .put(Technique.COMBINED, 0.90)
          .build()
      );*/

  private Map<Technique, Double> thresholdData = Maps
      .newHashMap(ImmutableMap.<Technique, Double>builder()
          .put(Technique.NC, 1.00)
          .put(Technique.NC_CLASS, 1.00)
          .put(Technique.NC_MULTI, 1.00)
          .put(Technique.NS_CONTAINS, 1.00)
          .put(Technique.NS_CONTAINS_CLASS, 1.00)
          .put(Technique.NS_CONTAINS_MULTI, 1.00)
          .put(Technique.NS_COMMON_SUBSEQ, 0.45)
          .put(Technique.NS_COMMON_SUBSEQ_CLASS, 0.45)
          .put(Technique.NS_COMMON_SUBSEQ_MULTI, 0.45)
          .put(Technique.NS_COMMON_SUBSEQ_N, 0.55)
          .put(Technique.NS_COMMON_SUBSEQ_N_CLASS, 0.55)
          .put(Technique.NS_COMMON_SUBSEQ_N_MULTI, 0.55)
          .put(Technique.NS_COMMON_SUBSEQ_FUZ, 0.80)
          .put(Technique.NS_COMMON_SUBSEQ_FUZ_CLASS, 0.80)
          .put(Technique.NS_COMMON_SUBSEQ_FUZ_MULTI, 0.80)
          .put(Technique.NS_COMMON_SUBSEQ_FUZ_N, 0.75)
          .put(Technique.NS_COMMON_SUBSEQ_FUZ_N_CLASS, 0.75)
          .put(Technique.NS_COMMON_SUBSEQ_FUZ_N_MULTI, 0.75)
          .put(Technique.NS_LEVENSHTEIN, 0.35)
          .put(Technique.NS_LEVENSHTEIN_CLASS, 0.35)
          .put(Technique.NS_LEVENSHTEIN_MULTI, 0.35)
          .put(Technique.NS_LEVENSHTEIN_N, 0.95)
          .put(Technique.NS_LEVENSHTEIN_N_CLASS, 0.95)
          .put(Technique.NS_LEVENSHTEIN_N_MULTI, 0.95)
          .put(Technique.LAST_CALL_BEFORE_ASSERT, 1.0)
          .put(Technique.LAST_CALL_BEFORE_ASSERT_CLASS, 1.0)
          .put(Technique.LAST_CALL_BEFORE_ASSERT_MULTI, 1.0)
          .put(Technique.FAULT_LOC_TARANTULA, 0.995)
          .put(Technique.FAULT_LOC_TARANTULA_CLASS, 0.995)
          .put(Technique.FAULT_LOC_TARANTULA_MULTI, 0.995)
          .put(Technique.FAULT_LOC_OCHIAI, 0.95)
          .put(Technique.IR_TFIDF_11, 0.95)
          .put(Technique.IR_TFIDF_11_CLASS, 0.95)
          .put(Technique.IR_TFIDF_11_MULTI, 0.95)
          .put(Technique.IR_TFIDF_12, 0.95)
          .put(Technique.IR_TFIDF_12_CLASS, 0.95)
          .put(Technique.IR_TFIDF_12_MULTI, 0.95)
          .put(Technique.IR_TFIDF_21, 0.95)
          .put(Technique.IR_TFIDF_21_CLASS, 0.95)
          .put(Technique.IR_TFIDF_21_MULTI, 0.95)
          .put(Technique.IR_TFIDF_22, 0.95)
          .put(Technique.IR_TFIDF_22_CLASS, 0.95)
          .put(Technique.IR_TFIDF_22_MULTI, 0.95)
          .put(Technique.IR_TFIDF_31, 0.95)
          .put(Technique.IR_TFIDF_31_CLASS, 0.95)
          .put(Technique.IR_TFIDF_31_MULTI, 0.95)
          .put(Technique.IR_TFIDF_32, 0.90)
          .put(Technique.IR_TFIDF_32_CLASS, 0.90)
          .put(Technique.IR_TFIDF_32_MULTI, 0.90)
          .put(Technique.COVERAGE, 0.95)
          .put(Technique.COMBINED, 0.80)
          .put(Technique.COMBINED_CLASS, 0.80)
          .put(Technique.COMBINED_MULTI, 0.80)
          .build()
      );

  private List<Technique> techniquesToAugment = Arrays.asList(
      Technique.NC,
      Technique.NS_CONTAINS,
      Technique.NS_COMMON_SUBSEQ,
      Technique.NS_COMMON_SUBSEQ_FUZ,
      Technique.NS_LEVENSHTEIN,
      Technique.NS_LEVENSHTEIN_N,
      Technique.FAULT_LOC_TARANTULA,
      Technique.FAULT_LOC_OCHIAI,
      Technique.IR_TFIDF_11,
      Technique.IR_TFIDF_12,
      Technique.IR_TFIDF_21,
      Technique.IR_TFIDF_22,
      Technique.IR_TFIDF_31,
      Technique.IR_TFIDF_32,
      Technique.COMBINED);

  private boolean normaliseClassScore = true;
  private AugmentationType augmentationType = AugmentationType.COMBINED_SCORE;
  private ScoreCombinationMethod scoreCombinationMethod = ScoreCombinationMethod.AVERAGE;
  private boolean useWeightedCombination = false;
  private double weightingZeroPenalty = 0.01;
  private double percentileThreshold = 0.90;
  private double commonThresholdValue = -1.0; // Set if all threshold values are the same (except NS_CONTAINS)
  private boolean createClassLevelGroundTruthSample = false;
  private boolean autoExtractDeveloperLinks = false;

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
        ",\nuseWeightedCombination=" + useWeightedCombination +
        ",\nweightingZeroPenalty=" + weightingZeroPenalty +
        '}';
  }

  public Map<String, String> getProjectBaseDirs() {
    return projectBaseDirs;
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

  public boolean isUseWeightedCombination() {
    return useWeightedCombination;
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
      if (thresholdEntry.getKey() != Technique.NS_CONTAINS
          && thresholdEntry.getKey() != Technique.NS_CONTAINS_CLASS
          && thresholdEntry.getKey() != Technique.NS_CONTAINS_MULTI
          && thresholdEntry.getKey() != Technique.LAST_CALL_BEFORE_ASSERT
          && thresholdEntry.getKey() != Technique.LAST_CALL_BEFORE_ASSERT_CLASS
          && thresholdEntry.getKey() != Technique.LAST_CALL_BEFORE_ASSERT_MULTI) {
        thresholdEntry.setValue(value);
      }
    }
    commonThresholdValue = value;
  }

  // Sets the threshold value for all techniques except NS_CONTAINS, which is a binary (1/0) technique.
  // Range: 0-1
  public void setAllClassLevelThresholdValues(double value) {
    for (Map.Entry<Technique, Double> thresholdEntry : thresholdData.entrySet()) {
      if (thresholdEntry.getKey() != Technique.NS_CONTAINS) {
        thresholdEntry.setValue(value);
      }
    }
    commonThresholdValue = value;
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
        if (thresholdEntry.getKey() != Technique.NS_CONTAINS) {
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
