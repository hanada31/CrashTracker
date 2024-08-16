package com.iscas.crashtracker.client.crash;

import com.iscas.crashtracker.Main;
import com.iscas.crashtracker.base.Global;
import com.iscas.crashtracker.base.MyConfig;
import com.iscas.crashtracker.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;
import soot.options.Options;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @Author hanada
 * @Date 2022/9/8 13:59
 * @Version 1.0
 */
@Slf4j
public class ApkCrashAnalysisClientTest {
    @org.junit.Test
    public void testConfig() {

        setArgs();
        Main.startAnalyze();
        System.exit(0);

    }
    private void setArgs() {
        String path;
//        path = "D:\\SoftwareData\\dataset\\apk\\Empirical500\\";
        path = "D:\\SoftwareData\\dataset\\apk\\FanDataICSE2018-before\\";

        String name;
        name = "com.streema.podcast-22.apk";//Not Override Method 1, Not Override Method 2
        name = "cgeo.geocaching-600.apk"; //Key Variable Related 1, Key Variable Related 2
        name = "com.cracked.android.lite-483.apk";//Key Variable Related 3
        name = "be.thomashermine.prochainbus-96.apk";//Key Variable Related 4
        name = "cgeo.geocaching-4450.apk";//Key API Related 1, Key API Related 2
//        name = "com.Android56-123.apk";// Framework Recall
//        name = "com.kalpvaig.quest-150.apk";// Framework Recall
//        name = "org.wordpress.android-1590.apk";// ParaAndField

        String client = "ApkCrashAnalysisClient";
        MyConfig.getInstance().setAppName(name);
        MyConfig.getInstance().setAppPath(path + File.separator);
        MyConfig.getInstance().setClient(client);
        MyConfig.getInstance().setResultWarpperFolder("..\\results" + File.separator);
        MyConfig.getInstance().setResultFolder(MyConfig.getInstance().getResultWarpperFolder()+ "output" + File.separator);
        MyConfig.getInstance().setTimeLimit(10);
        MyConfig.getInstance().setAndroidJar("E:\\AndroidSDK\\android-sdk-windows-new\\platforms");
        MyConfig.getInstance().setSrc_prec(Options.src_prec_apk);
        MyConfig.getInstance().setStrategy("");
//        MyConfig.getInstance().setStrategy("NoRelatedMethodFilter");
//        MyConfig.getInstance().setAndroidOSVersion("2.3");

        MyConfig.getInstance().setCrashInfoFilePath("..\\Files\\crashInfo.json");
        MyConfig.getInstance().setExceptionFolderPath("..\\Files\\ETSResults\\");
        String summary_app_dir = MyConfig.getInstance().getResultFolder() + Global.v().getAppModel().getAppName()
                + File.separator;
        FileUtils.createFolder(summary_app_dir);
        MyConfig.getInstance().setExceptionFilePath(summary_app_dir+"exceptionInfo"+File.separator);

//        MyConfig.getInstance().setCallgraphAlgorithm("SPARK");
//        MyConfig.getInstance().setCallgraphAlgorithm("CHA");


    }

}