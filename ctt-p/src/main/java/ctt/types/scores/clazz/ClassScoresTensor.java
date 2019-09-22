package ctt.types.scores.clazz;

import ctt.Configuration;
import ctt.Utilities;
import ctt.types.Technique;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by RRGWhite on 06/08/2019
 */
public abstract class ClassScoresTensor {
  protected INDArray scoresTensor_S;
  protected boolean normalise;
  protected ArrayList<String> testClassFqns;
  protected ArrayList<String> nonTestClassFqns;
  protected ArrayList<Technique> techniques;
  protected Configuration config;

  public Map<Technique, Double> getAllScoresForTestClassNonTestClassPair(String testClassFqn,
                                                                         String nonTestClassFqn) {
    int i = testClassFqns.indexOf(testClassFqn);
    int j = nonTestClassFqns.indexOf(nonTestClassFqn);

    if (i == -1 || j == -1) {
      return new HashMap<>();
    }

    INDArrayIndex[] idxs = new INDArrayIndex[3];
    idxs[0] = NDArrayIndex.point(i);
    idxs[1] = NDArrayIndex.point(i);
    idxs[2] = NDArrayIndex.all();
    INDArray rawScores = scoresTensor_S.get(idxs);

    HashMap<Technique, Double> scores = new HashMap<>();
    for (Technique technique : techniques) {
      double score = rawScores.getDouble(techniques.indexOf(technique));
      scores.put(technique, score);
    }

    return scores;
  }

  public double getSingleScoreForTestClassNonTestClassPair(String testClassFqn,
                                                           String nonTestClassFqn,
                                                           Technique technique) {
    int i = testClassFqns.indexOf(testClassFqn);
    int j = nonTestClassFqns.indexOf(nonTestClassFqn);
    int k = techniques.indexOf(technique);

    if (i == -1 || j == -1 || k == -1) {
      return 0.0;
    }

    int[] indices = new int[3];
    indices[0] = i;
    indices[1] = j;
    indices[2] = k;
    double score = scoresTensor_S.getDouble(indices);
    return score;
  }

  public Map<String, Double> getScoresForTestClassForTechnique(String testClassFqn,
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
  }

  public double getPercentileValueForTechnique(double percentile, Technique technique) {
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
  }

  protected void normaliseWithinTestClassByTechnique() {
    for (String testClassFqn : testClassFqns) {
      int i = testClassFqns.indexOf(testClassFqn);
      for (Technique technique : techniques) {
        if (!Arrays.asList(config.getTechniquesToNormalize()).contains(technique)) {
          continue;
        }

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

  protected void normaliseAllWithinTestClassByTechnique() {
    for (String testClassFqn : testClassFqns) {
      int i = testClassFqns.indexOf(testClassFqn);
      for (Technique technique : techniques) {
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

  public INDArray getScoresTensor_S() {
    return scoresTensor_S;
  }

  public boolean isNormalise() {
    return normalise;
  }

  public ArrayList<String> getTestClassFqns() {
    return testClassFqns;
  }

  public ArrayList<String> getNonTestClassFqns() {
    return nonTestClassFqns;
  }

  public ArrayList<Technique> getTechniques() {
    return techniques;
  }
}
