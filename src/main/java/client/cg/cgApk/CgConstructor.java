package main.java.client.cg.cgApk;

import main.java.base.Analyzer;
import main.java.base.MyConfig;
import main.java.client.soot.SootAnalyzer;
import main.java.utils.FileUtils;
import main.java.utils.SootUtils;
import org.xmlpull.v1.XmlPullParserException;
import soot.PackManager;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.infoflow.InfoflowConfiguration.CallgraphAlgorithm;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.resources.ARSCFileParser;
import soot.jimple.infoflow.android.resources.LayoutFileParser;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.options.Options;

import java.io.IOException;

/**
 * generate callgraph
 * 
 * @author 79940
 *
 */
public class CgConstructor extends Analyzer {
	SetupApplication setupApplication;

	public CgConstructor() {
		super();
		try {
			setupApplication = new SetupApplication(MyConfig.getInstance().getAndroidJar(), appModel.getAppPath());
		}catch (Exception e){
			e.printStackTrace();
		}
	}

	@Override
	public void analyze() {
		// dummy constuct, call back collect, fragment collect
		try {
			constructDummyMainMethods();
		} catch (Exception e) {
			constructBySoot();
		}
		collectDummyAsEntries();
		collectLifeCycleAsEntries();
		collectSelfCollectEntries();
		collectAllMethodsAsEntries();

		// isFragmentSwitch
		collectFragmentClasses();

		if(!Scene.v().hasCallGraph()){
			System.out.println("Call Graph is empty.");
			appModel.setCg(new CallGraph());
		}else{
			appModel.setCg(Scene.v().getCallGraph());
		}
		System.out.println("Call Graph has " + appModel.getCg().size() + " edges.");
	}

	private void constructBySoot() {
		if (appModel.getAppName() == null)
			return;
		SootAnalyzer.sootInit();
		sootTransform();
		SootAnalyzer.sootEnd();
//		System.out.println("Call Graph has " + Scene.v().getCallGraph().size() + " edges.");
	}

	/**
	 * dummy constuct, call back collect, fragment collect
	 * 
	 */
	private void constructDummyMainMethods() throws XmlPullParserException, IOException {
		setupApplication.getConfig().getCallbackConfig().setCallbackAnalysisTimeout(120);
		setupApplication.getConfig().setCallgraphAlgorithm(CallgraphAlgorithm.AutomaticSelection);
		setupApplication.getConfig().setMergeDexFiles(true);
		setupApplication.runInfoflow();
		FileUtils.delFolder("sootOutput");

	}

	/**
	 * if isDummyMainSwitch is false
	 */
	private void collectAllMethodsAsEntries() {
		for (SootClass sc : Scene.v().getApplicationClasses()) {
			for (SootMethod sMethod : sc.getMethods()) {
				if (!appModel.getEntryMethod2Component().containsKey(sMethod)) {
					appModel.addEntryMethod2Component(sMethod, sc);
				}
			}
		}
	}

	/**
	 * if isDummyMainSwitch is true
	 */
	private void collectDummyAsEntries() {
		for (SootClass sc : Scene.v().getApplicationClasses()) {
			for (SootMethod sMethod : sc.getMethods()) {
				if (SootUtils.isComponentEntryMethod(sMethod))
					if (!appModel.getEntryMethod2Component().containsKey(sMethod)) {
						appModel.addEntryMethod2Component(sMethod, sc);
						appModel.getEntryMethods().add(sMethod);
					}
			}
		}
	}

	private void collectSelfCollectEntries() {
		for (SootClass sc : Scene.v().getApplicationClasses()) {
			for (SootMethod sMethod : sc.getMethods()) {
				if (SootUtils.isSelfEntryMethods(sMethod.getSignature()))
					if (!appModel.getEntryMethod2Component().containsKey(sMethod)) {
						appModel.addEntryMethod2Component(sMethod, sc);
						appModel.getEntryMethods().add(sMethod);
					}
			}
		}

	}

	/**
	 * if isDummyMainSwitch is true
	 */
	private void collectLifeCycleAsEntries() {
		for (SootClass sc : Scene.v().getApplicationClasses()) {
			for (SootMethod sMethod : sc.getMethods()) {
				if (SootUtils.isLifeCycleMethods(sMethod.getSignature()))
					if (!appModel.getEntryMethod2Component().containsKey(sMethod)) {
						appModel.addEntryMethod2Component(sMethod, sc);
						appModel.getEntryMethods().add(sMethod);
					}
			}
		}
	}

	/**
	 * if isFragmentSwitch is true collectFragmentClasses
	 * appModel.getFragmentClasses()
	 */
	private void collectFragmentClasses() {
		ARSCFileParser resParser = new ARSCFileParser();
		try {
			resParser.parse(appModel.getAppPath());
		} catch (IOException e) {
			e.printStackTrace();
		}
		LayoutFileParser layoutParser = new LayoutFileParser(appModel.getPackageName(), resParser);
		layoutParser.parseLayoutFile(appModel.getAppPath());
		PackManager.v().getPack("wjtp").apply();

		for (SootClass sc : Scene.v().getApplicationClasses()) {
			if (SootUtils.isFragmentClass(sc)) {
				appModel.getFragmentClasses().add(sc.getName());
			}
		}
	}



	/**
	 * add transforms for analyzing
	 */
	private void sootTransform() {
		String algo = MyConfig.getInstance().getCallgraphAlgorithm();
		switch (algo) {
		case "CHA":
			Options.v().setPhaseOption("cg.cha", "on");
			break;
		case "SPARK":
			Options.v().setPhaseOption("cg.spark", "on");
			Options.v().setPhaseOption("cg.spark", "string-constants:true");
			break;
		default:
			break;
		}
	}

}