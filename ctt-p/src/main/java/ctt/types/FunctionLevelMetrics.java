package ctt.types;

import com.google.common.collect.Table;
import ctt.types.scores.method.MethodScoresTensor;

import java.util.Map;
import java.util.SortedSet;

/**
 * Created by RRGWhite on 01/07/2019
 */
public class FunctionLevelMetrics {

  // Keys: Test, Method | Value: Map<Technique, Relevance Value>
  private Table<String, String, Map<Technique, Double>> relevanceTable;

  private MethodScoresTensor methodScoresTensor;

  // Keys: Test, Technique | Value: Sorted set of method candidates (highest relevance first)
  private Table<String, Technique, SortedSet<MethodValuePair>> aggregatedResults;

  // Keys: Test, Technique | Value: Sorted candidate set
  private Table<String, Technique, SortedSet<MethodValuePair>> candidateTable;

  // Keys: Technique, Test | Value: Evaluation Metrics (true positives, etc)
  private Table<Technique, String, EvaluationMetrics> metricTable;

  public FunctionLevelMetrics(
      MethodScoresTensor methodScoresTensor,
      Table<String, String, Map<Technique, Double>> relevanceTable,
      Table<String, Technique, SortedSet<MethodValuePair>> aggregatedResults,
      Table<String, Technique, SortedSet<MethodValuePair>> candidateTable,
      Table<Technique, String, EvaluationMetrics> metricTable) {
    this.relevanceTable = relevanceTable;
    this.methodScoresTensor = methodScoresTensor;
    this.aggregatedResults = aggregatedResults;
    this.candidateTable = candidateTable;
    this.metricTable = metricTable;
  }

  public Table<String, String, Map<Technique, Double>> getRelevanceTable() {
    return relevanceTable;
  }

  public MethodScoresTensor getMethodScoresTensor() {
    return methodScoresTensor;
  }

  public Table<String, Technique, SortedSet<MethodValuePair>> getAggregatedResults() {
    return aggregatedResults;
  }

  public Table<String, Technique, SortedSet<MethodValuePair>> getCandidateTable() {
    return candidateTable;
  }

  public Table<Technique, String, EvaluationMetrics> getMetricTable() {
    return metricTable;
  }
}
