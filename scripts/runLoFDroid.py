import os
import sys
import shutil

reRun = True
filterList = list()
SDKVersion= "8.0"

def analyzeApk(apkPath, resPath, sdk):
    id=0
    logDir = resPath+"/logs"
    outputDir = resPath+"/output"
    if(not os.path.exists(logDir)): 
        os.makedirs(logDir) 
    if(not os.path.exists(outputDir)): 
        os.makedirs(outputDir) 
        
    if(os.path.exists(apkPath)): 
        
        apks = os.listdir(apkPath)
        extraArgs = "" #"-noLibCode "# 
        for apk in apks:
            if len(filterList)>0  and apk not in filterList:
                continue
            if apk[-4:] ==".apk":
                id+=1
                print ("\n\n\nThis is the "+str(id)+"th app " +apk)
                resFile = logDir+"/"+apk[:-4]+".txt"
                if(reRun or not os.path.exists(resFile)): 
                    print("java -jar "+jarFile+"  -path "+ apkPath +" -name "+apk+" -androidJar "+ sdk +"/platforms  "+ extraArgs +"-client CrashAnalysisClient  -SDKVersion " + SDKVersion +" -outputDir "+outputDir+" >> "+logDir+"/"+apk[:-4]+".txt")
                    os.system("java -jar "+jarFile+"  -path "+ apkPath +" -name "+apk+" -androidJar "+ sdk +"/platforms "+ extraArgs +" -client CrashAnalysisClient  -SDKVersion " + SDKVersion +" -outputDir "+outputDir+" >> "+logDir+"/"+apk[:-4]+".txt")


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
    
    
    os.system("mvn -f pom.xml package -q")
    if os.path.exists("target/LoFDroid.jar"):
        print("Successfully build! generate jar-with-dependencies in folder target/")
        shutil.copy("target/LoFDroid.jar", jarFile)
        print("copy jar to the root directory.")
    else:
        print("Fail to build! Please run \"mvn -f pom.xml package\" to see the detail info.")
    
    if len(sys.argv)>3:
        filterFile = sys.argv[3]    
        readFilterFile(filterFile)
    analyzeApk(apkPath, resPath, sdk)
    
