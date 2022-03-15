package main.java.client.exception;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import main.java.Analyzer;
import main.java.Global;
import main.java.analyze.model.analyzeModel.MethodSummaryModel;
import main.java.analyze.utils.StringUtils;
import main.java.client.obj.ObjectAnalyzer;
import main.java.client.statistic.model.StatisticResult;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JCastExpr;
import soot.jimple.internal.JVirtualInvokeExpr;
import soot.shimple.PhiExpr;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;
import soot.toolkits.scalar.ValueUnitPair;

import java.util.*;

/**
 * @Author hanada
 * @Date 2022/3/11 15:21
 * @Version 1.0
 */
public class ExceptionAnalyzer extends Analyzer {
    public ExceptionAnalyzer(StatisticResult result) {
        super();
    }
    @Override
    public void analyze() {
        Global.v().getAppModel().setExceptionMap(getThrowMessage());
    }

    public static Map<SootMethod, Map<String, Set<String>>> getThrowMessage(){
        Map<SootMethod, Map<String, Set<String>>> result = Maps.newHashMap();
        HashSet<SootClass> applicationClasses = new HashSet<>(Scene.v().getApplicationClasses());
        Set<SootMethod> allCannotGetExceptionMethodSet = Sets.newHashSet();
        int totalCaught = 0;
        for (SootClass sootClass : applicationClasses) {
            if (!sootClass.getPackageName().startsWith("android")) {
                continue;
            }
//            System.out.println(sootClass.getName());
            HashSet<SootMethod> sootMethods = new HashSet<SootMethod>(sootClass.getMethods());
            for (SootMethod sootMethod : sootMethods) {
                if (sootMethod.hasActiveBody()) {
                    Body body = sootMethod.getActiveBody();
                    try {
                        ArrayList<Integer> list = Lists.newArrayList();
                        Map<String, Set<String>> message = getMessage(body, sootMethod, allCannotGetExceptionMethodSet, list);
                        if (!message.isEmpty()) {
                            result.put(sootMethod, message);
                        }
                        if (!list.isEmpty()) {
                            totalCaught += list.get(0);
                        }
                    } catch (Exception |  Error e) {
                        System.out.println("Exception |  Error:::" + sootMethod.getSignature());
                        e.printStackTrace();
                    }

                }
            }
        }

        System.out.println("TotalCaught:::" + totalCaught);
        return result;
    }

    /**
     * get message of an exception
     * @param body
     * @param sootMethod
     * @param allCannotGetExceptionMethodSet
     * @param integers
     * @return
     */
    public static Map<String, Set<String>> getMessage(Body body, SootMethod sootMethod, Set<SootMethod> allCannotGetExceptionMethodSet, List<Integer> integers){
        int totalCaught = 0;
        Map<String, Set<String>> result = Maps.newHashMap();
        BriefUnitGraph unitGraph = new BriefUnitGraph(body);
        for (Unit unit : body.getUnits()) {
            if (unit instanceof ThrowStmt) {
                ThrowStmt throwStmt = (ThrowStmt) unit;
                Value throwValue = throwStmt.getOp();
                if (throwValue instanceof Local) {
                    List<String> exceptionNameList = Lists.newArrayList();
                    Set<SootMethod> caughtExceptionMethodSet = Sets.newHashSet();
                    getExceptionName(sootMethod, unitGraph, (Local) throwValue, unit, exceptionNameList, caughtExceptionMethodSet);
                    if (exceptionNameList.isEmpty()) {
                        if (!caughtExceptionMethodSet.contains(sootMethod)) {
                            allCannotGetExceptionMethodSet.add(sootMethod);
                        } else {
                            totalCaught++;
                        }
                    }
                    if (!exceptionNameList.isEmpty()) {
                        String exceptionName = exceptionNameList.get(0);
                        List<String> exceptionMessageList = Lists.newArrayList();
                        List<Integer> times = Lists.newArrayList();
                        getExceptionMessage(unitGraph, unit, exceptionName, exceptionMessageList, times);
                        if (!exceptionMessageList.isEmpty()) {
                            String exceptionMessage = exceptionMessageList.get(0);
                            if (exceptionMessage != null && !exceptionMessage.isEmpty()) {
                                Set<String> messageSet = Sets.newHashSet();
                                messageSet.add(exceptionMessage);
                                if (result.containsKey(exceptionName)) {
                                    messageSet.addAll(result.get(exceptionName));
                                }
                                result.put(exceptionName, messageSet);
                            } else {
                                result.put(exceptionName, Sets.newHashSet());
                            }
                        } else {
                            result.put(exceptionName, Sets.newHashSet());
                        }
                    }
                }
            }
        }

        integers.add(totalCaught);
        return result;
    }

