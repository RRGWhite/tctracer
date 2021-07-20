package ctt;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;

public class Logger implements Runnable {

  private enum LogType {LOG, LOG_AND_PRINT, LOG_LINE, LOG_AND_PRINT_LINE}

  private static Logger instance;
  private final String logFilePath;
  private final LinkedList<LogItem> queue;
  private boolean terminateWorkingOutputScheduled;
  private boolean terminateLogThreadScheduled;

  private Logger() {
    String logDir = "log";
    String logFile = "relatest.log";
    logFilePath = logDir + "/" + logFile;

    File dstDir = new File(logDir);
    if (!dstDir.exists()) {
      if (!dstDir.mkdir()) {
        Logger.get().logAndPrintLn("Couldn't make dir " + dstDir.getName());
      }
    }

    queue = new LinkedList<>();
    terminateWorkingOutputScheduled = false;
    terminateLogThreadScheduled = false;
  }

  public static Logger get() {
    if (instance == null) {
      instance = new Logger();
      new Thread(instance).start();
    }
    return instance;
  }

  public void log(String str) {
    synchronized (queue) {
      queue.add(new LogItem(str, LogType.LOG));
    }
  }

  public void logAndPrint(String str) {
    synchronized (queue) {
      queue.add(new LogItem(str, LogType.LOG_AND_PRINT));
    }
  }

  public void logLn(String str) {
    synchronized (queue) {
      queue.add(new LogItem(str, LogType.LOG_LINE));
    }
  }

  public void logAndPrintLn(String str) {
    synchronized (queue) {
      queue.add(new LogItem(str, LogType.LOG_AND_PRINT_LINE));
    }
  }

  public void startShowingWorkingOutput() {
    terminateWorkingOutputScheduled = false;
    new Thread(() -> {
      long i = 0;
      while (!terminateWorkingOutputScheduled) {
        logAndPrint(".");
        if (++i % 100 == 0) {
          logAndPrint("\n");
        }
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          ExceptionHandler.handleCaughtThrowable(e, false);
        }
      }
    }).start();
  }

  public void stopShowingWorkingOutput() {
    terminateWorkingOutputScheduled = true;
  }

  public String getCurrentTimestamp() {
    return new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
  }

  public void setTerminateLogThreadScheduled(boolean terminateLogThreadScheduled) {
    this.terminateLogThreadScheduled = terminateLogThreadScheduled;
  }

  @Override
  public void run() {
    while (true) {
      LogItem currentItem = null;
      synchronized (queue) {
        if (queue.size() > 0) {
          currentItem = queue.removeFirst();
        } else if (terminateLogThreadScheduled) {
          break;
        }
      }

      if (currentItem != null) {
        switch (currentItem.getType()) {
          case LOG:
            FileSystemHandler.writeToFile(logFilePath, getCurrentTimestamp() + ": "
                + currentItem.getLogStr(), true);
            break;
          case LOG_AND_PRINT:
            FileSystemHandler.writeToFile(logFilePath, getCurrentTimestamp() + ": "
                + currentItem.getLogStr(), true);
            System.out.print(currentItem.getLogStr());
            break;
          case LOG_LINE:
            FileSystemHandler.writeToFile(logFilePath, getCurrentTimestamp() + ": "
                    + currentItem.getLogStr() + "\n",
                true);
            break;
          case LOG_AND_PRINT_LINE:
            FileSystemHandler.writeToFile(logFilePath, getCurrentTimestamp() + ": "
                    + currentItem.getLogStr() + "\n",
                true);
            System.out.println(currentItem.getLogStr());
            break;
          default:
            break;
        }
      }

      try {
        Thread.sleep(5);
      } catch (InterruptedException e) {
        ExceptionHandler.handleCaughtThrowable(e, false);
      }
    }
  }

  private class LogItem {

    private final String logStr;
    private final LogType type;

    private LogItem(String logStr, LogType type) {
      this.logStr = logStr;
      this.type = type;
    }

    public String getLogStr() {
      return logStr;
    }

    public LogType getType() {
      return type;
    }
  }
}
