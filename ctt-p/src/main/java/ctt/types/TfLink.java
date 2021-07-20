package ctt.types;

import java.util.Objects;

public class TfLink extends MethodPair {

  private final TestMethod testMethod;
  private final FunctionalityMethod functionalityMethod;

  public TfLink(TestMethod testMethod, FunctionalityMethod functionalityMethod, double similarity) {
    super(testMethod, functionalityMethod, similarity);
    this.testMethod = testMethod;
    this.functionalityMethod = functionalityMethod;
  }

  public TestMethod getTestMethod() {
    return testMethod;
  }

  public FunctionalityMethod getFunctionalityMethod() {
    return functionalityMethod;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    TfLink link = (TfLink) o;
    return testMethod.equals(link.testMethod) &&
        functionalityMethod.equals(link.functionalityMethod);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), testMethod, functionalityMethod);
  }
}
