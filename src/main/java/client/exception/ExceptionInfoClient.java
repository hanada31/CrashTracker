package main.java.client.exception;

import main.java.Analyzer;
import main.java.Global;
import main.java.MyConfig;
import main.java.analyze.utils.output.FileUtils;
import main.java.client.BaseClient;
import main.java.client.cg.cgJava.CallGraphofJavaClient;
import main.java.client.soot.SootAnalyzer;
import main.java.client.statistic.model.StatisticResult;
import org.dom4j.DocumentException;

import java.io.File;
import java.io.IOException;

/**
 * @Author hanada
 * @Date 2022/3/11 15:03
 * @Version 1.0
 */
public class ExceptionInfoClient extends BaseClient {

    /**
     * analyze logic for single app
     *
     * @return
     */
    @Override
    protected void clientAnalyze() {
        result = new StatisticResult();
        if (!MyConfig.getInstance().isSootAnalyzeFinish()) {
            SootAnalyzer sootAnalyzer = new SootAnalyzer();
            sootAnalyzer.analyze();
        }
//        if (!MyConfig.getInstance().isCallGraphAnalyzeFinish()) {
//            new CallGraphofJavaClient().start();
//            MyConfig.getInstance().setCallGraphAnalyzeFinish(true);
//        }
//        if (!MyConfig.getInstance().isStaitiucValueAnalyzeFinish()) {
//            if (MyConfig.getInstance().getMySwithch().isStaticFieldSwitch()) {
//                StaticValueAnalyzer staticValueAnalyzer = new StaticValueAnalyzer();
//                staticValueAnalyzer.analyze();
//                MyConfig.getInstance().setStaitiucValueAnalyzeFinish(true);
//            }
//        }
        System.out.println("Start analyze with ExceptionInfoClient.");
        Analyzer analyzer = new ExceptionAnalyzer(result);
        analyzer.analyze();
        System.out.println("Successfully analyze with ExceptionInfoClient.");
    }

    @Override
    public void clientOutput() throws IOException, DocumentException {
//        String summary_app_dir = MyConfig.getInstance().getResultFolder() + Global.v().getAppModel().getAppName()
//                + File.separator;
//        FileUtils.createFolder(summary_app_dir);
//
//        ExceptionInfoClientOutput.writeToJson(summary_app_dir+Global.v().getAppModel().getAppName()+".json", Global.v().getAppModel().getExceptionInfoList());

    }
}
