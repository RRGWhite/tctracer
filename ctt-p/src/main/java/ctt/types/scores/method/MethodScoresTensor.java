package ctt.types.scores.method;

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
public abstract class MethodScoresTensor {
  protected INDArray scoresTensor_S;
  protected boolean normalise;
  protected ArrayList<String> testsFqns;
  //protected HashMap<String, Integer> testFqnIndexes;
  protected ArrayList<String> functionFqns;
  //protected HashMap<String, Integer> functionFqnIndexes;
  protected ArrayList<Technique> techniques;
  protected Configuration config;

  public double getSingleScoreForTestFunctionPair(String testFqn, String functionFqn,
                                                   Technique technique) {
    int i = testsFqns.indexOf(testFqn);
    int j = functionFqns.indexOf(functionFqn);
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

  public Map<Technique, Double> getAllScoresForTestFunctionPair(String testFqn,
                                                                String functionFqn) {
    int i = testsFqns.indexOf(testFqn);
    int j = functionFqns.indexOf(functionFqn);

    if (i == -1 || j == -1) {
      return new HashMap<>();
    }

    INDArrayIndex[] idxs = new INDArrayIndex[3];
    idxs[0] = NDArrayIndex.point(i);
    idxs[1] = NDArrayIndex.point(j);
    idxs[2] = NDArrayIndex.all();
    INDArray rawScores = scoresTensor_S.get(idxs);

    HashMap<Technique, Double> scores = new HashMap<>();
    for (Technique technique : techniques) {
      double score = rawScores.getDouble(techniques.indexOf(technique));
      scores.put(technique, score);
    }

    return scores;
  }

  public Map<String, Double> getScoresForTestForTechnique(String testFqn,
                                                          Technique technique) {
    int i = testsFqns.indexOf(testFqn);
    int k = techniques.indexOf(technique);

    if (i == -1 || k == -1) {
      return new HashMap<>();
    }

    HashMap<String, Double> nonTestClassToScore = new HashMap<>();
    for (String nonTestClass : functionFqns) {
      int[] idxs = new int[3];
      idxs[0] = i;
      idxs[1] = functionFqns.indexOf(nonTestClass);
      idxs[2] = k;
      double score = scoresTensor_S.getDouble(idxs);
      nonTestClassToScore.put(nonTestClass, score);
    }

    return nonTestClassToScore;
  }

  public double getPercentileValueForTechnique(double percentile, Technique technique) {
    int k = techniques.indexOf(technique);
    ArrayList<Double> vals = new ArrayList<>();
    for (String testClass : testsFqns) {
      for (String nonTestClass : functionFqns) {
        int[] idxs = new int[3];
        idxs[0] = testsFqns.indexOf(testClass);
        idxs[1] = functionFqns.indexOf(nonTestClass);
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

  protected void normaliseWithinTestByTechnique() {
    for (String testClassFqn : testsFqns) {
      int i = testsFqns.indexOf(testClassFqn);
      for (Technique technique : techniques) {
        if (!Arrays.asList(config.getTechniquesToNormalize()).contains(technique)) {
          continue;
        }

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

  protected void normaliseAllWithinTestByTechnique() {
    for (String testClassFqn : testsFqns) {
      int i = testsFqns.indexOf(testClassFqn);
      for (Technique technique : techniques) {
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

  public INDArray getScoresTensor_S() {
    return scoresTensor_S;
  }

  public boolean isNormalise() {
    return normalise;
  }

  public ArrayList<String> getTestsFqns() {
    return testsFqns;
  }

  public ArrayList<String> getFunctionFqns() {
    return functionFqns;
  }

  public ArrayList<Technique> getTechniques() {
    return techniques;
  }
}
