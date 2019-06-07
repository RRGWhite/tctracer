package ctt.agent;

import ctt.agent.interop.RWUtilities;
import ctt.annotations.CTTTrace;
import ctt.annotations.TestedMethod;
import net.bytebuddy.asm.Advice;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Advice for the main program code.
 * Note: Functions must have public access modifier in order to be called by instrumented application code.
 */
public class CodeAdvice {
    public static ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0); // call depth

    public static ThreadLocal<String> classUnderTest = ThreadLocal.withInitial(() -> "");
    public static ThreadLocal<Executable> methodUnderTest = new ThreadLocal<>();

    // To get thread ID: Thread.currentThread().getId()

    // Returns the specified annotation from a method or constructor if it exists, null otherwise.
    public static Annotation getAnnotation(Executable executable, Class<Annotation> annotationClass) {
        if (annotationClass != null) {
            return executable.getAnnotation(annotationClass);
        }
        return null;
    }

    public static boolean isTestMethod(Method method) {
        // JUnit 5 Test
        if (getAnnotation(method, CTTAgent.JUNIT_5_TEST_ANNOTATION_CLASS) != null) {
            return true;
        }

        // JUnit 4 Test
        if (getAnnotation(method, CTTAgent.JUNIT_4_TEST_ANNOTATION_CLASS) != null) {
            return true;
        }

        // JUnit 3 Test
        if (CTTAgent.JUNIT_3_TESTCASE_CLASS != null) {
            // JUnit 3 test names must start with "test" and the test suite must extend junit.framework.TestCase.
            if (method.getName().startsWith("test")
                    && CTTAgent.JUNIT_3_TESTCASE_CLASS.isAssignableFrom(method.getDeclaringClass())) {
                return true;
            }
        }

        return false;
    }

    public static boolean isActive() {
        if (CTTAgent.CONFIG_ONLY_LOG_ANNOTATED && methodUnderTest.get() == null) {
            // If configured to only log methods with ground truth and we're not currently in any test, return false
            return false;
        } else {
            return true;
        }
    }

    public static String getGroundTruthString(Method method) {
        String groundTruthStr = "";

        CTTTrace groundTruthAnnotation = method.getAnnotation(CTTTrace.class);
        if (groundTruthAnnotation != null) {
            groundTruthStr = String.join(",", groundTruthAnnotation.value());
            System.out.println(">>> TEST HAS GROUND TRUTH DATA <<<");
            System.out.println(Arrays.toString(groundTruthAnnotation.value()));
        }

        List<String> rwGroundTruthList = new ArrayList<>();
        TestedMethod[] rwGroundTruthAnnotations = method.getAnnotationsByType(TestedMethod.class);
        if (rwGroundTruthAnnotations.length > 0) {
            System.out.println(">>> TEST HAS GROUND TRUTH DATA <<<");
            for (TestedMethod rwAnnotation : rwGroundTruthAnnotations) {
                String fqMethodName = rwAnnotation.fqMethodName();
                RWUtilities.RWMethod rwMethod = RWUtilities.parseRWMethodName(fqMethodName);

                // Debug: Display what was parsed from the annotation
                System.out.println(fqMethodName);
                // System.out.println(rwMethod.className + " " + rwMethod.methodName);
                // System.out.println(rwMethod.argTypes);

                // Find the true method
                try {
                    Class groundTruthClass = Class.forName(rwMethod.className);
                    Executable trueMethod = RWUtilities.getClassMethod(groundTruthClass, rwMethod.methodName, rwMethod.argTypes);

                    if (trueMethod != null) {
                        String methodStr = Utilities.getMethodString(trueMethod);
                        rwGroundTruthList.add(methodStr);
                        System.out.println("Found true method: " + methodStr);
                    } else {
                        System.err.printf("WARNING: Unable to resolve method %s from ground truth annotation %s. Ground truth will not be bound! %n", rwMethod.methodName, fqMethodName);
                    }

                } catch (ClassNotFoundException e) {
                    System.err.printf("WARNING: Unable to resolve class %s from ground truth annotation %s. Ground truth will not be bound! %n", rwMethod.className, fqMethodName);
                }
            }
            groundTruthStr = String.join("&", rwGroundTruthList);
        }
        return groundTruthStr;
    }

    // Note that not all actions are permitted in constructors.
    public static class CtorAdvice {
        @Advice.OnMethodEnter
        public static void onMethodEntry(@Advice.Origin Constructor method,
                                         @Advice.Origin("#t") String className,
                                         @Advice.Origin("#t.#m") String methodName,
                                         @Advice.Origin("#s") String methodSignature) {
            if (!isActive()) return;

            String indentation = String.join("", Collections.nCopies(depth.get(), " "));
            CTTLogger.println(indentation + Utilities.getMethodString(method));
        }
    }

    public static class MethodAdvice {
        // public static ThreadLocal<Integer> testEntryDepth = ThreadLocal.withInitial(() -> 0); // call depth

        @Advice.OnMethodEnter
        public static void onMethodEntry(@Advice.Origin Method method,
                                         @Advice.Origin("#t") String className
        ) {
            boolean isTest = isTestMethod(method);
            if (isTest) {
                String groundTruthStr = getGroundTruthString(method);

                if (CTTAgent.CONFIG_ONLY_LOG_ANNOTATED && groundTruthStr.length() == 0) {
                    // If configured to only log methods with ground truth and there isn't any, bail out.
                    return;
                }

                if (!classUnderTest.get().equals(className)) {
                    classUnderTest.set(className);
                    // System.out.println("New test suite entered: " + className);

                    // New test suite entered
                    CTTLogger.switchFile(className);
                }

                // testEntryDepth.set(depth.get());
                methodUnderTest.set(method);
                CTTLogger.printf(">>> TEST START <<< | %s%n", groundTruthStr);
            }

            if (!isActive()) return;

            String indentation = String.join("", Collections.nCopies(depth.get(), " "));
            CTTLogger.println(indentation + Utilities.getMethodString(method)); // method.toString()

            depth.set(depth.get() + 1);
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void onMethodExit(@Advice.Origin Method method) {
            if (CTTAgent.CONFIG_ONLY_LOG_ANNOTATED && methodUnderTest.get() == null) {
                // If configured to only log methods with ground truth and we're not currently in any test, bail out.
                return;
            }

            depth.set(depth.get() - 1);

            boolean isTest = isTestMethod(method);
            if (isTest) {
                methodUnderTest.set(null);
                CTTLogger.println(">>> TEST END <<<");
                // if (!depth.get().equals(testEntryDepth.get())) {
                //     CTTLogger.println("WARNING: Depth is not equal to entry depth at the end of a test, resetting to entry depth: " + testEntryDepth.get());
                //     depth.set(testEntryDepth.get());
                // }
            }
        }
    }

    /**
     * Advice for the test framework (JUnit)
     */
    public static class TestAdvice {
        public static ThreadLocal<Integer> testCallDepth = ThreadLocal.withInitial(() -> 0);

        @Advice.OnMethodEnter
        public static void onMethodEntry(@Advice.Origin("#t") String className, @Advice.Origin("#t.#m") String methodName) {
            if (!isActive()) return;

            int depth = CodeAdvice.depth.get() + testCallDepth.get();

            // Only print top-level calls of the test runner
            if (testCallDepth.get() == 0) {
                String indentation = String.join("", Collections.nCopies(depth, " "));
                CTTLogger.println(indentation + "[ASSERT] " + methodName);
            }

            testCallDepth.set(testCallDepth.get() + 1);
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void onMethodExit() {
            if (!isActive()) return;

            testCallDepth.set(testCallDepth.get() - 1);
        }
    }
}
