package ctt.metrics;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import ctt.Logger;
import ctt.SpectraParser;
import ctt.types.EvaluationMetrics;
import ctt.types.Technique;
import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.skb.interfaces.transformers.textformat.TextAlignment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by RRGWhite on 11/07/2019
 */
public class TechniqueMetricsProvider {

  public static Table<Technique, SpectraParser.Metric, Double> computeTechniqueMetrics(
      Table<Technique, String, EvaluationMetrics> metricTable) {
    // Keys: Technique, Metric, Value
    Table<Technique, SpectraParser.Metric, Double> techniqueMetrics = HashBasedTable.create();

    for (Map.Entry<Technique, Map<String, EvaluationMetrics>> techniqueEntry : metricTable.rowMap()
        .entrySet()) {
      int numTests = techniqueEntry.getValue().size();
      int totalTruePositives = 0, totalFalsePositives = 0, totalFalseNegatives = 0;
      double totalBpref = 0.0, totalAveragePrecision = 0.0;
      Technique technique = techniqueEntry.getKey();

      for (EvaluationMetrics evaluationMetrics : techniqueEntry.getValue().values()) {
        totalTruePositives += evaluationMetrics.truePositives;
        totalFalsePositives += evaluationMetrics.falsePositives;
        totalFalseNegatives += evaluationMetrics.falseNegatives;
        totalBpref += evaluationMetrics.getBpref();
        totalAveragePrecision += evaluationMetrics.getAveragePrecision();
      }

      /*Logger.get().logAndPrintLn("Technique: " + technique);
      Logger.get().logAndPrintLn("Total True Positives: " + totalTruePositives);
      Logger.get().logAndPrintLn("Total False Positives: " + totalFalsePositives);
      Logger.get().logAndPrintLn("Total False Negatives: " + totalFalseNegatives);*/

      double precision = EvaluationMetrics
          .computePrecision(totalTruePositives, totalFalsePositives);
      double recall = EvaluationMetrics.computeRecall(totalTruePositives, totalFalseNegatives);
      double fScore = EvaluationMetrics.computeFScore(precision, recall);
      double meanAveragePrecision = totalAveragePrecision / numTests;
      double bpref = totalBpref / numTests;

      techniqueMetrics.put(technique, SpectraParser.Metric.PRECISION, precision);
      techniqueMetrics.put(technique, SpectraParser.Metric.RECALL, recall);
      techniqueMetrics.put(technique, SpectraParser.Metric.F_SCORE, fScore);
      techniqueMetrics.put(technique, SpectraParser.Metric.MAP, meanAveragePrecision);
      techniqueMetrics.put(technique, SpectraParser.Metric.BPREF, bpref);
      techniqueMetrics.put(technique, SpectraParser.Metric.TRUE_POSITIVES,
          (double) totalTruePositives);
      techniqueMetrics.put(technique, SpectraParser.Metric.FALSE_POSITIVES,
          (double) totalFalsePositives);
      techniqueMetrics.put(technique, SpectraParser.Metric.FALSE_NEGATIVES,
          (double) totalFalseNegatives);

      // System.out.printf("%s:%n\tPrecision: %f | Recall: %f | F-Score: %f %n", technique.toString(), precision, recall, fScore);
    }
    return techniqueMetrics;
  }

  public static void printTechniqueMetrics(Technique[] techniqueOrder,
                                           Table<Technique,
                                               SpectraParser.Metric, Double> techniqueMetrics) {
    SpectraParser.Metric[] metricOrder = {
        SpectraParser.Metric.PRECISION,
        SpectraParser.Metric.RECALL,
        SpectraParser.Metric.MAP,
        SpectraParser.Metric.F_SCORE,
        SpectraParser.Metric.TRUE_POSITIVES,
        SpectraParser.Metric.FALSE_POSITIVES,
        SpectraParser.Metric.FALSE_NEGATIVES
        // Metric.BPREF,
    };

    AsciiTable at = new AsciiTable();
    at.addRule();
    at.addRow(Stream
        .concat(Stream.of("Technique \\ Metric"), Arrays.stream(metricOrder).map(
            SpectraParser.Metric::toString))
        .collect(Collectors.toList()));
    at.addRule();

    for (Technique technique : techniqueOrder) {
      List<String> rowStrings = new ArrayList<>();
      rowStrings.add(technique.toString());
      for (SpectraParser.Metric metric : metricOrder) {
        rowStrings.add(String.format("%.4f", techniqueMetrics.get(technique, metric)));
      }

      at.addRow(rowStrings);
      at.addRule();
    }

    at.setTextAlignment(TextAlignment.LEFT);
    String renderedTable = at.render(25 * metricOrder.length);
    System.out.println(renderedTable);
  }

  public static void printTechniqueMetricsData(Technique[] techniqueOrder,
                                               Table<Technique, SpectraParser.Metric, Double> techniqueMetrics) {
    SpectraParser.Metric[] metricOrder = {
        SpectraParser.Metric.PRECISION,
        SpectraParser.Metric.RECALL,
        SpectraParser.Metric.MAP,
        SpectraParser.Metric.F_SCORE,
        SpectraParser.Metric.TRUE_POSITIVES,
        SpectraParser.Metric.FALSE_POSITIVES,
        SpectraParser.Metric.FALSE_NEGATIVES
        // Metric.BPREF,
    };

    StringBuilder sb = new StringBuilder();

    System.out.printf("Metrics [%d]: %s%n", metricOrder.length, Arrays.asList(metricOrder));
    System.out.printf(
        "Techniques [%d]: %s%n", techniqueOrder.length, Arrays.asList(techniqueOrder));

    for (SpectraParser.Metric metric : metricOrder) {
      List<String> rowStrings = new ArrayList<>();
      for (Technique technique : techniqueOrder) {
        double value = techniqueMetrics.get(technique, metric);
        rowStrings.add(String.format("%.1f\\%%", value * 100));
      }
      sb.append(String.join(" & ", rowStrings));
      sb.append(" \\\\");
      sb.append(System.lineSeparator());
    }

    System.out.println(sb.toString());
  }
}
