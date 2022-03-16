package main.java.client.exception;

import com.google.common.collect.Lists;
import main.java.Analyzer;
import main.java.Global;
import main.java.analyze.utils.SootUtils;
import main.java.analyze.utils.StringUtils;
import main.java.client.statistic.model.StatisticResult;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.shimple.PhiExpr;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.scalar.ValueUnitPair;

import java.util.*;

/**
 * @Author hanada
 * @Date 2022/3/11 15:21
 * @Version 1.0
 */
public class ExceptionAnalyzer extends Analyzer {
    Set<ExceptionInfo> exceptionInfoSet;

    public ExceptionAnalyzer(StatisticResult result) {
        super();
        exceptionInfoSet = new HashSet<ExceptionInfo>();
        Global.v().getAppModel().setExceptionMap(exceptionInfoSet);
    }

    @Override
    public void analyze() {
        HashSet<SootClass> applicationClasses = new HashSet<>(Scene.v().getApplicationClasses());
        for (SootClass sootClass : applicationClasses) {
            if (!sootClass.getPackageName().startsWith("android.")) {
                continue;
            }
            HashSet<SootMethod> sootMethods = new HashSet<SootMethod>(sootClass.getMethods());
            for (SootMethod sootMethod : sootMethods) {
                if (sootMethod.hasActiveBody()) {
//                    if (!sootMethod.getSignature().contains("removeOnUidImportanceListener")) {
//                        continue;
//                    }
                    try {
                        analyzeMethod(sootMethod);
                    } catch (Exception |  Error e) {
                        System.out.println("Exception |  Error:::" + sootMethod.getSignature());
                        e.printStackTrace();
                    }

                }
            }
        }
        System.out.println("TotalCaught:::" + exceptionInfoSet.size());
    }

    /**
     * get message of an exception
     * @param sootMethod
     * @return
     */
    public void analyzeMethod(SootMethod sootMethod){
        Body body = sootMethod.getActiveBody();
        BriefUnitGraph unitGraph = new BriefUnitGraph(body);
        for (Unit unit : body.getUnits()) {
            if (unit instanceof ThrowStmt) {
                ThrowStmt throwStmt = (ThrowStmt) unit;
                Value throwValue = throwStmt.getOp();
                if (throwValue instanceof Local) {
                    Local value = (Local) throwValue;
                    analyzeThrowUnits(sootMethod, value, unit);
                }
            }
        }
    }

    /**
     * analyze each Throw Unit
     * @param sootMethod
     * @param localTemp
     * @param unit
     */
    public void analyzeThrowUnits(SootMethod sootMethod,  Local localTemp, Unit unit){
        Body body = sootMethod.getActiveBody();
        BriefUnitGraph unitGraph = new BriefUnitGraph(body);
        List<Unit> defsOfOps = SootUtils.getDefOfLocal(sootMethod.getSignature(),localTemp, unit);
        if (defsOfOps.size() >= 1) {
            Unit defOfLocal = defsOfOps.get(0);
            if (defOfLocal.equals(unit)) {
                return;
            }
            if (defOfLocal instanceof DefinitionStmt) {
                Value rightValue = ((DefinitionStmt)defOfLocal).getRightOp();
                if (rightValue instanceof NewExpr) {
                    NewExpr newRightValue = (NewExpr) rightValue;
                    String name = newRightValue.getBaseType().getSootClass().toString();
                    creatNewExceptionInfo(sootMethod, unit, name);
                } else if (rightValue instanceof NewArrayExpr) {
                    NewArrayExpr rightValue1 = (NewArrayExpr) rightValue;
                    String s = rightValue1.getBaseType().toString();
                    if (s.endsWith("Exception") || s.equals("java.lang.Throwable")) {
                        String name = s;
                        creatNewExceptionInfo(sootMethod, unit, name);
                    }
                } else if (rightValue instanceof Local) {
                    analyzeThrowUnits(sootMethod, (Local) rightValue, unit);
                } else if (rightValue instanceof JCastExpr) {
                    JCastExpr castExpr = (JCastExpr) rightValue;
                    String s = castExpr.getType().toString();
                    if (s.endsWith("Exception") || s.equals("java.lang.Throwable")) {
                        String name = s;
                        creatNewExceptionInfo(sootMethod, unit, name);
                    } else {
                        Value value = castExpr.getOpBox().getValue();
                        if (value instanceof Local) {
                            analyzeThrowUnits(sootMethod, (Local) value, unit);
                        }
                    }
                } else if (rightValue instanceof InvokeExpr) {
                    InvokeExpr invokeExpr = (InvokeExpr) rightValue;
                    Type returnType = invokeExpr.getMethod().getReturnType();
                    if (returnType.toString().endsWith("Exception") || returnType.toString().equals("java.lang.Throwable")) {
                        String name = returnType.toString();
                        creatNewExceptionInfo(sootMethod, unit, name);
                    }

                } else if (rightValue instanceof CaughtExceptionRef) {
                    //caught an Exception here
                    //$r1 := @caughtexception;
                } else if (rightValue instanceof PhiExpr) {
                    PhiExpr phiExpr = (PhiExpr) rightValue;
                    for (ValueUnitPair arg : phiExpr.getArgs()) {
                        if (arg.getValue() instanceof Local) {
                            analyzeThrowUnits(sootMethod, (Local) arg.getValue(), unit);
                        }
                    }

                } if (rightValue instanceof FieldRef) {
                    FieldRef rightValue1 = (FieldRef) rightValue;
                    String s = rightValue1.getField().getType().toString();
                    if (s.endsWith("Exception") || s.equals("java.lang.Throwable")) {
                        String name = s;
                        creatNewExceptionInfo(sootMethod, unit, name);
                    }
                } else if (rightValue instanceof ParameterRef) {
                    ParameterRef rightValue1 = (ParameterRef) rightValue;
                    String s = rightValue1.getType().toString();
                    if (s.endsWith("Exception") || s.equals("java.lang.Throwable")) {
                        String name = s;
                        creatNewExceptionInfo(sootMethod, unit, name);
                    }
                }  else if (rightValue instanceof ArrayRef) {
                    ArrayRef rightValue1 = (ArrayRef) rightValue;
                    Value value = rightValue1.getBaseBox().getValue();
                    if (value instanceof Local) {
                        analyzeThrowUnits(sootMethod, (Local) value, unit);
                    }
                }
            }
        }
    }

