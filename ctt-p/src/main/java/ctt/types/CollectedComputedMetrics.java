package ctt.types;

/**
 * Created by RRGWhite on 01/07/2019
 */
public class CollectedComputedMetrics {

  private FunctionLevelMetrics functionLevelMetrics;
  private ClassLevelMetrics classLevelMetrics;
  private FunctionLevelMetrics augmentedFunctionLevelMetrics;
  private ClassLevelMetrics augmentedClassLevelMetrics;

  public CollectedComputedMetrics(FunctionLevelMetrics functionLevelMetrics,
                                  ClassLevelMetrics classLevelMetrics,
                                  FunctionLevelMetrics augmentedFunctionLevelMetrics,
                                  ClassLevelMetrics augmentedClassLevelMetrics) {
    this.functionLevelMetrics = functionLevelMetrics;
    this.classLevelMetrics = classLevelMetrics;
    this.augmentedFunctionLevelMetrics = augmentedFunctionLevelMetrics;
    this.augmentedClassLevelMetrics = augmentedClassLevelMetrics;
  }

  public FunctionLevelMetrics getFunctionLevelMetrics() {
    return functionLevelMetrics;
  }

  public ClassLevelMetrics getClassLevelMetrics() {
    return classLevelMetrics;
  }

  public FunctionLevelMetrics getAugmentedFunctionLevelMetrics() {
    return augmentedFunctionLevelMetrics;
  }

  public ClassLevelMetrics getAugmentedClassLevelMetrics() {
    return augmentedClassLevelMetrics;
  }
}
