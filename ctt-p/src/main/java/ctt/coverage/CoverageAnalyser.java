package ctt.coverage;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import ctt.Utilities;
import org.apache.bcel.classfile.Utility;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static ctt.Utilities.createIfAbsent;

public class CoverageAnalyser {

  // NOTE: This entry point is for testing only! For normal invocation, use the static method CoverageAnalyser.analyseReports.
  public static void main(String[] args) throws Exception {
    System.out.println("CTT Coverage Analyser");
    String reportsDir = "ctt/output";
    Table<String, String, Map<CounterType, CoverageStat>> coverageData = CoverageAnalyser
        .analyseReports(Paths.get(reportsDir));
    // Place breakpoint here to inspect coverage data.
  }

  // Coverage Counters
  // https://www.eclemma.org/jacoco/trunk/doc/counters.html
  public enum CounterType {
    INSTRUCTION,
    BRANCH,
    LINE,
    COMPLEXITY,
    METHOD,
    CLASS
  }

  public static class CoverageStat {

    public int missed;
    public int covered;

    public CoverageStat(int missed, int covered) {
      this.missed = missed;
      this.covered = covered;
    }
  }

  // Note: testName and methodName can be reversed to obtain the opposite metric
  public static double getCoverageScore(
      Table<String, String, Map<CounterType, CoverageStat>> coverageData, String testName,
      String methodName) {
    Map<CounterType, CoverageStat> coverageStats = coverageData.get(testName, methodName);
    if (coverageStats != null) {
      CoverageStat stat = coverageStats.get(CounterType.INSTRUCTION);
      if (stat != null) {
        return (double) stat.covered / (stat.covered + stat.missed);
      } else {
        System.err.printf(
            "[Coverage] WARNING: Counter type not present for test %s and method %s. Returning 1.0 (no discount).%n",
            testName, methodName);
        return 1.0;
      }
    } else {
      // System.err.printf("[Coverage] No match for test %s and method %s.%n", testName, methodName);
    }
    return 0.0; // Not present in the coverage data.
  }

  // Analyses all reports in the given directory.
  // Report syntax: https://github.com/jacoco/jacoco/blob/master/org.jacoco.report/src/org/jacoco/report/xml/report.dtd
  // Returns coverage data table with keys: Test, Method, Map<CounterType, CoverageStat>
  public static Table<String, String, Map<CounterType, CoverageStat>> analyseReports(
      Path reportsDirPath)
      throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {
    Table<String, String, Map<CounterType, CoverageStat>> coverageData = HashBasedTable.create();

    File reportsDir = reportsDirPath.toFile();
    File[] reports = reportsDir.listFiles((dir, name) -> name.endsWith(".xml"));
    for (int m = 0; m < reports.length; m++) {
      File reportFile = reports[m];
      System.out
          .printf("[%d/%d] Parsing report %s %n", m + 1, reports.length, reportFile.getName());

      String unifiedTestName = reportFile.getName().replace('#', '.').replace(".xml", "") + "()";
      // System.out.println("Test: " + unifiedTestName);

      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",
          false); // disable loading dtd
      DocumentBuilder docBuilder = dbf.newDocumentBuilder();
      Document doc = docBuilder.parse(reportFile);
      doc.getDocumentElement().normalize();

      XPath xPath = XPathFactory.newInstance().newXPath();
      String startPath = "/report/package";

      NodeList packageList = (NodeList) xPath.compile(startPath)
          .evaluate(doc, XPathConstants.NODESET);
      // For each package
      for (int i = 0; i < packageList.getLength(); i++) {
        Node pkg = packageList.item(i);

        // For each class
        NodeList classList = pkg.getChildNodes();
        for (int j = 0; j < classList.getLength(); j++) {
          Node cls = classList.item(j);
          if (!cls.getNodeName().equals("class")) {
            continue;
          }

          String className = cls.getAttributes().getNamedItem("name").getNodeValue();

          // For each method
          NodeList methodList = cls.getChildNodes();
          for (int k = 0; k < methodList.getLength(); k++) {
            Node method = methodList.item(k);
            if (!method.getNodeName().equals("method")) {
              continue;
            }

            String methodName = method.getAttributes().getNamedItem("name").getNodeValue();
            String methodDesc = method.getAttributes().getNamedItem("desc").getNodeValue();
            String[] methodArgTypes = Utility.methodSignatureArgumentTypes(methodDesc, false);

            // String methodReturnType = Utility.methodSignatureReturnType(methodDesc);
            String canonicalClassName = className.replace('/', '.');
            String unifiedMethodName = String.format("%s.%s(%s)", canonicalClassName, methodName,
                String.join(", ", methodArgTypes));

            // System.out.println(unifiedMethodName);

            // For each counter
            NodeList counterList = method.getChildNodes();
            for (int c = 0; c < counterList.getLength(); c++) {
              Node counter = counterList.item(c);
              if (!counter.getNodeName().equals("counter")) {
                continue;
              }

              String counterType = counter.getAttributes().getNamedItem("type").getNodeValue();
              String counterMissed = counter.getAttributes().getNamedItem("missed").getNodeValue();
              String counterCovered = counter.getAttributes().getNamedItem("covered")
                  .getNodeValue();
              CoverageStat stat = new CoverageStat(Integer.parseInt(counterMissed),
                  Integer.parseInt(counterCovered));

              Map<CounterType, CoverageStat> counterMap = createIfAbsent(coverageData,
                  Utilities.removePackagesFromFqnParamTypes(unifiedTestName),
                  Utilities.removePackagesFromFqnParamTypes(unifiedMethodName),
                  HashMap::new);
              switch (counterType) {
                case "INSTRUCTION":
                  counterMap.put(CounterType.INSTRUCTION, stat);
                  break;
                case "BRANCH":
                  counterMap.put(CounterType.BRANCH, stat);
                  break;
                case "LINE":
                  counterMap.put(CounterType.LINE, stat);
                  break;
                case "COMPLEXITY":
                  counterMap.put(CounterType.COMPLEXITY, stat);
                  break;
                case "METHOD":
                  counterMap.put(CounterType.METHOD, stat);
                  break;
                case "CLASS":
                  counterMap.put(CounterType.CLASS, stat);
                  break;
              }
            }
          }
        }
      }
    }
    return coverageData;
  }
}
