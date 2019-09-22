package ctt.types.scores.method;

import com.google.common.collect.Table;
import ctt.Configuration;
import ctt.Main;
import ctt.Utilities;
import ctt.types.Technique;
import ctt.types.scores.clazz.ClassScoresTensor;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;

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
    //testFqnIndexes = new HashMap<>();
    functionFqns = pureMethodScoresTensor.getFunctionFqns();
    //functionFqnIndexes = new HashMap<>();
    techniques = new ArrayList<>(Arrays.asList(Utilities.getTechniques(config,
        Configuration.Level.METHOD, Main.ScoreType.AUGMENTED)));

    /*int nextTestIdx = 0;
    int nextFunctionIdx = 0;
    for (Table.Cell<String, String, Map<Technique, Double>> relevanceTableCell :
        relevanceTable.cellSet()) {
      String testFqn = relevanceTableCell.getRowKey();
      String functionFqn = relevanceTableCell.getColumnKey();

      if (!testsFqns.contains(testFqn)) {
        testsFqns.add(testFqn);
        testFqnIndexes.put(testFqn, nextTestIdx++);
      }

      if (!functionFqns.contains(functionFqn)) {
        functionFqns.add(functionFqn);
        functionFqnIndexes.put(functionFqn, nextFunctionIdx++);
      }
    }*/

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
            classLevelTechnique = Technique.IR_TFIDF_32_CLASS;
            break;
          case CONTAINS:
            classLevelTechnique = Technique.NS_CONTAINS_CLASS;
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
      //normaliseWithinTestByTechnique();
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
    if (config.isUseWeightedCombination()) {
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
        //augmentedScore = (methodLevelScore * classLevelScore);
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

  /*private void addCombinedScore() {
    INDArray newScoresTensor_S = Nd4j.zeros(testsFqns.size(), functionFqns.size(),
        config.getClassLevelTechniqueList().length);

    for (Technique technique : techniques) {
      INDArrayIndex[] indices = new INDArrayIndex[3];
      indices[0] = NDArrayIndex.all();
      indices[1] = NDArrayIndex.all();
      indices[2] = NDArrayIndex.point(techniques.indexOf(technique));
      newScoresTensor_S.put(indices, scoresTensor_S.get(indices));
    }

    INDArray scoresToCombine = scoresTensor_S.dup();

    if (config.isUseWeightedCombination()) {
      INDArray weightVector = Nd4j.zeros(techniques.size());
      for (Technique technique : techniques) {
        weightVector.putScalar(techniques.indexOf(technique),
            config.getMethodLevelTechniqueWeights().get(technique));
      }

      for (String testClassFqn : testsFqns) {
        int i = testsFqns.indexOf(testClassFqn);
        for (String nonTestClassFqn : functionFqns) {
          int j = functionFqns.indexOf(nonTestClassFqn);
          INDArrayIndex[] indices = new INDArrayIndex[3];
          indices[0] = NDArrayIndex.point(i);
          indices[1] = NDArrayIndex.point(j);
          indices[2] = NDArrayIndex.all();
          INDArray unweightedScoresVector = scoresToCombine.get(indices);
          INDArray zeroReplacementsvector =
              Nd4j.zeros(unweightedScoresVector.shape()).addi(config.getWeightingZeroPenalty());
          INDArray preparedUnweightedScoresVector =
              unweightedScoresVector.replaceWhere(zeroReplacementsvector,
                  new LessThanOrEqual(0));
          INDArray weightedScoresVector = preparedUnweightedScoresVector.mul(weightVector);
          scoresToCombine.put(indices, weightedScoresVector);
        }
      }
    }

    techniques.add(Technique.COMBINED);

    INDArray combinedTechniquesMatrix;
    switch (config.getScoreCombinationMethod()) {
      case AVERAGE:
        combinedTechniquesMatrix = scoresToCombine.sum(2);
        combinedTechniquesMatrix.divi(techniques.size() - 1);
        break;
      case SUM:
        combinedTechniquesMatrix = scoresToCombine.sum(2);
        break;
      case PRODUCT:
        combinedTechniquesMatrix = scoresToCombine.prod(2);
        break;
      default:
        combinedTechniquesMatrix = Nd4j.zeros(scoresToCombine.shape()[0], scoresToCombine.shape()[1]);
        break;
    }

    INDArrayIndex[] methodLevelCombinedIndices = new INDArrayIndex[3];
    methodLevelCombinedIndices[0] = NDArrayIndex.all();
    methodLevelCombinedIndices[1] = NDArrayIndex.all();
    methodLevelCombinedIndices[2] = NDArrayIndex.point(techniques.indexOf(Technique.COMBINED));
    newScoresTensor_S.put(methodLevelCombinedIndices, combinedTechniquesMatrix);

    scoresTensor_S = newScoresTensor_S;
  }*/

  private void normaliseCombinedScoreWithinTestClass() {
    for (String testClassFqn : testsFqns) {
      int i = testsFqns.indexOf(testClassFqn);

      Technique technique = Technique.COMBINED;
      int k = techniques.indexOf(technique);
      INDArrayIndex[] indices = new INDArrayIndex[3];
      indices[0] = NDArrayIndex.indices(i);
      indices[1] = NDArrayIndex.interval(0, (functionFqns.size()));
      indices[2] = NDArrayIndex.indices(k);

      INDArray nonTestClassVectorForTestClassAndTechnique = scoresTensor_S.get(indices);
      INDArray normalisedNonTestClassVectorForTestClassAndTechnique =
          Utilities.normaliseTensorByMaxValue(nonTestClassVectorForTestClassAndTechnique);
      scoresTensor_S.put(indices, normalisedNonTestClassVectorForTestClassAndTechnique);
    }
  }
}
