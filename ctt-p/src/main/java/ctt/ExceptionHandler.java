package ctt;

public abstract class ExceptionHandler {

  public static void handleCaughtThrowable(Throwable e, boolean fatal) {
    String callerClassName = Thread.currentThread().getStackTrace()[2].getClassName();
    String callerMethodName = Thread.currentThread().getStackTrace()[2].getMethodName();
    System.out.println("Exception caught in " + callerClassName + "." + callerMethodName
        + ": " + e);
    e.printStackTrace();
    if (fatal) {
      System.exit(-1);
    }
  }
}
