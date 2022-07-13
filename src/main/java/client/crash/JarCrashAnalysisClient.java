package main.java.client.crash;

import main.java.base.Analyzer;
import main.java.base.Global;
import main.java.base.MyConfig;
import main.java.client.BaseClient;
import main.java.client.cg.cgApk.CallGraphofApkClient;
import main.java.client.cg.cgJava.CallGraphofJavaClient;
import main.java.client.soot.SootAnalyzer;
import main.java.utils.FileUtils;
import org.dom4j.DocumentException;

import java.io.File;
import java.io.IOException;

/**
 * @Author hanada
 * @Date 2022/3/22 20:04
 * @Version 1.0
 */
public class JarCrashAnalysisClient extends BaseClient {
    @Override
    protected void clientAnalyze() {
        if (!MyConfig.getInstance().isSootAnalyzeFinish()) {
            SootAnalyzer sootAnalyzer = new SootAnalyzer();
            sootAnalyzer.analyze();
        }
        if (!MyConfig.getInstance().isCallGraphAnalyzeFinish()) {
            new CallGraphofJavaClient().start();
            MyConfig.getInstance().setCallGraphAnalyzeFinish(true);
        }

        System.out.println("Start analyze with CrashAnalysisClient.");
        Analyzer analyzer = new CrashAnalysis();
        analyzer.analyze();
        System.out.println("Successfully analyze with CrashAnalysisClient.");
        //        PackManager.v().writeOutput();

    }

    @Override
    public void clientOutput() throws IOException, DocumentException {
        String summary_app_dir = MyConfig.getInstance().getResultFolder() + Global.v().getAppModel().getAppName()
                + File.separator;
        FileUtils.createFolder(summary_app_dir);

        CrashAnalysisClientOutput outer = new CrashAnalysisClientOutput();
        outer.writeToJson(summary_app_dir+Global.v().getAppModel().getAppName()+".json", Global.v().getAppModel().getCrashInfoList());

    }
}
