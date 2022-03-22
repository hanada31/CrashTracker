package main.java.client.cg.cgJava;

import main.java.MyConfig;
import main.java.analyze.utils.ConstantUtils;
import main.java.analyze.utils.SootUtils;
import soot.*;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.util.Chain;

import java.util.*;

public class CallGraphBuilder {

	private static CallGraph cg = Scene.v().getCallGraph();

	static {
		filterEdges(cg);
		System.out.println("filtered CG Edge Size ***** " + cg.size());
		addEdgesByOurAnalyze(cg);
		System.out.println("enhanced CG Edge Size ***** " + cg.size());
	}

	private static void filterEdges(CallGraph cg) {
		Set<Edge> res = new HashSet<>();
		Iterator<Edge> it = cg.iterator();
		while(it.hasNext()){
			Edge e = it.next();
			if(!e.getSrc().method().getDeclaringClass().getPackageName().startsWith(ConstantUtils.PKGPREFIX) ||
					!e.getTgt().method().getDeclaringClass().getPackageName().startsWith(ConstantUtils.PKGPREFIX)){
				res.add(e);
			}
		}
		for(Edge e: res) {
			cg.removeEdge(e);
		}
	}

	public static CallGraph getCallGraph() {
		return cg;
	}

	private static void addEdgesByOurAnalyze(CallGraph callGraph) {
		for (SootClass sc : Scene.v().getApplicationClasses()) {
			if(!sc.getPackageName().startsWith(ConstantUtils.PKGPREFIX)) continue;
			if (!MyConfig.getInstance().getMySwithch().allowLibCodeSwitch()) {
				if (!SootUtils.isNonLibClass(sc.getName()))
					continue;
			}
			ArrayList<SootMethod> methodList = new ArrayList<SootMethod>(sc.getMethods());
			for (SootMethod sm : methodList) {
				if (SootUtils.hasSootActiveBody(sm) == false)
					continue;
				Iterator<Unit> it = SootUtils.getSootActiveBody(sm).getUnits().iterator();
				while (it.hasNext()) {
					Unit u = it.next();
					InvokeExpr exp = SootUtils.getInvokeExp(u);
					if (exp == null)  continue;
					InvokeExpr invoke = SootUtils.getSingleInvokedMethod(u);
					if (invoke != null) { // u is invoke stmt
						Set<SootMethod> targetSet = SootUtils.getInvokedMethodSet(sm, u);
						for (SootMethod target : targetSet) {
							if(!target.getDeclaringClass().getPackageName().startsWith(ConstantUtils.PKGPREFIX)) continue;
							Edge e = new Edge(sm, (Stmt) u, target);
							callGraph.addEdge(e);
						}
					}
				}
			}
		}
	}
}

