package com.iscas.crashtracker.client.exception;

import com.iscas.crashtracker.Main;
import com.iscas.crashtracker.base.MyConfig;
import lombok.extern.slf4j.Slf4j;
import soot.options.Options;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @Author hanada
 * @Date 2022/9/8 14:02
 * @Version 1.0
 */
@Slf4j
public class ExceptionInfoClientTest {

    @org.junit.Test
    public void testConfig() {
        setArgs();
        Main.startAnalyze();
        log.info("ExceptionInfoClientTest Finish...\n");
        System.exit(0);
    }

    private void setArgs() {
        String path, targetFolder;
        path = "C:\\Users\\yanjw\\programs\\framework\\classes\\";
        path = "D:\\SoftwareData\\dataset\\android-framework\\classes\\";
//        path = "..\\M_framework\\";

        String client = "ExceptionInfoClient";

        targetFolder = "android2.3";
//        targetFolder = "android-support-v7-appcompat";
//        targetFolder = "test";
        MyConfig.getInstance().setAppName(targetFolder);
        MyConfig.getInstance().setAppPath(path + File.separator);
        MyConfig.getInstance().setClient(client);
        MyConfig.getInstance().setResultWarpperFolder("..\\ETSResults" + File.separator);
        MyConfig.getInstance().setResultFolder("..\\ETSResults" + File.separator);
        MyConfig.getInstance().setTimeLimit(100);
        MyConfig.getInstance().setAndroidJar("E:\\AndroidSDK\\android-sdk-windows-new\\platforms");
        MyConfig.getInstance().setSrc_prec(Options.src_prec_only_class);
        MyConfig.getInstance().setFileSuffixLength(0);
        String androidFolder = MyConfig.getInstance().getResultFolder() +File.separator+targetFolder+File.separator;
        MyConfig.getInstance().setExceptionFilePath(androidFolder+"exceptionInfo"+File.separator);
        MyConfig.getInstance().setPermissionFilePath(androidFolder+"Permission"+File.separator+"permission.txt");
        MyConfig.getInstance().setAndroidCGFilePath(androidFolder+"CallGraphInfo"+File.separator+"cg.txt");
        MyConfig.getInstance().setMethodInfoFilePath(androidFolder+"CodeInfo"+File.separator+"methodInfo.json");
    }
}
