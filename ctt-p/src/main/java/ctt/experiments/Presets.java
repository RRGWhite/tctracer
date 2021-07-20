package ctt.experiments;

import ctt.Configuration;
import ctt.types.Technique;

import java.util.ArrayList;
import java.util.List;

/**
 * Standardised, well-defined presets of Configurations to run across projects.
 */
public class Presets {

  /*public static final Technique[] TECHNIQUES_NAME_SIMILARITY = {
      Technique.NCC,
      Technique.LCS_B_N,
      Technique.LCS_U_N,
      Technique.NS_LEVENSHTEIN,
      Technique.LEVENSHTEIN_N,
  };

  public static final Technique[] TECHNIQUES_OTHER = {
      Technique.LCBA,
      Technique.TARANTULA,
      Technique.TFIDF,
  };

  public static final Technique[] TECHNIQUES_ALL = {
      Technique.NCC,
      Technique.NS_COMMON_SUBSEQ_FUZ,
      Technique.NS_COMMON_SUBSEQ,
      Technique.NS_LEVENSHTEIN,
      Technique.LEVENSHTEIN_N,
      Technique.LCBA,
      Technique.TARANTULA,
      Technique.TFIDF,
  };*/

  public static List<Configuration> getConfigurations() {
    List<Configuration> configs = new ArrayList<>();

    // Thresholds
    configs.add(new Configuration.Builder()
        .setThresholdValue(0.95)
        .build());

    configs.add(new Configuration.Builder()
        .setThresholdValue(0.90)
        .build());

    configs.add(new Configuration.Builder()
        .setThresholdValue(0.80)
        .build());

    // Call Depth Discounting
    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(1.0) // no discounting
        .build());

    configs.add(new Configuration.Builder()
        .setThresholdValue(0.75)
        .build());

    configs.add(new Configuration.Builder()
        .setThresholdValue(0.4)
        .build());

    configs.add(new Configuration.Builder()
        .setThresholdValue(0.0) // only consider top-level methods
        .build());

    return configs;
  }

  public static List<Configuration> getThresholdConfigurations() {
    List<Configuration> configs = new ArrayList<>();

    // Thresholds
    configs.add(new Configuration.Builder()
        .setThresholdValue(1.0)
        .build());

    configs.add(new Configuration.Builder()
        .setThresholdValue(0.90)
        .build());

    configs.add(new Configuration.Builder()
        .setThresholdValue(0.80)
        .build());

    configs.add(new Configuration.Builder()
        .setThresholdValue(0.70)
        .build());

    configs.add(new Configuration.Builder()
        .setThresholdValue(0.60)
        .build());

    configs.add(new Configuration.Builder()
        .setThresholdValue(0.50)
        .build());

    configs.add(new Configuration.Builder()
        .setThresholdValue(0.40)
        .build());

    configs.add(new Configuration.Builder()
        .setThresholdValue(0.30)
        .build());

    configs.add(new Configuration.Builder()
        .setThresholdValue(0.20)
        .build());

    configs.add(new Configuration.Builder()
        .setThresholdValue(0.10)
        .build());

    configs.add(new Configuration.Builder()
        .setThresholdValue(0.00)
        .build());

    configs.add(new Configuration.Builder()
        .setThresholdValue(0.95)
        .build());

    configs.add(new Configuration.Builder()
        .setThresholdValue(0.85)
        .build());

    configs.add(new Configuration.Builder()
        .setThresholdValue(0.75)
        .build());

    configs.add(new Configuration.Builder()
        .setThresholdValue(0.65)
        .build());

    configs.add(new Configuration.Builder()
        .setThresholdValue(0.55)
        .build());

    configs.add(new Configuration.Builder()
        .setThresholdValue(0.45)
        .build());

    configs.add(new Configuration.Builder()
        .setThresholdValue(0.35)
        .build());

    configs.add(new Configuration.Builder()
        .setThresholdValue(0.25)
        .build());

    configs.add(new Configuration.Builder()
        .setThresholdValue(0.15)
        .build());

    configs.add(new Configuration.Builder()
        .setThresholdValue(0.05)
        .build());

    return configs;
  }

  public static List<Configuration> getCallDepthConfigurations() {
    List<Configuration> configs = new ArrayList<>();

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(1.0)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.98)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.96)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.94)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.92)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.90)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.88)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.86)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.84)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.82)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.80)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.78)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.76)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.74)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.72)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.70)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.68)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.66)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.64)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.62)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.60)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.58)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.56)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.54)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.52)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.50)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.48)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.46)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.44)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.42)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.40)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.30)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.20)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.10)
        .build());

    configs.add(new Configuration.Builder()
        .setCallDepthDiscountFactor(0.00)
        .build());

    return configs;
  }

}
