package ctt.types;

/**
 * Created by RRGWhite on 08/08/2019
 */
public class PrecisionRecallPair {
  private double precision;
  private double recall;

  public PrecisionRecallPair(double precision, double recall) {
    this.precision = precision;
    this.recall = recall;
  }

  public double getPrecision() {
    return precision;
  }

  public double getRecall() {
    return recall;
  }
}
