package main.java.client.exception;

import main.java.base.Analyzer;
import main.java.base.MyConfig;
import main.java.client.BaseClient;
import main.java.client.cg.cgJava.CallGraphofJavaClient;
import main.java.client.soot.SootAnalyzer;
import main.java.utils.ConstantUtils;
import org.dom4j.DocumentException;

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
        if (!MyConfig.getInstance().isSootAnalyzeFinish()) {
            SootAnalyzer sootAnalyzer = new SootAnalyzer();
            sootAnalyzer.analyze();
        }
        if (!MyConfig.getInstance().isCallGraphAnalyzeFinish()) {
            ConstantUtils.CGANALYSISPREFIX = ConstantUtils.FRAMEWORKPREFIX;
            new CallGraphofJavaClient().start();
            MyConfig.getInstance().setCallGraphAnalyzeFinish(true);
        }

        System.out.println("Start analyze with ExceptionInfoClient.");
        Analyzer analyzer = new ExceptionAnalyzer();
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
//
    }
}
