package ctt.types.scores.clazz;

import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import ctt.Configuration;
import ctt.Main;
import ctt.Utilities;
import ctt.types.Technique;
import ctt.types.TestClassToClassTensors;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.indexing.conditions.LessThanOrEqual;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by RRGWhite on 04/07/2019
 */
public class AugmentedClassScoresTensor extends ClassScoresTensor {

  public AugmentedClassScoresTensor(Configuration config,
                                    ClassScoresTensor pureClassScoresTensor,
                                    Table<String, String, TestClassToClassTensors> allTestClassToClassTensors,
                                    boolean normalise) {
    Utilities.logger.info("Constructing augmented class scores tensor");
    this.config = config;
    this.normalise = normalise;

    testClassFqns = pureClassScoresTensor.getTestClassFqns();
    nonTestClassFqns = pureClassScoresTensor.getNonTestClassFqns();
    techniques = new ArrayList<>(Arrays.asList(Utilities.getTechniques(config,
        Configuration.Level.CLASS, Main.ScoreType.AUGMENTED)));

    /*for (Cell<String, String, TestClassToClassTensors> testClassToClassTensors :
        allTestClassToClassTensors.cellSet()) {
      if (testClassToClassTensors.getValue().getTestClassFqn() == null
          || testClassToClassTensors.getValue().getTestClassFqn().isEmpty()
          || testClassToClassTensors.getValue().getNonTestClassFqn() == null
          || testClassToClassTensors.getValue().getNonTestClassFqn().isEmpty()) {
        Utilities.logger.debug("DEUGGING NULL METHODS");
        continue;
      }

      if (!testClassFqns.contains(testClassToClassTensors.getValue().getTestClassFqn())) {
        testClassFqns.add(testClassToClassTensors.getValue().getTestClassFqn());
      }

      if (!nonTestClassFqns.contains(testClassToClassTensors.getValue().getNonTestClassFqn())) {
        nonTestClassFqns.add(testClassToClassTensors.getValue().getNonTestClassFqn());
      }

      for (Technique technique : testClassToClassTensors.getValue().getClassLevelTechniques()) {
        if (!techniques.contains(technique)) {
          techniques.add(technique);
        }
      }
    }*/

    INDArray methodLevelScoresTensor = Nd4j.zeros(testClassFqns.size(), nonTestClassFqns.size(),
        techniques.size());

    for (Cell<String, String, TestClassToClassTensors> testClassToClassTensors :
        allTestClassToClassTensors.cellSet()) {
      int i = testClassFqns.indexOf(testClassToClassTensors.getValue().getTestClassFqn());
      int j = nonTestClassFqns.indexOf(testClassToClassTensors.getValue().getNonTestClassFqn());
      for (Technique technique : techniques) {
        Technique methodLevelTechnique = Utilities.getTechniqueForMethodLevel(technique);
        int k = techniques.indexOf(technique);
        int[] indices = new int[3];
        indices[0] = i;
        indices[1] = j;
        indices[2] = k;

        double methodLevelScore = testClassToClassTensors.getValue().getMethodLevelScore(
            methodLevelTechnique);
        methodLevelScoresTensor.putScalar(indices, methodLevelScore);
      }
    }

    //UNCOMMENT THIS STATEMENT FOR OLD RQ3 RESULTS (AUGMENTED CLASS LEVEL)
    /*scoresTensor_S = combineMethodLevelAndClassLevelScores(methodLevelScoresTensor,
        pureClassScoresTensor.getScoresTensor_S());*/

    //UNCOMMENT THIS STATEMENT FOR NEW RQ3 RESULTS (METHOD LEVEL INFO ONLY)
    scoresTensor_S = methodLevelScoresTensor;

    if (normalise) {
      normaliseAllWithinTestClassByTechnique();
      //normaliseWithinTestClassByTechnique();
    }

    Utilities.logger.info("Augmented class scores tensor constructed");
  }

  private INDArray combineMethodLevelAndClassLevelScores(INDArray unweightedMethodLevelScores,
                                                         INDArray unweightedClassLevelScores) {
    INDArray methodLevelScores;
    INDArray classLevelScores;
    if (config.isUseTechniqueWeightingForAugmentation()) {
      methodLevelScores = weightMethodLevelScores(unweightedMethodLevelScores);
      classLevelScores = weightClassLevelScores(unweightedClassLevelScores);
    } else {
      methodLevelScores = unweightedMethodLevelScores.dup();
      classLevelScores = unweightedClassLevelScores.dup();
    }

    INDArray augmentedScores;
    switch (config.getScoreCombinationMethod()) {
      case PRODUCT:
        augmentedScores = methodLevelScores.mul(classLevelScores);
        break;
      case AVERAGE:
        augmentedScores = methodLevelScores.add(classLevelScores).div(2);
        break;
      case SUM:
        augmentedScores = methodLevelScores.add(classLevelScores);
        break;
      default:
        augmentedScores = Nd4j.zeros(methodLevelScores.shape());
    }

    return augmentedScores;
  }

