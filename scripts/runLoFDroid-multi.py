import os
import sys
import shutil
from concurrent.futures import ThreadPoolExecutor
import threading
import time

reRun = True
filterList = list()


def analyzeApk(apkPath, resPath, sdk, AndroidOSVersion):
    logDir = resPath+"/logs"
    outputDir = resPath+"/output"
    if(not os.path.exists(logDir)): 
        os.makedirs(logDir) 
    if(not os.path.exists(outputDir)): 
        os.makedirs(outputDir) 
        
    if(os.path.exists(apkPath)): 
        apks = os.listdir(apkPath)
        extraArgs = "" #"-noLibCode "# 
        
        pool = ThreadPoolExecutor(max_workers=4)

        for apk in apks:
            if len(filterList)>0  and apk not in filterList:
                continue
            if apk[-4:] ==".apk":
                resFile = logDir+"/"+apk[:-4]+".txt"
                if(reRun or not os.path.exists(resFile)): 
                    if AndroidOSVersion != "no": 
                        command = "java -jar "+jarFile+"  -path "+ apkPath +" -name "+apk+" -androidJar "+ sdk +"/platforms  "+ extraArgs +"-client CrashAnalysisClient  -AndroidOSVersion " + AndroidOSVersion +" -outputDir "+outputDir+" >> "+logDir+"/"+apk[:-4]+".txt"
                        future1 = pool.submit(executeCmd, command)
                    else:
                        command = "java -jar "+jarFile+"  -path "+ apkPath +" -name "+apk+" -androidJar "+ sdk +"/platforms  "+ extraArgs +"-client CrashAnalysisClient -outputDir "+outputDir+" >> "+logDir+"/"+apk[:-4]+".txt"
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
    sdk = "lib/"   
    jarFile = "LoFDroid.jar"
    
    apkPath = sys.argv[1]
    resPath = sys.argv[2]
    AndroidOSVersion = sys.argv[3]
    os.system("mvn -f pom.xml package -q")
    if os.path.exists("target/LoFDroid.jar"):
        print("Successfully build! generate jar-with-dependencies in folder target/")
        shutil.copy("target/LoFDroid.jar", jarFile)
        print("copy jar to the root directory.")
    else:
        print("Fail to build! Please run \"mvn -f pom.xml package\" to see the detail info.")
    
    if len(sys.argv)>4:
        filterFile = sys.argv[4]    
        readFilterFile(filterFile)
    analyzeApk(apkPath, resPath, sdk, AndroidOSVersion)
    
