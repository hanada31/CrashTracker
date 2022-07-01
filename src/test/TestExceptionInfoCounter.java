package test;

import main.java.MyConfig;
import main.java.client.dataAnalysis.ExceptionInfoCount;
import main.java.client.dataAnalysis.ExceptionMather;

import java.io.File;

/**
 * @Author hanada
 * @Date 2022/3/11 15:27
 * @Version 1.0
 */
public class TestExceptionInfoCounter {

    @org.junit.Test
    public void testExceptionMatch() {
        MyConfig.getInstance().setResultFolder("results/"  + File.separator);
        ExceptionInfoCount analyzer = new ExceptionInfoCount();
        analyzer.analyze();
    }
}
