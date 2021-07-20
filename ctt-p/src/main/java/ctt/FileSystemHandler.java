package ctt;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class FileSystemHandler {

  public static String normalisePathSeperators(String path) {
    return path.replace("\\", "/");
  }

  public static String getWorkingDirectory() {
    return System.getProperty("user.dir").replace("\\", "/");
  }

  public static ArrayList<File> fetchFilesFromDir(String dirPath) {
    ArrayList<File> files = new ArrayList<>();

    File dir = new File(dirPath);
    File[] allFsElements = dir.listFiles();
    if (allFsElements != null) {
      for (int i = 0; i < allFsElements.length; ++i) {
        if (allFsElements[i].isFile()) {
          files.add(allFsElements[i]);
        }
      }
    }

    return files;
  }

  public static void writeToFile(String dstFilePath, String strToWrite, boolean append) {
    if (dstFilePath == null) {
      Logger.get().logAndPrintLn("FileSystemHandler.writeToFile rejecting null destination file "
          + "path");
      return;
    }

    String[] splitDstFilePath = dstFilePath.replace("\\", "/").split("/");
    String[] splitNewDstFilePath = new String[splitDstFilePath.length - 1];
    for (int i = 0; i < splitNewDstFilePath.length; ++i) {
      splitNewDstFilePath[i] = splitDstFilePath[i];
    }

    String dstDirPath = String.join("/", splitNewDstFilePath);
    File dstDir = new File(dstDirPath);
    if (!dstDir.exists()) {
      if (!dstDir.mkdir()) {
        Logger.get().logAndPrintLn("Couldn't make dir " + dstDir.getName());
      }
    }

    BufferedWriter bw = null;
    FileWriter fw = null;

    try {
      fw = new FileWriter(dstFilePath, append);
      bw = new BufferedWriter(fw);
      bw.write(strToWrite);
    } catch (Exception e) {
      ExceptionHandler.handleCaughtThrowable(e, false);
    } finally {
      try {
        if (bw != null) {
          bw.close();
        }
        if (fw != null) {
          fw.close();
        }
      } catch (IOException e) {
        ExceptionHandler.handleCaughtThrowable(e, false);
      }
    }
  }

  public static BufferedReader getBufferedReader(String filepath) {
    if (filepath == null) {
      Logger.get().logAndPrintLn("filepath passed to FileSystemHandler.getBufferedReader is " +
          "invalid");
      return null;
    }

    BufferedReader br = null;
    try {
      br = new BufferedReader(new FileReader(new File(filepath)));
    } catch (FileNotFoundException e) {
      ExceptionHandler.handleCaughtThrowable(e, true);
    }
    return br;
  }

  public static BufferedWriter getBufferedWriter(String filepath, boolean append) {
    if (filepath == null) {
      Logger.get().logAndPrintLn("filepath passed to FileSystemHandler.getBufferedReader is " +
          "invalid");
      return null;
    }

    BufferedWriter bw = null;
    try {
      bw = new BufferedWriter(new FileWriter(new File(filepath), append));
    } catch (IOException e) {
      ExceptionHandler.handleCaughtThrowable(e, true);
    }
    return bw;
  }

  public static String readContentFromFile(String filePath) {
    String content = "";
    File file = new File(filePath);
    if (file.exists()) {
      try {
        List<String> file1Lines = Files.readAllLines(Paths.get(file.getPath()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < file1Lines.size(); ++i) {
          sb.append(file1Lines.get(i) + "\n");
        }
        content = sb.toString();
      } catch (IOException e) {
        ExceptionHandler.handleCaughtThrowable(e, false);
      }
    } else {
      Logger.get().logAndPrintLn(
          "File passed to FileSystemHandler.readContentFromFile does not exist");
    }
    return content;
  }

  public static ArrayList<String> readLinesFromFile(String filePath) {
    ArrayList<String> lines = null;
    File file = new File(filePath);
    if (file.exists()) {
      try {
        lines = (ArrayList<String>) Files.readAllLines(file.toPath());
      } catch (IOException e) {
        ExceptionHandler.handleCaughtThrowable(e, false);
      }
    } else {
      Logger.get().logAndPrintLn(
          "File passed to FileSystemHandler.readContentFromFile does not exist");
    }
    return lines;
  }

  public static void createDirStructureForFile(String path) {
    try {
      String[] splitDstFilePath = path.replace("\\", "/").split("/");
      String[] splitNewDstFilePath = new String[splitDstFilePath.length - 1];
      for (int i = 0; i < splitNewDstFilePath.length; ++i) {
        splitNewDstFilePath[i] = splitDstFilePath[i];
      }

      String dstDirPath = String.join("/", splitNewDstFilePath);
      File dstDir = new File(dstDirPath);
      if (!dstDir.exists()) {
        if (!dstDir.mkdirs()) {
          Logger.get().logAndPrintLn("Couldn't make dirs for path" + path);
        }
      }
    } catch (Exception e) {
      ExceptionHandler.handleCaughtThrowable(e, true);
    }
  }

  /**
   * Delete a specified folder
   *
   * @param location location of the folder
   */
  public static void recreateAFolder(String location) {
    try {
      File directory = new File(location);
      FileUtils.deleteDirectory(directory);
      if (!directory.mkdirs()) {
        Logger.get().logLn("Couldn't make dir " + directory.getName());
      }
    } catch (IOException e) {
      ExceptionHandler.handleCaughtThrowable(e, false);
    }
  }

  public static void serializeObject(Object object, File dstFile) {
    createDirStructureForFile(dstFile.getAbsolutePath());
    try {
      FileOutputStream fos = new FileOutputStream(dstFile.getPath());
      ObjectOutputStream oos = new ObjectOutputStream(fos);
      oos.writeObject(object);
      oos.close();
      fos.close();
      Logger.get().logLn("Object serialized to " + dstFile.getPath());
    } catch (IOException e) {
      ExceptionHandler.handleCaughtThrowable(e, false);
    }
  }

  public static Object deserializeObject(File srcFile) {
    Object object = null;
    try {
      FileInputStream fis = new FileInputStream(srcFile.getPath());
      ObjectInputStream ois = new ObjectInputStream(fis);
      object = ois.readObject();
      ois.close();
      fis.close();
      Logger.get().logLn("Object deserialized from " + srcFile.getPath());
    } catch (Exception e) {
      ExceptionHandler.handleCaughtThrowable(e, false);
    }
    return object;
  }

  public static String replaceDisallowedFileCharsWithPlaceholders(String name) {
    String replaced = name.replace("<", "~");
    replaced = replaced.replace(">", "@");
    replaced = replaced.replace("?", ";");
    return replaced;
  }

  public static String replacePlaceholdersWithAngleBraces(String name) {
    String replaced = name.replace("~", "<");
    replaced = replaced.replace("@", ">");
    replaced = replaced.replace(";", "?");
    return replaced;
  }

  public static String takePathUpALevel(String inPath) {
    if (inPath == null) {
      Logger.get().logAndPrintLn("FileSystemHandler.writeToFile rejecting null destination file "
          + "path");
      return null;
    }

    String[] splitInPath = inPath.replace("\\", "/").split("/");
    String[] splitOutPath = new String[splitInPath.length - 1];
    for (int i = 0; i < splitOutPath.length; ++i) {
      splitOutPath[i] = splitInPath[i];
    }

    return String.join("/", splitOutPath);
  }

  public static String getProjectNameFromDir(String projectDir) {
    String[] splitPath = projectDir.split("/");
    return splitPath[splitPath.length - 1];
  }

  public static String getFileNameFromPath(String path) {
    String[] splitFilePath = path.split("/");
    return splitFilePath[splitFilePath.length - 1];
  }

  public static File getMostRecentlyModifiedFileFromDir(File dir) {
    if (!dir.isDirectory()) {
      Logger.get().logAndPrintLn("FileSystemHandler.getMostRecentlyModifiedFileFromDir error: dir"
          + " is not a directory");
      return null;
    }

    File[] files = dir.listFiles();
    File latestModifiedFile = files[0];
    for (int i = 0; i < files.length; ++i) {
      if (files[i].lastModified() > latestModifiedFile.lastModified()) {
        latestModifiedFile = files[i];
      }
    }

    return latestModifiedFile;
  }

  public static void deleteUnneededFilesFromDir(File dir) {
    String[] usefulExtensions = {"java", "list", "set"};
    ArrayList<String> usefulExtensionsList = new ArrayList<>();

    for (int i = 0; i < usefulExtensions.length; ++i) {
      usefulExtensionsList.add(usefulExtensions[i]);
    }

    List<File> listOfUsefulFiles = (List<File>) FileUtils.listFiles(dir, usefulExtensions, true);
    List<File> listOfAllFiles = (List<File>) FileUtils.listFiles(dir, null, true);

    if ((listOfAllFiles.size() - listOfUsefulFiles.size()) > 0) {
      Iterator<File> iter = FileUtils.iterateFiles(dir,TrueFileFilter.TRUE,
        TrueFileFilter.INSTANCE);

      while (iter.hasNext()) {
        File file = iter.next();
        String fileName = file.toString();
        String[] splitName = fileName.split("\\.");
        String ext = splitName.length > 0 ? splitName[splitName.length - 1] : "";
        if (!usefulExtensionsList.contains(ext)) {
          //Logger.get().logAndPrintLn("Deleting file: " + file.getPath());
          file.delete();
        }
      }

      List<File> listOfAllFilesAndDirs = (List<File>) FileUtils.listFilesAndDirs(dir, TrueFileFilter.TRUE,
          TrueFileFilter.INSTANCE);
      for (File element : listOfAllFilesAndDirs) {
        if (element.isDirectory()) {
          if (FileUtils.listFiles(element, null, true).size() == 0) {
            //Logger.get().logAndPrintLn("Deleting dir: " + element.getPath());
            element.delete();
          }
        }
      }
    }
  }

  public static String makePathSafeForFilename(String directoryPath, String path) {
    int maxTotalPathLength = 100;
    String safePath = path.replace("\\", "-")
        .replace("/", "-")
        .replace(":", "_")
        .split("\\.")[0];

    String candidateResultingPath = directoryPath + "/" + safePath;
    if (candidateResultingPath.length() > maxTotalPathLength) {
      int availableChars = maxTotalPathLength - directoryPath.length();
      safePath = safePath.substring(safePath.length() - availableChars);
    }

    return safePath;
  }
}
