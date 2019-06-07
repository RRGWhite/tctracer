package ctt.agent;

import java.io.*;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class CTTLogger {
    private static PrintWriter printWriter = null;
    private static String basePath = "";
    private static Set<String> seenFiles = new HashSet<>();

    public static void init(String basePath) throws Exception {
        if (printWriter == null) {
            CTTLogger.basePath = basePath;
            File dir = new File(basePath);
            if (!dir.exists()) dir.mkdirs();
        } else {
            throw new Exception("CTTLogger already initialized!");
        }
    }

    public static void switchFile(String newFilename) {
        if (printWriter != null) close();
        try {
            boolean seen = seenFiles.contains(newFilename); // if seen in this session, open the file in APPEND mode.
            FileWriter fileWriter = new FileWriter(Paths.get(basePath, newFilename + ".log").toString(), seen);
            printWriter = new PrintWriter(new BufferedWriter(fileWriter));
            if (!seen) {
                seenFiles.add(newFilename);
            }
        } catch (IOException e) {
            System.err.println("CTTLogger error - cannot open " + newFilename);
        }
    }

    public static void print(String msg) {
        printWriter.print(msg);
    }

    public static void println(String msg) {
        printWriter.println(msg);
    }

    public static void printf(String msg, Object... args) {
        printWriter.printf(msg, args);
    }

    public static void close() {
        printWriter.flush();
        printWriter.close();
    }
}
