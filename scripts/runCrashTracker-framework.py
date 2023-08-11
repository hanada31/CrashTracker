import os
import sys


def execute_cmd(cmd):
    print(cmd)
    os.system(cmd)


if __name__ == '__main__':
    sdk = "platforms"
    jarFile = "CrashTracker.jar"

    if not os.path.exists("CrashTracker.jar"):
        print("CrashTracker not found! Please run \"mvn -f pom.xml package\" first")
        exit()

    # do not use the android.jar file in the Android SDK, as they have empty implementation. Instead,
    # extract android.jar files from your android phone or an emulator with the target version. Also,
    # you can download from  https://github.com/hanada31/AndroidFrameworkImpl and unzip files path =
    # "framework/classes/"; "D:\\SoftwareData\\dataset\\android-framework\\classes\\";
    path = sys.argv[1]
    name = sys.argv[2]
    version = sys.argv[3]
    limit = sys.argv[4]
    output = sys.argv[5]

    command = "java -jar " + jarFile + " -path " + path + " -name " + name + " -androidJar " + sdk + \
              " -client ExceptionInfoClient" + " -outputDir " + output + " -frameworkVersion " + version + \
              " -conditionLimit " + limit
    print("Start command")
    execute_cmd(command)
    print("Execute over!")
