package main.java.client.exception;

import com.alibaba.fastjson.JSONArray;
import com.google.common.collect.Lists;
import main.java.Analyzer;
import main.java.Global;
import main.java.MyConfig;
import main.java.analyze.model.sootAnalysisModel.Context;
import main.java.analyze.model.sootAnalysisModel.Counter;
import main.java.analyze.model.sootAnalysisModel.NestableObj;
import main.java.analyze.utils.ConstantUtils;
import main.java.analyze.utils.SootUtils;
import main.java.analyze.utils.StringUtils;
import main.java.analyze.utils.ValueObtainer;
import main.java.analyze.utils.output.FileUtils;
import main.java.client.statistic.model.StatisticResult;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.jimple.toolkits.callgraph.Edge;
import soot.shimple.PhiExpr;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.UnitValueBoxPair;
import soot.toolkits.scalar.ValueUnitPair;

import java.io.File;
import java.util.*;

import static main.java.analyze.utils.SootUtils.getFiledValueAssigns;

/**
 * @Author hanada
 * @Date 2022/3/11 15:21
 * @Version 1.0
 */
public class ExceptionAnalyzer extends Analyzer {
    List<ExceptionInfo> exceptionInfoList;
    JSONArray exceptionListElement  = new JSONArray(new ArrayList<>());
    Set<String> permissionSet = new HashSet<>();
    public ExceptionAnalyzer(StatisticResult ignoredResult) {
        super();
    }


