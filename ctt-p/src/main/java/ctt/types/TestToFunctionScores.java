package ctt.types;

import java.util.Map;
import java.util.Objects;

public class TestToFunctionScores {

  private String testFqn;
  private String functionFqn;
  private Map<Technique, Double> scores;

  public TestToFunctionScores(String testFqn, String functionFqn, Map<Technique, Double> scores) {
    this.testFqn = testFqn;
    this.functionFqn = functionFqn;
    this.scores = scores;
  }

  public String getTestFqn() {
    return testFqn;
  }

  public String getFunctionFqn() {
    return functionFqn;
  }

  public Map<Technique, Double> getScores() {
    return scores;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TestToFunctionScores)) {
      return false;
    }
    TestToFunctionScores that = (TestToFunctionScores) o;
    return testFqn.equals(that.testFqn) &&
        functionFqn.equals(that.functionFqn);
  }

  @Override
  public int hashCode() {
    return Objects.hash(testFqn, functionFqn);
  }
}
