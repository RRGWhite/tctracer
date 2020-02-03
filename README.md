



## TCtracer
**TCtracer** is a tool that enables the establishment of traceability links between JUnit tests and test classes and their tested functions and classes.

WARNING: THIS TOOL IS CURRENTLY ENGINEERED FOR RUNNING EXPERIMENTS AND IS NOT PRODUCTION READY. IT HAS NO EASE OF USE NICETIES - EVERYTHING IS CONFIGURED DIRECTLY IN THE CODE. 

### Usage
The basic sequence for using **TCtracer** is as follows:

 1. Use the instrumenting agent **TCagent** to gather dynamic trace information from the execution of the test suite
 2. Configure **TCtracer** with the name and location of the system-under-analysis (SUA)
 3. Run **TCtracer** 
 4. Collect the predicted links from the produced CSV files

#### 1). Gathering Dynamic Trace Information
**TCtracer** requires dynamic information gathered by an instrumenting agent, **TCagent**, from a complete run of the test suite of the SUA.
Therefore, in order to use **TCtracer** one must first execute the test suite, passing the **TCagent** jar to the JVM via the -javaagent command line parameter. The **TCagent** jar can be found at: *ctt-agent-p\build\libs\TCagent.jar*.

**TCagent** will create a folder in the base folder of the SUA called *ctt_logs* containing the dynamic information.

#### 2). Configuring TCtracer
**TCtracer** must be configured with the name and location of the SUA. This is done in the source file Configuration.java by adding the name and location of the SUA to the *projectBaseDirs* map:

```
private String corpusPath = "../corpus";

private Map<String, String> projectBaseDirs = Maps
    .newHashMap(ImmutableMap.<String, String>builder()
        .put("commons-lang", corpusPath + "/commons-lang")
        .build()
    );
```
The SUA now needs to be added to the projects list at the start of the *main* method:

```
ArrayList<String> projects = new ArrayList<>();
projects.add("commons-lang");
```

The first time that **TCtracer** is run after the dynamic information has been collected, it needs to process the raw logs into hit spectra. This is controlled by setting *setParseCttLogs* to true when constructing the configuration object in the *singleRun* method in the *Main* class, e.g.:
```
Configuration config = new Configuration.Builder()
    .setProjects(Arrays.asList(project))
    .setIsSingleProject(true)
    .setParseCttLogs(false)
    .build();
```
One the logs are parsed a folder in the base folder of the SUA called *ctt_spectrum* will be created containing the hit spectra. Once this exists *setParseCttLogs* can be set to false.

#### 3). Running TCtracer
As everything is currently configured in the code, it is sensible to just run it from IntelliJ IDEA, however, it can also be run from the command line with a runnable jar.

When running on large projects it is sensible to give the VM as much RAM as possible using the *-Xmx* JVM argument, e.g.: *-Xmx22G*

#### 4). Collecting Predicted Links

The predicted links are output as CSV files and are split by level (method level, class level) as well as technique (see the paper for a list of techniques and the corresponding RQs).

The predicted method level links are in *results/test-to-function* and the predicted class level links are in *results/class-to-class*

