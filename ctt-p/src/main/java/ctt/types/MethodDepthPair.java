package ctt.types;

/**
 * Created by RRGWhite on 11/07/2019
 */
public class MethodDepthPair {
  private String methodName;
  private int callDepth;

  public MethodDepthPair(String methodName, int callDepth) {
    this.methodName = methodName;
    this.callDepth = callDepth;
  }

  public String getMethodName() {
    return methodName;
  }

  public int getCallDepth() {
    return callDepth;
  }
}
