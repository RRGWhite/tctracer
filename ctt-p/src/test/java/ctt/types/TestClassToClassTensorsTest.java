package ctt.types;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import ctt.Configuration;
import ctt.Utilities;
import org.junit.Before;
import org.junit.Test;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 * Created by RRGWhite on 11/07/2019
 */
public class TestClassToClassTensorsTest {

  private static final double DELTA = 0.000001;
  private String testClassFqn;
  private List<String> testFqns;
  private String nonTestClassFqn;
  private List<String> functionFqns;
  private ArrayList<TestToFunctionScores> testToFunctionScores;
  private Table<String, String, Map<Technique, Double>> scoresTable;
  private Map<String, Map<Technique, Double>> combinedTestScores;
  private Configuration config;
  private double tfIdf32ClassScore;

  @Before
  public void setUp() throws Exception {
    testClassFqn = "testclass";
    nonTestClassFqn = "nontestclass";
    config = new Configuration.Builder().build();
    testToFunctionScores = new ArrayList<>();
    scoresTable = HashBasedTable.create();
    combinedTestScores = new HashMap<>();
    Random random = new Random();
    tfIdf32ClassScore = 0.0;

    testFqns = Arrays.asList("test1", "test2", "test3");
    functionFqns = Arrays.asList("function1", "function2", "function3");
    for (String testFqn : testFqns) {
      for (String functionFqn : functionFqns) {
        Map<Technique, Double> scores = new HashMap<>();
        for (Technique technique : config.getMethodLevelTechniqueList()) {
          double score;
          if (technique.equals(Technique.NS_CONTAINS)) {
            score = (double) random.nextInt(2);
          } else if (technique.equals(Technique.NS_LEVENSHTEIN)) {
            score = (double) random.nextInt(10);
          } else if (technique.equals(Technique.LAST_CALL_BEFORE_ASSERT)) {
            score = (double) random.nextInt(2);
          } else {
            score = random.nextDouble();
          }

          if (technique.equals(Technique.IR_TFIDF_32)) {
            tfIdf32ClassScore += score;
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

          Utilities.logger.info("Score for " + testFqn + ", " + functionFqn + ", " + technique +
              ": " + score);
        }

        testToFunctionScores.add(new TestToFunctionScores(testFqn, functionFqn, scores));
      }
    }
  }

  @Test
  public void testConstructor() {
    TestClassToClassTensors testClassToClassTensors = new TestClassToClassTensors(config,
        testClassFqn, nonTestClassFqn, testToFunctionScores, null);

    Utilities.logger.info("tests: " + testClassToClassTensors.getTests());
    Utilities.logger.info("functions: " + testClassToClassTensors.getFunctions());
    Utilities.logger.info("techniques: " + testClassToClassTensors.getMethodLevelTechniques());

    String functionToInspect = testClassToClassTensors.getFunctions().get(0);
    String testToInspect = testClassToClassTensors.getTests().get(0);
    Technique techniqueToInspect = Technique.IR_TFIDF_32;

    Utilities.logger.info("Manually validating TestClassToClassTensors using " + functionToInspect +
        ", " + testToInspect + ", and " + techniqueToInspect);

    int i = testClassToClassTensors.getTests().indexOf(testToInspect);
    int j = testClassToClassTensors.getFunctions().indexOf(functionToInspect);
    int k = testClassToClassTensors.getMethodLevelTechniques().indexOf(techniqueToInspect);

    INDArrayIndex[] indices = new INDArrayIndex[3];
    indices[0] = NDArrayIndex.interval(0, (testClassToClassTensors.getTests().size()));
    indices[1] = NDArrayIndex.interval(0, (testClassToClassTensors.getFunctions().size()));
    indices[2] = NDArrayIndex.indices(k);

    INDArray allScoresForTechnique = testClassToClassTensors.getFullScores_C().get(indices);

    Utilities.logger.info("All function and test scores for " + techniqueToInspect + ": " +
        allScoresForTechnique);

    String table = "";
    for (String testFqn : testClassToClassTensors.getTests()) {
      table += "| ";
      int m = testClassToClassTensors.getTests().indexOf(testFqn);
      for (String functionFqn : testClassToClassTensors.getFunctions()) {
        int n = testClassToClassTensors.getFunctions().indexOf(functionFqn);
        float score = (float) testClassToClassTensors.getFullScores_C().getDouble(m,n,k);
        table += score + " | ";
      }
      table += "\n";
    }
    Utilities.logger.info("\n" + table);

    assertEquals(allScoresForTechnique.getDouble(0,0,0), scoresTable.get(testFqns.get(0),
        functionFqns.get(0)).get(techniqueToInspect), DELTA);

    assertEquals(allScoresForTechnique.getDouble(2,2,0), scoresTable.get(testFqns.get(2),
        functionFqns.get(2)).get(techniqueToInspect), DELTA);

    indices = new INDArrayIndex[2];
    indices[0] = NDArrayIndex.all();
    indices[1] = NDArrayIndex.point(k);

    INDArray combinedTestScoresForTechnique = testClassToClassTensors.getTestScoresSummed_sf().get(
        indices);

    Utilities.logger.info("Combined test scores for " + techniqueToInspect + ": " +
        combinedTestScoresForTechnique);

    assertEquals(combinedTestScoresForTechnique.getDouble(0),
        combinedTestScores.get(functionFqns.get(0)).get(techniqueToInspect), DELTA);

    assertEquals(combinedTestScoresForTechnique.getDouble(2),
        combinedTestScores.get(functionFqns.get(2)).get(techniqueToInspect), DELTA);

    indices = new INDArrayIndex[1];
    indices[0] = NDArrayIndex.indices(k);

    INDArray combinedTestAndFunctionScoresForTechnique =
        testClassToClassTensors.getSummedMethodLevelScores_s().get(indices);

    Utilities.logger.info("Combined test and function scores for " + techniqueToInspect + ": " +
        combinedTestAndFunctionScoresForTechnique);

    assertEquals(tfIdf32ClassScore, combinedTestAndFunctionScoresForTechnique.getDouble(0),
        DELTA);

    /*assertEquals(tfIdf32ClassScore, testClassToClassTensors.getClassLevelScore
    (techniqueToInspect),
        DELTA);*/

    indices = new INDArrayIndex[3];
    indices[0] = NDArrayIndex.indices(i);
    indices[1] = NDArrayIndex.interval(0, (testClassToClassTensors.getFunctions().size()));
    indices[2] = NDArrayIndex.indices(k);

    INDArray allFunctionScoresForTestForTechnique = testClassToClassTensors.getFullScores_C().get(
        indices);

    Utilities.logger.info("All function scores for " + testToInspect + " for " +
        techniqueToInspect + ": " + allFunctionScoresForTestForTechnique);

    indices = new INDArrayIndex[3];
    indices[0] = NDArrayIndex.interval(0, (testClassToClassTensors.getTests().size()));
    indices[1] = NDArrayIndex.indices(j);
    indices[2] = NDArrayIndex.indices(k);

    INDArray allTestScoresForFunctionForTechnique = testClassToClassTensors.getFullScores_C().get(
        indices);

    Utilities.logger.info("All test scores for " + functionToInspect + " for " +
        techniqueToInspect + ": " + allTestScoresForFunctionForTechnique);

    indices = new INDArrayIndex[2];
    indices[0] = NDArrayIndex.indices(j);
    indices[1] = NDArrayIndex.indices(k);

    INDArray combinedTestScoresForFunctionForTechnique =
        testClassToClassTensors.getTestScoresSummed_sf().get(indices);

    Utilities.logger.info("Combined test scores for " + functionToInspect + " for " +
        techniqueToInspect + ": " + combinedTestScoresForFunctionForTechnique);
  }
}