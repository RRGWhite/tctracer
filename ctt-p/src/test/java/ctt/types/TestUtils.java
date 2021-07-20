package ctt.types;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import ctt.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Created by RRGWhite on 15/07/2019
 */
public class TestUtils {
  public static TestClassToClassTensors createExampleTestClassToClassTensors(String testClassFqn,
                                                                             String nonTestClassFqn) {
    Configuration config = new Configuration.Builder().build();
    ArrayList<TestToFunctionScores> testToFunctionScores = new ArrayList<>();
    Table<String, String, Map<Technique, Double>> scoresTable = HashBasedTable.create();
    Map<String, Map<Technique, Double>> combinedTestScores = new HashMap<>();
    Random random = new Random();

    List<String> testFqns = Arrays.asList("test1", "test2", "test3");
    List<String> functionFqns = Arrays.asList("function1", "function2", "function3");
    for (String testFqn : testFqns) {
      for (String functionFqn : functionFqns) {
        Map<Technique, Double> scores = new HashMap<>();
        for (Technique technique : config.getMethodLevelTechniqueList()) {
          double score;
          if (technique.equals(Technique.NCC)) {
            score = (double) random.nextInt(2);
          } else if (technique.equals(Technique.NS_LEVENSHTEIN)) {
            score = (double) random.nextInt(10);
          } else if (technique.equals(Technique.LCBA)) {
            score = (double) random.nextInt(2);
          } else {
            score = random.nextDouble();
          }

          scores.put(technique, score);

          if (!scoresTable.contains(testFqn, functionFqn)) {
            scoresTable.put(testFqn, functionFqn, new HashMap<>());
          }
          scoresTable.get(testFqn, functionFqn).put(technique, score);

          if (!combinedTestScores.containsKey(functionFqn)) {
            combinedTestScores.put(functionFqn, new HashMap<>());
          }

          Double currentScore = combinedTestScores.get(functionFqn).get(technique);
          if (currentScore == null) {
            currentScore = 0.0;
          }

          combinedTestScores.get(functionFqn).put(technique, currentScore + score);
        }

        testToFunctionScores.add(new TestToFunctionScores(testFqn, functionFqn, scores));
      }
    }

    return new TestClassToClassTensors(config, testClassFqn, nonTestClassFqn, testToFunctionScores,
        null);
  }
}
