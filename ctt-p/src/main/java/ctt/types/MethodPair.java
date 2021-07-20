package ctt.types;

import java.io.Serializable;

public abstract class MethodPair<T extends Method> implements Serializable {

  protected final T m1;
  protected final T m2;
  protected double similarity;

  public MethodPair(T m1, T m2, double similarity) {
    this.m1 = m1;
    this.m2 = m2;
    this.similarity = similarity;
  }

  public String toString() {
    return m1.getFullyQualifiedMethodName()
        + "," + m2.getFullyQualifiedMethodName()
        + "," + similarity;
  }

  public T getM1() {
    return m1;
  }

  public T getM2() {
    return m2;
  }

  public double getSimilarity() {
    return similarity;
  }

  @Override
  public int hashCode() {
    // adapted from https://www.mkyong.com/java/java-how-to-overrides-equals-and-hashcode/
    int result = 17;
    result = 31 * result + m1.hashCode();
    result = 31 * result + m2.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !(o instanceof MethodPair)) {
      return false;
    }

    MethodPair mp = (MethodPair) o;
    if (m1.equals(mp.getM1()) && m2.equals(mp.getM2())
        || m1.equals(mp.getM2()) && m2.equals(mp.getM1())) {
      return true;
    }

    return false;
  }

  /*public void updateSimilarity(SimilarityProvider.SimilarityMetric similarityMetric) {
    SimilarityProvider similarityProvider
        = SimilarityProvider.getSimilarityProvider(similarityMetric);
    similarity = similarityProvider.getSimilarityScore(m1.getSrc(), m2.getSrc());
  }*/
}
