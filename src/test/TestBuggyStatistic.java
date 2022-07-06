package test;

import main.java.MyConfig;
import main.java.client.dataAnalysis.BuggyStatistic;
import main.java.client.dataAnalysis.ExceptionMather;

import java.io.File;

/**
 * @Author hanada
 * @Date 2022/3/11 15:27
 * @Version 1.0
 */
public class TestBuggyStatistic {

    @org.junit.Test
    public void test() {
        BuggyStatistic a = new BuggyStatistic();
        a.analyze();
    }
}
