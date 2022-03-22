package main.java.client.exception;

import com.google.common.collect.Lists;
import main.java.Analyzer;
import main.java.Global;
import main.java.analyze.utils.ConstantUtils;
import main.java.analyze.utils.SootUtils;
import main.java.analyze.utils.StringUtils;
import main.java.client.statistic.model.StatisticResult;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.jimple.toolkits.callgraph.Edge;
import soot.shimple.PhiExpr;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.scalar.UnitValueBoxPair;
import soot.toolkits.scalar.ValueUnitPair;

import java.util.*;

/**
 * @Author hanada
 * @Date 2022/3/11 15:21
 * @Version 1.0
 */
public class ExceptionAnalyzer extends Analyzer {
    List<ExceptionInfo> exceptionInfoList;

    public ExceptionAnalyzer(StatisticResult ignoredResult) {
        super();
        exceptionInfoList = new ArrayList<>();
        Global.v().getAppModel().setExceptionInfoList(exceptionInfoList);
    }

    /**
     * true: not analyze
     * @param sootMethod
     * @return
     */
    private boolean filterMethod(SootMethod sootMethod) {
        List<String> mtds = new ArrayList<>();
        mtds.add("acquireReference");
        mtds.add("executeOnExecutor");
        mtds.add("throwIfClosedLocked");
        mtds.add("onDowngrade");
        mtds.add("bindServiceCommon");
        mtds.add("checkListener");
        mtds.add("forgetReceiverDispatcher");
        mtds.add("forgetServiceDispatcher");
        mtds.add("Spinner: void setAdapter");
        mtds.add("AudioRecord: void startRecording");
        for(String tag: mtds){
            if (sootMethod.getSignature().contains(tag)) {
                return false;
            }
        }
        return true;
    }
    @Override
    public void analyze() {
        HashSet<SootClass> applicationClasses = new HashSet<>(Scene.v().getApplicationClasses());
        for (SootClass sootClass : applicationClasses) {
            if(!sootClass.getPackageName().startsWith(ConstantUtils.PKGPREFIX)) continue;
            HashSet<SootMethod> sootMethods = new HashSet<>(sootClass.getMethods());
            for (SootMethod sootMethod : sootMethods) {
                if(filterMethod(sootMethod)) continue;
                if (sootMethod.hasActiveBody()) {
                    try {
                        analyzeMethod(sootMethod);
                    } catch (Exception |  Error e) {
                        System.out.println("Exception |  Error:::" + sootMethod.getSignature());
                        e.printStackTrace();
                    }
                }
            }
        }
        System.out.println("TotalCaught:::" + exceptionInfoList.size());
    }


