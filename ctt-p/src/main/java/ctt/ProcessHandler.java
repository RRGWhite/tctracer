package ctt;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public abstract class ProcessHandler {

  private static boolean printProcessHandlerOutput = false;

  public static ArrayList<String> executeCommand(String commandString, File workingDirectory) {
    /*System.out.println("ProcessHandler executing command:\n" + commandString + "\nin "
        + "directory: " + workingDirectory.getAbsolutePath());*/

    String[] splitCommandString = commandString.split(" ");
    List<String> cmdAndArgs = new ArrayList<>();
    for (int i = 0; i < splitCommandString.length; ++i) {
      cmdAndArgs.add(splitCommandString[i]);
    }

    ProcessBuilder processBuilder = new ProcessBuilder(cmdAndArgs);
    processBuilder.directory(workingDirectory);

    ArrayList<String> stdOutLines = new ArrayList<>();
    ArrayList<String> stdErrLines = new ArrayList<>();
    ArrayList<String> allOutLines = new ArrayList<>();

    try {
      Process process = processBuilder.start();
      BufferedReader ibr = new BufferedReader(
          new InputStreamReader(process.getInputStream()));
      BufferedReader ebr = new BufferedReader(
          new InputStreamReader(process.getErrorStream()));

      String errorLine;
      while ((errorLine = ebr.readLine()) != null) {
        if (printProcessHandlerOutput) {
          System.out.println(errorLine);
        }

        stdErrLines.add(errorLine);
        allOutLines.add(errorLine);

        if (errorLine.contains("method toClass in class ClassUtils cannot")) {
          System.out.println("DEBUGGING NON-COMPILING TEST IN SUITE");
        }
      }

      String line;
      while ((line = ibr.readLine()) != null) {
        if (printProcessHandlerOutput) {
          System.out.println(line);
        }

        stdOutLines.add(line);
        allOutLines.add(line);

        if (line.contains("method toClass in class ClassUtils cannot")) {
          System.out.println("DEBUGGING NON-COMPILING TEST IN SUITE");
        }
      }
    } catch (Exception e) {
      Utilities.handleCaughtThrowable(e, false);
    }

    /*if (commandString.contains("compile")) {
      System.out.println("DEBUGGING COMPILE OUTPUT FROM PROCESSHANDLER");
    }*/

    return allOutLines;
  }
}
