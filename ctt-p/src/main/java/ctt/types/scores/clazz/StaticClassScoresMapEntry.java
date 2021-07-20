package ctt.types.scores.clazz;

import java.io.Serializable;

/**
 * Created by RRGWhite on 29/10/2020
 */
public class StaticClassScoresMapEntry implements Serializable {
  private final String testClassFqn;
  private final String nonTestClassFqn;
  private final double score;

  public StaticClassScoresMapEntry(String testClassFqn, String nonTestClassFqn, double score) {
    this.testClassFqn = testClassFqn;
    this.nonTestClassFqn = nonTestClassFqn;
    this.score = score;
  }

  public String getTestClassFqn() {
    return testClassFqn;
  }

  public String getNonTestClassFqn() {
    return nonTestClassFqn;
  }

  public double getScore() {
    return score;
  }
}
