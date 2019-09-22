package ctt.types;

/**
 * Created by RRGWhite on 05/08/2019
 */
public class ClassDepthPair {
  private String className;
  private int callDepth;

  public ClassDepthPair(String className, int callDepth) {
    this.className = className;
    this.callDepth = callDepth;
  }

  public String getClassName() {
    return className;
  }

  public int getCallDepth() {
    return callDepth;
  }
}