    public static void getExceptionName(SootMethod sootMethod, UnitGraph unitGraph, Local localTemp, Unit unit, List<String> exceptionNameList, Set<SootMethod> caughtExceptionMethodSet){
        List<Unit> defsOfOps = new SimpleLocalDefs(unitGraph).getDefsOfAt(localTemp, unit);
        if (defsOfOps.size() == 1) {
            Unit defOfLocal = defsOfOps.get(0);
            if (defOfLocal.equals(unit)) {
                return;
            }
            if (defOfLocal instanceof DefinitionStmt) {
                Value rightValue = ((DefinitionStmt)defOfLocal).getRightOp();
                if (rightValue instanceof NewExpr) {
                    NewExpr newRightValue = (NewExpr) rightValue;
                    if(exceptionNameList.isEmpty()) {
                        exceptionNameList.add(newRightValue.getBaseType().getSootClass().toString());
                    }

                } else if (rightValue instanceof NewArrayExpr) {
                    NewArrayExpr rightValue1 = (NewArrayExpr) rightValue;
                    String s = rightValue1.getBaseType().toString();
                    if (s.endsWith("Exception") || s.equals("java.lang.Throwable")) {
                        exceptionNameList.add(s);
                    }
                } else if (rightValue instanceof Local) {
                    getExceptionName(sootMethod, unitGraph, (Local) rightValue, unit, exceptionNameList, caughtExceptionMethodSet);
                } else if (rightValue instanceof JCastExpr) {
                    JCastExpr castExpr = (JCastExpr) rightValue;
                    String s = castExpr.getType().toString();

                    if (s.endsWith("Exception") || s.equals("java.lang.Throwable")) {
                        exceptionNameList.add(s);
                    } else {
                        Value value = castExpr.getOpBox().getValue();
                        if (value instanceof Local) {
                            getExceptionName(sootMethod, unitGraph, (Local) value, unit, exceptionNameList, caughtExceptionMethodSet);
                        }
                    }

                } else if (rightValue instanceof InvokeExpr) {
                    InvokeExpr invokeExpr = (InvokeExpr) rightValue;
                    Type returnType = invokeExpr.getMethod().getReturnType();
                    if (returnType.toString().endsWith("Exception") || returnType.toString().equals("java.lang.Throwable")) {
                        exceptionNameList.add(returnType.toString());
                    }

                } else if (rightValue instanceof CaughtExceptionRef) {
                    caughtExceptionMethodSet.add(sootMethod);
                } else if (rightValue instanceof PhiExpr) {
                    PhiExpr phiExpr = (PhiExpr) rightValue;
                    for (ValueUnitPair arg : phiExpr.getArgs()) {
                        if (arg.getValue() instanceof Local) {
                            getExceptionName(sootMethod, unitGraph, (Local) arg.getValue(), unit, exceptionNameList, caughtExceptionMethodSet);
                        }
                    }

                } if (rightValue instanceof FieldRef) {
                    FieldRef rightValue1 = (FieldRef) rightValue;
                    String s = rightValue1.getField().getType().toString();
                    if (s.endsWith("Exception") || s.equals("java.lang.Throwable")) {
                        exceptionNameList.add(s);
                    }
                } else if (rightValue instanceof ParameterRef) {
                    ParameterRef rightValue1 = (ParameterRef) rightValue;
                    String s = rightValue1.getType().toString();
                    if (s.endsWith("Exception") || s.equals("java.lang.Throwable")) {
                        exceptionNameList.add(s);
                    }
                }  else if (rightValue instanceof ArrayRef) {
                    ArrayRef rightValue1 = (ArrayRef) rightValue;
                    Value value = rightValue1.getBaseBox().getValue();
                    if (value instanceof Local) {
                        getExceptionName(sootMethod, unitGraph, (Local) value, unit, exceptionNameList, caughtExceptionMethodSet);
                    }
                }
            }
        }
    }

