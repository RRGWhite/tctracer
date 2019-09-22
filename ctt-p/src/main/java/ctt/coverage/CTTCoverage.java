package ctt.coverage;

import com.google.gson.Gson;
import ctt.types.HitSpectrum;
import ctt.types.TestCollection;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Script to collect granular test coverage for each test for use with coverage scaling.
 *
 * Create ctt/jacoco_out, ctt/classes and ctt/output directories first. Coverage data only needs to
 * be generated once per project.
 *
 * The key function in this class is runOn, which takes a list of test names, project base
 * directory, class filter (directory format), class files path relative to base directory, and
 * source files path relative to base directory. The runOn* functions feed appropriate parameters to
 * the runOn function for a particular project.
 */
public class CTTCoverage {

  // Configuration - set paths here
  public static final String MAVEN_PATH = "C:\\dev\\Java\\apache-maven-3.6.0\\bin\\mvn.cmd";
  public static final String JACOCO_AGENT_PATH = "C:\\dev\\Java\\jacoco-0.8.3\\lib\\jacocoagent.jar";
  public static final String JACOCO_CLI_PATH = "C:\\dev\\Java\\jacoco-0.8.3\\lib\\jacococli.jar";

  public static void main(String[] args) throws Exception {
    System.out.println("CTTCoverage");

    // Obtain list of tests from hit spectrum.
    String inputDirectory = "c:\\dev\\git\\commons-io\\ctt_spectrum\\";
    File inputDir = new File(inputDirectory);
    File[] files = inputDir.listFiles();
    List<HitSpectrum> hitSpectra = new ArrayList<>();
    for (int i = 0; i < files.length; i++) {
      File file = files[i];
      if (file.isFile()) {
        System.out.printf("[%d/%d] Parsing %s %n", i + 1, files.length, file);

        BufferedReader br = new BufferedReader(new FileReader(file));

        Gson gson = new Gson();
        TestCollection testCollection = gson.fromJson(br, TestCollection.class);
        hitSpectra.addAll(testCollection.tests);
      }
    }
    List<String> testNames = hitSpectra.stream()
        .map(hs -> hs.cls + "#" + hs.test.substring(0, hs.test.lastIndexOf('(')))
        .collect(Collectors.toList());

    // runOnCommonsLang(testNames);
    // runOnJFreeCharts(testNames);
    runOnCommonsIO(testNames);
  }

  public static void runOnCommonsLang(List<String> testNames) throws Exception {
    String baseDir = "C:\\Users\\Raymond\\Downloads\\t2c-trace-ground-truth-corpus\\clang\\";
    String filterStr = "org/apache/commons/";
    String relativeClassFilesPath = "target/classes";
    String relativeSourceFilesPath = "src/main/java";
    runOn(testNames, baseDir, filterStr, relativeClassFilesPath, relativeSourceFilesPath);
  }

  public static void runOnJFreeCharts(List<String> testNames) throws Exception {
    String baseDir = "c:\\Users\\Raymond\\Downloads\\t2c-trace-ground-truth-corpus\\jfreechart\\";
    String filterStr = "org/jfree/";
    String relativeClassFilesPath = "target/classes";
    String relativeSourceFilesPath = "source";
    runOn(testNames, baseDir, filterStr, relativeClassFilesPath, relativeSourceFilesPath);
  }

  public static void runOnCommonsIO(List<String> testNames) throws Exception {
    String baseDir = "c:\\dev\\git\\commons-io\\";
    String filterStr = "org/apache/commons/";
    String relativeClassFilesPath = "target/classes";
    String relativeSourceFilesPath = "src/main/java";
    runOn(testNames, baseDir, filterStr, relativeClassFilesPath, relativeSourceFilesPath);
  }