    /**
     * creat a New ExceptionInfo object and add content
     * @param sootMethod
     * @param unit
     * @param exceptionName
     */
    private void creatNewExceptionInfo(SootMethod sootMethod, Unit unit, String exceptionName) {
        ExceptionInfo exceptionInfo =  new ExceptionInfo(sootMethod, unit, exceptionName);
        getExceptionMessage(sootMethod, unit, exceptionInfo, new ArrayList<Integer>());
        getExceptionCondition(sootMethod, unit, exceptionInfo, new ArrayList<Integer>());
        exceptionInfoSet.add(exceptionInfo);
    }

    /**
     * get the latest condition info for an ExceptionInfo
     * @param sootMethod
     * @param unit
     * @param exceptionInfo
     * @param times
     * only analyze one level if condition, forward
     */
    private void getExceptionCondition(SootMethod sootMethod, Unit unit, ExceptionInfo exceptionInfo, ArrayList<Integer> times) {
        //i0 := @parameter0: int; if i0 < 0 goto label10; -- label10: throw $r4;
        //r6 := @this: android.renderscript.AllocationAdapter;-->  i12_1 = Phi(i12 #0, i12_2 #7);  if i12_1 == 1 goto label02; -- label02: throw $r17;
        Body body = sootMethod.getActiveBody();
        BriefUnitGraph unitGraph = new BriefUnitGraph(body);
        times.add(1);
        if (times.size() > 50) { return;}
        List<Unit> gotoTargets = getGotoTargets(body);
        List<Unit> predsOf = unitGraph.getPredsOf(unit);
        for (Unit predUnit : predsOf) {
            if(exceptionInfo.getTracedUnits().size()>0 && gotoTargets.contains(predUnit))
                break;
            if (predUnit instanceof IfStmt) {
                exceptionInfo.getTracedUnits().add(predUnit);
                IfStmt ifStmt = (IfStmt) predUnit;
                Value cond = ifStmt.getCondition();
                exceptionInfo.getConditions().add(cond);
                if(cond instanceof ConditionExpr){
                    Value value = ((ConditionExpr)cond).getOp1();
                    extendRelatedValues(exceptionInfo, predUnit, value, new ArrayList<Value>());
                }
            }
            getExceptionCondition(sootMethod, predUnit, exceptionInfo,times);
        }
    }

