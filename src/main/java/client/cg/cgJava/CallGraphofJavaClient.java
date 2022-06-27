package main.java.client.cg.cgJava;

import main.java.Global;
import main.java.MyConfig;
import main.java.analyze.utils.ConstantUtils;
import main.java.analyze.utils.output.FileUtils;
import main.java.client.BaseClient;
import main.java.client.cg.CgClientOutput;
import soot.PhaseOptions;
import soot.Scene;
import soot.jimple.spark.SparkTransformer;
import soot.jimple.toolkits.callgraph.CHATransformer;
import soot.jimple.toolkits.callgraph.CallGraph;

import java.io.File;
import java.util.HashMap;

/**
 * Analyzer Class
 * 
 * @author hanada
 * @version 2.0
 */
public class CallGraphofJavaClient extends BaseClient {

	@Override
	protected void clientAnalyze() {
		System.out.println("Start analyze with CallGraphClient.");
		CallGraph cg = CallGraphBuilder.getCallGraph();
		Global.v().getAppModel().setCg(cg);
		System.out.println("Call Graph has " + cg.size() + " edges.");
		System.out.println("Successfully analyze with CallGraphClient.");

	}


	@Override
	public void clientOutput() {
		/** call graph, if needed, open output**/
//		String summary_app_dir = MyConfig.getInstance().getResultFolder() + Global.v().getAppModel().getAppName()
//				+ File.separator;
//		FileUtils.createFolder(summary_app_dir + ConstantUtils.CGFOLDETR);

//		CgClientOutput.writeCG(summary_app_dir + ConstantUtils.CGFOLDETR,
//				Global.v().getAppModel().getAppName()+"_cg.txt", Global.v().getAppModel().getCg());
	}

}