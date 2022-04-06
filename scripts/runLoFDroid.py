import os
import sys
import shutil

reRun = True

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
            if apk[-4:] ==".apk":
                id+=1
                print ("\n\n\nThis is the "+str(id)+"th app " +apk)
                resFile = logDir+"/"+apk[:-4]+".txt"
                if(reRun or not os.path.exists(resFile)): 
                    print("java -jar "+jarFile+"  -path "+ apkPath +" -name "+apk+" -androidJar "+ sdk +"/platforms  "+ extraArgs +" -time 5 -maxPathNumber 100 -client CrashAnalysisClient  -outputDir "+outputDir+" >> "+logDir+"/"+apk[:-4]+".txt")
                    os.system("java -jar "+jarFile+"  -path "+ apkPath +" -name "+apk+" -androidJar "+ sdk +"/platforms "+ extraArgs +" -time 5 -maxPathNumber 100 -client CrashAnalysisClient -outputDir "+outputDir+" >> "+logDir+"/"+apk[:-4]+".txt")


if __name__ == '__main__' :
    apkPath = sys.argv[1]
    resPath = sys.argv[2]
    jarFile = "LoFDroid.jar"
    
    os.system("mvn -f pom.xml package -q")
    if os.path.exists("target/LoFDroid.jar"):
        print("Successfully build! generate jar-with-dependencies in folder target/")
        shutil.copy("target/LoFDroid.jar", jarFile)
        print("copy jar to the root directory.")
    else:
        print("Fail to build! Please run \"mvn -f pom.xml package\" to see the detail info.")
    
    sdk = "lib/"    
    analyzeApk(apkPath, resPath, sdk)
    
