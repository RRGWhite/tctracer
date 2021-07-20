package ctt.types.scores.clazz;

import com.google.common.collect.Table;
import ctt.Configuration;
import ctt.Main;
import ctt.Utilities;
import ctt.types.Technique;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.indexing.conditions.LessThanOrEqual;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

/**
 * Created by RRGWhite on 06/08/2019
 */
public class PureClassScoresTensor extends ClassScoresTensor{
  //private boolean useClassLevelInformation;

  public PureClassScoresTensor(Configuration config,
                               Table<String, String, Map<Technique, Double>> classLevelRelevanceTable,
                               boolean normalisedWithinTestClassByTechnique) {
    Utilities.logger.info("Constructing pure class scores tensor");
    this.config = config;
    this.normalise = normalisedWithinTestClassByTechnique;

    testClassFqns = new ArrayList<>();
    nonTestClassFqns = new ArrayList<>();
    techniques = new ArrayList<>(Arrays.asList(Utilities.getTechniques(config,
        Configuration.Level.CLASS, Main.ScoreType.PURE)));

    for (Table.Cell<String, String, Map<Technique, Double>> relevanceTableCell :
        classLevelRelevanceTable.cellSet()) {
      String testClassFqn = relevanceTableCell.getRowKey();
      String nonTestClassFqn = relevanceTableCell.getColumnKey();
      /*if (testClassToClassTensors.getValue().getTestClassFqn() == null
          || testClassToClassTensors.getValue().getTestClassFqn().isEmpty()
          || testClassToClassTensors.getValue().getNonTestClassFqn() == null
          || testClassToClassTensors.getValue().getNonTestClassFqn().isEmpty()) {
        Utilities.logger.debug("DEUGGING NULL METHODS");
        continue;
      }*/

      if (!testClassFqns.contains(testClassFqn)) {
        testClassFqns.add(testClassFqn);
      }

      if (!nonTestClassFqns.contains(nonTestClassFqn)) {
        nonTestClassFqns.add(nonTestClassFqn);
      }

      /*for (Technique technique : relevanceTableCell.getValue().keySet()) {
        if (!techniques.contains(technique)) {
          techniques.add(technique);
        }
      }*/
    }

    scoresTensor_S = Nd4j.zeros(testClassFqns.size(), nonTestClassFqns.size(),
        techniques.size());

    for (Table.Cell<String, String, Map<Technique, Double>> relevanceTableCell :
        classLevelRelevanceTable.cellSet()) {
      String testClassFqn = relevanceTableCell.getRowKey();
      String nonTestClassFqn = relevanceTableCell.getColumnKey();

      int i = testClassFqns.indexOf(testClassFqn);
      int j = nonTestClassFqns.indexOf(nonTestClassFqn);
      for (Technique technique : techniques) {
        int k = techniques.indexOf(technique);
        int[] indices = new int[3];
        indices[0] = i;
        indices[1] = j;
        indices[2] = k;

        double score;
        if (relevanceTableCell.getValue().get(technique) == null) {
          score = 0;
        } else {
          score = relevanceTableCell.getValue().get(technique);
        }

        scoresTensor_S.putScalar(indices, score);
      }
    }

    if (normalisedWithinTestClassByTechnique) {
      normaliseWithinTestClassByTechnique();
    }

    addCombinedScore();

    if (normalisedWithinTestClassByTechnique) {
      normaliseCombinedScoreWithinTestClass();
    }

    Utilities.logger.info("Pure class scores tensor constructed");
  }

  private void addCombinedScore() {
    INDArray newScoresTensor_S = Nd4j.zeros(testClassFqns.size(), nonTestClassFqns.size(),
        Utilities.getTechniques(config, Configuration.Level.CLASS, Main.ScoreType.PURE).length);

    for (Technique technique : techniques) {
      INDArrayIndex[] indices = new INDArrayIndex[3];
      indices[0] = NDArrayIndex.all();
      indices[1] = NDArrayIndex.all();
      indices[2] = NDArrayIndex.point(techniques.indexOf(technique));
      newScoresTensor_S.put(indices, scoresTensor_S.get(indices));
    }

    INDArray scoresToCombine = scoresTensor_S.dup();

    if (config.isUseTechniqueWeightingForCombinedScore()) {
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
    switch (config.getScoreCombinationMethod()) {
      case AVERAGE:
        combinedTechniquesMatrix = scoresToCombine.sum(2);
        combinedTechniquesMatrix.divi(techniques.size() - 1);
        break;
      case SUM:
        combinedTechniquesMatrix = scoresToCombine.sum(2);
        break;
      case PRODUCT:
        INDArray zeroReplacementsTensor =
            Nd4j.zeros(scoresToCombine.shape()).addi(config.getWeightingZeroPenalty());
        INDArray preparedScoresToCombine =
            scoresToCombine.replaceWhere(zeroReplacementsTensor, new LessThanOrEqual(0));

        combinedTechniquesMatrix = preparedScoresToCombine.prod(2);
        break;
      default:
        combinedTechniquesMatrix = Nd4j.zeros(scoresToCombine.shape()[0], scoresToCombine.shape()[1]);
        break;
    }

    INDArrayIndex[] classLevelCombinedIndices = new INDArrayIndex[3];
    classLevelCombinedIndices[0] = NDArrayIndex.all();
    classLevelCombinedIndices[1] = NDArrayIndex.all();
    classLevelCombinedIndices[2] = NDArrayIndex.point(techniques.indexOf(Technique.COMBINED_CLASS));
    newScoresTensor_S.put(classLevelCombinedIndices, combinedTechniquesMatrix);

    scoresTensor_S = newScoresTensor_S;
  }

  /*public Map<String, Double> getScoresForTestClassForTechnique(String testClassFqn,
                                                               Technique technique) {
    int i = testClassFqns.indexOf(testClassFqn);
    int k = techniques.indexOf(technique);

    if (i == -1 || k == -1) {
      return new HashMap<>();
    }

    HashMap<String, Double> nonTestClassToScore = new HashMap<>();
    for (String nonTestClass : nonTestClassFqns) {
      int[] idxs = new int[3];
      idxs[0] = i;
      idxs[1] = nonTestClassFqns.indexOf(nonTestClass);
      idxs[2] = k;
      double score = scoresTensor_S.getDouble(idxs);
      nonTestClassToScore.put(nonTestClass, score);
    }

    return nonTestClassToScore;
  }*/

  /*public double getPercentileValueForTechnique(double percentile, Technique technique) {
    int k = techniques.indexOf(technique);
    ArrayList<Double> vals = new ArrayList<>();
    for (String testClass : testClassFqns) {
      for (String nonTestClass : nonTestClassFqns) {
        int[] idxs = new int[3];
        idxs[0] = testClassFqns.indexOf(testClass);
        idxs[1] = nonTestClassFqns.indexOf(nonTestClass);
        idxs[2] = k;
        double score = scoresTensor_S.getDouble(idxs);
        vals.add(score);
      }
    }

    double[] valsArr = new double[vals.size()];
    for (int i = 0; i < vals.size(); ++i) {
      valsArr[i] = vals.get(i);
    }

    DescriptiveStatistics descriptiveStatistics = new DescriptiveStatistics(valsArr);
    return descriptiveStatistics.getPercentile(percentile);
  }*/

  private void normaliseCombinedScoreWithinTestClass() {
    for (String testClassFqn : testClassFqns) {
      int i = testClassFqns.indexOf(testClassFqn);

      Technique technique = Technique.COMBINED_CLASS;
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
