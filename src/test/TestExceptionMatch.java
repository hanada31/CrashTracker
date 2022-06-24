package test;

import main.java.MainClass;
import main.java.MyConfig;
import main.java.SummaryLevel;
import main.java.analyze.utils.ConstantUtils;
import main.java.client.crash.ExceptionMather;
import soot.options.Options;

import java.io.File;

/**
 * @Author hanada
 * @Date 2022/3/11 15:27
 * @Version 1.0
 */
public class TestExceptionMatch {

    @org.junit.Test
    public void testExceptionMatch() {
        MyConfig.getInstance().setResultFolder("results/"  + File.separator);
        MyConfig.getInstance().setCrashInfoFilePath("Files\\crashInfo.json");
        ExceptionMather matcher = new ExceptionMather();
        matcher.analyze();
    }
}
