import os
import sys
import shutil
from concurrent.futures import ThreadPoolExecutor
import threading
import time

reRun = False
filterList = list()

def isAPKisAnalyzed(resPath, name):
    file_object1 = open(resPath+os.sep+"Ranking-"+resPath+".txt",'r')
    try:
        while True:
            line = file_object1.readline()
            if line:
                if name in line:
                    return True
            else:
                break
    finally:
        file_object1.close()
    print (name +" is not analyzed: " )
    return False


def analyzeJar(jarPath, resPath, sdk, frameworkVersion, strategy):
    logDir = resPath+os.sep+"logs"
    outputDir = resPath+os.sep+"output"
    if(not os.path.exists(logDir)): 
        os.makedirs(logDir) 
    if(not os.path.exists(outputDir)): 
        os.makedirs(outputDir) 
        
    if(os.path.exists(jarPath)): 
        jars = os.listdir(jarPath)
        
        extraArgs = "" 
        
        if frameworkVersion != "no": 
            extraArgs = extraArgs +  " -frameworkVersion " + frameworkVersion +" "
        extraArgs = extraArgs +" -strategy " +strategy +" "
        
        pool = ThreadPoolExecutor(max_workers=4)

        for jar in jars:
            if len(filterList)>0  and jar  in filterList:
                continue
            if jar[-4:] ==".jar":
                resFile = outputDir + os.sep + jar[:-4] + os.sep +jar[:-4] + ".json"
                analyzed = isAPKisAnalyzed(resPath,jar[:-4])
                if(reRun or not os.path.exists(resFile) or not analyzed ): 
                    command = "java -jar "+jarFile+"  -path "+ jarPath +" -name "+jar+" -androidJar "+ sdk +"/platforms  "+ extraArgs +" -crashInput Files/crashInfo.json  -exceptionInput Files/   -client JarCrashAnalysisClient" +" -outputDir "+outputDir+" >> "+logDir+"/"+jar[:-4]+".txt"
                    print(command + "@@@"+ str(analyzed))
                    future1 = pool.submit(executeCmd, command)
        pool.shutdown()

def executeCmd(cmd):
    print(cmd)
    os.system(cmd)

def readFilterFile(filterFile):
    f = open(filterFile, 'r')
    lines = f.readlines()
    f.close()
    for line in lines:
        filterList.append(line.strip()+".jar")
    
if __name__ == '__main__' :
    sdk = "lib/"   
    sdk = "../../other/android-sdk-linux/"
    jarFile = "CrashTracker.jar"
    
    jarPath = sys.argv[1]
    resPath = sys.argv[2]
    frameworkVersion = sys.argv[3]
    strategy = sys.argv[4] 
    #os.system("mvn -f pom.xml package")
    if os.path.exists("target/CrashTracker.jar"):
        print("Successfully build! generate jar-with-dependencies in folder target/")
        shutil.copy("target/CrashTracker.jar", jarFile)
        print("copy jar to the root directory.")
    else:
        print("Fail to build! Please run \"mvn -f pom.xml package\" to see the detail info.")
    
    if len(sys.argv)>5:
        filterFile = sys.argv[5]    
        readFilterFile(filterFile)
    analyzeJar(jarPath, resPath, sdk, frameworkVersion, strategy)
    