  private INDArray weightMethodLevelScores(INDArray methodLevelScores) {
    INDArray weightedScores = methodLevelScores.dup();
    INDArray methodLevelWeightVector = Nd4j.zeros(techniques.size());
    for (Technique technique : techniques) {
      methodLevelWeightVector.putScalar(techniques.indexOf(technique),
          config.getMethodLevelTechniqueWeights().get(Utilities.getTechniqueForMethodLevel(technique)));
    }

    for (String testClassFqn : testClassFqns) {
      int i = testClassFqns.indexOf(testClassFqn);
      for (String nonTestClassFqn : nonTestClassFqns) {
        int j = nonTestClassFqns.indexOf(nonTestClassFqn);
        INDArrayIndex[] indices = new INDArrayIndex[3];
        indices[0] = NDArrayIndex.point(i);
        indices[1] = NDArrayIndex.point(j);
        indices[2] = NDArrayIndex.all();
        INDArray unweightedScoresVector = weightedScores.get(indices);
        INDArray zeroReplacementsvector =
            Nd4j.zeros(unweightedScoresVector.shape()).addi(config.getWeightingZeroPenalty());
        INDArray preparedUnweightedScoresVector =
            unweightedScoresVector.replaceWhere(zeroReplacementsvector,
                new LessThanOrEqual(0));
        INDArray weightedScoresVector = preparedUnweightedScoresVector.mul(methodLevelWeightVector);
        weightedScores.put(indices, weightedScoresVector);
      }
    }

    return weightedScores;
  }

  private INDArray weightClassLevelScores(INDArray classLevelScores) {
    INDArray weightedScores = classLevelScores.dup();
    INDArray classLevelWeightVector = Nd4j.zeros(techniques.size());
    for (Technique technique : techniques) {
      classLevelWeightVector.putScalar(techniques.indexOf(technique),
          config.getClassLevelTechniqueWeights().get(Utilities.getTechniqueForClassLevel(technique)));
    }

    for (String testClassFqn : testClassFqns) {
      int i = testClassFqns.indexOf(testClassFqn);
      for (String nonTestClassFqn : nonTestClassFqns) {
        int j = nonTestClassFqns.indexOf(nonTestClassFqn);
        INDArrayIndex[] indices = new INDArrayIndex[3];
        indices[0] = NDArrayIndex.point(i);
        indices[1] = NDArrayIndex.point(j);
        indices[2] = NDArrayIndex.all();
        INDArray unweightedScoresVector = weightedScores.get(indices);
        INDArray zeroReplacementsvector =
            Nd4j.zeros(unweightedScoresVector.shape()).addi(config.getWeightingZeroPenalty());
        INDArray preparedUnweightedScoresVector =
            unweightedScoresVector.replaceWhere(zeroReplacementsvector,
                new LessThanOrEqual(0));
        INDArray weightedScoresVector = preparedUnweightedScoresVector.mul(classLevelWeightVector);
        weightedScores.put(indices, weightedScoresVector);
      }
    }

    return weightedScores;
  }

