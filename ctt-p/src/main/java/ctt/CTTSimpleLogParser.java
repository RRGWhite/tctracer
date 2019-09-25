package ctt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import ctt.types.HitSpectrum;
import ctt.types.TestCollection;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

/**
 * Parses the 'raw' format which lists full names of method invocations on each line
 */
public class CTTSimpleLogParser {

  public static void parse(File logPath, File outputFile) throws IOException {
    System.out.println("[CTTSimpleLogParser] Parse start for file " + logPath);

    TestCollection collection = new TestCollection();
    HitSpectrum activeHS = null;
    int callDepthOnTestEntry = 0; // The call depth at the entry of a test. Typically 0.
    String lastTopLevelMethod = null; // last top-level method relative to a test (used to determine last-call-before-assert)

    BufferedReader br = new BufferedReader(new FileReader(logPath));

    String line;
    while ((line = br.readLine()) != null) {
      if (activeHS != null) {
        // Within test

        if (line.equals(">>> TEST END <<<")) {
          // End of test
          // Save test trace
          collection.tests.add(activeHS);
          activeHS = null;
        } else {
          // Method invocation
          int callDepth = getCallDepth(line) - callDepthOnTestEntry
              - 1; // Depth 0 == top level functions called by test
          assert callDepth >= 0;
          String trimmedLine = line.trim();

          if (trimmedLine.startsWith("[ASSERT]")) {
            if (lastTopLevelMethod != null) {
              activeHS.callsBeforeAssert.add(lastTopLevelMethod);
            }
          } else {
            // Exclude any methods that are within the test suite
            if (!trimmedLine.startsWith(activeHS.cls)) {
              int savedCallDepth = activeHS.hitSet.getOrDefault(trimmedLine, Integer.MAX_VALUE);
              if (callDepth < savedCallDepth) {
                activeHS.hitSet.put(trimmedLine, callDepth);
              }
            }

            if (callDepth == 0) {
              // Top level functions relative to the test
              lastTopLevelMethod = trimmedLine;
            }
          }
        }
      } else {
        // Outside test

        if (line.startsWith(">>> TEST START <<<")) {
          // New test entry
          // Start a new test trace
          activeHS = new HitSpectrum();

          String[] lineSplit = line.split("\\|");
          String groundTruthStr = lineSplit[1].trim();
          if (!groundTruthStr.isEmpty()) {
            String[] groundTruth = groundTruthStr.split("&");
            activeHS.groundTruth = new HashSet<>();
            activeHS.groundTruth.addAll(Arrays.asList(groundTruth));
          }

          line = br.readLine(); // read the next line containing the test class and name
          String funcname = line.trim();
          if (funcname.equals(">>> TEST START <<< |")) {
            continue;
          }

          if (funcname.lastIndexOf('.') > 0) {
            activeHS.cls = funcname.substring(0, funcname.lastIndexOf('.'));
          } else {
            activeHS.cls = "null";
          }

          activeHS.test = funcname.substring(funcname.lastIndexOf('.') + 1);
          callDepthOnTestEntry = getCallDepth(line);
        }
      }
    }

    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    FileWriter fileWriter = new FileWriter(outputFile);
    gson.toJson(collection, fileWriter);
    fileWriter.close();

    System.out.println("Parse finished");
  }

  private static int getCallDepth(String line) {
    int callDepth = 0;
    for (int i = 0; i < line.length(); i++) {
      if (line.charAt(i) == ' ') {
        callDepth++;
      } else {
        break; // bail once we see a non-space character
      }
    }
    return callDepth;
  }
}
