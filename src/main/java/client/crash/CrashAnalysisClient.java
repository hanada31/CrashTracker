package main.java.client.crash;

import main.java.Analyzer;
import main.java.Global;
import main.java.MyConfig;
import main.java.analyze.utils.output.FileUtils;
import main.java.client.BaseClient;
import main.java.client.cg.cgApk.CallGraphofApkClient;
import main.java.client.soot.SootAnalyzer;
import main.java.client.statistic.model.StatisticResult;
import org.dom4j.DocumentException;
import soot.PackManager;

import java.io.File;
import java.io.IOException;

/**
 * @Author hanada
 * @Date 2022/3/22 20:04
 * @Version 1.0
 */
public class CrashAnalysisClient extends BaseClient {
    @Override
    protected void clientAnalyze() {
        result = new StatisticResult();
        MyConfig.getInstance().setFileSuffixLength(4);
        if (!MyConfig.getInstance().isSootAnalyzeFinish()) {
            SootAnalyzer sootAnalyzer = new SootAnalyzer();
            sootAnalyzer.analyze();
        }
        PackManager.v().writeOutput();

        if (!MyConfig.getInstance().isCallGraphAnalyzeFinish()) {
            new CallGraphofApkClient().start();
            MyConfig.getInstance().setCallGraphAnalyzeFinish(true);
        }
//        if (!MyConfig.getInstance().isStaitiucValueAnalyzeFinish()) {
//            if (MyConfig.getInstance().getMySwithch().isStaticFieldSwitch()) {
//                StaticValueAnalyzer staticValueAnalyzer = new StaticValueAnalyzer();
//                staticValueAnalyzer.analyze();
//                MyConfig.getInstance().setStaitiucValueAnalyzeFinish(true);
//            }
//        }

        System.out.println("Start analyze with CrashAnalysisClient.");
        Analyzer analyzer = new CrashAnalysis(result);
        analyzer.analyze();
        System.out.println("Successfully analyze with CrashAnalysisClient.");
    }

    @Override
    public void clientOutput() throws IOException, DocumentException {
        String summary_app_dir = MyConfig.getInstance().getResultFolder() + Global.v().getAppModel().getAppName()
                + File.separator;
        FileUtils.createFolder(summary_app_dir);

        CrashAnalysisClientOutput outer = new CrashAnalysisClientOutput(this.result);
        outer.writeToJson(summary_app_dir+Global.v().getAppModel().getAppName()+".json", Global.v().getAppModel().getCrashInfoList());

    }
}
