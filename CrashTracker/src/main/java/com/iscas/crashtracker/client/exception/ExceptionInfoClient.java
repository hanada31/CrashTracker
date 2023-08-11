package com.iscas.crashtracker.client.exception;

import com.iscas.crashtracker.base.Analyzer;
import com.iscas.crashtracker.base.MyConfig;
import com.iscas.crashtracker.client.BaseClient;
import com.iscas.crashtracker.client.cg.cgJava.CallGraphJavaClient;
import com.iscas.crashtracker.client.soot.SootAnalyzer;
import com.iscas.crashtracker.utils.ConstantUtils;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.DocumentException;
import soot.PackManager;

import java.io.IOException;

/**
 * @author hanada
 * @version 1.0
 * @since 2022/3/11 15:03
 */

@Slf4j
public class ExceptionInfoClient extends BaseClient {
    /**
     * analyze logic for single app
     */
    @Override
    protected void clientAnalyze() {
        if (!MyConfig.getInstance().isSootAnalyzeFinish()) {
            SootAnalyzer sootAnalyzer = new SootAnalyzer();
            sootAnalyzer.analyze();
//            PackManager.v().writeOutput();
        }

        if (!MyConfig.getInstance().isCallGraphAnalyzeFinish()) {
            ConstantUtils.CGANALYSISPREFIX = ConstantUtils.FRAMEWORKPREFIX;
            new CallGraphJavaClient().start();
            MyConfig.getInstance().setCallGraphAnalyzeFinish(true);
        }

        log.info("Start analyze with ExceptionInfoClient.");
        Analyzer analyzer = new ExceptionAnalyzer();
        analyzer.analyze();
        log.info("Successfully analyze with ExceptionInfoClient.");
    }

    @Override
    public void clientOutput() throws IOException, DocumentException {
    }
}
