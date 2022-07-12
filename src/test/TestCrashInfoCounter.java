package test;

import main.java.base.MyConfig;
import main.java.client.dataAnalysis.CrashInfoCounter;

import java.io.File;

/**
 * @Author hanada
 * @Date 2022/3/11 15:27
 * @Version 1.0
 */
public class TestCrashInfoCounter {

    @org.junit.Test
    public void testCrashInfoCounter() {
        MyConfig.getInstance().setResultFolder("Files"  + File.separator);
        MyConfig.getInstance().setCrashInfoFilePath("Files\\crashInfo.json");
        CrashInfoCounter matcher = new CrashInfoCounter();
        matcher.analyze();
    }
}
