package ctt.types;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.gson.internal.$Gson$Preconditions;
import ctt.Utilities;
import ctt.types.scores.clazz.AugmentedClassScoresTensor;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Created by RRGWhite on 12/07/2019
 */
public class AugmentedClassScoresTensorTest {

  private static final double DELTA = 0.000001;

  /*@Test
  public void testConstructor() {
    TestClassToClassTensors testClass1ToClass1Tensors =
        TestUtils.createExampleTestClassToClassTensors("class1Test",
            "class1");
    TestClassToClassTensors testClass1ToClass2Tensors =
        TestUtils.createExampleTestClassToClassTensors("class1Test",
            "class2");
    TestClassToClassTensors testClass2ToClass1Tensors =
        TestUtils.createExampleTestClassToClassTensors("class2Test",
            "class1");
    TestClassToClassTensors testClass2ToClass2Tensors =
        TestUtils.createExampleTestClassToClassTensors("class2Test",
            "class2");

    Table<String, String, TestClassToClassTensors> allTestClassToClassTensors =
        HashBasedTable.create();

    allTestClassToClassTensors.put("class1Test", "class1",
        testClass1ToClass1Tensors);
    allTestClassToClassTensors.put("class1Test", "class2",
        testClass1ToClass2Tensors);
    allTestClassToClassTensors.put("class2Test", "class1",
        testClass2ToClass1Tensors);
    allTestClassToClassTensors.put("class2Test", "class2",
        testClass2ToClass2Tensors);

    AugmentedClassScoresTensor normalisedAugmentedClassScoresTensor =
        new AugmentedClassScoresTensor(allTestClassToClassTensors, true);
    AugmentedClassScoresTensor unnormalisedAugmentedClassScoresTensor =
        new AugmentedClassScoresTensor(allTestClassToClassTensors, false);

    for (Technique technique : normalisedAugmentedClassScoresTensor.getTechniques()) {
      Map<String, Double> unnormalisedTestClass1Scores =
          unnormalisedAugmentedClassScoresTensor.getScoresForTestClassForTechnique("class1Test",
              technique);
      Utilities.logger.info("Unnormalised scores for class1Test for " + technique + ": " +
          unnormalisedTestClass1Scores);

      Map<String, Double> normalisedTestClass1Scores =
          normalisedAugmentedClassScoresTensor.getScoresForTestClassForTechnique("class1Test",
              technique);
      Utilities.logger.info("Normalised scores for class1Test for " + technique + ": " +
          normalisedTestClass1Scores);

      double maxUnnormalisedScore = 0.0;
      for (Double score : unnormalisedTestClass1Scores.values()) {
        if (score >= maxUnnormalisedScore) {
          maxUnnormalisedScore = score;
        }
      }

      for (Map.Entry<String, Double> score : unnormalisedTestClass1Scores.entrySet()) {
        assertEquals(normalisedTestClass1Scores.get(score.getKey()),
            maxUnnormalisedScore == 0 ? 0 : (score.getValue() / maxUnnormalisedScore), DELTA);
      }

      double maxNormalisedScore = 0.0;
      for (Double score : normalisedTestClass1Scores.values()) {
        if (score >= maxNormalisedScore) {
          maxNormalisedScore = score;
        }
      }

      if (maxUnnormalisedScore > 0) {
        assertEquals(1.0, maxNormalisedScore, DELTA);
      } else {
        assertEquals(0.0, maxNormalisedScore, DELTA);
      }

      Map<String, Double> unnormalisedTestClass2Scores =
          unnormalisedAugmentedClassScoresTensor.getScoresForTestClassForTechnique(
          "class2Test", technique);
      Utilities.logger.info("Unnormalised scores for class2Test for " + technique + ": " +
          unnormalisedTestClass2Scores);

      Map<String, Double> normalisedTestClass2Scores = normalisedAugmentedClassScoresTensor.getScoresForTestClassForTechnique(
          "class2Test", technique);
      Utilities.logger.info("Normalised scores for class2Test for " + technique + ": " +
          normalisedTestClass2Scores);

      maxUnnormalisedScore = 0.0;
      for (Double score : unnormalisedTestClass2Scores.values()) {
        if (score >= maxUnnormalisedScore) {
          maxUnnormalisedScore = score;
        }
      }

      for (Map.Entry<String, Double> score : unnormalisedTestClass2Scores.entrySet()) {
        assertEquals(normalisedTestClass2Scores.get(score.getKey()),
            maxUnnormalisedScore == 0 ? 0 : (score.getValue() / maxUnnormalisedScore), DELTA);
      }

      maxNormalisedScore = 0.0;
      for (Double score : normalisedTestClass2Scores.values()) {
        if (score >= maxNormalisedScore) {
          maxNormalisedScore = score;
        }
      }

      if (maxUnnormalisedScore > 0) {
        assertEquals(1.0, maxNormalisedScore, DELTA);
      } else {
        assertEquals(0.0, maxNormalisedScore, DELTA);
      }
    }
  }*/

  @Test
  public void getScore() {
  }
}