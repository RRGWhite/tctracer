package ctt.types;

/**
 * Created by RRGWhite on 01/07/2019
 */
public class MethodValuePair implements Comparable<MethodValuePair> {

  private String method;
  private double value;

  public MethodValuePair(String method, double value) {
    this.method = method;
    this.value = value;
  }

  @Override
  public int compareTo(MethodValuePair other) {
    int valueCompare = Double.compare(this.value, other.value);
    if (valueCompare != 0) {
      return -valueCompare; // negating to reverse order
    } else {
      return method.compareTo(other.method); // String comparison
    }
  }

  public String getMethod() {
    return method;
  }

  public double getValue() {
    return value;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public void setValue(double value) {
    this.value = value;
  }
}
