import os
import sys
import shutil


def executeCmd(cmd):
    print(cmd)
    os.system(cmd)

if __name__ == '__main__' :
    sdk = "lib/"   
    jarFile = "LoFDroid.jar"
   
    
    os.system("mvn -f pom.xml package -q")
    if os.path.exists("target/LoFDroid.jar"):
        print("Successfully build! generate jar-with-dependencies in folder target/")
        shutil.copy("target/LoFDroid.jar", jarFile)
        print("copy jar to the root directory.")
    else:
        print("Fail to build! Please run \"mvn -f pom.xml package\" to see the detail info.")
    

    path = "C:\\Users\\yanjw\\programs\\framework\\classes\\";
    name = "android10.0";
    
    command = "java -jar "+jarFile+"  -path "+ path +" -name "+name+" -androidJar "+ sdk +"/platforms -client ExceptionInfoClient"  
    executeCmd(command)
                    
