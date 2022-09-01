# [CrashTracker](https://github.com/hanada31/CrashTracker)
Locating Framework-specific Crashing Faults with Compact and Explainable Candidate Set

Requirements：

1. Python 3+

2. Java 1.8+



build and run *CrashTracker* to analyze single apk/ class Folder: : 



```
mvn -f pom.xml clean package

cp target/CrashTracker.jar CrashTracker.jar 

java -jar CrashTracker.jar  -path apk// -name ICCBotBench.apk -androidJar lib//platforms -time 30 -maxPathNumber 100 -client CTGClient -outputDir results//output
```
or analyze apks under given folder with Python script:

```
python scripts/runCrashTracker-Apk.py  [apkPath] [resultPath] [target framework version] [strategy name]
[target framework version]: have a folder called Files/android[target framework version], which stores the framework code. E.g., "4.4", "6.0", or "no"
[strategy name]:  "NoCallFilter", "NoSourceType", "ExtendCGOnly",  "NoKeyAPI", "NoParaChain, "NoAppDataTrace", "NOParaChainANDDataTrace"or "no"
```



Usage of CrashTracker.jar:

```
java -jar CrashTracker.jar -h

usage: java -jar CrashTracker.jar [options] [-path] [-name] [-androidJar] [-outputDir][-client]
 
 -androidCGPath <arg>      -androidCGPath: Android CallGraph file
                           [optional.
 -androidJar <arg>         -androidJar: Set the path of android.jar.
 -client <arg>             -client ExceptionInfoClient: Extract exception information from Android framework.
                           CrashAnalysisClient: Analysis the crash information for an apk.
                           JarCrashAnalysisClient: Analysis the crash information for an third party SDK.
                           CallGraphClient: Output call graph files.
                           ManifestClient: Output manifest.xml file.
                           IROutputClient: Output soot IR files.
 -crashPath <arg>          -crashPath: crash information file.
 -exceptionPath <arg>      -exceptionPath: exception file folder [optional].
 -frameworkVersion <arg>   -frameworkVersion: The version of framework under analysis
 -h                        -h: Show the help information.
 -name <arg>               -name: Set the name of the apk under analysis.
 -outputDir <arg>          -outputDir: Set the output folder of the apk.
 -path <arg>               -path: Set the path to the apk under analysis.
 -permissionPath <arg>     -permissionPath: Android permissionPath file
                           [optional.
 -sootOutput               -sootOutput: Output the sootOutput
 -strategy <arg>           -strategy: effectiveness of strategy m
 -time <arg>               -time [default:90]: Set the max running time (min).

```
