package test;

import main.java.MainClass;
import main.java.base.MyConfig;
import soot.options.Options;

import java.io.File;

/**
 * @Author hanada
 * @Date 2022/3/11 15:27
 * @Version 1.0
 */
public class TestIROutputClient {

    @org.junit.Test
    public void testConfig() {
        setArgs();
        MainClass.startAnalyze();
        System.out.println("Finish...\n");
        System.exit(0);
    }

    private void setArgs() {
        String path;
        path = "D:\\SoftwareData\\dataset\\apk\\Empirical500\\";
        path = "D:\\SoftwareData\\dataset\\apk\\FanDataICSE2018-before\\";
//        path = "C:\\Users\\yanjw\\programs\\Empirical500";
        String name;
        name = "cgeo.geocaching-4524.apk";

        String client = "IROutputClient";

        MyConfig.getInstance().setAppName(name);
        MyConfig.getInstance().setAppPath(path + File.separator);
        MyConfig.getInstance().setClient(client);
        MyConfig.getInstance().setMaxPathNumber(30);
        MyConfig.getInstance().setMaxFunctionExpandNumber(5); //10?
        MyConfig.getInstance().setMaxObjectSummarySize(100);
        MyConfig.getInstance().setResultWarpperFolder("results/" + File.separator);
        MyConfig.getInstance().setResultFolder(MyConfig.getInstance().getResultWarpperFolder()+ "output" + File.separator);
        MyConfig.getInstance().setTimeLimit(10);
        MyConfig.getInstance().setAndroidJar("lib/platforms");
        MyConfig.getInstance().setSrc_prec(Options.src_prec_apk);
        MyConfig.getInstance().setFileSuffixLength(4);
    }

}
