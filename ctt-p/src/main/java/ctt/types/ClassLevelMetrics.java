package ctt.types;

import com.google.common.collect.Table;
import ctt.types.scores.clazz.ClassScoresTensor;

/**
 * Created by RRGWhite on 02/07/2019
 */
public class ClassLevelMetrics {
  // Keys: Test Class, Non-test Class | Value: Test Class to Non-test Class Tensors
  //private Table<String, String, TestClassToClassTensors> testClassToClassTensors;

  private ClassScoresTensor ClassScoresTensor;

  // Keys: Technique, Test Class | Value: Evaluation Metrics (true positives, etc)
  private Table<Technique, String, EvaluationMetrics> metricTable;

  public ClassLevelMetrics(ClassScoresTensor ClassScoresTensor,
                           Table<Technique, String, EvaluationMetrics> metricTable) {
    //this.testClassToClassTensors = testClassToClassTensors;
    this.ClassScoresTensor = ClassScoresTensor;
    this.metricTable = metricTable;
  }

  /*public Table<String, String, TestClassToClassTensors> getTestClassToClassTensors() {
    return testClassToClassTensors;
  }*/

  public ClassScoresTensor getClassScoresTensor() {
    return ClassScoresTensor;
  }

  public Table<Technique, String, EvaluationMetrics> getMetricTable() {
    return metricTable;
  }
}
