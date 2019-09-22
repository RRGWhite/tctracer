package ctt.types;

import ctt.Configuration;
import ctt.Main;
import ctt.Utilities;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.commons.text.similarity.LongestCommonSubsequence;
import org.apache.commons.text.similarity.SimilarityScore;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Created by RRGWhite on 02/07/2019
 */
public class TestClassToClassTensors {
  private String testClassFqn;
  private String nonTestClassFqn;
  private ArrayList<String> tests;
  private ArrayList<String> functions;
  private List<Technique> methodLevelTechniques;
  private INDArray fullScores_C;
  private INDArray testScoresSummed_sf;
  private INDArray summedMethodLevelScores_s;
  private Configuration config;

  public TestClassToClassTensors(Configuration config, String testClassFqn,
                                 String nonTestClassFqn,
                                 ArrayList<TestToFunctionScores> allTestToFunctionScores,
                                 Map<Technique, Double> classLevelScores) {
    this.config = config;
    if (testClassFqn == null
        || testClassFqn.equals("null")
        || nonTestClassFqn == null
        || nonTestClassFqn.equals("null")) {
      System.out.println("DEBUGGING NULL TEST OR FUNCTION FQN");
    }

    /*if (classLevelScores == null) {
      System.out.println("Class level scores are null");
    }*/

    //useClassLevelInformation = true;

    this.testClassFqn = testClassFqn;
    this.nonTestClassFqn = nonTestClassFqn;

    tests = new ArrayList<>();
    functions = new ArrayList<>();
    fillTechniqueArrays(config);

    for (TestToFunctionScores testToFunctionScores : allTestToFunctionScores) {
      if (testToFunctionScores.getTestFqn() == null
          || testToFunctionScores.getTestFqn().isEmpty()
          || testToFunctionScores.getFunctionFqn() == null
          || testToFunctionScores.getFunctionFqn().isEmpty()) {
        Utilities.logger.debug("DEUGGING NULL METHODS");
        continue;
      }

      if (!tests.contains(testToFunctionScores.getTestFqn())) {
        tests.add(testToFunctionScores.getTestFqn());
      }

      if (!functions.contains(testToFunctionScores.getFunctionFqn())) {
        functions.add(testToFunctionScores.getFunctionFqn());
      }

      for (Technique technique : testToFunctionScores.getScores().keySet()) {
        if (technique == null) {
          Utilities.logger.debug("DEUGGING NULL TECHNIQUE");
          continue;
        }

        /*if (!techniques.contains(technique)) {
          techniques.add(technique);
        }*/
      }
    }

    fullScores_C = Nd4j.zeros(tests.size(), functions.size(), methodLevelTechniques.size());
    for (TestToFunctionScores testToFunctionScores : allTestToFunctionScores) {
      int i = tests.indexOf(testToFunctionScores.getTestFqn());
      int j = functions.indexOf(testToFunctionScores.getFunctionFqn());
      for (Entry<Technique, Double> score : testToFunctionScores.getScores().entrySet()) {
        Technique technique = score.getKey();
        double scoreValue = score.getValue();
        int k = methodLevelTechniques.indexOf(technique);
        if (k != -1) {
          int[] indices = new int[3];
          indices[0] = i;
          indices[1] = j;
          indices[2] = k;
          fullScores_C.putScalar(indices, scoreValue);
        }
      }
    }

    //testScoresSummed_sf = fullScores_C.sum(0).div(tests.size());
    testScoresSummed_sf = fullScores_C.sum(0);

    /*testScoresSummed_sf = fullScores_C.sum(0);
    if (config.getScoreCombinationMethod().equals(Configuration.ScoreCombinationMethod.AVERAGE)) {
      testScoresSummed_sf = testScoresSummed_sf.div(tests.size());
    }*/

    //summedMethodLevelScores_s = testScoresSummed_sf.sum(0).div(functions.size());
    summedMethodLevelScores_s = testScoresSummed_sf.sum(0);

    /*summedMethodLevelScores_s = testScoresSummed_sf.sum(0);
    if (config.getScoreCombinationMethod().equals(Configuration.ScoreCombinationMethod.AVERAGE)) {
      summedMethodLevelScores_s = summedMethodLevelScores_s.div(functions.size());
    }*/

    /*if (useClassLevelInformation) {
      //integrateClassLevelInformation();
      //addClassLevelInformation();
      addScoresToSPrime(classLevelScores);
    } else {*/
      //methodAndClassLevelTechniqueScores_s_prime = summedMethodLevelScores_s.dup();
    //}
  }

  public double getTestToFunctionScore(String testFqn, String functionFqn, Technique technique) {
    int i = tests.indexOf(testFqn);
    int j = functions.indexOf(functionFqn);
    int k = methodLevelTechniques.indexOf(technique);

    if (i == -1 || j == -1 || k == -1) {
      return 0.0;
    }

    int[] indices = new int[3];
    indices[0] = i;
    indices[1] = j;
    indices[2] = k;
    double score = fullScores_C.getDouble(indices);
    return score;
  }

  public double getFunctionScore(String functionFqn, Technique technique) {
    int[] indices = new int[2];
    indices[0] = functions.indexOf(functionFqn);
    indices[1] = methodLevelTechniques.indexOf(technique);
    double score = testScoresSummed_sf.getDouble(indices);
    return score;
  }

  public double getMethodLevelScore(Technique technique) {
    int i = methodLevelTechniques.indexOf(technique);
    if (i == -1) {
      return 0.0;
    }

    int[] indices = new int[1];
    indices[0] = i;
    double score = summedMethodLevelScores_s.getDouble(indices);
    return score;
  }

  /*public double getClassLevelScore(Technique technique) {
    int i = classLevelTechniques.indexOf(technique);
    if (i == -1) {
      return 0.0;
    }

    int[] indices = new int[1];
    indices[0] = i;
    double score = methodAndClassLevelTechniqueScores_s_prime.getDouble(indices);
    return score;
  }*/

  private void fillTechniqueArrays(Configuration config) {
    methodLevelTechniques = Arrays.asList(Utilities.getTechniques(config, Configuration.Level.METHOD,
        Main.ScoreType.PURE));
  }


  public String getTestClassFqn() {
    return testClassFqn;
  }

  public String getNonTestClassFqn() {
    return nonTestClassFqn;
  }

  public ArrayList<String> getTests() {
    return tests;
  }

  public ArrayList<String> getFunctions() {
    return functions;
  }

  public List<Technique> getMethodLevelTechniques() {
    return methodLevelTechniques;
  }

  public INDArray getFullScores_C() {
    return fullScores_C;
  }

  public INDArray getTestScoresSummed_sf() {
    return testScoresSummed_sf;
  }

  public INDArray getSummedMethodLevelScores_s() {
    return summedMethodLevelScores_s;
  }
}