    /**
     * tracing the values relates to the one used in if condition
     *
     * @param exceptionInfo
     * @param unit
     * @param value
     * @return
     */
    private void extendRelatedValues(ExceptionInfo exceptionInfo, Unit unit, Value value, List<Value> history) {
        if(history.contains(value)) return;
        history.add(value);
        if(value instanceof  Local) {
            List<Unit> defsOfOps = SootUtils.getDefOfLocal(exceptionInfo.getSootMethod().getSignature(),(Local) value, unit);
            Unit defUnit = defsOfOps.get(0);
            if (defUnit.equals(unit)) {
                return ;
            } else if (defUnit instanceof JIdentityStmt) {
                JIdentityStmt identityStmt = (JIdentityStmt)defUnit;
                identityStmt.getRightOp();
                if(identityStmt.getRightOp() instanceof  ThisRef){
                    //from static value
                    exceptionInfo.addRelatedStaticValues(value);
                } else if(identityStmt.getRightOp() instanceof  ParameterRef) {
                    //from parameter
                    exceptionInfo.addRelatedParamValue(value);
                }
            } else if (defUnit instanceof JAssignStmt) {
                JAssignStmt assignStmt = (JAssignStmt) defUnit;
                Value rightOp = assignStmt.getRightOp();
                InvokeExpr invokeExpr = SootUtils.getInvokeExp(assignStmt);
                if (rightOp instanceof  Local) {
                    extendRelatedValues(exceptionInfo, defUnit, rightOp, history);
                }else if (rightOp instanceof  InstanceFieldRef){
                    Value base = ((InstanceFieldRef)rightOp).getBase();
                    extendRelatedValues(exceptionInfo, defUnit, base, history);
                }else if (rightOp instanceof Expr){
                    if (rightOp instanceof  InstanceInvokeExpr) {
                        Value base = ((InstanceInvokeExpr) invokeExpr).getBase();
                        extendRelatedValues(exceptionInfo, defUnit, base, history);
                    }else if (rightOp instanceof  AbstractCastExpr || rightOp instanceof  AbstractBinopExpr || rightOp instanceof  AbstractUnopExpr){
                       for(ValueBox vb: rightOp.getUseBoxes()){
                           extendRelatedValues(exceptionInfo, defUnit, vb.getValue(), history);
                       }
                    }else{
                        //JStaticInvokeExpr, Constant, JInstanceOfExpr, JNewArrayExpr
//                        System.out.println("todo1, consider "+ rightOp.getClass().getName());
                    }
                }else if (rightOp instanceof  StaticFieldRef){
                    //from static value
                    exceptionInfo.addRelatedStaticValues(value);
                }else {
                    //Constant, JArrayRef
//                    System.out.println("todo2, consider "+ rightOp.getClass().getName());
                }


            } else {
                System.out.println(defUnit.getClass().getName() + "::" + defUnit);
            }
        }
        return;
    }

    /**
     * get the goto destination of IfStatement
     * @param body
     * @return
     */
    private List<Unit> getGotoTargets(Body body) {
        List<Unit> res = new ArrayList<Unit>();
        for(Unit u : body.getUnits()){
            if(u instanceof JIfStmt){
                JIfStmt ifStmt = (JIfStmt)u;
                res.add(ifStmt.getTargetBox().getUnit());
            }
            else if(u instanceof GotoStmt){
                GotoStmt gotoStmt = (GotoStmt)u;
                res.add(gotoStmt.getTargetBox().getUnit());
            }
        }
        return res;
    }

    /**
     * get the msg info for an ExceptionInfo
     * @param sootMethod
     * @param unit
     * @param exceptionInfo
     * @param times
     */
    public void getExceptionMessage(SootMethod sootMethod, Unit unit, ExceptionInfo exceptionInfo, List<Integer> times){
        Body body = sootMethod.getActiveBody();
        BriefUnitGraph unitGraph = new BriefUnitGraph(body);
        String exceptionClassName = exceptionInfo.getExceptionType();
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
                            getMsgContentByTracingValue(sootMethod, (Local) arg, unit, message);
                            exceptionInfo.setExceptionMsg(message.get(0));
                        } else if (arg instanceof Constant) {
                            StringConstant arg1 = (StringConstant) arg;
                            exceptionInfo.setExceptionMsg(arg1.value);
                        }
                    }
                } else {
                    getExceptionMessage(sootMethod, predUnit, exceptionInfo,times);
                }
            } else {
                getExceptionMessage(sootMethod, predUnit, exceptionInfo,times);
            }
        }
    }


    /**
     * getMsgContentByTracingValue
     * @param sootMethod
     * @param localTemp
     * @param unit
     * @param message 只有一个元素
     */
    public void getMsgContentByTracingValue(SootMethod sootMethod, Local localTemp, Unit unit, List<String> message){
        List<Unit> defsOfOps = SootUtils.getDefOfLocal(sootMethod.getSignature(),localTemp, unit);
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
                        getMsgContentByTracingValue(sootMethod, (Local) value, unit, message);
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
                        getMsgContentByTracingValue(sootMethod, (Local) value, unit, message);
                    }
                }
            } else if (rightOp instanceof NewExpr) {
                NewExpr rightOp1 = (NewExpr) rightOp;
                if (rightOp1.getBaseType().toString().equals("java.lang.StringBuilder")) {
                    traceStringBuilderBack(sootMethod, defOfLocal, message, 0);
                }
            } else if (rightOp instanceof Local) {
                getMsgContentByTracingValue(sootMethod, (Local) rightOp, unit, message ) ;
            }
        }
    }

    /**
     * traceStringBuilderBack
     * @param sootMethod
     * @param unit
     * @param message
     * @param index
     */
    public void traceStringBuilderBack(SootMethod sootMethod, Unit unit, List<String> message, int index){
        if (index > 10) {
            return;
        }
        Body body = sootMethod.getActiveBody();
        BriefUnitGraph unitGraph = new BriefUnitGraph(body);
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
            traceStringBuilderBack(sootMethod, succs, message, index + 1);
        }
    }
}