  public static void runOn(List<String> testNames, String baseDir, String filterStr,
      String relativeClassFilesPath, String relativeSourceFilesPath) throws Exception {
    for (String testName : testNames) {
      String execFile = run(baseDir, testName);
      List<String> relevantClasses = parse(baseDir, execFile, filterStr);
      String filteredClassPath = copyClassesToTempDir(baseDir, testName, relevantClasses,
          relativeClassFilesPath).toString();
      exportReport(baseDir, execFile, filteredClassPath, relativeSourceFilesPath, testName);
    }
  }

  // Executes a single test run
  // Returns the relative path to the generated exec file.
  public static String run(String workingDirectoryPath, String testCase) throws Exception {
    File workingDirectory = new File(workingDirectoryPath);
    if (!workingDirectory.exists()) {
      throw new Exception("Invalid directory");
    }

    String destinationExecFile = String.format("ctt/jacoco_out/%s.exec", testCase);
    String jacocoAgentArgLine = String
        .format("%s=append=false,destfile=%s", JACOCO_AGENT_PATH, destinationExecFile);

    String commandLine = String
        .format("%s surefire:test -Dtest=%s -DargLine=\"-javaagent:%s\"", MAVEN_PATH, testCase,
            jacocoAgentArgLine);
    System.out.println("Command line is: " + commandLine);

    Process p = Runtime.getRuntime().exec(commandLine, null, workingDirectory);

    // Display output
    BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
    String line;
    while ((line = input.readLine()) != null) {
      System.out.println(line);
    }

    return destinationExecFile;
  }

  // Parses a jacoco exec file and returns a list of classes that match the given filter
  // e.g. [org/apache/commons/lang3/ArrayUtils, ...]
  public static List<String> parse(String workingDirectoryPath, String jacocoExecPath,
      String filterStr) {
    List<String> output = new ArrayList<>();
    String commandLine = String.format("java -jar %s execinfo %s", JACOCO_CLI_PATH, jacocoExecPath);

    try {
      Process p = Runtime.getRuntime().exec(commandLine, null, new File(workingDirectoryPath));
      // Display output
      BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
      String line;
      while ((line = input.readLine()) != null) {
        if (line.contains(filterStr)) {
          int idx_space = line.lastIndexOf(' ');
          String className = line.substring(idx_space + 1);
          output.add(className);
          System.out.println(className);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    return output;
  }

  // Copies relevant classes to temp directory (ctt/classes)
  public static Path copyClassesToTempDir(String baseDir, String testName, List<String> classList,
      String relativeClassFilesPath) throws IOException {
    Path classFilesPath = Paths.get(baseDir, relativeClassFilesPath);
    Path destDir = Paths.get(baseDir, "ctt/classes", testName);

    for (String classPath : classList) {
      Path sourceFile = classFilesPath.resolve(classPath + ".class");
      if (Files.exists(sourceFile)) {
        Path dest = destDir.resolve(classFilesPath.relativize(sourceFile));
        Files.createDirectories(dest.getParent());

        try {
          Files.copy(sourceFile, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }

    return destDir;
  }

  // Exports report
  public static void exportReport(String workingDirectoryPath, String jacocoExecPath,
      String filteredClassFilesPath, String sourceFilesPath, String testName) throws Exception {
    File workingDirectory = new File(workingDirectoryPath);
    if (!workingDirectory.exists()) {
      throw new Exception("Invalid directory");
    }

    Files.createDirectories(Paths.get(workingDirectoryPath, "ctt/output"));

    String commandLine = String.format(
        "java -jar \"%s\" report \"%s\" --classfiles \"%s\" --sourcefiles \"%s\" --html \"ctt/output/%s\" --xml \"ctt/output/%s.xml\"",
        JACOCO_CLI_PATH, jacocoExecPath, filteredClassFilesPath, sourceFilesPath, testName,
        testName);
    System.out.println("Command line is: " + commandLine);

    try {
      Process p = Runtime.getRuntime().exec(commandLine, null, workingDirectory);

      // Display output
      BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
      String line;
      while ((line = input.readLine()) != null) {
        System.out.println(line);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
