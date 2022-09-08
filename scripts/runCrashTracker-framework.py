import os
import sys
import shutil


def executeCmd(cmd):
    print(cmd)
    os.system(cmd)

if __name__ == '__main__' :
    sdk = "lib/"   
    jarFile = "CrashTracker.jar"
   
    
    os.system("mvn -f pom.xml package -q")
    if os.path.exists("target/CrashTracker.jar"):
        print("Successfully build! generate jar-with-dependencies in folder target/")
        shutil.copy("target/CrashTracker.jar", jarFile)
        print("copy jar to the root directory.")
    else:
        print("Fail to build! Please run \"mvn -f pom.xml package\" to see the detail info.")
    

    path = "/home/yanjw/myTools/CrashTracker-develop/framework/classes/";
    name = "android"+sys.argv[1];
    
    command = "java -jar "+jarFile+"  -path "+ path +" -name "+name+" -androidJar "+ sdk +"/platforms  -exceptionInput ../Files/  -client ExceptionInfoClient " +" -outputDir Files/ "  +" -frameworkVersion " + sys.argv[1] +" >> Files/logs/android-" +sys.argv[1]+".txt"
    executeCmd(command)
                    
