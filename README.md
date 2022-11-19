# [CrashTracker](https://github.com/hanada31/CrashTracker)
Locating Framework-specific Crashing Faults with Compact and Explainable Candidate Set

Requirements：

1. Python 3+ 

2. Java 1.8+



build and run *CrashTracker* to analyze single apk/ class Folder: : 



```
# Initialize soot-dev submodule
git submodule update --init soot-dev

# Use -DskipTests to skip tests of soot (make build faster)
mvn -f pom.xml clean package -DskipTests

# Copy jar to root directory
cp target/CrashTracker.jar CrashTracker.jar

# Execute tool
java -jar CrashTracker.jar  -path apk// -name xxx.apk -androidJar androidSdk//platforms  -crashInput ../Files/crashInfo.json  -exceptionInput ../Files/ -client ApkCrashAnalysisClient -time 30  -outputDir results//output
                    
```
or analyze apks under given folder with Python script:

```
modify the sdk folder in the scripts.

run:
python scripts/runCrashTracker-Apk.py  [apkPath] [resultPath] [target framework version] [strategy name]
- [target framework version]: have a folder called Files/android[target framework version], which stores the framework code. E.g., "4.4", "6.0", or "no"
- [strategy name]:  "NoCallFilter", "NoSourceType", "ExtendCGOnly",  "NoKeyAPI", "NoParaChain, "NoAppDataTrace", "NOParaChainANDDataTrace"or "no"
```



Usage of CrashTracker.jar:

```
java -jar CrashTracker.jar -h

usage: java -jar CrashTracker.jar [options] [-path] [-name] [-androidJar] [-outputDir] [-crashInput] [-exceptionInput] [-client]
 -h                        -h: Show the help information.
 -client <arg>             -client 
 						   	   ExceptionInfoClient: Extract exception information from Android framework.
                               CrashAnalysisClient: Analysis the crash information for an apk.
                               JarCrashAnalysisClient: Analysis the crash information for an third party SDK.
                               CallGraphClient: Output call graph files.
                               ManifestClient: Output manifest.xml file.
                               IROutputClient: Output soot IR files.
 -name <arg>               -name: Set the name of the apk under analysis.
 -path <arg>               -path: Set the path to the apk under analysis.
 -crashPath <arg>          -crashInput: crash information file.
 -exceptionInput <arg>     -exceptionPath: exception file folder.
 -androidJar <arg>         -androidJar: Set the path of android.jar.
 -frameworkVersion <arg>   -frameworkVersion: The version of framework under analysis
 -strategy <arg>           -strategy: effectiveness of strategy "NoCallFilter", "NoSourceType", "ExtendCGOnly",  "NoKeyAPI", "NoParaChain, "NoAppDataTrace", "NOParaChainANDDataTrace"or "no"
 -time <arg>               -time [default:90]: Set the max running time (min).
 -outputDir <arg>          -outputDir: Set the output folder of the apk.

```
