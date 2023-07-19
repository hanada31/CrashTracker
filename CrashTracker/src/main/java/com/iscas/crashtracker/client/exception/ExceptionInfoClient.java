package com.iscas.crashtracker.client.exception;

import com.iscas.crashtracker.base.Analyzer;
import com.iscas.crashtracker.base.MyConfig;
import com.iscas.crashtracker.client.BaseClient;
import com.iscas.crashtracker.client.cg.cgJava.CallGraphofJavaClient;
import com.iscas.crashtracker.client.soot.SootAnalyzer;
import com.iscas.crashtracker.utils.ConstantUtils;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.DocumentException;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

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
        }

        if (!MyConfig.getInstance().isCallGraphAnalyzeFinish()) {
            ConstantUtils.CGANALYSISPREFIX = ConstantUtils.FRAMEWORKPREFIX;
            new CallGraphofJavaClient().start();
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