    /**
     * true: not analyze
     * @param sootMethod
     * @return
     */
    //<android.app.ContextImpl: android.content.Intent registerReceiver(android.content.BroadcastReceiver
    private boolean filterMethod(SootMethod sootMethod) {
        List<String> mtds = new ArrayList<>();
        mtds.add("android.app.Instrumentation: void checkStartActivityResult");
        for(String tag: mtds){
            if (sootMethod.getSignature().contains(tag)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void analyze() {
        getPermissionSet();
        getExceptionList();
    }

    private void getExceptionList() {
        JSONArray exceptionListElement  = new JSONArray(new ArrayList<>());
        HashSet<SootClass> applicationClasses = new HashSet<>(Scene.v().getApplicationClasses());
        for (SootClass sootClass : applicationClasses) {
            if(!sootClass.getPackageName().startsWith(ConstantUtils.PKGPREFIX)) continue;
            exceptionInfoList = new ArrayList<>();
            for (SootMethod sootMethod : sootClass.getMethods()) {

                //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
//                if(filterMethod(sootMethod)) continue;

                if (sootMethod.hasActiveBody()) {
                    try {
                        Map<Unit, String> unit2Message = new HashMap<>();
                        Map<Unit,Local> unit2Value = getThrowUnitWithValue(sootMethod);
                        for(Map.Entry<Unit,Local> entry: unit2Value.entrySet()){
                            getThrowUnitWithMessage(unit2Message, sootMethod, entry.getKey(),entry.getValue());
                        }
                        for(Map.Entry<Unit,String> entry: unit2Message.entrySet()) {
                            creatNewExceptionInfo(sootMethod, entry.getKey(), entry.getValue());
                        }

                    } catch (Exception |  Error e) {
                        System.out.println("Exception |  Error:::" + sootMethod.getSignature());
                        e.printStackTrace();
                    }
                }
            }
            ExceptionInfoClientOutput.writeJsonForCurrentClass(sootClass, exceptionInfoList);
            ExceptionInfoClientOutput.getSummaryJsonArray(exceptionInfoList, exceptionListElement);
        }
        ExceptionInfoClientOutput.writeJsonForFramework(exceptionListElement);
    }

    private void getPermissionSet() {
        HashSet<SootClass> applicationClasses = new HashSet<>(Scene.v().getApplicationClasses());
        for (SootClass sootClass : applicationClasses) {
            if (!sootClass.getPackageName().startsWith(ConstantUtils.PKGPREFIX)) continue;
            for (SootMethod sootMethod : sootClass.getMethods()) {
                if (sootMethod.hasActiveBody()) {
                    for(Unit u: sootMethod.getActiveBody().getUnits()){
                        if(u.toString().contains("android.permission.")){
                            String str = u.toString().substring(u.toString().indexOf("android.permission."), u.toString().length());
                            if(str.indexOf('"')>0) {
                                str = str.substring(0, str.indexOf('"'));
                                permissionSet.add(str);
                            }
                        }
                    }
                }
            }
        }
        String folder = MyConfig.getInstance().getResultFolder() +File.separator+ MyConfig.getInstance().getAppName()+File.separator+"Permission"+File.separator;
        FileUtils.createFolder(folder);
        FileUtils.writeList2File(folder,"permission.txt", permissionSet,false);
        System.out.println("permissionSet:::" + permissionSet.size());
    }

    /**
     * get throw units with value from a method
     */
    public Map<Unit,Local> getThrowUnitWithValue(SootMethod sootMethod){
        Map<Unit,Local> unit2Value = new HashMap<>();
        for (Unit unit : sootMethod.getActiveBody().getUnits()) {
            if (unit instanceof ThrowStmt) {
                ThrowStmt throwStmt = (ThrowStmt) unit;
                Value throwValue = throwStmt.getOp();
                if (throwValue instanceof Local) {
                    unit2Value.put(unit,(Local) throwValue);
                }
            }
        }
        return  unit2Value;
    }

    /**
     * get throw units with message from a method
     */
    public void getThrowUnitWithMessage(Map<Unit, String> unit2Message, SootMethod sootMethod, Unit unit, Local localTemp){
        List<Unit> defsOfOps = SootUtils.getDefOfLocal(sootMethod.getSignature(),localTemp, unit);
        if (defsOfOps.size() == 0) return;
        Unit defOfLocal = defsOfOps.get(0);
        if (defOfLocal.equals(unit)) return;

        if (defOfLocal instanceof DefinitionStmt) {
            Value rightValue = ((DefinitionStmt)defOfLocal).getRightOp();
            if (rightValue instanceof NewExpr) {
                NewExpr newRightValue = (NewExpr) rightValue;
                String name = newRightValue.getBaseType().getSootClass().toString();
                unit2Message.put(unit,name);
            } else if (rightValue instanceof NewArrayExpr) {
                NewArrayExpr rightValue1 = (NewArrayExpr) rightValue;
                String s = rightValue1.getBaseType().toString();
                if (s.endsWith("Exception") || s.equals("java.lang.Throwable")) {
                    unit2Message.put(unit,s);
                }
            } else if (rightValue instanceof Local) {
                getThrowUnitWithMessage(unit2Message, sootMethod, unit, (Local) rightValue);
            } else if (rightValue instanceof JCastExpr) {
                JCastExpr castExpr = (JCastExpr) rightValue;
                String s = castExpr.getType().toString();
                if (s.endsWith("Exception") || s.equals("java.lang.Throwable")) {
                    unit2Message.put(unit,s);
                } else {
                    Value value = castExpr.getOpBox().getValue();
                    if (value instanceof Local) {
                        getThrowUnitWithMessage(unit2Message, sootMethod, unit, (Local) value);
                    }
                }
            } else if (rightValue instanceof InvokeExpr) {
                InvokeExpr invokeExpr = (InvokeExpr) rightValue;
                Type returnType = invokeExpr.getMethod().getReturnType();
                if (returnType.toString().endsWith("Exception") || returnType.toString().equals("java.lang.Throwable")) {
                    unit2Message.put(unit,returnType.toString());
                }

            } else if (rightValue instanceof CaughtExceptionRef) {
                //todo
                //caught an Exception here
                //$r1 := @caughtexception;
            } else if (rightValue instanceof PhiExpr) {
                PhiExpr phiExpr = (PhiExpr) rightValue;
                for (ValueUnitPair arg : phiExpr.getArgs()) {
                    if (arg.getValue() instanceof Local) {
                        getThrowUnitWithMessage(unit2Message, sootMethod, unit, (Local) arg.getValue());
                    }
                }
            } if (rightValue instanceof FieldRef) {
                FieldRef rightValue1 = (FieldRef) rightValue;
                String s = rightValue1.getField().getType().toString();
                if (s.endsWith("Exception") || s.equals("java.lang.Throwable")) {
                    unit2Message.put(unit,s);
                }
            } else if (rightValue instanceof ParameterRef) {
                ParameterRef rightValue1 = (ParameterRef) rightValue;
                String s = rightValue1.getType().toString();
                if (s.endsWith("Exception") || s.equals("java.lang.Throwable")) {
                    unit2Message.put(unit,s);
                }
            }  else if (rightValue instanceof ArrayRef) {
                ArrayRef rightValue1 = (ArrayRef) rightValue;
                Value value = rightValue1.getBaseBox().getValue();
                if (value instanceof Local) {
                    getThrowUnitWithMessage(unit2Message, sootMethod, unit, (Local) value);
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
        getConditionandValueFromUnit(sootMethod, unit, exceptionName, exceptionInfo, true);

        Set<Unit> retUnits = new HashSet<>();
        for (Unit u: sootMethod.getActiveBody().getUnits()) {
            if (u instanceof ReturnStmt) {
                retUnits.add(u);
            }
        }
        for(Unit condUnit: exceptionInfo.getConditionUnits()) {
            getRetUnitsFlowIntoConditionUnits(sootMethod, condUnit, retUnits, new HashSet<Unit>());
        }
        for(Unit retUnit: retUnits){
            getConditionandValueFromUnit(sootMethod, retUnit, exceptionName, exceptionInfo, false);
        }
        if(containPermissionString(exceptionInfo.getExceptionMsg())){
            exceptionInfo.setManifestRelated(true);
        }
        String name = sootMethod.getSignature();
        if(name.contains("hardware") || name.contains("opengl")
                || name.contains("nfc") || name.contains("bluetooth") ){
            exceptionInfo.setHardwareRelated(true);
        }
        exceptionInfoList.add(exceptionInfo);
    }

    private void getRetUnitsFlowIntoConditionUnits(SootMethod sootMethod, Unit unit, Set<Unit> retUnits, HashSet<Unit> history) {
        if(history.contains(unit)) return;
        history.add(unit);
        BriefUnitGraph graph = new BriefUnitGraph(sootMethod.getActiveBody());
        for (Unit u: graph.getSuccsOf(unit)) {
            if(u instanceof ReturnStmt){
                retUnits.remove(u);
            }else{
                getRetUnitsFlowIntoConditionUnits(sootMethod, u, retUnits, history);
            }
        }
    }

    private void getConditionandValueFromUnit(SootMethod sootMethod, Unit unit, String exceptionName, ExceptionInfo exceptionInfo, boolean fromThrow) {
        List<String> trace = new ArrayList<>();
        trace.add(sootMethod.getSignature());

        getExceptionCondition(sootMethod, unit, exceptionInfo, new HashSet<>(), fromThrow);
        if(exceptionInfo.getRelatedParamValues().size()>0 && exceptionInfo.getRelatedFieldValues().size() ==0) {
            List<String> newTrace = new ArrayList<>(trace);
            RelatedMethod addMethod = new RelatedMethod(sootMethod.getSignature(), RelatedMethodSource.CALLER, 0, newTrace);
            exceptionInfo.addRelatedMethodsInSameClassMap(addMethod);
            exceptionInfo.addRelatedMethods(sootMethod.getSignature());
            List<String> newTrace2 = new ArrayList<>(trace);
            getExceptionCallerByParam(sootMethod, exceptionInfo, new HashSet<>(), 1,
                    RelatedMethodSource.CALLER, exceptionInfo.getRelatedValueIndex(), newTrace2);
        }else if(exceptionInfo.getRelatedParamValues().size()==0 && exceptionInfo.getRelatedFieldValues().size()>0) {
            List<String> newTrace = new ArrayList<>(trace);
            getExceptionCallerByField(sootMethod, exceptionInfo, new HashSet<>(), 1,
                    RelatedMethodSource.FIELD, newTrace);
        }else if(exceptionInfo.getRelatedParamValues().size()>0 && exceptionInfo.getRelatedFieldValues().size()>0){
            List<String> newTrace = new ArrayList<>(trace);
            getExceptionCallerByField(sootMethod, exceptionInfo, new HashSet<>(), 1,
                    RelatedMethodSource.FIELD, newTrace);
            List<String> newTrace2 = new ArrayList<>(trace);
            getExceptionCallerByParam(sootMethod, exceptionInfo, new HashSet<>(),1,
                    RelatedMethodSource.CALLER, exceptionInfo.getRelatedValueIndex(), newTrace2);
        }
    }

    /**
     * getExceptionCaller
     * @param sootMethod
     * @param exceptionInfo
     * @param trace
     */
    private void getExceptionCallerByParam(SootMethod sootMethod, ExceptionInfo exceptionInfo,
                                           Set<SootMethod> callerHistory, int depth,
                                           RelatedMethodSource mtdSource, Set<Integer> paramIndexCallee, List<String> trace) {
        if(callerHistory.contains(sootMethod) || depth >ConstantUtils.CALLDEPTH)  return;
        callerHistory.add(sootMethod);
        for (Iterator<Edge> it = Global.v().getAppModel().getCg().edgesInto(sootMethod); it.hasNext(); ) {
            Edge edge = it.next();
            SootMethod edgeSourceMtd = edge.getSrc().method();
            Set<Integer> paramIndexCaller = new HashSet<>();
            if(mtdSource == RelatedMethodSource.CALLER){
                paramIndexCaller = SootUtils.getIndexesFromMethod(edge, paramIndexCallee);
                if(paramIndexCaller.size() ==0 ) continue;
            }
            List<SootClass> subClasses = SootUtils.getSubClasses(edgeSourceMtd);
            for (SootClass sootClass : subClasses) {
                boolean flag = false;
                String pkg1 = sootClass.getPackageName();
                String pkg2 = exceptionInfo.getSootMethod().getDeclaringClass().getPackageName();
                if (StringUtils.getPkgPrefix(pkg1, 2).equals(StringUtils.getPkgPrefix(pkg2, 2))
                        || edgeSourceMtd.getName().equals(sootMethod.getName())) {
                    String signature = edgeSourceMtd.getSignature().replace(edgeSourceMtd.getDeclaringClass().getName(), sootClass.getName());
                    SootMethod sm = SootUtils.getSootMethodBySignature(signature);
                    if(sm == null|| sootClass == edgeSourceMtd.getDeclaringClass()) {
                        //filter a set of candidates!!!
                        List<String> newTrace = new ArrayList<>(trace);
                        newTrace.add(0, signature);
                        RelatedMethod addMethodObj = new RelatedMethod(signature, mtdSource, depth, newTrace);
                        addRelatedMethodInstance(edgeSourceMtd, addMethodObj, exceptionInfo);
                        flag = true;
                    }
                    if(sm!=null) {
                        Iterator<SootClass> it2 = sm.getDeclaringClass().getInterfaces().iterator();
                        while (it2.hasNext()) {
                            SootClass interfaceSC = it2.next();
                            for (SootMethod interfaceSM : interfaceSC.getMethods()) {
                                List<String> newTrace = new ArrayList<>(trace);
                                newTrace.add(0, interfaceSM.getSignature());
                                RelatedMethod addMethodObj = new RelatedMethod(interfaceSM.getSignature(), mtdSource, depth, newTrace);
                                addRelatedMethodInstance(edgeSourceMtd, addMethodObj, exceptionInfo);
                                flag = true;
                            }

                        }
                    }
                    if(flag) {
                        List<String> newTrace = new ArrayList<>(trace);
                        newTrace.add(0, edgeSourceMtd.getSignature());
                        getExceptionCallerByParam(edgeSourceMtd, exceptionInfo, callerHistory, depth + 1, mtdSource, paramIndexCaller, newTrace);
                    }
                }
            }
        }
    }

    private void addRelatedMethodInstance(SootMethod edgeSource, RelatedMethod addMethod, ExceptionInfo exceptionInfo) {
        if(edgeSource.isPublic()) {
            if (edgeSource.getDeclaringClass() == exceptionInfo.getSootMethod().getDeclaringClass())
                exceptionInfo.addRelatedMethodsInSameClassMap(addMethod);
            else
                exceptionInfo.addRelatedMethodsInDiffClassMap(addMethod);
            exceptionInfo.addRelatedMethods(addMethod.getMethod());
        }
    }


    /**
     * getExceptionCallerByField
     * @param sootMethod
     * @param exceptionInfo
     * @param callerHistory
     * @param depth
     * @param mtdSource
     * @param trace
     */
    private void getExceptionCallerByField(SootMethod sootMethod, ExceptionInfo exceptionInfo, HashSet<SootMethod> callerHistory, int depth, RelatedMethodSource mtdSource, List<String> trace) {
        for(SootField field: exceptionInfo.getRelatedFieldValues()){
            for(SootMethod otherMethod: sootMethod.getDeclaringClass().getMethods()){
                if(!otherMethod.hasActiveBody()) continue;
//                //if only one field and one condition, judge whether the condition is satisfied
//                //satisfy and not satisfy both may relate to bug fix... so do not judge
//                if(exceptionInfo.getRelatedFieldValues().size()==1 && exceptionInfo.getConditions().size()==1){
//                    boolean check = conditionCheck(exceptionInfo,otherMethod, field);
//                    if(!check) continue;
//                }
                if(SootUtils.fieldIsChanged(field, otherMethod)){
                    if(otherMethod.isPublic()) {
                        List<String> newTrace = new ArrayList<>(trace);
                        newTrace.add(0,"key field: " + field.toString());
                        newTrace.add(0,otherMethod.getSignature());
                        RelatedMethod addMethod = new RelatedMethod(otherMethod.getSignature(),mtdSource,depth, trace);
                        if(otherMethod.getDeclaringClass() == exceptionInfo.getSootMethod().getDeclaringClass())
                            exceptionInfo.addRelatedMethodsInSameClassMap(addMethod);
                        else
                            exceptionInfo.addRelatedMethodsInDiffClassMap(addMethod);
                        exceptionInfo.addRelatedMethods(otherMethod.getSignature());
                    }
                    List<String> newTrace = new ArrayList<>(trace);
                    newTrace.add(0,"key field: " + field.toString());
                    newTrace.add(0,otherMethod.getSignature());
                    getExceptionCallerByParam(otherMethod, exceptionInfo, callerHistory,
                            depth+1, RelatedMethodSource.FIELDCALLER, new HashSet<>(), newTrace);
                }
            }
        }
    }

    /**
     * conditionCheck
     *
     * @param exceptionInfo
     * @param sootMethod
     * @param field
     * @return
     */
    private boolean conditionCheck(ExceptionInfo exceptionInfo, SootMethod sootMethod, SootField field) {
        List<String> assigns = new ArrayList<>();
        for(Unit u: sootMethod.getActiveBody().getUnits()){
            if(u instanceof  JAssignStmt){
                JAssignStmt jAssignStmt = (JAssignStmt) u;
                if(jAssignStmt.getLeftOp() instanceof  FieldRef){
                    if (field ==  jAssignStmt.getFieldRef().getField()) {
                        ValueObtainer vo = new ValueObtainer(sootMethod.getSignature(), "", new Context(), new Counter());
                        NestableObj nestableObj = vo.getValueofVar(jAssignStmt.getRightOp(), u, 0);
                        for (String res : nestableObj.getValues()) {
                            assigns.add(res);
                        }
                    }
                }
            }
        }
        for(String val : assigns) {
            ValueObtainer vo = new ValueObtainer(exceptionInfo.getSootMethod().getSignature(), "", new Context(), new Counter());
            Value condition = exceptionInfo.getConditions().get(0);
            Unit conditionUnit = exceptionInfo.getConditionUnits().get(0);
            List<String> conditionRightValues = vo.getValueofVar(((BinopExpr) condition).getOp2(),conditionUnit, 0).getValues();
            if (condition instanceof JNeExpr) {
                if(!conditionRightValues.contains(val))
                    return false;
            }else if (condition instanceof JEqExpr) {
                if(conditionRightValues.contains(val))
                    return false;
            }
//            else if (condition instanceof JGeExpr) {
//            }else if (condition instanceof JGtExpr) {
//            }else if (condition instanceof JLeExpr) {
//            }else if (condition instanceof JLtExpr) {
//            }
        }
        return true;
    }


    /**
     * get the latest condition info for an ExceptionInfo
     * only analyze one level if condition, forward
     */
    private void getExceptionCondition(SootMethod sootMethod, Unit unit, ExceptionInfo exceptionInfo, Set<Unit> getCondHistory, boolean fromThrow) {
        if(getCondHistory.contains(unit) || getCondHistory.size()> ConstantUtils.CONDITIONHISTORYSIZE) return;// if defUnit is not a pred of unit
        getCondHistory.add(unit);
        Body body = sootMethod.getActiveBody();
        ExceptionalUnitGraph unitGraph = new ExceptionalUnitGraph(body);
        List<Unit> allPreds = new ArrayList<>();
        SootUtils.getAllPredsofUnit(sootMethod, unit,allPreds);
        List<Unit> gotoTargets = getGotoTargets(body);
        List<Unit> predsOf = unitGraph.getPredsOf(unit);
        for (Unit predUnit : predsOf) {
            if(fromThrow && exceptionInfo.getTracedUnits().size()>0 && gotoTargets.contains(predUnit))
                continue;
            if (predUnit instanceof IfStmt) {
                exceptionInfo.getTracedUnits().add(predUnit);
                IfStmt ifStmt = (IfStmt) predUnit;
                Value cond = ifStmt.getCondition();
                exceptionInfo.addRelatedCondition(cond);
                exceptionInfo.getConditionUnits().add(ifStmt);
                if(cond instanceof ConditionExpr){
                    Value value = ((ConditionExpr)cond).getOp1();
                    extendRelatedValues(allPreds, exceptionInfo, predUnit, value, new ArrayList<>(),getCondHistory, fromThrow);
                }
            }else if (predUnit instanceof SwitchStmt) {
                exceptionInfo.getTracedUnits().add(predUnit);
                SwitchStmt swStmt = (SwitchStmt) predUnit;
                Value key = swStmt.getKey();
                extendRelatedValues(allPreds, exceptionInfo, predUnit, key, new ArrayList<>(), getCondHistory, fromThrow);
            }else if (predUnit instanceof JIdentityStmt ) {
                JIdentityStmt stmt = (JIdentityStmt) predUnit;
                if(stmt.getRightOp() instanceof CaughtExceptionRef){
                    exceptionInfo.addCaughtedValues(stmt.getRightOp());
                    //analyzed try-catch contents
                }
            }
            getExceptionCondition(sootMethod, predUnit, exceptionInfo,getCondHistory, fromThrow);
        }
    }

    /**
     * tracing the values relates to the one used in if condition
     */
    private String extendRelatedValues(List<Unit> allPreds, ExceptionInfo exceptionInfo, Unit unit, Value value,
                                     List<Value> valueHistory, Set<Unit> getCondHistory , boolean fromThrow) {
        if(valueHistory.contains(value) || !allPreds.contains(unit)) return "";// if defUnit is not a pred of unit
        valueHistory.add(value);
        if(value instanceof  Local) {
            String methodSig = exceptionInfo.getSootMethod().getSignature();
            for(Unit defUnit: SootUtils.getDefOfLocal(methodSig,value, unit)) {
                //if the define unit is under a check
                if(defUnit.toString().contains("android.content.pm.ApplicationInfo: int targetSdkVersion")){
                    exceptionInfo.setOsVersionRelated(true);
                }else if(defUnit.toString().contains("android.content.res.AssetManager")){
                  exceptionInfo.setAssessRelated(true);
                }
//                else if(defUnit.toString().contains("android.content.pm.ApplicationInfo")){
//                    exceptionInfo.setManifestRelated(true);
//                }
                if (defUnit instanceof JIdentityStmt) {
                    JIdentityStmt identityStmt = (JIdentityStmt) defUnit;
                    identityStmt.getRightOp();
                    if (identityStmt.getRightOp() instanceof ParameterRef) {//from parameter
                        exceptionInfo.addRelatedParamValue(identityStmt.getRightOp());
                        exceptionInfo.getRelatedValueIndex().add(((ParameterRef) identityStmt.getRightOp()).getIndex());
                        return "ParameterRef";
                    }else if(identityStmt.getRightOp() instanceof CaughtExceptionRef){
                        exceptionInfo.addCaughtedValues(identityStmt.getRightOp());
                        return "CaughtExceptionRef";
                    }else if(identityStmt.getRightOp() instanceof ThisRef){
                       return "ThisRef";
                    }
                } else if (defUnit instanceof JAssignStmt) {
                    Value rightOp = ((JAssignStmt) defUnit).getRightOp();
                    if (rightOp instanceof Local) {
                        extendRelatedValues(allPreds, exceptionInfo, defUnit, rightOp, valueHistory, getCondHistory, fromThrow);
                    } else if (rightOp instanceof AbstractInstanceFieldRef) {
                        //if base is from parameter, field is omitted, if base is this, parameter is recorded
                        Value base = ((AbstractInstanceFieldRef) rightOp).getBase();
                        String defType = extendRelatedValues(allPreds, exceptionInfo, defUnit, base, valueHistory, getCondHistory, fromThrow);
                        //if the this variable is assigned from parameter, it is not field related.
                        if(defType.equals("ThisRef")){
                            SootField field = ((AbstractInstanceFieldRef) rightOp).getField();
                            Value baseF = ((AbstractInstanceFieldRef) rightOp).getBase();
                            List<Value> rightValues = getFiledValueAssigns(baseF, field, allPreds);
                            for(Value rv: rightValues){
                                extendRelatedValues(allPreds, exceptionInfo, defUnit, rv, valueHistory, getCondHistory, fromThrow);
                            }
                            exceptionInfo.addRelatedFieldValues(field);

                        }
                    } else if (rightOp instanceof Expr) {
                        if (rightOp instanceof InvokeExpr) {
                            InvokeExpr invokeExpr = SootUtils.getInvokeExp(defUnit);
                            for (Value val : invokeExpr.getArgs())
                                extendRelatedValues(allPreds, exceptionInfo, defUnit, val, valueHistory, getCondHistory, fromThrow);
                            if (rightOp instanceof InstanceInvokeExpr) {
                                extendRelatedValues(allPreds, exceptionInfo, defUnit, ((InstanceInvokeExpr) rightOp).getBase(), valueHistory, getCondHistory, fromThrow);
                            }
                        } else if (rightOp instanceof AbstractInstanceOfExpr || rightOp instanceof AbstractCastExpr
                                || rightOp instanceof AbstractBinopExpr || rightOp instanceof AbstractUnopExpr) {
                            for (ValueBox vb : rightOp.getUseBoxes()) {
                                extendRelatedValues(allPreds, exceptionInfo, defUnit, vb.getValue(), valueHistory, getCondHistory, fromThrow);
                            }
                        } else if (rightOp instanceof NewExpr) {
                            List<UnitValueBoxPair> usesOfOps = SootUtils.getUseOfLocal(exceptionInfo.getSootMethod().getSignature(), defUnit);
                            for (UnitValueBoxPair use : usesOfOps) {
                                for (ValueBox vb : use.getUnit().getUseBoxes())
                                    extendRelatedValues(allPreds, exceptionInfo, use.getUnit(), vb.getValue(), valueHistory, getCondHistory, fromThrow);
                            }
                        } else {
                            getExceptionCondition(exceptionInfo.getSootMethod(), defUnit, exceptionInfo, getCondHistory, fromThrow);
                        }
                    } else if (rightOp instanceof StaticFieldRef) {
                        //from static field value
                        exceptionInfo.addRelatedFieldValues(((StaticFieldRef) rightOp).getField());
                    }else if (rightOp instanceof JArrayRef) {
                        JArrayRef jArrayRef = (JArrayRef) rightOp;
                        extendRelatedValues(allPreds, exceptionInfo, defUnit, jArrayRef.getBase(), valueHistory, getCondHistory, fromThrow);
                    }else if (rightOp instanceof JInstanceFieldRef) {
                        JInstanceFieldRef jInstanceFieldRef = (JInstanceFieldRef) rightOp;
                        extendRelatedValues(allPreds, exceptionInfo, defUnit, jInstanceFieldRef.getBase(), valueHistory, getCondHistory, fromThrow);
                    }else {
                        getExceptionCondition(exceptionInfo.getSootMethod(), defUnit, exceptionInfo, getCondHistory, fromThrow);
                    }
                } else {
                    System.out.println(defUnit.getClass().getName() + "::" + defUnit);
                }
            }
        }
        return "";
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
                            String exceptionMsg = addQeSymbolToMessage(message.get(0));
                            exceptionInfo.setExceptionMsg(exceptionMsg);
                        } else if (arg instanceof Constant) {
                            StringConstant arg1 = (StringConstant) arg;
                            String exceptionMsg = addQeSymbolToMessage(arg1.value);
                            exceptionInfo.setExceptionMsg(exceptionMsg);
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

    private String addQeSymbolToMessage(String input) {
        String exceptionMsg = "";
        String[] ss =input.split("\\Q[\\s\\S]*\\E");
        for(int i= 0; i<ss.length-1;i++){
            exceptionMsg+="\\Q"+ss[i]+"\\E"+"[\\s\\S]*";
        }
        exceptionMsg+="\\Q"+ss[ss.length-1]+"\\E";
        if(input.endsWith("[\\s\\S]*"))
            exceptionMsg+="[\\s\\S]*";

        String temp = "";
        while(!exceptionMsg.equals(temp)) {
            temp= exceptionMsg;
            exceptionMsg = exceptionMsg.replace("\\Q\\E", "");
            exceptionMsg = exceptionMsg.replace("\\E\\Q", "");
            exceptionMsg = exceptionMsg.replace("[\\s\\S]*[\\s\\S]*", "[\\s\\S]*");
        }
        return exceptionMsg;
    }


    /**
     * getMsgContentByTracingValue
     */
    private void getMsgContentByTracingValue(SootMethod sootMethod, Local localTemp, Unit unit, List<String> message){
        List<Unit> defsOfOps = SootUtils.getDefOfLocal(sootMethod.getSignature(),localTemp, unit);
        if(defsOfOps==null || defsOfOps.size()==0) return;
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
                        s = "[\\s\\S]*" + message.get(0) ;
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


    private boolean containPermissionString(String msg) {
        if(msg==null) return false;
        for(String str: permissionSet){
            if(msg.contains(str.trim().replace("android.permission.",""))){
                return true;
            }
        }
        return  false;
    }


}
