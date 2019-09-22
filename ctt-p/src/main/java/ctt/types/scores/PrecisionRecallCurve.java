package ctt.types.scores;

import ctt.types.PrecisionRecallPair;
import ctt.types.Technique;

import java.util.ArrayList;

/**
 * Created by RRGWhite on 08/08/2019
 */
public class PrecisionRecallCurve {
  private Technique technique;
  private ArrayList<PrecisionRecallPair> prPoints;
  private int totalTruePositives;
  private int totalTrueNegatives;

  public PrecisionRecallCurve(Technique technique, ArrayList<PrecisionRecallPair> prPoints, int totalTruePositives, int totalTrueNegatives) {
    this.technique = technique;
    this.prPoints = prPoints;
    this.totalTruePositives = totalTruePositives;
    this.totalTrueNegatives = totalTrueNegatives;
  }

  public Technique getTechnique() {
    return technique;
  }

  public ArrayList<PrecisionRecallPair> getPrPoints() {
    return prPoints;
  }

  public int getTotalTruePositives() {
    return totalTruePositives;
  }

  public int getTotalTrueNegatives() {
    return totalTrueNegatives;
  }
}