    public static void getExceptionMessage(UnitGraph unitGraph, Unit unit, String exceptionClassName,
                                           List<String> exceptionMessageList, List<Integer> times){
        times.add(1);
        if (times.size() > 50) {
            return;
        }
        List<Unit> predsOf = unitGraph.getPredsOf(unit);
        for (Unit predUnit : predsOf) {
            if (predUnit instanceof InvokeStmt) {
                InvokeStmt invokeStmt = (InvokeStmt) predUnit;
                InvokeExpr invokeExpr = invokeStmt.getInvokeExpr();
                if (invokeExpr.getMethod().getDeclaringClass().toString().equals(exceptionClassName)) {
                    // 可能初始化会有多个参数，只关注第一个String参数
                    if (invokeExpr.getArgCount() > 0 && StringUtils.isStringType(invokeExpr.getArgs().get(0).getType())) {
                        Value arg = invokeExpr.getArgs().get(0);
                        if (arg instanceof Local) {
                            List<String> message = Lists.newArrayList();
                            message.add("");
                            traceDef(unitGraph, (Local) arg, unit, message);
                            if (exceptionMessageList.isEmpty()) {
                                if (!message.get(0).isEmpty()) {
                                    exceptionMessageList.add(message.get(0));
                                }

                            }
                        } else if (arg instanceof Constant) {
                            StringConstant arg1 = (StringConstant) arg;
                            if (exceptionMessageList.isEmpty()) {
                                if (arg1.value!=null && !arg1.value.isEmpty()) {
                                    exceptionMessageList.add(arg1.value);
                                }
                            }
                        }
                    }
                } else {
                    getExceptionMessage(unitGraph, predUnit, exceptionClassName, exceptionMessageList,times);
                }
            } else {
                getExceptionMessage(unitGraph, predUnit, exceptionClassName, exceptionMessageList,times);
            }
        }
    }

    // message 只有一个元素
    public static void traceDef(UnitGraph unitGraph, Local localTemp, Unit unit, List<String> message){
        List<Unit> defsOfOps = new SimpleLocalDefs(unitGraph).getDefsOfAt(localTemp, unit);
        Unit defOfLocal = defsOfOps.get(0);
        if (defOfLocal.equals(unit)) {
            return;
        }
        if (defOfLocal instanceof DefinitionStmt) {
            Value rightOp = ((DefinitionStmt) defOfLocal).getRightOp();
            if (rightOp instanceof Constant) {
                String s = message.get(0) + rightOp;
                message.set(0,s);
            } else if (rightOp instanceof InvokeExpr) {
                InvokeExpr invokeExpr = (InvokeExpr) rightOp;
                String invokeSig = invokeExpr.getMethod().getSignature();
                if (invokeSig.equals("<java.lang.StringBuilder: java.lang.String toString()>")) {
                    Value value = invokeExpr.getUseBoxes().get(0).getValue();
                    if (value instanceof Local) {
                        traceDef(unitGraph, (Local) value, unit, message);
                    }
                } else if (invokeSig.startsWith("<java.lang.StringBuilder: java.lang.StringBuilder append")) {
                    Value argConstant = invokeExpr.getArgs().get(0);
                    String s = "";
                    if (argConstant instanceof Constant) {
                        if (argConstant instanceof StringConstant) {
                            String value = ((StringConstant) argConstant).value;
                            s = value + message.get(0);
                        } else {
                            s = argConstant.toString() + message.get(0);
                        }

                    } else {
                        s = "[\\s\\S]*" + message.get(0);
                    }
                    message.set(0, s);

                    Value value = ((JVirtualInvokeExpr) invokeExpr).getBaseBox().getValue();
                    if (value instanceof Local) {
                        traceDef(unitGraph, (Local) value, unit, message);
                    }
                }
            } else if (rightOp instanceof NewExpr) {
                NewExpr rightOp1 = (NewExpr) rightOp;
                if (rightOp1.getBaseType().toString().equals("java.lang.StringBuilder")) {
                    traceStringBuilderBack(unitGraph, defOfLocal, message, 0);
                }
            } else if (rightOp instanceof Local) {
                traceDef(unitGraph, (Local) rightOp, unit, message ) ;
            }
        }
    }

    public static void traceStringBuilderBack(UnitGraph unitGraph, Unit unit, List<String> message, int index){
        if (index > 10) {
            return;
        }
        List<Unit> succsOf = unitGraph.getSuccsOf(unit);
        for (Unit succs : succsOf) {
            if (succs instanceof InvokeStmt) {
                InvokeExpr invokeExpr = ((InvokeStmt) succs).getInvokeExpr();
                String invokeSig = invokeExpr.getMethod().getSignature();
                if (invokeSig.startsWith("<java.lang.StringBuilder: java.lang.StringBuilder append")) {
                    Value argConstant = invokeExpr.getArgs().get(0);
                    String s = "";
                    if (argConstant instanceof Constant) {
                        if (argConstant instanceof StringConstant) {
                            String value = ((StringConstant) argConstant).value;
                            s = message.get(0) + value;
                        } else {
                            s = message.get(0) + argConstant.toString();
                        }
                    } else{
                        s = message.get(0) + "[\\s\\S]*";
                    }
                    message.set(0, s);
                }
            } else if (succs instanceof ThrowStmt) {
                return;
            }
            traceStringBuilderBack(unitGraph, succs, message, index + 1);
        }
    }
}
