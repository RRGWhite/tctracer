package ctt.types.scores.method;

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
public class PureMethodScoresTensor extends MethodScoresTensor {

  public PureMethodScoresTensor(Configuration config,
                                Table<String, String, Map<Technique, Double>> relevanceTable,
                                boolean normalisedWithinTestByTechnique) {
    Utilities.logger.info("Constructing pure method scores tensor");
    this.config = config;
    this.normalise = normalisedWithinTestByTechnique;

    testsFqns = new ArrayList<>();
    //testFqnIndexes = new HashMap<>();
    functionFqns = new ArrayList<>();
    //functionFqnIndexes = new HashMap<>();
    techniques = new ArrayList<>(Arrays.asList(Utilities.getTechniques(config,
        Configuration.Level.METHOD, Main.ScoreType.PURE)));

    //int nextTestIdx = 0;
    //int nextFunctionIdx = 0;
    for (Table.Cell<String, String, Map<Technique, Double>> relevanceTableCell :
        relevanceTable.cellSet()) {
      String testFqn = relevanceTableCell.getRowKey();
      String functionFqn = relevanceTableCell.getColumnKey();

      if (!testsFqns.contains(testFqn)) {
        testsFqns.add(testFqn);
        //testFqnIndexes.put(testFqn, nextTestIdx++);
      }

      if (!functionFqns.contains(functionFqn)) {
        functionFqns.add(functionFqn);
        //functionFqnIndexes.put(functionFqn, nextFunctionIdx++);
      }
    }

    scoresTensor_S = Nd4j.zeros(testsFqns.size(), functionFqns.size(),
        techniques.size());

    for (Table.Cell<String, String, Map<Technique, Double>> relevanceTableCell :
        relevanceTable.cellSet()) {
      String testFqn = relevanceTableCell.getRowKey();
      String functionFqn = relevanceTableCell.getColumnKey();

      int i = testsFqns.indexOf(testFqn);
      int j = functionFqns.indexOf(functionFqn);
      for (Technique technique : techniques) {
        if (technique.equals(Technique.COMBINED)) {
          continue;
        }

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

    if (normalisedWithinTestByTechnique) {
      normaliseWithinTestByTechnique();
    }

    addCombinedScore(relevanceTable);

    if (normalisedWithinTestByTechnique) {
      normaliseCombinedScoreWithinTest();
    }

    Utilities.logger.info("Pure method scores tensor constructed");
  }

  private void addCombinedScore(Table<String, String, Map<Technique, Double>> relevanceTable) {
    INDArray newScoresTensor_S = Nd4j.zeros(testsFqns.size(), functionFqns.size(),
        Utilities.getTechniques(config, Configuration.Level.METHOD, Main.ScoreType.PURE).length);

    for (Technique technique : techniques) {
      INDArrayIndex[] indices = new INDArrayIndex[3];
      indices[0] = NDArrayIndex.all();
      indices[1] = NDArrayIndex.all();
      indices[2] = NDArrayIndex.point(techniques.indexOf(technique));
      newScoresTensor_S.put(indices, scoresTensor_S.get(indices));
    }

    INDArray scoresToCombine = scoresTensor_S.dup();

    if (config.isUseTechniqueWeightingForCombinedScore()) {
      INDArray weightVector = Nd4j.zeros(1, 1, techniques.size());
      for (Technique technique : techniques) {
        weightVector.putScalar(techniques.indexOf(technique),
            config.getMethodLevelTechniqueWeights().get(technique));
      }

      /*INDArray weightMatrx = weightVector;
      for (String testClassFqn : testsFqns) {
        weightMatrx = Nd4j.concat(0, weightMatrx, weightVector);
      }

      INDArray weightTensor = weightMatrx;
      for (String functionFqn : functionFqns) {
        weightTensor = Nd4j.concat(1, weightTensor, weightMatrx);
      }

      //INDArray unweightedScoresTensor = scoresToCombine.dup();
      INDArray zeroReplacementsTensor =
          Nd4j.zeros(scoresToCombine.shape()).addi(config.getWeightingZeroPenalty());
      INDArray preparedUnweightedScoresTensor =
          scoresToCombine.replaceWhere(zeroReplacementsTensor,
              new LessThanOrEqual(0));
      scoresToCombine = preparedUnweightedScoresTensor.mul(weightTensor);*/

      /*for (String testClassFqn : testsFqns) {
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
      }*/

      for (Table.Cell<String, String, Map<Technique, Double>> relevanceTableCell :
          relevanceTable.cellSet()) {
        String testFqn = relevanceTableCell.getRowKey();
        String functionFqn = relevanceTableCell.getColumnKey();
        int i = testsFqns.indexOf(testFqn);
        int j = functionFqns.indexOf(functionFqn
        );
        INDArrayIndex[] indices = new INDArrayIndex[3];
        indices[0] = NDArrayIndex.point(i);
        indices[1] = NDArrayIndex.point(j);
        indices[2] = NDArrayIndex.all();
        INDArray unweightedScoresVector = scoresToCombine.get(indices);
       /*INDArray zeroReplacementsvector =
            Nd4j.zeros(unweightedScoresVector.shape()).addi(config.getWeightingZeroPenalty());
        INDArray preparedUnweightedScoresVector =
            unweightedScoresVector.replaceWhere(zeroReplacementsvector,
                new LessThanOrEqual(0));
        INDArray weightedScoresVector = preparedUnweightedScoresVector.mul(weightVector);*/
        INDArray weightedScoresVector = unweightedScoresVector.mul(weightVector);
        scoresToCombine.put(indices, weightedScoresVector);
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

    INDArrayIndex[] methodLevelCombinedIndices = new INDArrayIndex[3];
    methodLevelCombinedIndices[0] = NDArrayIndex.all();
    methodLevelCombinedIndices[1] = NDArrayIndex.all();
    methodLevelCombinedIndices[2] = NDArrayIndex.point(techniques.indexOf(Technique.COMBINED));
    newScoresTensor_S.put(methodLevelCombinedIndices, combinedTechniquesMatrix);

    scoresTensor_S = newScoresTensor_S;
  }

  private void normaliseCombinedScoreWithinTest() {
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
