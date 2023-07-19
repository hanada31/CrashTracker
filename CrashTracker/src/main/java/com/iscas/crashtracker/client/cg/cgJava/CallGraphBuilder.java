package com.iscas.crashtracker.client.cg.cgJava;

import com.iscas.crashtracker.utils.ConstantUtils;
import com.iscas.crashtracker.utils.SootUtils;
import lombok.extern.slf4j.Slf4j;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
@Slf4j
public class CallGraphBuilder {

	private static final CallGraph cg = Scene.v().getCallGraph();

	static {
		log.info("filtered CG Edge Size ***** " + cg.size());
		addEdgesByOurAnalyze();
		log.info("enhanced CG Edge Size ***** " + cg.size());
	}

	private static void filterEdges(CallGraph cg) {
		Set<Edge> res = new HashSet<>();
		for (Edge e : cg) {
			if (!e.getSrc().method().getDeclaringClass().getPackageName().startsWith(ConstantUtils.CGANALYSISPREFIX) ||
					!e.getTgt().method().getDeclaringClass().getPackageName().startsWith(ConstantUtils.CGANALYSISPREFIX)) {
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

	/**
	 * Add edge for all invoke statement to callGraph
	 */
	private static void addEdgesByOurAnalyze() {
		for (SootClass sc : Scene.v().getApplicationClasses()) {
			if(!sc.getPackageName().startsWith(ConstantUtils.CGANALYSISPREFIX)) continue;
			ArrayList<SootMethod> methodList = new ArrayList<>(sc.getMethods());
			for (SootMethod sm : methodList) {
				if (!SootUtils.hasSootActiveBody(sm))
					continue;
				for (Unit u : SootUtils.getSootActiveBody(sm).getUnits()) {
					InvokeExpr exp = SootUtils.getInvokeExp(u);
					if (exp == null) continue;
					Set<SootMethod> targetSet = SootUtils.getInvokedMethodSet(sm, u);
					for (SootMethod target : targetSet) {
						if (!target.getDeclaringClass().getPackageName().startsWith(ConstantUtils.CGANALYSISPREFIX))
							continue;
						Edge e = new Edge(sm, (Stmt) u, target);
						cg.addEdge(e);
					}
				}
			}
		}
	}
}

