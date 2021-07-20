package ctt.types;

import com.github.javaparser.ast.expr.MethodCallExpr;

/**
 * Created by RRGWhite on 28/10/2020
 */
public class MethodCallExprDepthPair {
  private MethodCallExpr methodCallExpr;
  private int callDepth;

  public MethodCallExprDepthPair(MethodCallExpr methodCallExpr, int callDepth) {
    this.methodCallExpr = methodCallExpr;
    this.callDepth = callDepth;
  }

  public MethodCallExpr getMethodCallExpr() {
    return methodCallExpr;
  }

  public int getCallDepth() {
    return callDepth;
  }
}
