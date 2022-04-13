package test;

import main.java.MainClass;
import main.java.MyConfig;
import main.java.SummaryLevel;
import soot.options.Options;

import java.io.File;

/**
 * @Author hanada
 * @Date 2022/3/11 15:27
 * @Version 1.0
 */
public class TestAPP {

    @org.junit.Test
    public void testConfig() {
        setArgs();
        setMySwitch();
        MainClass.startAnalyze();
        System.out.println("Finish...\n");
        System.exit(0);
    }

    private void setArgs() {
        String path;
        path = "D:\\SoftwareData\\ToolsInREST\\yanjiwei\\ICCBot\\Tool\\Empirical500\\";
        String name;
        name = "com.justnote-78.apk";
//        name = "com.shenyuan.militarynews-80.apk";
//        name = "com.sillens.shapeupclub-415.apk";
//        name = "com.ccemusic.downloader-151.apk";
//        name = "com.avpig.exam-245.apk";
//        name = "com.apps.salad.bengali.recipe-229.apk";
//        name = "qukean.spaceship-327.apk";
//        name = "com.xy.mobile.shaketoflashlight-52.apk";
//        name = "com.gb.compassleveler-158.apk";
//        name = "com.turkusoz_tablet-287.apk";
//        name = "hk.gogovan.GoGoVanClient2-180.apk";
//        name = "com.infomil.leclercdrive-277.apk";
        name = "com.kalpvaig.quest-150.apk";
//        name = "hamsoft.inc.barcodegen-466.apk";


        String client = "CrashAnalysisClient";

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
        MyConfig.getInstance().setCrashInfoFilePath("Files\\crashInfo.json");
        MyConfig.getInstance().setExceptionFilePath("Files\\android\\exceptionInfo\\");
        MyConfig.getInstance().setAndroidCGFilePath("Files\\android\\CallGraphInfo\\cg.txt");
        MyConfig.getInstance().setPermissionFilePath("Files\\android\\Permission\\permission.txt");

    }

    /**
     * analyze parameters for evaluation
     */
    private void setMySwitch() {
        MyConfig.getInstance().getMySwithch().setDummyMainSwitch(false);
        MyConfig.getInstance().getMySwithch().setCallBackSwitch(true);
        MyConfig.getInstance().getMySwithch().setFunctionExpandSwitch(true);

        MyConfig.getInstance().getMySwithch().setAsyncMethodSwitch(true);
        MyConfig.getInstance().getMySwithch().setPolymSwitch(true);

        MyConfig.getInstance().getMySwithch().setAdapterSwitch(true);
        MyConfig.getInstance().getMySwithch().setStringOpSwitch(true);
        MyConfig.getInstance().getMySwithch().setStaticFieldSwitch(true);

        MyConfig.getInstance().getMySwithch().setFragmentSwitch(true);
        MyConfig.getInstance().getMySwithch().setLibCodeSwitch(true);
        MyConfig.getInstance().getMySwithch().setWrapperAPISwitch(true);

        MyConfig.getInstance().getMySwithch().setImplicitLaunchSwitch(true);
        MyConfig.getInstance().getMySwithch().setDynamicBCSwitch(true);

        MyConfig.getInstance().getMySwithch().setSummaryStrategy(SummaryLevel.object);
        MyConfig.getInstance().getMySwithch().setVfgStrategy(true);
        MyConfig.getInstance().getMySwithch().setCgAnalyzeGroupedStrategy(false);
    }
}
