package main.java.client.soot;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import com.google.common.collect.Lists;
import main.java.Analyzer;
import main.java.Global;
import main.java.MyConfig;
import main.java.analyze.utils.ConstantUtils;
import soot.*;
import soot.jimple.spark.SparkTransformer;
import soot.jimple.toolkits.callgraph.CHATransformer;
import soot.options.Options;

public class SootAnalyzer extends Analyzer {
	public SootAnalyzer() {
		super();
	}

	/**
	 * analyze using soot 1) set setActiveBody 2) def-use analyze
	 */
	@Override
	public void analyze() {
		if (appModel.getAppName() == null)
			return;
		sootInit();
		sootTransform();
		sootEnd();
		if (Global.v().getAppModel().getApplicationClassNames().size() == 0) {
			for (SootClass sc : Scene.v().getApplicationClasses()) {
				Global.v().getAppModel().addApplicationClassNames(sc.getName());
			}
			System.out.println("Soot Analysis finish.");
		}
	}

	/**
	 * initialize soot
	 */
	public static void sootInit() {
		soot.G.reset();
		if(Global.v().getAppModel().getAppPath().endsWith(".apk") ){
			Options.v().set_android_jars(MyConfig.getInstance().getAndroidJar());
		}
		List<String> processDir = Lists.newArrayList();
		processDir.add(Global.v().getAppModel().getAppPath());
//		System.out.println("aaaa "+ Global.v().getAppModel().getAppPath());

		Options.v().set_include_all(true);
		Options.v().set_process_dir(processDir);
		Options.v().set_no_bodies_for_excluded(true);
		Options.v().set_process_multiple_dex(true);
		if (MyConfig.getInstance().isJimple())
			Options.v().set_output_format(Options.output_format_jimple);
		else
			Options.v().set_output_format(Options.output_format_shimple);
		String out = MyConfig.getInstance().getResultFolder() + Global.v().getAppModel().getAppName() + File.separator
				+ ConstantUtils.SOOTOUTPUT;
		Options.v().set_output_dir(out);
		Options.v().set_src_prec(MyConfig.getInstance().getSrc_prec());

		Options.v().allow_phantom_refs();
//		Options.v().set_whole_program(true);

		setExcludePackage();

	}

	/**
	 * add transforms for analyzing
	 */
	private void sootTransform() {
		String pack = "jtp";
		if (!MyConfig.getInstance().isJimple())
			pack = "stp";
		// set setActiveBody
		ActiveBodyTransformer abTran = new ActiveBodyTransformer();
		Transform t1 = new Transform(pack + ".bt", abTran);
		PackManager.v().getPack(pack).add(t1);

	}

	/**
	 * end setting of soot
	 */
	public static void sootEnd() {
		soot.Main.v().autoSetOptions();
		Scene.v().loadNecessaryClasses();
		Scene.v().loadBasicClasses();
		PackManager.v().runPacks();
	}

	private static void enableSparkCallGraph() {
		//Enable Spark
		HashMap<String,String> opt = new HashMap<String,String>();
		//opt.put("propagator","worklist");
		//opt.put("simple-edges-bidirectional","false");
		opt.put("on-fly-cg","true");
		//opt.put("set-impl","double");
		//opt.put("double-set-old","hybrid");
		//opt.put("double-set-new","hybrid");
		//opt.put("pre_jimplify", "true");
		SparkTransformer.v().transform("",opt);
		PhaseOptions.v().setPhaseOption("cg.spark", "enabled:true");
	}

	private static void enableCHACallGraph() {
		CHATransformer.v().transform();
	}
	/**
	 * packages refuse to be analyzed
	 */
	public static void setExcludePackage() {
		ArrayList<String> excludeList = new ArrayList<String>();
//		excludeList.add("android.*");
		excludeList.add("androidx.*");
		excludeList.add("kotlin.*");
		excludeList.add("com.*");
		excludeList.add("soot.*");
		excludeList.add("junit.*");
		excludeList.add("java.*");
		excludeList.add("javax.*");
		excludeList.add("sun.*");
		excludeList.add("org.*");
		Options.v().set_exclude(excludeList);
	}
}
