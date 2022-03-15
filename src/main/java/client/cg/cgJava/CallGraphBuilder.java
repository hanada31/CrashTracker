package main.java.client.cg.cgJava;

import soot.*;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.util.Chain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CallGraphBuilder {

	private static CallGraph cg = new CallGraph();

	static {
		addEdges();
		System.out.println("new CG Edge Size ***** " + cg.size());
		System.out.println("old CG Edge Size ***** " + Scene.v().getCallGraph().size());
	}

	public static CallGraph getCallGraph() {
		return cg;
	}

	private static void addEdges() {
		Chain<SootClass> classes = Scene.v().getApplicationClasses();
		Set<SootClass> sootClasses = new HashSet<>(classes);
		try {
			for (SootClass sootClass : sootClasses) {
				try {
					if (sootClass.isInterface()) {
						continue;
					}
					List<SootMethod> ms = sootClass.getMethods();
					Set<SootMethod> methods = new HashSet<>(ms);
					for (final SootMethod sootMethod : methods) {
						if (sootMethod.isAbstract() || sootMethod.isNative()) {
							continue;
						}
						Body body = null;
						try {
							body = sootMethod.getActiveBody();
						} catch (Exception e) {
//							e.printStackTrace();
						}
						if (body == null) {
							continue;
						}
//					UnitGraph unitGraph = new BriefUnitGraph(body);
						for (final Unit unit : body.getUnits()) {
							if (unit instanceof Stmt) {
								final Stmt stmt = (Stmt) unit;
								if (stmt.containsInvokeExpr()) {
									SootMethod invokeMethod = stmt.getInvokeExpr().getMethod();
									synchronized (cg) {
										Edge e = new Edge(sootMethod, stmt, invokeMethod);
										cg.addEdge(e);
									}
								}
							}
						}

					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
