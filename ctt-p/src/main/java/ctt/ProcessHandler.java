package ctt;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public abstract class ProcessHandler {

  private static boolean printProcessHandlerOutput = false;

  /*public static ArrayList<String> executeCommand(String commandString, File workingDirectory) {
    System.out.println("ProcessHandler executing command:\n" + commandString + "\nin "
        + "directory: " + workingDirectory.getAbsolutePath());

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
      }

      String line;
      while ((line = ibr.readLine()) != null) {
        if (printProcessHandlerOutput) {
          System.out.println(line);
        }

        stdOutLines.add(line);
        allOutLines.add(line);
      }
    } catch (Exception e) {
      Utilities.handleCaughtThrowable(e, false);
    }

    return allOutLines;
  }*/

  public static ArrayList<String> executeCommand(String commandString, File workingDirectory,
                                                 File outputFile) {
    /*System.out.println("ProcessHandler executing command:\n" + commandString + "\nin "
        + "directory: " + workingDirectory.getAbsolutePath());*/

    if (!outputFile.exists()) {
      FileSystemHandler.createDirStructureForFile(outputFile.getPath());
      try {
        outputFile.createNewFile();
      } catch (IOException e) {
        ExceptionHandler.handleCaughtThrowable(e, false);
      }
    }

    FileSystemHandler.writeToFile(outputFile.getPath(), "", false);

    String[] splitCommandString = commandString.split(" ");
    List<String> cmdAndArgs = new ArrayList<>();
    for (int i = 0; i < splitCommandString.length; ++i) {
      cmdAndArgs.add(splitCommandString[i]);
    }

    ProcessBuilder processBuilder = new ProcessBuilder(cmdAndArgs);
    processBuilder.directory(workingDirectory);

    try {
      processBuilder.redirectOutput(outputFile);
      processBuilder.redirectError(outputFile);
      processBuilder.redirectInput(outputFile);
      processBuilder.start().waitFor();
    } catch (Exception e) {
      ExceptionHandler.handleCaughtThrowable(e, false);
    }

    if (printProcessHandlerOutput) {
      Logger.get().logAndPrintLn(FileSystemHandler.readContentFromFile(outputFile.getPath()));
    }

    ArrayList<String> allOutLines = FileSystemHandler.readLinesFromFile(outputFile.getPath());
    return allOutLines;
  }
}