  private void addMultiLevelCombinedScore() {
    INDArray newScoresTensor_S = Nd4j.zeros(testClassFqns.size(), nonTestClassFqns.size(),
        Utilities.getTechniques(config, Configuration.Level.CLASS, Main.ScoreType.AUGMENTED).length);

    for (Technique technique : techniques) {
      INDArrayIndex[] indices = new INDArrayIndex[3];
      indices[0] = NDArrayIndex.all();
      indices[1] = NDArrayIndex.all();
      indices[2] = NDArrayIndex.point(techniques.indexOf(technique));
      newScoresTensor_S.put(indices, scoresTensor_S.get(indices));
    }

    /*INDArray scoresToCombine = scoresTensor_S.dup();
    if (config.isUseWeightedCombination()) {
      INDArray weightVector = Nd4j.zeros(techniques.size());
      for (Technique technique : techniques) {
        weightVector.putScalar(techniques.indexOf(technique),
            config.getClassLevelTechniqueWeights().get(technique));
      }

      for (String testClassFqn : testClassFqns) {
        int i = testClassFqns.indexOf(testClassFqn);
        for (String nonTestClassFqn : nonTestClassFqns) {
          int j = nonTestClassFqns.indexOf(nonTestClassFqn);
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

    techniques.add(Technique.COMBINED_CLASS);

    INDArray combinedTechniquesMatrix;
    INDArrayIndex[] methodLevelCombinedIndices = new INDArrayIndex[3];
    methodLevelCombinedIndices[0] = NDArrayIndex.all();
    methodLevelCombinedIndices[1] = NDArrayIndex.all();
    methodLevelCombinedIndices[2] = NDArrayIndex.point(techniques.indexOf(Technique.COMBINED));

    switch (config.getScoreCombinationMethod()) {
      case AVERAGE:
        combinedTechniquesMatrix = scoresToCombine.sum(2);
        combinedTechniquesMatrix.subi(newScoresTensor_S.get(methodLevelCombinedIndices));
        combinedTechniquesMatrix.divi(techniques.size() - 1);
        break;
      case SUM:
        combinedTechniquesMatrix = scoresToCombine.sum(2);
        combinedTechniquesMatrix.subi(newScoresTensor_S.get(methodLevelCombinedIndices));
        break;
      case PRODUCT:
        combinedTechniquesMatrix = scoresToCombine.prod(2);

        INDArray methodLevelCombinedMatrix =
            newScoresTensor_S.get(methodLevelCombinedIndices).dup().muli(
            config.getClassLevelTechniqueWeights().get(Technique.COMBINED));
        INDArray zeroReplacementsMatrix = Nd4j.ones(methodLevelCombinedMatrix.shape());
        INDArray preparedMethodLevelCombinedMatrix =
            methodLevelCombinedMatrix.replaceWhere(zeroReplacementsMatrix, new LessThanOrEqual(0));

        combinedTechniquesMatrix.divi(preparedMethodLevelCombinedMatrix);
        break;
      default:
        combinedTechniquesMatrix = Nd4j.zeros(scoresToCombine.shape()[0], scoresToCombine.shape()[1]);
        break;
    }

    INDArrayIndex[] classLevelCombinedIndices = new INDArrayIndex[3];
    classLevelCombinedIndices[0] = NDArrayIndex.all();
    classLevelCombinedIndices[1] = NDArrayIndex.all();
    classLevelCombinedIndices[2] = NDArrayIndex.point(techniques.indexOf(Technique.COMBINED_CLASS));
    newScoresTensor_S.put(classLevelCombinedIndices, combinedTechniquesMatrix);*/

    techniques.add(Technique.COMBINED_MULTI);

    INDArrayIndex[] methodLevelCombinedIndices = new INDArrayIndex[3];
    methodLevelCombinedIndices[0] = NDArrayIndex.all();
    methodLevelCombinedIndices[1] = NDArrayIndex.all();
    methodLevelCombinedIndices[2] = NDArrayIndex.point(techniques.indexOf(Technique.COMBINED));
    INDArray methodLevelCombinedMatrix = newScoresTensor_S.get(methodLevelCombinedIndices);

    INDArrayIndex[] classLevelCombinedIndices = new INDArrayIndex[3];
    classLevelCombinedIndices[0] = NDArrayIndex.all();
    classLevelCombinedIndices[1] = NDArrayIndex.all();
    classLevelCombinedIndices[2] = NDArrayIndex.point(techniques.indexOf(Technique.COMBINED_CLASS));
    INDArray classLevelCombinedMatrix = newScoresTensor_S.get(classLevelCombinedIndices);

    if (config.isUseTechniqueWeightingForAugmentation()) {
      methodLevelCombinedMatrix.muli(config.getClassLevelTechniqueWeights().get(Technique.COMBINED));
      classLevelCombinedMatrix.muli(config.getClassLevelTechniqueWeights().get(Technique.COMBINED_CLASS));
    }

    INDArray multiLevelCombinedMatrix;
    switch (config.getScoreCombinationMethod()) {
      case PRODUCT:
        multiLevelCombinedMatrix = methodLevelCombinedMatrix.mul(classLevelCombinedMatrix);
        break;
      case AVERAGE:
        multiLevelCombinedMatrix = methodLevelCombinedMatrix.add(classLevelCombinedMatrix).div(2);
        break;
      case SUM:
        multiLevelCombinedMatrix = methodLevelCombinedMatrix.add(classLevelCombinedMatrix);
        break;
      default:
        multiLevelCombinedMatrix = Nd4j.zeros(methodLevelCombinedMatrix.shape());
    }

    INDArrayIndex[] multiLevelCombinedIndices = new INDArrayIndex[3];
    multiLevelCombinedIndices[0] = NDArrayIndex.all();
    multiLevelCombinedIndices[1] = NDArrayIndex.all();
    multiLevelCombinedIndices[2] = NDArrayIndex.point(techniques.indexOf(Technique.COMBINED_MULTI));
    newScoresTensor_S.put(multiLevelCombinedIndices, multiLevelCombinedMatrix);

    scoresTensor_S = newScoresTensor_S;
  }

  private void normaliseMultilevelCombinedScoresWithinTestClass() {
    for (String testClassFqn : testClassFqns) {
      int i = testClassFqns.indexOf(testClassFqn);

      Technique technique = Technique.COMBINED_MULTI;
      int k = techniques.indexOf(technique);
      INDArrayIndex[] indices = new INDArrayIndex[3];
      indices[0] = NDArrayIndex.indices(i);
      indices[1] = NDArrayIndex.interval(0, (nonTestClassFqns.size()));
      indices[2] = NDArrayIndex.indices(k);

      INDArray nonTestClassVectorForTestClassAndTechnique = scoresTensor_S.get(indices);
      INDArray normalisedNonTestClassVectorForTestClassAndTechnique =
          Utilities.normaliseTensorByMaxValue(nonTestClassVectorForTestClassAndTechnique);
      scoresTensor_S.put(indices, normalisedNonTestClassVectorForTestClassAndTechnique);
    }
  }
}
