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
public class TestExceptionInfoClient {

    @org.junit.Test
    public void testConfig() {
        setArgs();
        setMySwitch();
        MainClass.startAnalyze();
        System.out.println("Finish...\n");
        System.exit(0);
    }

    private void setArgs() {
        String path, name;
        path = "C:\\Users\\yanjw\\programs\\framework\\classes\\";
        path = "D:\\SoftwareData\\dataset\\android-framework\\classes\\";
        name = "android2.3";

        String client = "ExceptionInfoClient";
//        client = "IROutputClient";

        MyConfig.getInstance().setAppName(name);
        MyConfig.getInstance().setAppPath(path + File.separator);
        MyConfig.getInstance().setClient(client);
        MyConfig.getInstance().setMaxPathNumber(30);
        MyConfig.getInstance().setMaxFunctionExpandNumber(5); //10?
        MyConfig.getInstance().setMaxObjectSummarySize(100);
        MyConfig.getInstance().setResultWarpperFolder("results/" + File.separator);
        MyConfig.getInstance().setResultFolder("Files" + File.separator);
        MyConfig.getInstance().setTimeLimit(100);
        MyConfig.getInstance().setAndroidJar("lib/platforms");
        MyConfig.getInstance().setSrc_prec(Options.src_prec_only_class);
        MyConfig.getInstance().setFileSuffixLength(0);
        MyConfig.getInstance().setFileSuffixLength(0);
        String androidFolder = "Files"+File.separator+name+File.separator;
        MyConfig.getInstance().setExceptionFilePath(androidFolder+"exceptionInfo"+File.separator);
        MyConfig.getInstance().setPermissionFilePath(androidFolder+"Permission"+File.separator+"permission.txt");
        MyConfig.getInstance().setAndroidCGFilePath(androidFolder+"CallGraphInfo"+File.separator+"cg.txt");

//        MyConfig.getInstance().setJimple(false);
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
