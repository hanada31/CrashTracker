package main.java.client.cg.cgApk;

import heros.solver.Pair;
import main.java.base.Analyzer;
import main.java.base.Global;
import main.java.base.MyConfig;
import main.java.client.soot.SootAnalyzer;
import main.java.utils.ConstantUtils;
import main.java.utils.FileUtils;
import main.java.utils.SootUtils;
import org.xmlpull.v1.XmlPullParserException;
import soot.*;
import soot.jimple.InvokeExpr;
import soot.jimple.infoflow.InfoflowConfiguration.CallgraphAlgorithm;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.callbacks.AndroidCallbackDefinition;
import soot.jimple.infoflow.android.resources.ARSCFileParser;
import soot.jimple.infoflow.android.resources.LayoutFileParser;
import soot.jimple.spark.ondemand.genericutil.MultiMap;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.options.Options;
import soot.util.HashMultiMap;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * generate callgraph
 * 
 * @author 79940
 *
 */
public class CgConstructor extends Analyzer {
	SetupApplication setupApplication;
	MultiMap<SootClass, AndroidCallbackDefinition> callBacks;

	public CgConstructor() {
		super();
		setupApplication = new SetupApplication(MyConfig.getInstance().getAndroidJar(), appModel.getAppPath());
		callBacks = (MultiMap<SootClass, AndroidCallbackDefinition>) new HashMultiMap<SootClass, AndroidCallbackDefinition>();
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
	 * addCallBackListeners from "AndroidCallbacks.txt"
	 */
	private void addCallBackListeners() {

		Global.v().getAppModel().setCallbacks(FileUtils.getSetFromFile(ConstantUtils.DEFAULTCALLBACKFILE));

		Set<String> callBacks = new HashSet<String>();
		for (SootClass sc : Scene.v().getApplicationClasses()) {
			for (SootClass interFace : sc.getInterfaces()) {
				if (!callBacks.contains(interFace.getName())) {
					if (interFace.getName().contains("Listener") || interFace.getName().contains("listener")
							|| interFace.getName().contains("callback") || interFace.getName().contains("Callback"))
						callBacks.add(interFace.getName());
				}
			}
		}
		Global.v().getAppModel().getCallbacks().addAll(callBacks);
	}

	/**
	 * collectUserCustumizedListeners add user custmized callbacks
	 * 
	 * @param sm
	 * @param u
	 * @param invoke
	 */
	private void collectUserCustumizedListeners(SootMethod sm, Unit u, InvokeExpr invoke) {
		SootMethod invMethod = invoke.getMethod();
		int id = 0;
		for (Type type : invMethod.getParameterTypes()) {
			if (Global.v().getAppModel().getCallbacks().contains(type.toString())) {
				Value value = invoke.getArg(id);
				List<Unit> defs = SootUtils.getDefOfLocal(sm.getSignature(), value, u);
				for (Unit defUnit : defs) {
					String targetClsStr = SootUtils.getTargetClassListenerBelongto(defUnit);
					SootClass targetCls = SootUtils.getSootClassByName(targetClsStr);
					if (targetCls == null) {
						continue;
					}
					for (SootClass interfaceClass : targetCls.getInterfaces()) {
						for (SootMethod interfaceMethod : interfaceClass.getMethods()) {
							SootMethod realInvolkedMethod = SootUtils.getMethodBySubSignature(targetCls,
									interfaceMethod.getSubSignature());
							if (realInvolkedMethod != null) {
								if (!appModel.getEntryMethod2Component().containsKey(realInvolkedMethod)) {
									appModel.addEntryMethod2Component(realInvolkedMethod, sm.getDeclaringClass());
									appModel.getEntryMethods().add(realInvolkedMethod);
									if (appModel.getComponentMap().containsKey(sm.getDeclaringClass().getName()))
										continue;
									if (appModel.getFragmentClasses().contains(sm.getDeclaringClass().getName()))
										continue;
									Pair<SootMethod, Unit> pair = new Pair<SootMethod, Unit>(sm, u);
									appModel.getEntryMethod2MethodAddThisCallBack().putIfAbsent(pair,
											new HashSet<SootMethod>());
									appModel.getEntryMethod2MethodAddThisCallBack().get(pair).add(realInvolkedMethod);
								}
							}
						}
					}
				}
			}
			id++;
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