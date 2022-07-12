package test;

import main.java.client.dataAnalysis.BuggyStatistic;

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
