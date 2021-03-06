package ctt.types;

import ctt.Utilities;

public class EvaluationMetrics {

  // # Relevant = truePositives + falseNegatives
  // # Selected = truePositives
  public int truePositives;
  public int falsePositives;
  public int falseNegatives;
  private double bpref;
  private double averagePrecision;

  public EvaluationMetrics(int truePositives, int falsePositives, int falseNegatives, double bpref,
      double averagePrecision) {
    this.truePositives = truePositives;
    this.falsePositives = falsePositives;
    this.falseNegatives = falseNegatives;
    this.bpref = bpref;
    this.averagePrecision = averagePrecision;
  }

  public double getPrecision() {
    return computePrecision(truePositives, falsePositives);
  }

  public double getRecall() {
    return computeRecall(truePositives, falseNegatives);
  }

  public double getFScore() {
    return computeFScore(getPrecision(), getRecall());
  }

  public double getBpref() {
    return bpref;
  }

  public double getAveragePrecision() {
    return averagePrecision;
  }

  public double getAreaUnderCurve() {
    return computeAreaUnderCurve();
  }

  public static double computePrecision(int truePositives, int falsePositives) {
    int denominator = truePositives + falsePositives;
    if (denominator == 0) {
      return 0.0; // No positives at all - an empty candidate set. Possibly threshold set too high.
    }
    return ((double) truePositives / denominator);
  }

  public static double computeRecall(int truePositives, int falseNegatives) {
    double recall = ((double) truePositives / (truePositives + falseNegatives));
    if (recall > 1.0) {
      Utilities.logger.debug("Debugging recall greater than 1.0");
    }
    return recall;
  }

  public static double computeFScore(double precision, double recall) {
    return ((precision + recall) != 0) ? 2 * (precision * recall) / (precision + recall) : 0;
  }

  public static double computeAreaUnderCurve() {
    //return ((precision + recall) != 0) ? 2 * (precision * recall) / (precision + recall) : 0;
    //TODO: Implement
    return 0;
  }
}
