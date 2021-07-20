package ctt.types.scores.method;

import com.google.common.collect.Table;
import ctt.Configuration;
import ctt.Main;
import ctt.Utilities;
import ctt.types.Technique;
import ctt.types.scores.clazz.ClassScoresTensor;
import org.nd4j.linalg.factory.Nd4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

/**
 * Created by RRGWhite on 06/08/2019
 */
public class AugmentedMethodScoresTensor extends MethodScoresTensor {
  public AugmentedMethodScoresTensor(Configuration config,
                                     Table<String, String, Map<Technique, Double>> relevanceTable,
                                     MethodScoresTensor pureMethodScoresTensor,
                                     ClassScoresTensor pureClassScoresTensor,
                                     boolean normalise) {
    Utilities.logger.info("Constructing augmented method scores tensor");
    this.config = config;
    this.normalise = normalise;

    testsFqns = pureMethodScoresTensor.getTestsFqns();
    functionFqns = pureMethodScoresTensor.getFunctionFqns();
    techniques = new ArrayList<>(Arrays.asList(Utilities.getTechniques(config,
        Configuration.Level.METHOD, Main.ScoreType.AUGMENTED)));

    scoresTensor_S = Nd4j.zeros(testsFqns.size(), functionFqns.size(),
        techniques.size());

    for (Table.Cell<String, String, Map<Technique, Double>> relevanceTableCell :
        relevanceTable.cellSet()) {
      String testFqn = relevanceTableCell.getRowKey();
      String functionFqn = relevanceTableCell.getColumnKey();

      int i = testsFqns.indexOf(testFqn);
      int j = functionFqns.indexOf(functionFqn);
      for (Technique technique : techniques) {
        Technique methodLevelTechnique = Utilities.getTechniqueForMethodLevel(technique);
        Technique classLevelTechnique;
        switch (config.getAugmentationType()) {
          case COMBINED_SCORE:
            classLevelTechnique = Technique.COMBINED_CLASS;
            break;
          case TFIDF:
            classLevelTechnique = Technique.TFIDF_CLASS;
            break;
          case CONTAINS:
            classLevelTechnique = Technique.NCC_CLASS;
            break;
          default:
            classLevelTechnique = Utilities.getTechniqueForClassLevel(technique);
            break;
        }

        int k = techniques.indexOf(technique);
        int[] indices = new int[3];
        indices[0] = i;
        indices[1] = j;
        indices[2] = k;

        double methodLevelScore =
            pureMethodScoresTensor.getSingleScoreForTestFunctionPair(testFqn, functionFqn,
                methodLevelTechnique);

        double classLevelScore =
            pureClassScoresTensor.getSingleScoreForTestClassNonTestClassPair(
                Utilities.getClassFqnFromMethodFqn(testFqn),
                Utilities.getClassFqnFromMethodFqn(functionFqn),
                classLevelTechnique);

        double combinedScore = combineMethodAndClassLevelScores(methodLevelScore, classLevelScore,
            methodLevelTechnique, classLevelTechnique);

        scoresTensor_S.putScalar(indices, combinedScore);
      }
    }

    if (normalise) {
      normaliseAllWithinTestByTechnique();
    }

    Utilities.logger.info("Augmented method scores tensor constructed");
  }

  private double combineMethodAndClassLevelScores(double unweightedMethodLevelScore,
                                                  double unweightedClassLevelScore,
                                                  Technique methodLevelTechnique,
                                                  Technique classLevelTechnique) {
    if (methodLevelTechnique == null || classLevelTechnique == null) {
      Utilities.logger.debug("DEBUGGING NULL TECHNIQUES IN combineMethodAndClassLevelScores");
    }

    double methodLevelScore;
    double classLevelScore;
    if (config.isUseTechniqueWeightingForAugmentation()) {
      methodLevelScore = weightMethodLevelScore(unweightedMethodLevelScore, methodLevelTechnique);
      classLevelScore = weightClassLevelScore(unweightedClassLevelScore, classLevelTechnique);
    } else {
      methodLevelScore = unweightedMethodLevelScore;
      classLevelScore = unweightedClassLevelScore;
    }

    double augmentedScore;
    switch (config.getScoreCombinationMethod()) {
      case PRODUCT:
        if (methodLevelScore == 0) {
          methodLevelScore = config.getWeightingZeroPenalty();
        } else if (classLevelScore == 0) {
          classLevelScore = config.getWeightingZeroPenalty();
        }

        augmentedScore = methodLevelScore * classLevelScore;
        break;
      case AVERAGE:
        augmentedScore = (methodLevelScore + classLevelScore) / 2.0;
        break;
      case SUM:
        augmentedScore = methodLevelScore + classLevelScore;
        break;
      default:
        augmentedScore = 0;
    }

    return augmentedScore;
  }

  private double weightMethodLevelScore(double methodLevelScore, Technique technique) {
    if (methodLevelScore == 0) {
      methodLevelScore = config.getWeightingZeroPenalty();
    }

    double weightedScore = methodLevelScore * config.getMethodLevelTechniqueWeights().get(technique);
    return weightedScore;
  }

  private double weightClassLevelScore(double classLevelScore, Technique technique) {
    if (classLevelScore == 0) {
      classLevelScore = config.getWeightingZeroPenalty();
    }

    double weightedScore = classLevelScore * config.getClassLevelTechniqueWeights().get(technique);
    return weightedScore;
  }
}
