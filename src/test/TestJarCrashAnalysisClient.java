package test;

import main.java.MainClass;
import main.java.base.MyConfig;
import main.java.utils.ConstantUtils;
import soot.options.Options;

import java.io.File;

/**
 * @Author hanada
 * @Date 2022/3/11 15:27
 * @Version 1.0
 */
public class TestJarCrashAnalysisClient {

    @org.junit.Test
    public void testConfig() {
        setArgs();
        MainClass.startAnalyze();
        System.out.println("Finish...\n");
        System.exit(0);

    }

    private void setArgs() {
        String path;
        path = "apk\\";
        String name;
        name = "com.google.maps-768.jar";

        String client = "JarCrashAnalysisClient";

        MyConfig.getInstance().setAppName(name);
        MyConfig.getInstance().setAppPath(path + File.separator);
        MyConfig.getInstance().setClient(client);
        MyConfig.getInstance().setMaxPathNumber(30);
        MyConfig.getInstance().setMaxFunctionExpandNumber(5); //10?
        MyConfig.getInstance().setMaxObjectSummarySize(100);
        MyConfig.getInstance().setResultWarpperFolder("results/" + File.separator);
        MyConfig.getInstance().setResultFolder(MyConfig.getInstance().getResultWarpperFolder()+ "output" + File.separator);
        MyConfig.getInstance().setTimeLimit(10);
        MyConfig.getInstance().setAndroidJar("E:\\AndroidSDK\\android-sdk-windows-new\\platforms");
        MyConfig.getInstance().setSrc_prec(Options.src_prec_apk);
        MyConfig.getInstance().setCrashInfoFilePath("Files\\crashInfoJar.json");
        MyConfig.getInstance().setStrategy("");
//        MyConfig.getInstance().setAndroidOSVersion("2.3");

        ConstantUtils.PKGPREFIX = "";
    }

}
