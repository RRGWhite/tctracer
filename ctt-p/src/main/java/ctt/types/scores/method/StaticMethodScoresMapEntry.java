package ctt.types.scores.method;

import ctt.types.FunctionalityMethod;
import ctt.types.TestMethod;

import java.io.Serializable;

/**
 * Created by RRGWhite on 29/10/2020
 */
public class StaticMethodScoresMapEntry implements Serializable {
  private final TestMethod testMethod;
  private final FunctionalityMethod functionalityMethod;
  private final double score;

  public StaticMethodScoresMapEntry(TestMethod testMethod, FunctionalityMethod functionalityMethod, double score) {
    this.testMethod = testMethod;
    this.functionalityMethod = functionalityMethod;
    this.score = score;
  }

  public TestMethod getTestMethod() {
    return testMethod;
  }

  public FunctionalityMethod getFunctionalityMethod() {
    return functionalityMethod;
  }

  public double getScore() {
    return score;
  }
}
