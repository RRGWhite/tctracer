package ctt.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.annotation.Annotation;
import java.lang.instrument.Instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class CTTAgent {

    // Configuration
    public static final boolean CONFIG_ONLY_LOG_ANNOTATED = false; // Only log tests annotated with ground truth.

    public static final String JUNIT_3_REGEX = "junit.*"; // JUnit 3
    public static final String JUNIT_4_5_REGEX = "org\\.junit\\..+"; // JUnit 4 and 5

    public static final String JUNIT_3_TEST_ANNOTATION = "junit.framework.TestCase"; // JUnit 3
    public static final String JUNIT_4_TEST_ANNOTATION = "org.junit.Test"; // JUnit 4
    public static final String JUNIT_5_TEST_ANNOTATION = "org.junit.jupiter.api.Test"; // JUnit 5

    public static final String CTT_TRACE_ANNOTATION = "ctt.annotations.CTTTrace";

    public static Class<?> JUNIT_3_TESTCASE_CLASS = null;
    public static Class<Annotation> JUNIT_4_TEST_ANNOTATION_CLASS = null;
    public static Class<Annotation> JUNIT_5_TEST_ANNOTATION_CLASS = null;
    public static Class<Annotation> CTT_TRACE_ANNOTATION_CLASS = null;

    private enum ClassMatchMode { REGEX, PREFIX }
    private static ClassMatchMode classMatchMode = ClassMatchMode.REGEX;
    private static String classPattern = null; // e.g. Regex: org\.apache\..+ or Prefix: org.apache.

    public static void premain(String args, Instrumentation instrumentation) {
        System.out.println("CTTAgent v1.0");
        if (args != null) {
            String[] argsArr = args.split(","); // arguments delimited by ','
            for (String arg : argsArr) {
                String[] argSplit = arg.split("="); // key=value pairs delimited by '='
                if (argSplit.length == 2) {
                    String key = argSplit[0].trim();
                    String value = argSplit[1].trim();
                    switch (key.toLowerCase()) {
                        case "classregex":
                            classMatchMode = ClassMatchMode.REGEX;
                            classPattern = value;
                            break;
                        case "classprefix":
                            classMatchMode = ClassMatchMode.PREFIX;
                            classPattern = value;
                            break;
                    }
                }
            }
        }

        System.out.printf("Instrumenting classes: %s %n", classPattern == null ? "<NONE>" : classPattern);
        System.out.println();

        // Initialize CTT logger
        try {
            CTTLogger.init("ctt_logs");
            CTTLogger.switchFile("init");
        } catch (Exception e) {
            System.err.println("WARNING: Failed to initialize logger.");
        }

        // Register shutdown hook to commit logs to disk
        // https://docs.oracle.com/javase/7/docs/api/java/lang/Runtime.html#addShutdownHook(java.lang.Thread)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            CTTLogger.close();
        }));

        CTTLogger.print("Hello from CTTAgent v1.0\n");

        ElementMatcher.Junction<NamedElement> classMatcher = nameMatches(classPattern); // regex
        if (classMatchMode == ClassMatchMode.PREFIX) {
            classMatcher = nameStartsWith(classPattern);
        }

        // Note: Advices installed later run first (TBC)
        new AgentBuilder.Default()
            // .with(AgentBuilder.Listener.StreamWriting.toSystemOut()) // for debugging
            .type(classMatcher) // class to match
            .transform((builder, typeDescription, classLoader, module) -> builder
                // Methods
                .visit(Advice.to(CodeAdvice.MethodAdvice.class).on(
                    not(isTypeInitializer())
                    .and(not(isConstructor()))
                ))
                // Constructors
                .visit(Advice.to(CodeAdvice.CtorAdvice.class).on(
                    not(isTypeInitializer())
                    .and(isConstructor())
                ))
            )
            .installOn(instrumentation);

        // Code executed by the test framework
        new AgentBuilder.Default()
            .type(nameMatches(JUNIT_4_5_REGEX).or(nameMatches(JUNIT_3_REGEX))) // class to match
            .transform((builder, typeDescription, classLoader, module) -> builder
                .visit(Advice.to(CodeAdvice.TestAdvice.class).on(
                    // not(isConstructor()) // DEBUG ONLY - display all methods from test framework
                    ElementMatchers.nameStartsWith("assert")
                    .or(ElementMatchers.named("fail"))
                ))
            )
            .installOn(instrumentation);

        populateAnnotationClasses();
    }

    // External class type information has been erased during compile-time
    @SuppressWarnings("unchecked")
    private static void populateAnnotationClasses() {
        boolean foundJUnitIdentifier = false;

        try {
            JUNIT_5_TEST_ANNOTATION_CLASS = (Class<Annotation>) Class.forName(CTTAgent.JUNIT_5_TEST_ANNOTATION);
            foundJUnitIdentifier = true;
        } catch (ClassNotFoundException e) {
            // JUnit 5 not found - continue looking for presence of JUnit 4 or 3.
        }

        try {
            JUNIT_4_TEST_ANNOTATION_CLASS = (Class<Annotation>) Class.forName(CTTAgent.JUNIT_4_TEST_ANNOTATION);
            foundJUnitIdentifier = true;
        } catch (ClassNotFoundException e) {
            // continue
        }

        try {
            JUNIT_3_TESTCASE_CLASS = Class.forName(CTTAgent.JUNIT_3_TEST_ANNOTATION);
            foundJUnitIdentifier = true;
        } catch (ClassNotFoundException e) {
            // continue
        }

        if (!foundJUnitIdentifier) {
            System.err.println("WARNING: Unable to find any of JUnit 3/4/5, test boundaries will not be logged!");
        }

        try {
            CTT_TRACE_ANNOTATION_CLASS = (Class<Annotation>) Class.forName(CTTAgent.CTT_TRACE_ANNOTATION);
        } catch (ClassNotFoundException e) {
            // Note: not a big deal that there is no ground truth data
            System.err.println("WARNING: Cannot find CTT Trace annotation class definition, ground truth data will not be logged");
        }
    }
}
