# LoFDroid
Locating Buggy Method by Framework-aware Analysis

Requirements：

1. Python 3+

2. Java 1.8+

3. Install GraphViz (http://www.graphviz.org/download/) 


build and run *LoFDroid* to analyze single apk/ class Folder: : 
```
mvn -f pom.xml clean package

cp target/LoFDroid.jar LoFDroid.jar

java -jar LoFDroid.jar  -path apk// -name ICCBotBench.apk -androidJar lib//platforms -time 30 -maxPathNumber 100 -client CTGClient -outputDir results//output
```
or analyze apks under given folder with Python script:

```
python .\scripts\runLoFDroid.py [apkPath] [resultPath]
```



Usage of LoFDroid.jar:

```
java -jar LoFDroid.jar -h

usage: java -jar LoFDroid.jar [options] [-path] [-name] [-androidJar] [-outputDir][-client]
 
 -h                     -h: Show the help information.
 -name <arg>            -name: Set the name of the apk under analysis.
 -path <arg>            -path: Set the path to the apk under analysis.
 -outputDir <arg>       -outputDir: Set the output folder of the apk.
 -client <arg>          -client 
                         "CallGraphClient: Output call graph files."
                         "ManifestClient: Output manifest.xml file."
                         "IROutputClient: Output soot IR files."
                         "FragmentClient: Output the fragment loading results."
                         "CTGClient/MainClient: Resolve ICC and generate CTG."
                         "ICCSpecClient:  Report ICC specification for each component."
                        
 -androidJar <arg>      -androidJar: Set the path of android.jar.                
 -version <arg>         -version [default:23]: Version of Android SDK.
 -maxPathNumber <arg>   -maxPathNumber [default:10000]: Set the max number of paths.
 -time <arg>            -time [default:90]: Set the max running time (min).

```



Input: 

Output: 
