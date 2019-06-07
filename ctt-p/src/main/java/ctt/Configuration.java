package ctt;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.Map;

/**
 * This class represents all configurable parameters
 *
 * - Threshold data
 */
public class Configuration {
    private Configuration(){}

    // Whether or not to discount based on coverage.
    // 100% coverage = full score. 90% coverage = score * 0.9.
    private boolean applyCoverageDiscount = false;

    // The discount factor to apply based on call depth.
    // Discounted score = score * (discountFactor^callDepth)
    // Set to 1 to disable discounting. Set to 0 to only consider top-level methods.
    private double callDepthDiscountFactor = 0.50;

    // All techniques to compute metrics for.
    // Do not include ground truth technique in this list!
    private Technique[] techniqueList = {
            Technique.NS_CONTAINS,
            Technique.NS_COMMON_SUBSEQ_FUZ,
            Technique.NS_COMMON_SUBSEQ,
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
            // Technique.COVERAGE,
    };

    // All techniques listed here will be normalized.
    private Technique[] techniquesToNormalize = {
            Technique.NS_LEVENSHTEIN_N,
            Technique.FAULT_LOC_TARANTULA,
            Technique.IR_TFIDF_11,
            Technique.IR_TFIDF_12,
            Technique.IR_TFIDF_21,
            Technique.IR_TFIDF_22,
            Technique.IR_TFIDF_31,
            Technique.IR_TFIDF_32,
            Technique.COVERAGE,
    };

    // Relevance values must be at or above these thresholds for a method to be included in the candidate set.
    // If not present in this map, the default is no threshold (0.0).
    private Map<Technique, Double> thresholdData = Maps.newHashMap(ImmutableMap.<Technique, Double>builder()
            .put(Technique.NS_CONTAINS          , 1.00)
            .put(Technique.NS_COMMON_SUBSEQ     , 0.45)
            .put(Technique.NS_COMMON_SUBSEQ_FUZ , 0.80)
            .put(Technique.NS_LEVENSHTEIN       , 0.35)
            .put(Technique.NS_LEVENSHTEIN_N     , 0.95)
            .put(Technique.FAULT_LOC_TARANTULA  , 0.95)
            .put(Technique.FAULT_LOC_OCHIAI     , 0.95)
            .put(Technique.IR_TFIDF_11          , 0.95)
            .put(Technique.IR_TFIDF_12          , 0.95)
            .put(Technique.IR_TFIDF_21          , 0.95)
            .put(Technique.IR_TFIDF_22          , 0.95)
            .put(Technique.IR_TFIDF_31          , 0.95)
            .put(Technique.IR_TFIDF_32          , 0.90)
            .put(Technique.COVERAGE             , 0.95)
            .build()
    );

    private double commonThresholdValue = -1.0; // Set if all threshold values are the same (except NS_CONTAINS)

    // List of tests to inspect (print to standard out)
    private String[] testTrackList = {};

    // Displays a human-readable form of all configuration settings.
    public void printConfiguration() {
        System.out.println("==== CONFIGURATION ====");
        System.out.printf("Apply Coverage Discount: %s %n", applyCoverageDiscount);
        System.out.printf("Call Depth Discount Factor: %f %n", callDepthDiscountFactor);
        if (this.commonThresholdValue != -1.0) {
            System.out.printf("Threshold Value: %f %n", commonThresholdValue);
        }

    }

    public boolean shouldApplyCoverageDiscount() {
        return applyCoverageDiscount;
    }

    public double getCallDepthDiscountFactor() {
        return callDepthDiscountFactor;
    }

    public Technique[] getTechniqueList() {
        return techniqueList;
    }

    public Technique[] getTechniquesToNormalize() {
        return techniquesToNormalize;
    }

    public Map<Technique, Double> getThresholdData() {
        return thresholdData;
    }

    public double getCommonThresholdValue() {
        return commonThresholdValue;
    }

    public String[] getTestTrackList() {
        return testTrackList;
    }

    public void setTestTrackList(String[] testTrackList) {
        this.testTrackList = testTrackList;
    }

    public static class Builder {
        private Configuration config = new Configuration();

        public Builder setApplyCoverageDiscount(boolean applyCoverageDiscount) {
            config.applyCoverageDiscount = applyCoverageDiscount;
            return this;
        }

        public Builder setCallDepthDiscountFactor(double callDepthDiscountFactor) {
            config.callDepthDiscountFactor = callDepthDiscountFactor;
            return this;
        }

        public Builder setTechniqueList(Technique[] techniqueList) {
            config.techniqueList = techniqueList;
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
            return config;
        }

    }
}