    /**
     * get message of an exception
     */
    public void analyzeMethod(SootMethod sootMethod){
        Body body = sootMethod.getActiveBody();
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
     */
    public void analyzeThrowUnits(SootMethod sootMethod,  Local localTemp, Unit unit){
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
                        creatNewExceptionInfo(sootMethod, unit, s);
                    }
                } else if (rightValue instanceof Local) {
                    analyzeThrowUnits(sootMethod, (Local) rightValue, unit);
                } else if (rightValue instanceof JCastExpr) {
                    JCastExpr castExpr = (JCastExpr) rightValue;
                    String s = castExpr.getType().toString();
                    if (s.endsWith("Exception") || s.equals("java.lang.Throwable")) {
                        creatNewExceptionInfo(sootMethod, unit, s);
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
                    //todo
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
                        creatNewExceptionInfo(sootMethod, unit, s);
                    }
                } else if (rightValue instanceof ParameterRef) {
                    ParameterRef rightValue1 = (ParameterRef) rightValue;
                    String s = rightValue1.getType().toString();
                    if (s.endsWith("Exception") || s.equals("java.lang.Throwable")) {
                        creatNewExceptionInfo(sootMethod, unit, s);
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
     */
    private void creatNewExceptionInfo(SootMethod sootMethod, Unit unit, String exceptionName) {
        ExceptionInfo exceptionInfo =  new ExceptionInfo(sootMethod, unit, exceptionName);
        getExceptionMessage(sootMethod, unit, exceptionInfo, new ArrayList<>());
        getExceptionCondition(sootMethod, unit, exceptionInfo, new HashSet<>());
        if(exceptionInfo.getRelatedParamValues().size()>0 && exceptionInfo.getRelatedFieldValues().size() ==0)
            getExceptionCallerByParam(sootMethod, exceptionInfo, new HashSet<>(),1,RelatedMethodSource.CALLER);
        else if(exceptionInfo.getRelatedParamValues().size()==0 && exceptionInfo.getRelatedFieldValues().size()>0) {
            getExceptionCallerByField(sootMethod, exceptionInfo, new HashSet<>(), 1,RelatedMethodSource.FIELD);
        }else if(exceptionInfo.getRelatedParamValues().size()>0 && exceptionInfo.getRelatedFieldValues().size()>0){
            getExceptionCallerByParam(sootMethod, exceptionInfo, new HashSet<>(),1, RelatedMethodSource.CALLER);
            getExceptionCallerByField(sootMethod, exceptionInfo, new HashSet<>(), 1, RelatedMethodSource.FIELD);
        }
        exceptionInfoList.add(exceptionInfo);
    }

    /**
     * getExceptionCaller
     * @param sootMethod
     * @param exceptionInfo
     */
    private void getExceptionCallerByParam(SootMethod sootMethod, ExceptionInfo exceptionInfo, Set<SootMethod> callerHistory, int depth, RelatedMethodSource mtdSource) {
        if(callerHistory.contains(sootMethod) || depth >ConstantUtils.CALLDEPTH)
            return;
        callerHistory.add(sootMethod);
        for (Iterator<Edge> it = Global.v().getAppModel().getCg().edgesInto(sootMethod); it.hasNext(); ) {
            Edge edge = it.next();
            SootMethod edgeSource = edge.getSrc().method();
            if(edgeSource.isPublic()) {
                RelatedMethod addMethod = new RelatedMethod(edgeSource.getSignature(), mtdSource, depth);
                if(edgeSource.getDeclaringClass() == exceptionInfo.getSootMethod().getDeclaringClass())
                    exceptionInfo.addRelatedMethodsInSameClass(addMethod);
                else
                    exceptionInfo.addRelatedMethodsInDiffClass(addMethod);
                exceptionInfo.addRelatedMethods(edgeSource.getSignature());
            }
        }
        for (Iterator<Edge> it = Global.v().getAppModel().getCg().edgesInto(sootMethod); it.hasNext(); ) {
            Edge edge = it.next();
            SootMethod edgeSource = edge.getSrc().method();
//            if(exceptionInfo.getCallerMethods().size()>2000) {
//                continue;
//            }
            getExceptionCallerByParam(edgeSource, exceptionInfo, callerHistory, depth + 1, mtdSource);
        }

    }

    private void getExceptionCallerByField(SootMethod sootMethod, ExceptionInfo exceptionInfo, HashSet<SootMethod> callerHistory, int depth,RelatedMethodSource mtdSource) {
        for(SootField field: exceptionInfo.getRelatedFieldValues()){
            for(SootMethod otherMethod: sootMethod.getDeclaringClass().getMethods()){
                if(!otherMethod.hasActiveBody()) continue;
                if(fieldIsChanged(field, otherMethod)){
                    if(otherMethod.isPublic()) {
                        RelatedMethod addMethod = new RelatedMethod(otherMethod.getSignature(),mtdSource,depth);
                        if(otherMethod.getDeclaringClass() == exceptionInfo.getSootMethod().getDeclaringClass())
                            exceptionInfo.addRelatedMethodsInSameClass(addMethod);
                        else
                            exceptionInfo.addRelatedMethodsInDiffClass(addMethod);
                        exceptionInfo.addRelatedMethods(otherMethod.getSignature());
                    }
                    getExceptionCallerByParam(otherMethod, exceptionInfo, callerHistory, depth+1, RelatedMethodSource.FIELDCALLER);
                }
            }
        }
    }

    private boolean fieldIsChanged(SootField field, SootMethod sootMethod) {
        for(Unit u: sootMethod.getActiveBody().getUnits()){
            if(u instanceof  JAssignStmt){
                JAssignStmt jAssignStmt = (JAssignStmt) u;
                if(jAssignStmt.getLeftOp() instanceof  FieldRef){
                    if (field ==  jAssignStmt.getFieldRef().getField()) {
                        return true;
                    }
                }else if(jAssignStmt.getRightOp() instanceof  FieldRef){
                    if (field ==  jAssignStmt.getFieldRef().getField()) {
                        List<UnitValueBoxPair> uses = SootUtils.getUseOfLocal(sootMethod.getSignature(), jAssignStmt);
                        for(UnitValueBoxPair pair:uses){
                            if(pair.getUnit() instanceof JAssignStmt){
                                JAssignStmt jAssignStmt2 = (JAssignStmt) pair.getUnit();
                                if(jAssignStmt2.getRightOp() == pair.getValueBox().getValue()){
                                    return  false;
                                }
                                return true;
                            }else if(pair.getUnit() instanceof JInvokeStmt){
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }


    /**
     * get the latest condition info for an ExceptionInfo
     * only analyze one level if condition, forward
     */
    private void getExceptionCondition(SootMethod sootMethod, Unit unit, ExceptionInfo exceptionInfo, Set<Unit> getCondHistory) {
        //i0 := @parameter0: int; if i0 < 0 goto label10; -- label10: throw $r4;
        //r6 := @this: android.renderscript.AllocationAdapter;-->  i12_1 = Phi(i12 #0, i12_2 #7);  if i12_1 == 1 goto label02; -- label02: throw $r17;
        if(getCondHistory.contains(unit) || getCondHistory.size()> ConstantUtils.CONDITIONHISTORYSIZE) return;// if defUnit is not a pred of unit
        getCondHistory.add(unit);
        Body body = sootMethod.getActiveBody();
        BriefUnitGraph unitGraph = new BriefUnitGraph(body);
        List<Unit> allPreds = new ArrayList<>();
        SootUtils.getAllPredsofUnit(sootMethod, unit,allPreds);
        List<Unit> gotoTargets = getGotoTargets(body);
        List<Unit> predsOf = unitGraph.getPredsOf(unit);
        for (Unit predUnit : predsOf) {
            if(exceptionInfo.getTracedUnits().size()>0 && gotoTargets.contains(predUnit))
                break;
            if (predUnit instanceof IfStmt) {
                exceptionInfo.getTracedUnits().add(predUnit);
                IfStmt ifStmt = (IfStmt) predUnit;
                Value cond = ifStmt.getCondition();
                exceptionInfo.addRelatedCondition(cond);
                if(cond instanceof ConditionExpr){
                    Value value = ((ConditionExpr)cond).getOp1();
                    extendRelatedValues(allPreds, exceptionInfo, predUnit, value, new ArrayList<>(),getCondHistory);
                }
            }else if (predUnit instanceof SwitchStmt) {
                exceptionInfo.getTracedUnits().add(predUnit);
                SwitchStmt swStmt = (SwitchStmt) predUnit;
                Value key = swStmt.getKey();
                extendRelatedValues(allPreds, exceptionInfo, predUnit, key, new ArrayList<>(), getCondHistory);
            }else if (predUnit instanceof JIdentityStmt ) {
                JIdentityStmt stmt = (JIdentityStmt) predUnit;
                if(stmt.getRightOp() instanceof CaughtExceptionRef){
                    exceptionInfo.addCaughtedValues(stmt.getRightOp());
                }
            }
            getExceptionCondition(sootMethod, predUnit, exceptionInfo,getCondHistory);
        }
    }

    /**
     * tracing the values relates to the one used in if condition
     */
    private void extendRelatedValues(List<Unit> allPreds, ExceptionInfo exceptionInfo, Unit unit, Value value,
                                     List<Value> valueHistory, Set<Unit> getCondHistory) {
        if(valueHistory.contains(value) || !allPreds.contains(unit)) return;// if defUnit is not a pred of unit
        valueHistory.add(value);
        if(value instanceof  Local) {
            String methodSig = exceptionInfo.getSootMethod().getSignature();
            for(Unit defUnit: SootUtils.getDefOfLocal(methodSig,value, unit)) {
                //if the define unit is under a check
                if (defUnit instanceof JIdentityStmt) {
                    JIdentityStmt identityStmt = (JIdentityStmt) defUnit;
                    identityStmt.getRightOp();
                    if (identityStmt.getRightOp() instanceof ParameterRef) {
                        //from parameter
                        exceptionInfo.addRelatedParamValue(identityStmt.getRightOp());
                    }else if(identityStmt.getRightOp() instanceof CaughtExceptionRef){
                        exceptionInfo.addCaughtedValues(identityStmt.getRightOp());
                    }
                } else if (defUnit instanceof JAssignStmt) {
                    Value rightOp = ((JAssignStmt) defUnit).getRightOp();
                    if (rightOp instanceof Local) {
                        extendRelatedValues(allPreds, exceptionInfo, defUnit, rightOp, valueHistory, getCondHistory);
                    } else if (rightOp instanceof AbstractInstanceFieldRef) {
                        SootField f = ((AbstractInstanceFieldRef) rightOp).getField();
                        exceptionInfo.addRelatedFieldValues(f);
                    } else if (rightOp instanceof Expr) {
                        if (rightOp instanceof InvokeExpr) {
                            InvokeExpr invokeExpr = SootUtils.getInvokeExp(defUnit);
                            for (Value val : invokeExpr.getArgs())
                                extendRelatedValues(allPreds, exceptionInfo, defUnit, val, valueHistory, getCondHistory);
                            if (rightOp instanceof InstanceInvokeExpr) {
                                extendRelatedValues(allPreds, exceptionInfo, defUnit, ((InstanceInvokeExpr) rightOp).getBase(), valueHistory, getCondHistory);
                            }
                        } else if (rightOp instanceof AbstractInstanceOfExpr || rightOp instanceof AbstractCastExpr
                                || rightOp instanceof AbstractBinopExpr || rightOp instanceof AbstractUnopExpr) {
                            for (ValueBox vb : rightOp.getUseBoxes()) {
                                extendRelatedValues(allPreds, exceptionInfo, defUnit, vb.getValue(), valueHistory, getCondHistory);
                            }
                        } else if (rightOp instanceof NewExpr) {
                            List<UnitValueBoxPair> usesOfOps = SootUtils.getUseOfLocal(exceptionInfo.getSootMethod().getSignature(), defUnit);
                            for (UnitValueBoxPair use : usesOfOps) {
                                for (ValueBox vb : use.getUnit().getUseBoxes())
                                    extendRelatedValues(allPreds, exceptionInfo, use.getUnit(), vb.getValue(), valueHistory, getCondHistory);
                            }
                        } else {
                            getExceptionCondition(exceptionInfo.getSootMethod(), defUnit, exceptionInfo, getCondHistory);
                        }
                    } else if (rightOp instanceof StaticFieldRef) {
                        //from static field value
                        exceptionInfo.addRelatedFieldValues(((StaticFieldRef) rightOp).getField());
                        return;
                    } else {
                        getExceptionCondition(exceptionInfo.getSootMethod(), defUnit, exceptionInfo, getCondHistory);
                    }
                } else {
                    System.out.println(defUnit.getClass().getName() + "::" + defUnit);
                }
            }
        }
    }

    /**
     * get the goto destination of IfStatement
     */
    private List<Unit> getGotoTargets(Body body) {
        List<Unit> res = new ArrayList<>();
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
     */
    private void getExceptionMessage(SootMethod sootMethod, Unit unit, ExceptionInfo exceptionInfo, List<Integer> times){
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
     */
    private void getMsgContentByTracingValue(SootMethod sootMethod, Local localTemp, Unit unit, List<String> message){
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
                    String s;
                    if (argConstant instanceof Constant) {
                        if (argConstant instanceof StringConstant) {
                            String value = ((StringConstant) argConstant).value;
                            s = value + message.get(0);
                        } else {
                            s = argConstant + message.get(0);
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
     */
    private void traceStringBuilderBack(SootMethod sootMethod, Unit unit, List<String> message, int index){
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
                    String s;
                    if (argConstant instanceof Constant) {
                        if (argConstant instanceof StringConstant) {
                            String value = ((StringConstant) argConstant).value;
                            s = message.get(0) + value;
                        } else {
                            s = message.get(0) + argConstant;
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
