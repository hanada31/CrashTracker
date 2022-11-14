import os
import sys
import shutil
from concurrent.futures import ThreadPoolExecutor
import threading
import time

reRun = False
filterList = list()


def analyzeApk(apkPath, resPath, sdk, frameworkVersion, strategy):
    logDir = resPath+os.sep+"logs"
    outputDir = resPath+os.sep+"output"
    if(not os.path.exists(logDir)): 
        os.makedirs(logDir) 
    if(not os.path.exists(outputDir)): 
        os.makedirs(outputDir) 
        
    if(os.path.exists(apkPath)): 
        apks = os.listdir(apkPath)
        
        extraArgs = "" 
        if frameworkVersion != "no": 
            extraArgs = extraArgs +  " -frameworkVersion " + frameworkVersion +" "
        extraArgs = extraArgs +" -strategy " +strategy +" "
        
        pool = ThreadPoolExecutor(max_workers=4)

        for apk in apks:
            if len(filterList)>0  and apk  in filterList:
                continue
            if apk[-4:] ==".apk":
                resFile = outputDir+"/"+apk[:-4]+".json"
                if(reRun or not os.path.exists(resFile)): 
                    command = "java -jar "+jarFile+"  -path "+ apkPath +" -name "+apk+" -androidJar "+ sdk +"/platforms  "+ extraArgs +"-crashInput Files/crashInfo.json  -exceptionInput Files/  -client ApkCrashAnalysisClient " +" -outputDir "+outputDir+" >> "+logDir+"/"+apk[:-4]+".txt"
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
        filterList.append(line.strip()+".apk")
    
if __name__ == '__main__' :
    sdk = "E:/AndroidSDK/android-sdk-windows-new/"
    jarFile = "CrashTracker.jar"
    
    apkPath = sys.argv[1]
    resPath = sys.argv[2]
    frameworkVersion = sys.argv[3]
    strategy = sys.argv[4] 
    os.system("mvn -f pom.xml package -q")
    if os.path.exists("target/CrashTracker.jar"):
        print("Successfully build! generate jar-with-dependencies in folder target/")
        shutil.copy("target/CrashTracker.jar", jarFile)
        print("copy jar to the root directory.")
    else:
        print("Fail to build! Please run \"mvn -f pom.xml package\" to see the detail info.")
    
    if len(sys.argv)>5:
        filterFile = sys.argv[5]    
        readFilterFile(filterFile)
    analyzeApk(apkPath, resPath, sdk, frameworkVersion, strategy)
    
