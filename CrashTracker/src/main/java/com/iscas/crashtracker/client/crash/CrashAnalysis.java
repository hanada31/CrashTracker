package com.iscas.crashtracker.client.crash;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.iscas.crashtracker.base.Analyzer;
import com.iscas.crashtracker.base.Global;
import com.iscas.crashtracker.base.MyConfig;
import com.iscas.crashtracker.client.exception.*;
import com.iscas.crashtracker.model.analyzeModel.MethodModel;
import com.iscas.crashtracker.utils.*;
import lombok.extern.slf4j.Slf4j;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.scalar.Pair;
import soot.toolkits.scalar.UnitValueBoxPair;

import java.io.File;
import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.iscas.crashtracker.utils.ConstantUtils.isLibraryMethod;


/**
 * @Author hanada
 * @Date 2022/3/22 20:20
 * @Version 1.0
 */
@Slf4j
public class CrashAnalysis extends Analyzer {
    List<CrashInfo> crashInfoList;
    Set<String> loadedExceptionSummary;
    Map<String, Set<String>> androidCGMap;
    Map<String, String> androidSimpleName2Signature;
    String relatedVarType="";
    String[] versions = {"2.3", "4.4", "5.0", "6.0", "7.0", "8.0", "9.0", "10.0", "11.0", "12.0"};
    int[] APILevels = {10, 19, 21, 23, 24, 26, 27, 28, 29, 30};

    public CrashAnalysis() {
        crashInfoList = Global.v().getAppModel().getCrashInfoList();
        loadedExceptionSummary = new HashSet<>();
        androidCGMap = new HashMap<>();
        androidSimpleName2Signature = new HashMap<>();

    }

    @Override
    public void analyze() {
        readCrashInfo();
        log.info("readCrashInfo Finish...");
        getExceptionOfCrashInfo();
        log.info("getExceptionOfCrashInfo Finish...");
        readMethodInfo();
        readAndroidCG();
        getCandidateBuggyMethods();
        log.info("getCandidateBuggyMethods Finish...");
        printCrash2Edges();
    }

    /**
     * key method
     * find candidates according to the type of corresponding exception
     */
    private void getCandidateBuggyMethods() {
        for(CrashInfo crashInfo : crashInfoList){
            ExceptionInfo exceptionInfo = crashInfo.getExceptionInfo();
            checkIsNoAppRelatedHandler(crashInfo);
//            log.info(PrintUtils.printMap(crashInfo.getExtendedCallDepth()));
            if(exceptionInfo!=null && exceptionInfo.getRelatedVarType()!=null) {
                switch (exceptionInfo.getRelatedVarType()) {
                    //first choice filterExtendedCG false, second choice true
                    case Empty:
                        relatedVarType=RelatedVarType.Empty.toString();
                        overrideMissingHandler(ConstantUtils.INITSCORE,crashInfo); //OMA
                        break;
                    case Parameter:
                        relatedVarType=RelatedVarType.Parameter.toString();
                        withParameterHandler(ConstantUtils.INITSCORE, crashInfo, true); //TMA
                        break;
                    case Field:
                        relatedVarType=RelatedVarType.Field.toString();
                        withFieldHandler(ConstantUtils.INITSCORE, crashInfo);
                        break;
                    case ParaAndField:
                        relatedVarType=RelatedVarType.ParaAndField.toString();
                        withFieldHandler(ConstantUtils.INITSCORE, crashInfo); //FCA
                        withParameterHandler(ConstantUtils.INITSCORE, crashInfo, true); //TMA
                        break;
                    case Unknown:
                        relatedVarType=RelatedVarType.Unknown.toString();
                        withParameterHandler(ConstantUtils.INITSCORE, crashInfo, false);
                        break;
                }
            }else{
                relatedVarType="Unknown";
                withParameterHandler(ConstantUtils.INITSCORE, crashInfo, false);
            }
            log.info("### relatedVarType is " + relatedVarType);
        }
    }




    private void getPartOfExtendedCallTrace(CrashInfo crashInfo) {
        //methods that preds of the next one in call stack
        JSONObject reason = new JSONObject(true);
//        reason.put("Explanation", "");
//        reason.put("Trace", new JSONArray());
        for (int index = crashInfo.getCrashMethodList().size() - 1; index >= 0; index--) {
            String candi = crashInfo.getCrashMethodList().get(index);
            crashInfo.addExtendedCallDepth(candi, 1, PrintUtils.deepCopy(reason));
            Set<SootMethod> methods = SootUtils.getSootMethodBySimpleName(candi);
            for (SootMethod sm : methods) {
                addEntryMethods2ExtendedCG(sm, crashInfo, PrintUtils.deepCopy(reason));
                String last = (index == 0) ? crashInfo.getCrashAPI() : crashInfo.getCrashMethodList().get(index - 1);
                addPredCallersOfMethodsInStack(last, sm, crashInfo, PrintUtils.deepCopy(reason));
            }
        }
    }
    /**
     * use the same strategy as CrashLocator, extend cg, remove control flow and data flow unrelated edges
     * @param crashInfo
     */
    private void getExtendedCallTrace(CrashInfo crashInfo) {
        //all function in the last method
        //methods that preds of the next one in call stack
        JSONObject reason = new JSONObject(true);
//        reason.put("Explanation", "");
        for (int index = crashInfo.getCrashMethodList().size() - 1; index >= 0; index--) {
            String candi = crashInfo.getCrashMethodList().get(index);
            JSONArray trace = new JSONArray();
//            reason.put("Trace", trace);
            crashInfo.addExtendedCallDepth(candi, 1, PrintUtils.deepCopy(reason));
            Set<SootMethod> methods = SootUtils.getSootMethodBySimpleName(candi);
            for (SootMethod sm : methods) {
                trace.add(sm.getSignature());
                addEntryMethods2ExtendedCG(sm, crashInfo, PrintUtils.deepCopy(reason));
                String last = (index == 0) ? crashInfo.getCrashAPI() : crashInfo.getCrashMethodList().get(index - 1);
                addPredCallersOfMethodsInStack(last, sm, crashInfo, PrintUtils.deepCopy(reason));
                break;
            }
        }
        String firstAppMethod = crashInfo.getCrashMethodList().get(crashInfo.getCrashMethodList().size() - 1);
        Set<SootMethod> methods = SootUtils.getSootMethodBySimpleName(firstAppMethod);
        for (SootMethod sm : methods) {
            addCallersOfCrashMethod(sm, crashInfo, 2, PrintUtils.deepCopy(reason));
        }

    }

    private void addEntryMethods2ExtendedCG(SootMethod sm, CrashInfo crashInfo, JSONObject reason) {
        if(!sm.hasActiveBody())return;
        for(SootMethod entry: sm.getDeclaringClass().getMethods()) {
            if(appModel.getEntryMethods().contains(entry) || entry.getName().startsWith("on")){
                String callee = entry.getDeclaringClass().getName()+ "." + entry.getName();
                JSONObject newReason = PrintUtils.deepCopy(reason);
                newReason.getJSONArray("Trace").add(callee);
                if(crashInfo.addExtendedCallDepth(callee, 2, newReason)) {
                    addAllCallee2ExtendedCG(entry, crashInfo, 3, newReason);
                }
            }
        }
    }

    private void addCallersOfCrashMethod(SootMethod sm, CrashInfo crashInfo, int depth, JSONObject reason) {
        if(depth> ConstantUtils.EXTENDCGDEPTH) return;
        for (Iterator<Edge> it = Global.v().getAppModel().getCg().edgesInto(sm); it.hasNext(); ) {
            Edge edge = it.next();
            String caller = edge.getTgt().method().getDeclaringClass().getName() + "." + edge.getSrc().method().getName();
            JSONObject newReason = PrintUtils.deepCopy(reason);
            newReason.getJSONArray("Trace").add(caller);
            if (crashInfo.addExtendedCallDepth(caller, depth, newReason)) {
                addAllCallee2ExtendedCG(edge.getSrc().method(), crashInfo, depth + 1, newReason);
                addCallersOfCrashMethod(sm, crashInfo, depth+1, newReason);
            }
        }

    }

    private void addPredCallersOfMethodsInStack(String last, SootMethod sm, CrashInfo crashInfo, JSONObject reason) {
        if(!sm.hasActiveBody())return;
        for(Unit u : sm.getActiveBody().getUnits()){
            InvokeExpr invoke = SootUtils.getInvokeExp(u);
            if (invoke == null) continue;
            String callee = invoke.getMethod().getDeclaringClass().getName()+ "." + invoke.getMethod().getName();
            if(callee.equals(last)){
                JSONObject newReason = PrintUtils.deepCopy(reason);
                newReason.getJSONArray("Trace").add(callee);
                addPredsOfUnit2ExtendedCG(u, sm, crashInfo,2, newReason,null);
            }
        }
    }


    /**
     * add all preds unit of u
     * remove both the control flow and  data flow unrelated edges
     * @param u
     * @param sm
     * @param crashInfo
     * @param depth
     * @param value
     */
    private void addPredsOfUnit2ExtendedCG(Unit u, SootMethod sm, CrashInfo crashInfo, int depth, JSONObject reason, Value value) {
        BriefUnitGraph graph = new BriefUnitGraph(SootUtils.getSootActiveBody(sm));
        List<Unit> worklist = new ArrayList<>();
        List<Unit> predUnits = new ArrayList<>();
        //control flow filter
        worklist.add(u);
        while(worklist.size()>0){
            Unit todo = worklist.get(0);
            worklist.remove(0);
            List<Unit> preds = graph.getPredsOf(todo);
            for(Unit pred: preds){
                if(!predUnits.contains(pred)){
                    predUnits.add(pred);
                    worklist.add(pred);
                }
            }
        }
        //data flow filter
        Set<Unit> dataSlice = new HashSet<>();
        getDataSliceOfUnit(dataSlice, sm, u, predUnits, value);
        for(Unit pred: predUnits){
            if(!dataSlice.contains(pred)) continue;
            Set<SootMethod> calleeMethod = SootUtils.getInvokedMethodSet(sm, pred);
            for(SootMethod method: calleeMethod){
                String callee = method.getDeclaringClass().getName()+ "." + method.getName();
                JSONObject newReason = PrintUtils.deepCopy(reason);
                newReason.getJSONArray("Trace").add(callee);
                if(crashInfo.addExtendedCallDepth(callee, depth, newReason)){
                    addAllCallee2ExtendedCG(method, crashInfo, depth+1, newReason);
                }
            }
        }
    }

    private void getDataSliceOfUnit(Set<Unit> dataSlice, SootMethod sm, Unit u, List<Unit> predUnits, Value value) {
        InvokeExpr invokeExpr = SootUtils.getInvokeExp(u);
        if(invokeExpr==null) return;
        for (Value val : invokeExpr.getArgs()){
            if(value!=null && val !=value) continue;
            List<Unit> defs = SootUtils.getDefOfLocal(sm.getSignature(), val, u);
            for(Unit def: defs) {
                List<UnitValueBoxPair> uses = SootUtils.getUseOfLocal(sm.getSignature(), def);
                for(UnitValueBoxPair pair : uses){
                    if(predUnits.contains(pair.getUnit()) && !dataSlice.contains(pair.getUnit()) ) {
                        dataSlice.add(pair.getUnit());
                        getDataSliceOfUnit(dataSlice, sm, pair.getUnit(), predUnits, value);
                    }
                }
            }
        }
    }

    private void addAllCallee2ExtendedCG(SootMethod sm, CrashInfo crashInfo, int depth, JSONObject reason) {
        if(depth> ConstantUtils.EXTENDCGDEPTH) return;
        for (Iterator<Edge> it = Global.v().getAppModel().getCg().edgesOutOf(sm); it.hasNext(); ) {
            Edge edge = it.next();
            String callee = edge.getTgt().method().getDeclaringClass().getName()+ "." + edge.getTgt().method().getName();
            JSONObject newReason = PrintUtils.deepCopy(reason);
            newReason.getJSONArray("Trace").add(callee);
            if(crashInfo.addExtendedCallDepth(callee, depth, newReason)){
                addAllCallee2ExtendedCG(edge.getTgt().method(), crashInfo, depth+1, newReason);
            }
        }
    }

    /**
     * ParameterOnly type
     * @param crashInfo
     *
     */
    private void withFieldHandler(int score, CrashInfo crashInfo) {
        log.info("withFieldHandler...");
        ExceptionInfo exceptionInfo = crashInfo.getExceptionInfo();
        for(RelatedMethod method: exceptionInfo.getRelatedMethodsInSameClass(false)){
            getBuggyFromRelatedMethods(crashInfo, method, score);
        }
        score = Math.max(crashInfo.maxScore-ConstantUtils.SMALLGAPSCORE, crashInfo.minScore - ConstantUtils.SMALLGAPSCORE);
        addCrashTracesKeyAPI(score, crashInfo);
        if(!crashInfo.findCandidateInTrace) {
            //add diff class results, when the same class results returns nothing
            for (RelatedMethod method : exceptionInfo.getRelatedMethodsInDiffClass(false)) {
                getBuggyFromRelatedMethods(crashInfo, method, score-ConstantUtils.DIFFCLASS);
            }
        }
    }
    private void addCrashTracesKeyAPI(int initscore, CrashInfo crashInfo) {
        int l = 0;
        for(String candi: crashInfo.getCrashMethodList()){
            if(l++>5) return;
            JSONObject reason = new JSONObject(true);
//            reason.put("Explanation", "Not influence the keyVar but in crash trace");
//            JSONArray trace = new JSONArray();
//            reason.put("Trace", trace);
            Set<SootMethod> methods = SootUtils.getSootMethodBySimpleName(candi);
            int finalScore = initscore;
            for(SootMethod sm: methods) {
//                trace.add(sm.getSignature());
                finalScore = crashInfo.addBuggyCandidates(candi, sm.getSignature(), initscore--, reason);
                break;
            }
            if(methods.size()==0){
                finalScore = crashInfo.addBuggyCandidates(candi, "", initscore--, reason);
            }
            reason.put("Reason Score",finalScore);
            GenerateReason.generateReasonForKeyAPIInStackTrace(reason, candi, crashInfo);
        }
    }


    /**
     * tracing the values relates to the one used in if condition
     */
    private String extendRelatedValues(SootMethod crashMethod, List<Unit> allPreds, Unit unit, Value value,
                                       List<Value> valueHistory, HashSet<SootField> sootFields) {
        if(valueHistory.contains(value)) return "";// if defUnit is not a pred of unit
        valueHistory.add(value);
        if(value instanceof  Local) {
            String methodSig = crashMethod.getSignature();
            for(Unit defUnit: SootUtils.getDefOfLocal(methodSig,value, unit)) {
                //if the defined unit is under a check
                if (defUnit instanceof JIdentityStmt) {
                    JIdentityStmt identityStmt = (JIdentityStmt) defUnit;
                    identityStmt.getRightOp();
                    if(identityStmt.getRightOp() instanceof ParameterRef) {//from parameter
                        return "ParameterRef";
                    }else if(identityStmt.getRightOp() instanceof CaughtExceptionRef){
                        return "CaughtExceptionRef";
                    }else if(identityStmt.getRightOp() instanceof ThisRef){
                        return "ThisRef";
                    }
                } else if (defUnit instanceof JAssignStmt) {
                    Value rightOp = ((JAssignStmt) defUnit).getRightOp();
                    if (rightOp instanceof Local) {
                        extendRelatedValues(crashMethod, allPreds, defUnit, rightOp, valueHistory, sootFields);
                    } else if (rightOp instanceof AbstractInstanceFieldRef) {
                        Value base = ((AbstractInstanceFieldRef) rightOp).getBase();
                        String defType = extendRelatedValues(crashMethod, allPreds, defUnit, base, valueHistory, sootFields);
                        if(defType.equals("ThisRef")){
                            SootField field = ((AbstractInstanceFieldRef) rightOp).getField();
                            Value baseF = ((AbstractInstanceFieldRef) rightOp).getBase();
                            List<Value> rightValues = SootUtils.getFiledValueAssigns(baseF, field, allPreds);
                            for(Value rv: rightValues){
                                extendRelatedValues(crashMethod, allPreds, defUnit, rv, valueHistory, sootFields);
                            }
                            sootFields.add(field);
                        }
                    } else if (rightOp instanceof Expr) {
                        if (rightOp instanceof InvokeExpr) {
                            InvokeExpr invokeExpr = SootUtils.getInvokeExp(defUnit);
                            for (Value val : invokeExpr.getArgs())
                                extendRelatedValues(crashMethod, allPreds, defUnit, val, valueHistory, sootFields);
                            if (rightOp instanceof InstanceInvokeExpr) {
                                extendRelatedValues(crashMethod, allPreds, defUnit, ((InstanceInvokeExpr) rightOp).getBase(), valueHistory, sootFields);
                            }
                        } else if (rightOp instanceof AbstractInstanceOfExpr || rightOp instanceof AbstractCastExpr
                                || rightOp instanceof AbstractBinopExpr || rightOp instanceof AbstractUnopExpr) {
                            for (ValueBox vb : rightOp.getUseBoxes()) {
                                extendRelatedValues(crashMethod, allPreds, defUnit, vb.getValue(), valueHistory, sootFields);
                            }
                        } else if (rightOp instanceof NewExpr) {
                            List<UnitValueBoxPair> usesOfOps = SootUtils.getUseOfLocal(crashMethod.getSignature(), defUnit);
                            for (UnitValueBoxPair use : usesOfOps) {
                                for (ValueBox vb : use.getUnit().getUseBoxes())
                                    extendRelatedValues(crashMethod, allPreds, use.getUnit(), vb.getValue(), valueHistory, sootFields);
                            }
                        }
                    } else if (rightOp instanceof StaticFieldRef) {
                        sootFields.add(((StaticFieldRef) rightOp).getField());
                    }else if (rightOp instanceof JArrayRef) {
                        JArrayRef jArrayRef = (JArrayRef) rightOp;
                        extendRelatedValues(crashMethod, allPreds, defUnit, jArrayRef.getBase(), valueHistory, sootFields);
                    }
                } else {
                    log.info(defUnit.getClass().getName() + "::" + defUnit);
                }
            }
        }
        return "";
    }
    /**
     * parameterOnlyHandler
     * @param score
     * @param crashInfo
     */
    private void withParameterHandler(int score, CrashInfo crashInfo, boolean hasFaultInducingParas) {
        String  paramValueTerminateCallerSignature = getParaPassingTerminateMethod(crashInfo, hasFaultInducingParas);
        analyzeParameterTerminateMethod(score, crashInfo, paramValueTerminateCallerSignature);
        analyzeParameterNonTerminateMethod(score, crashInfo);

        int nonTerminateScore = Math.max(crashInfo.maxScore-ConstantUtils.SMALLGAPSCORE, crashInfo.minScore - ConstantUtils.SMALLGAPSCORE);
        if(MyConfig.getInstance().getStrategy().equals(Strategy.ExtendCGOnly.toString())) {
//            getExtendedCallTrace(crashInfo);
//            addExtendedCallGraph(crashInfo);
        }else{
            getCrashAPIInvocationRelatedMethod(crashInfo,nonTerminateScore);
        }

        withCrashAPIParaHandler(nonTerminateScore, crashInfo);
    }

    /**
     * find the method that generate a buggy value that passed to the keyVar
     * @param crashInfo
     * @param hasFaultInducingParas
     * @return
     */
    private String getParaPassingTerminateMethod(CrashInfo crashInfo, boolean hasFaultInducingParas) {
        String paramValueTerminateCallerSignature =crashInfo.crashMethod;
        if(crashInfo.getExceptionInfo()!=null && crashInfo.getExceptionInfo().getRelatedVarType()!=null) {
            if(crashInfo.getExceptionInfo().getCallerOfSingnlar2SourceVar()!=null){
                if(hasFaultInducingParas) {
                    Map<String, List<Integer>> map = crashInfo.getExceptionInfo().getCallerOfSingnlar2SourceVar();
                    List<String> vars = new ArrayList<>();
                    Pair<String, List> temp = null;
                    for(String frameworkMtd : crashInfo.getFrameworkTrace()){
                        if(map.keySet().contains(frameworkMtd)){
                            Pair<String, List> pair = new Pair(frameworkMtd, map.get(frameworkMtd));
                            crashInfo.getFrameworkParamIdTrackingList().add(pair);
                            temp = pair;
                        }
                    }
                    if(temp!=null){
                        String signature = androidSimpleName2Signature.get(temp.getO1());
                        String paraStr = signature.split("\\(")[1].split("\\)")[0];
                        String[] paraList = paraStr.split(",");
                        for(int id : (List<Integer>)temp.getO2()) {
                            if(paraList.length>id && id>-1)
                                vars.add(paraList[id]);
                        }
                    }
//                    for (String callerOfSingnlar : map.keySet()) {
//                        if (crashInfo.getTrace().contains(callerOfSingnlar)) {
//                            Pair pair = new Pair(callerOfSingnlar, map.get(callerOfSingnlar));
//                            crashInfo.getFrameworkParamIdTrackingList().add(pair);
//                        }
//                    }
                    if (map.containsKey(crashInfo.getCrashAPI())) {
                        crashInfo.faultInducingParas = map.get(crashInfo.getCrashAPI());
                    } else {
                        //if the parameter point to relationship is not tracked, use the type information to filter
                        if(vars.size()==0)
                            vars = crashInfo.getExceptionInfo().getRelatedParamValuesInStr();
                        crashInfo.faultInducingParas = getParaIdListByType(crashInfo, vars);
                    }
                    paramValueTerminateCallerSignature = traceCallerOfParamValue(crashInfo, crashInfo.faultInducingParas);
//                    System.err.println(paramValueTerminateCallerSignature);
                }
            }
        }

        if(crashInfo.faultInducingParas==null || crashInfo.faultInducingParas.size()==0){
            getAllParaAsFaultInducingParas(crashInfo);
            traceCallerOfParamValue(crashInfo, crashInfo.faultInducingParas);
        }
        return paramValueTerminateCallerSignature;
    }

    private void getAllParaAsFaultInducingParas(CrashInfo crashInfo) {
        crashInfo.faultInducingParas = new ArrayList<>();
        Set<SootMethod> methods = SootUtils.getSootMethodBySimpleName(crashInfo.getCrashMethod());
        for (SootMethod crashMethod : methods) {
            for (Unit u : SootUtils.getUnitListFromMethod(crashMethod)) {
                InvokeExpr invoke = SootUtils.getInvokeExp(u);
                if (invoke == null) continue;
                String sig = invoke.getMethod().getSignature();
                if (sig.equals(crashInfo.getCrashAPI()) || SootUtils.getMethodSimpleNameFromSignature(sig).equals(crashInfo.getCrashAPI())) {
                    for (int i = 0; i < invoke.getArgs().size(); i++) {
                        if(!crashInfo.faultInducingParas.contains(i))
                            crashInfo.faultInducingParas.add(i);
                    }
                }
            }
        }
    }

    private void getCrashAPIInvocationRelatedMethod(CrashInfo crashInfo, int score) {
        Set<SootMethod> methods = SootUtils.getSootMethodBySimpleName(crashInfo.getCrashMethod());
        for (SootMethod crashMethod : methods) {
            for (Unit u : SootUtils.getUnitListFromMethod(crashMethod)) {
                InvokeExpr invoke = SootUtils.getInvokeExp(u);
                if (invoke == null) continue;
                String sig = invoke.getMethod().getSignature();
                if (sig.equals(crashInfo.getCrashAPI()) || SootUtils.getMethodSimpleNameFromSignature(sig).equals(crashInfo.getCrashAPI())) {
                    for(int id :crashInfo.faultInducingParas){
                        if (invoke.getArgs().size() > id) {
                            BriefUnitGraph graph = new BriefUnitGraph(SootUtils.getSootActiveBody(crashMethod));
                            List<Unit> worklist = new ArrayList<>();
                            List<Unit> predUnits = new ArrayList<>();
                            //control flow filter
                            worklist.add(u);
                            while(worklist.size()>0){
                                Unit todo = worklist.get(0);
                                worklist.remove(0);
                                List<Unit> preds = graph.getPredsOf(todo);
                                for(Unit pred: preds){
                                    if(!predUnits.contains(pred)){
                                        predUnits.add(pred);
                                        worklist.add(pred);
                                    }
                                }
                            }
                            //data flow filter
                            Set<Unit> dataSlice = new HashSet<>();
                            if(id > -1) {
                                getDataSliceOfUnit(dataSlice, crashMethod, u, predUnits, invoke.getArgs().get(id));
                            }

                            for(Unit pred: predUnits){
                                if(!dataSlice.contains(pred)) continue;
                                Set<SootMethod> calleeMethod = SootUtils.getInvokedMethodSet(crashMethod, pred);
                                for(SootMethod method: calleeMethod){
                                    crashInfo.getAppParamIdTrackingList().clear();

                                    String callee = method.getDeclaringClass().getName()+ "." + method.getName();
                                    Pair method2ParamIdPair1 = new Pair(callee, new ArrayList<>());
                                    crashInfo.getAppParamIdTrackingList().add(method2ParamIdPair1);

                                    Pair method2ParamIdPair2 = new Pair(crashInfo.getCrashAPI(), new ArrayList<>(Collections.singleton(id)));
                                    crashInfo.getAppParamIdTrackingList().add(method2ParamIdPair2);


                                    JSONObject reason = new JSONObject(true);
//                                    reason.put("Explanation", "Influences the value of keyVar by modifying the value of the passed parameters");
//                                    reason.put("Influenced parameter id", id);
//                                    reason.put("Influenced method", crashInfo.getSignaler());
//                                    JSONArray trace = new JSONArray();
//                                    reason.put("Trace", trace);
//                                    trace.add(crashInfo.getCrashAPI());
//                                    trace.add(method.getSignature());
                                    int finalScore = crashInfo.addBuggyCandidates(callee, method.getSignature(), score, reason);
                                    reason.put("Reason Score",finalScore);
                                    GenerateReason.generateReasonForKeyVariableRelatedPreviousCall(reason, callee, new ArrayList(id), crashInfo);
                                }
                            }
                        }
                    }
                }
            }
        }
    }



    private void addExtendedCallGraph(CrashInfo crashInfo) {
        for(Map.Entry entry: crashInfo.getExtendedCallDepth().entrySet()){
            String candi = (String) entry.getKey();
            CrashInfo.ExtendCandiMethod method = (CrashInfo.ExtendCandiMethod) entry.getValue();
            JSONObject reason = new JSONObject(true);
//            reason.put("Explanation", "Not in the crash stack but has been executed");
            reason.put("Trace", PrintUtils.deepCopy(method.trace));
            int score = ConstantUtils.INITSCORE-method.depth;
            Set<SootMethod> methods = SootUtils.getSootMethodBySimpleName(candi);
            for(SootMethod sm: methods) {
                crashInfo.addBuggyCandidates(candi, sm.getSignature(), score, reason);
                break;
            }
            if(methods.size()==0){
                crashInfo.addBuggyCandidates(candi, "", score, reason);
            }
        }
    }
    //according to the parameter send into framework API
    private void withCrashAPIParaHandler(int score, CrashInfo crashInfo) {
        //TODO
        if(MyConfig.getInstance().getStrategy().equals(Strategy.NoAppDataTrace.toString())){ return;}
        if(MyConfig.getInstance().getStrategy().equals(Strategy.NOParaChainANDDataTrace.toString())){ return;}

        boolean find= false;
        for(SootMethod crashMethod: getCrashSootMethod(crashInfo)) {
            for (Unit unit : crashMethod.getActiveBody().getUnits()) {
                InvokeExpr invoke = SootUtils.getInvokeExp(unit);
                if (invoke == null) continue;
                String sig = invoke.getMethod().getSignature();
                if (SootUtils.getMethodSimpleNameFromSignature(sig).equals(crashInfo.getCrashAPI())) {
                    find = true;
                    findMethodsWhoModifyParam(score, crashInfo, crashMethod, unit, invoke);
                }
            }
        }
        if(find == false){
            for(SootMethod crashMethod: getCrashSootMethod(crashInfo)){
                for(Unit unit : crashMethod.getActiveBody().getUnits()){
                    InvokeExpr invoke = SootUtils.getInvokeExp(unit);
                    if (invoke == null) continue;
                    String name = invoke.getMethod().getName();
                    int last = crashInfo.getCrashAPI().split("\\.").length-1;
                    if (name.equals(crashInfo.getCrashAPI().split("\\.")[last])) {
                        findMethodsWhoModifyParam(score, crashInfo, crashMethod, unit, invoke);
                    }
                }
            }
        }
    }

    private void findMethodsWhoModifyParam(int score, CrashInfo crashInfo, SootMethod crashMethod, Unit unit, InvokeExpr invoke) {
//        if (crashInfo.faultInducingParas != null) {
//            for (int argId : crashInfo.faultInducingParas)
//                if(argId>=0 && invoke.getArgCount()>argId) {
//                    findMethodsWhoModifyParam(score, crashInfo, crashMethod, unit, invoke.getArg(argId), argId);
//                }
//        } else {
//            int i=0;
//            for (Value argValue : invoke.getArgs()) {
//                findMethodsWhoModifyParam(score, crashInfo, crashMethod, unit, argValue, i);
//                crashInfo.faultInducingParas.add(i);
//                i++;
//            }
//        }
        for (int argId : crashInfo.faultInducingParas) {
            if (argId >= 0 && invoke.getArgCount() > argId) {
                List<Unit> allPreds = new ArrayList<>();
                SootUtils.getAllPredsofUnit(crashMethod, unit, allPreds);
                HashSet<SootField> fields = new HashSet<>();
                extendRelatedValues(crashMethod, allPreds, unit, invoke.getArg(argId), new ArrayList<>(), fields);
                List<SootField> keyFields = getKeySootFields(crashMethod, crashInfo, invoke.getArg(argId));
                for (SootField field : fields) {
                    if (keyFields != null && !keyFields.contains(field))
                        continue;
                    for (SootMethod otherMethod : crashMethod.getDeclaringClass().getMethods()) {
                        if (!otherMethod.hasActiveBody()) continue;
                        if (SootUtils.fieldIsChanged(field, otherMethod)) {
                            crashInfo.getAppParamIdTrackingList().clear();

                            String candi = otherMethod.getDeclaringClass().getName() + "." + otherMethod.getName();
                            Pair method2ParamIdPair1 = new Pair(candi,  new ArrayList(Collections.singleton(field.toString())));
                            crashInfo.getAppParamIdTrackingList().add(method2ParamIdPair1);


                            Pair method2ParamIdPair2 = new Pair(crashInfo.getCrashAPI(), new ArrayList(Collections.singleton(field.toString())));
                            crashInfo.getAppParamIdTrackingList().add(method2ParamIdPair2);


                            Pair method2ParamIdPair3 = new Pair(crashInfo.getCrashAPI(), new ArrayList<>(Collections.singleton(argId)));
                            crashInfo.getAppParamIdTrackingList().add(method2ParamIdPair3);

                            JSONObject reason = new JSONObject(true);
//                            reason.put("Explanation", "Influences the value of keyVar by modifying the value of related object fields");
//                            reason.put("Influenced Field", field.toString());
//                            reason.put("Influenced By Method", otherMethod.getSignature());
//                            JSONArray trace = new JSONArray();
//                            reason.put("Trace", trace);
//                            trace.add(otherMethod.getSignature());
//                            trace.add("modify key field: " + field);
//                            trace.add(crashMethod.getSignature());

                            int finalScore = crashInfo.addBuggyCandidates(candi, otherMethod.getSignature(), score, reason);
                            reason.put("Reason Score", finalScore);
                            GenerateReason.generateReasonForKeyVariableRelatedModifySameField(reason, candi, argId, field, crashInfo);
                        }
                    }
                }
            }
        }
    }


    private String traceCallerOfParamValue(CrashInfo crashInfo, List<Integer> faultInducingParaIds) {
//        System.out.println(faultInducingParaIds);
        String calleeMethod = crashInfo.getCrashAPI();
        Set<String> calleeMethodSet = new HashSet<>();
        calleeMethodSet.add(crashInfo.getCrashAPI());
        String result = null;
        SootMethod resultMethod = null;
        for(String candi : crashInfo.getCrashMethodList()) {
//            System.out.println("candi: " +candi);
            result = candi;
            Set<Integer> ids_temp = new HashSet<>();
            Pair<String, List> method2ParamIdPair = new Pair<>(calleeMethod, faultInducingParaIds);
            crashInfo.getAppParamIdTrackingList().add(method2ParamIdPair);
            Set<SootMethod> methods = SootUtils.getSootMethodBySimpleName(candi);
            for (SootMethod caller : methods) {
                boolean checkCaller = false;
//                System.out.println("caller: " +caller);
                //caller = <org.kontalk.util.MediaStorage: java.io.File cacheThumbnail(android.content.Context,android.net.Uri,java.lang.String,boolean)>
                resultMethod = caller;
                for (Unit u : SootUtils.getUnitListFromMethod(caller)) {
                    InvokeExpr invoke = SootUtils.getInvokeExp(u);
                    if (invoke == null) continue;
                    String sig = invoke.getMethod().getSignature();
                    if (sig.equals(calleeMethod) || SootUtils.getMethodSimpleNameFromSignature(sig).equals(calleeMethod)) {
                        checkCaller = true;
                        for (int id : faultInducingParaIds) {
//                            System.out.println("id: " + id);
                            if (id == -1)
                                log.info("this");
                            else if (invoke.getArgs().size() > id) {
                                //get the idth param
                                List<Unit> defs = SootUtils.getDefOfLocal(caller.getSignature(), invoke.getArgs().get(id), u);
                                getPossibleRelatedParaIdsFromDefs(caller.getSignature(), defs, ids_temp, new HashSet<>());
//                                System.out.println("ids_temp: " + PrintUtils.printSet(ids_temp));
                            }
                        }
                    }
                }
                if(checkCaller) {
                    break;
                }else{
                    ids_temp = new HashSet<>();
                }
            }
            if(ids_temp.size()==0){
                return result;
            }
            faultInducingParaIds = new ArrayList<>(ids_temp);
            calleeMethod = resultMethod.getSignature();

        }
        return result;
    }

    private void getPossibleRelatedParaIdsFromDefs(String signature, List<Unit> defs, Set<Integer> ids_temp, HashSet analyzedDefUnit) {
        for (Unit def : defs) {
            if(analyzedDefUnit.contains(def))continue;
            analyzedDefUnit.add(def);
            if (def instanceof IdentityStmt) {
                if (((IdentityStmt) def).getRightOp() instanceof ParameterRef) {
                    ids_temp.add(((ParameterRef)((IdentityStmt) def).getRightOp()).getIndex());
                }else{
                    for(ValueBox vb: ((IdentityStmt) def).getRightOp().getUseBoxes()){
                        List<Unit> defs2 = SootUtils.getDefOfLocal(signature, vb.getValue(), def);
                        getPossibleRelatedParaIdsFromDefs(signature,defs2,ids_temp,analyzedDefUnit);
                    }
                }
            }else{
                for(ValueBox vb: def.getUseBoxes()){
                    List<Unit> defs2 = SootUtils.getDefOfLocal(signature, vb.getValue(), def);
                    getPossibleRelatedParaIdsFromDefs(signature,defs2,ids_temp,analyzedDefUnit);
                }
            }
        }
    }


    private List<Integer>  getParaIdListByType(CrashInfo crashInfo, List<String> vars) {
        Set<Integer> paraIds = new HashSet<>();
        String signature = androidSimpleName2Signature.get(crashInfo.getCrashAPI());
        if(signature==null){
            //crash API is not in the analyzed framework code
            for(SootMethod crashMethod: getCrashSootMethod(crashInfo)) {
                for(Unit unit : crashMethod.getActiveBody().getUnits()){
                    InvokeExpr invoke = SootUtils.getInvokeExp(unit);
                    if (invoke == null) continue;
                    String simpleName = invoke.getMethod().getDeclaringClass().getName()+ "." + invoke.getMethod().getName();
                    if(simpleName.equals(crashInfo.getCrashAPI())){
                        signature = invoke.getMethod().getSignature();
                        break;
                    }
                }
            }
        }
        if(signature==null){
            //crash API is not in the analyzed framework code
            for(SootMethod crashMethod: getCrashSootMethod(crashInfo)) {
                for(Unit unit : crashMethod.getActiveBody().getUnits()){
                    InvokeExpr invoke = SootUtils.getInvokeExp(unit);
                    if (invoke == null) continue;
                    if((crashInfo.getCrashAPI().contains(invoke.getMethod().getName()))){
                        signature = invoke.getMethod().getSignature();
                        break;
                    }
                }
            }
        }
        if(signature==null) return new ArrayList<>();
        String paraStr = signature.split("\\(")[1].split("\\)")[0];
        String[] paraList = paraStr.split(",");
        int paraId = -1;
        for (String para : paraList) {
            ++paraId;
            for (String paraTye : vars) {
                if(para.contains(paraTye))
                    paraIds.add(paraId);
                    break;
                }
        }
        if(paraIds.size()==0) {
            paraId = -1;
            for (String para : paraList) {
                paraIds.add(++paraId);
            }

        }
        return new ArrayList<>(paraIds);
    }

    private void analyzeParameterTerminateMethod(int score, CrashInfo crashInfo, String finalCaller) {
        boolean find = false;

        ListIterator<String> iterator = crashInfo.getCrashMethodList().listIterator(crashInfo.getCrashMethodList().size()); // 从列表末尾开始
        while (iterator.hasPrevious()) {
            String candi = iterator.previous();
            String signature = candi;
            Set<SootMethod> methods = SootUtils.getSootMethodBySimpleName(candi);
            for(SootMethod sm: methods) {
                signature = sm.getSignature();
            }
            if (candi.equals(finalCaller)) {
                JSONObject reason = new JSONObject(true);
//                reason.put("Explanation", "Influences the value of keyVar by modifying the value of the passed parameters");
//                reason.put("Influenced parameter id", PrintUtils.printList(crashInfo.getFaultInducingParas()));
//                reason.put("Influenced method", crashInfo.getSignaler());
//                JSONArray trace = new JSONArray();
//                trace.add(finalCaller);
//                reason.put("Trace", trace);
                int finalScore = crashInfo.addBuggyCandidates(candi,signature, score,reason);
                reason.put("Reason Score",finalScore);
                GenerateReason.generateReasonForKeyVariableRelatedTerminate(reason, finalCaller, crashInfo.getFaultInducingParas(), crashInfo);
                find = true;
            }else{
                if(find) {
                    JSONObject reason = new JSONObject(true);
//                    reason.put("Explanation", "Influences the value of keyVar by modifying the value of the passed parameters");
//                    reason.put("Influenced parameter id", PrintUtils.printList(crashInfo.getFaultInducingParas()));
//                    reason.put("Influenced method", crashInfo.getSignaler());
//                    JSONArray trace = new JSONArray();
//                    trace.add(finalCaller);
//                    reason.put("Trace", trace);

                    int finalScore = crashInfo.addBuggyCandidates(candi, signature, --score, reason);
                    reason.put("Reason Score",finalScore);
                    GenerateReason.generateReasonForKeyVariableRelatedNotTerminate(reason, finalCaller, crashInfo.getFaultInducingParas(), crashInfo);
                }
            }
        }
    }

    private void analyzeParameterNonTerminateMethod(int initScore,  CrashInfo crashInfo) {
        List<String> invokingList = new ArrayList<>();
        initScore = initScore-crashInfo.getAppParamIdTrackingList().size();
        log.info("getParameterNonTerminateMethod...");
        int start = getTopNonLibMethod(crashInfo);
        int end = getBottomNonLibMethod(crashInfo);
        crashInfo.setEdges(new ArrayList<>());
        SootClass superCls = null;
        String sub ="";
        List<String> history = new ArrayList<>();
        for(int k=start; k<=end; k++){
            String candi = crashInfo.getTrace().get(k);
            invokingList.add(candi);
            if(!isLibraryMethod(candi)){
                JSONObject reason = new JSONObject(true);
//                reason.put("Explanation", "Influences the value of keyVar by modifying the value of the passed parameters");
//                reason.put("Influenced parameter id", PrintUtils.printList(crashInfo.getFaultInducingParas()));
//                reason.put("Influenced method", crashInfo.getSignaler());
//                JSONArray trace = new JSONArray();
//                reason.put("Trace", trace);
                Set<SootMethod> methods = SootUtils.getSootMethodBySimpleName(candi);
                int finalScore =initScore;
                for(SootMethod sm: methods) {
//                    trace.add(sm.getSignature());
                    finalScore = crashInfo.addBuggyCandidates(candi, sm.getSignature(), initScore--, reason);
                    break;
                }
                if(methods.size()==0){
                    finalScore = crashInfo.addBuggyCandidates(candi, "", initScore--, reason);
                }
                reason.put("Reason Score",finalScore);

                GenerateReason.generateReasonForKeyVariableRelatedNotTerminate(reason, candi,  crashInfo.faultInducingParas, crashInfo);
                for(SootMethod sm: methods) {
                    if (sm == null) continue;
                    sub = sm.getDeclaringClass().getName();
                    superCls = Scene.v().getActiveHierarchy().getSuperclassesOf(sm.getDeclaringClass()).get(0);
                    break;
                }
                // if candi invokes a method, it is a framework method, whose class is the father of current class,
                // the framework method invokes its method, the method in the subclass is executed
                analyzeApplicationMethodCalledByFrameworkMethod(methods,initScore,crashInfo,history, new ArrayList<>(invokingList));
            }else{
                if(superCls!= null && candi.contains(superCls.getName() )){
                    JSONObject reason = new JSONObject(true);
//                    reason.put("Explanation", "");
//                    JSONArray trace = new JSONArray();
//                    reason.put("Trace", trace);
//                    trace.add(0,candi);
//                    invokingList.add(0,candi);
                    getCalleeOfAndroidMethods(initScore,crashInfo, candi , sub, history, reason,new ArrayList<>(invokingList));
                }
                initScore--;
            }
        }
    }

    private void analyzeApplicationMethodCalledByFrameworkMethod(Set<SootMethod> methods, int initScore, CrashInfo crashInfo,
                                                                 List<String> history, List<String> invokingList) {
        SootClass superCls = null;
        String sub ="";
        for(SootMethod sm: methods) {
            if (sm == null) continue;
            sub = sm.getDeclaringClass().getName();
            superCls = Scene.v().getActiveHierarchy().getSuperclassesOf(sm.getDeclaringClass()).get(0);
            for (Iterator<Edge> it = Global.v().getAppModel().getCg().edgesOutOf(sm); it.hasNext(); ) {
                Edge outEdge = it.next();
                SootMethod callee = outEdge.getTgt().method();
                if (callee.getSignature().contains(superCls.getName())) {
                    JSONObject reason2 = new JSONObject(true);
//                    reason2.put("Reason Type", "");
//                    reason2.put("Explanation", "");
//                    JSONArray trace2 = new JSONArray();
//                    reason2.put("Trace", trace2);
//                    trace2.add(0, sm.getSignature());
//                    trace2.add(0, callee.getSignature());
                    ArrayList<String> myInvokingList = new ArrayList<>(invokingList);
                    myInvokingList.add(0, sm.getSignature());
                    myInvokingList.add(0, callee.getSignature());
                    getCalleeOfAndroidMethods(initScore, crashInfo, SootUtils.getMethodSimpleNameFromSignature(callee.getSignature()),
                            sub, history, reason2, myInvokingList);
                }
            }
        }
    }

    /**
     * in stack trace, app methods invokes framework methods, and framework methods recalls the app method
     * @param initScore
     * @param crashInfo
     * @param candi
     * @param sub
     * @param history
     * @param reason
     */
    private void getCalleeOfAndroidMethods(int initScore, CrashInfo crashInfo, String candi,
                                           String sub, List<String> history, JSONObject reason , List<String> invokingList) {
        if(history.contains(candi)) return;
        history.add(candi);
        readAndroidCG();
        if(!androidCGMap.containsKey(candi)) return;
        if(!invokingList.contains(candi))
            invokingList.add(candi);
        String candiClassName = candi.substring(0,candi.lastIndexOf("."));
        for(String callee: androidCGMap.get(candi)) {
            //the callee is a recall method
            if (callee.contains(candiClassName)) {
                if (callee.substring(callee.lastIndexOf(".")).startsWith(".on")) {
                    String realCallee = "";
                    SootClass subClass = Scene.v().getSootClass(sub);
                    for (SootClass superCls : Scene.v().getActiveHierarchy().getSuperclassesOf(subClass)) {
                        if (superCls.getName().contains(candiClassName)) {
                            realCallee = callee.replace(candiClassName, sub);
                        }
                    }
                    //only consider the recall method
                    Set<SootMethod> methods = SootUtils.getSootMethodBySimpleName(realCallee);
                    for (SootMethod realSootMethod : methods) {
                        if (realSootMethod != null) {
                            JSONObject newReason = PrintUtils.deepCopy(reason);
//                            newReason.getJSONArray("Trace").add(realSootMethod.getSignature());
                            ArrayList<String> myInvokingList = new ArrayList<>(invokingList);
                            myInvokingList.add(callee);
                            addCalleesOfSourceOfEdge(initScore, crashInfo, realSootMethod, 0, newReason, myInvokingList );
                        }
                    }
                }
                JSONObject newReason = PrintUtils.deepCopy(reason);
//                newReason.getJSONArray("Trace").add(callee);
                getCalleeOfAndroidMethods(initScore, crashInfo, callee, sub, history, newReason, new ArrayList<>(invokingList));
            }
        }
    }


    /**
     * addCalleesOfSourceOfEdge
     * @param crashInfo
     * @param sootMethod
     * @param depth
     */
    private void addCalleesOfSourceOfEdge(int initScore, CrashInfo crashInfo, SootMethod sootMethod, int depth, JSONObject reason , List<String> invokingList) {
        String candi = sootMethod.getDeclaringClass().getName()+ "." + sootMethod.getName();
        invokingList.add(sootMethod.getSignature());
        if(isLibraryMethod(candi)) return;
        int score = initScore - getOrderInTrace(crashInfo, candi)  - depth;

//        reason.put("Explanation", "Not in the crash stack but has been executed" );
        int finalScore = crashInfo.addBuggyCandidates(candi, sootMethod.getSignature(),score, reason);
        reason.put("Reason Score", finalScore );
        GenerateReason.generateReasonForFrameworkRecallMethod(reason, candi, crashInfo, new ArrayList<>(invokingList));

        for (Iterator<Edge> it = Global.v().getAppModel().getCg().edgesOutOf(sootMethod); it.hasNext(); ) {
            Edge edge2 = it.next();
            if(!crashInfo.getEdges().contains(edge2) && !edge2.toString().contains("dummyMainMethod")){
                crashInfo.add2EdgeMap(depth,edge2);
                JSONObject newReason =  PrintUtils.deepCopy(reason);
//                newReason.getJSONArray("Trace").add(edge2.getTgt().method().getSignature());
                addCalleesOfSourceOfEdge(initScore, crashInfo, edge2.getTgt().method(), depth+1, newReason,  new ArrayList<>(invokingList));
            }
        }
    }
    /**
     * addCallersOfSourceOfEdge
     * @param edge
     * @param relatedMethod
     * @param crashInfo
     * @param sootMethod
     * @param depth
     * @param keyAPIInvokingList
     */
    private void addCallersOfSourceOfEdge(int initScore, Edge edge, RelatedMethod relatedMethod,
                                          CrashInfo crashInfo, SootMethod sootMethod, int depth, JSONObject reason, List<String> keyAPIInvokingList) {
        keyAPIInvokingList.add(0, edge.getSrc().method().getSignature());
        String candi = sootMethod.getDeclaringClass().getName()+ "." + sootMethod.getName();
        int score = initScore - getOrderInTrace(crashInfo, candi) - relatedMethod.getDepth() - depth;
        if(crashInfo.getTrace().contains(candi)) score += ConstantUtils.METHODINTACE;
        if(currentMethodContainCandi(sootMethod, crashInfo)) {
//            reason.put("Explanation", "Caller of keyAPI " +relatedMethod.getMethod());
//            reason.put("Influenced Field", new JSONArray());
            List<String> fieldString = new ArrayList<>();
            for(String sf: crashInfo.getExceptionInfo().getRelatedFieldValuesInStr()){
                fieldString.add(sf);
//                if(reason.getJSONArray("Trace").toString().contains(sf)){
//                    reason.getJSONArray("Influenced Field").add(sf);
//                }
            }
//            reason.put("Signaler",crashInfo.getSignaler());

            int finalScore = crashInfo.addBuggyCandidates(candi, sootMethod.getSignature(), score, reason);
            reason.put("Reason Score", finalScore );
            GenerateReason.generateReasonForKeyAPIRelated(reason, candi, relatedMethod, crashInfo, fieldString, new ArrayList<>(keyAPIInvokingList));
        }
//        reason.getJSONArray("Trace").add(0,sootMethod.getSignature());
        //if the buggy type is not passed by parameter, do not find its caller
        Set<Integer> paramIndexCaller = SootUtils.getIndexesFromMethod(edge, crashInfo.exceptionInfo.getRelatedValueIndex());
        if(paramIndexCaller.size() == 0) return;

        if(!MyConfig.getInstance().getStrategy().equals(Strategy.NoCallFilter.toString())){
            int size = CollectionUtils.getSizeOfIterator(Global.v().getAppModel().getCg().edgesInto(sootMethod));
            if(size>ConstantUtils.LARGECALLERSET) return;
        }

        for (Iterator<Edge> it = Global.v().getAppModel().getCg().edgesInto(sootMethod); it.hasNext(); ) {
            Edge edge2 = it.next();
            if(edge2.toString().contains("dummyMainMethod")) continue;
            if( crashInfo.getEdges().contains(edge2) ) continue;
            crashInfo.add2EdgeMap(depth,edge2);
            JSONObject newReason = new JSONObject();
//            newReason.put("Reason Type", reason.get("Reason Type"));
//            newReason.put("Explanation", reason.get("Explanation"));
//            newReason.put("Influenced Field", reason.get("Influenced Field"));
//            newReason.put("Signaler", reason.get("Signaler"));
            addCallersOfSourceOfEdge(initScore, edge2, relatedMethod, crashInfo, edge2.getSrc().method(), depth+1,  newReason, keyAPIInvokingList);
        }
    }

    private boolean currentMethodContainCandi(SootMethod sootMethod, CrashInfo crashInfo) {
        if(MyConfig.getInstance().getStrategy().equals(Strategy.NoCallFilter)){
            return true;
        }
        for(String method: crashInfo.getCrashMethodList()) {
            for (SootMethod crashMethod : SootUtils.getSootMethodBySimpleName(method)) {
                if (crashMethod.hasActiveBody() && crashMethod.getActiveBody().toString().contains(sootMethod.getDeclaringClass().getName().split("\\$")[0])) {
                    return true;
                }
            }
        }
        return false;
    }
    /**
     * getBuggyFromRelatedMethods
     * @param crashInfo
     */
    private void getBuggyFromRelatedMethods(CrashInfo crashInfo, RelatedMethod relatedMethod, int initScore) {
//        log.error(relatedMethod.getMethod());
        crashInfo.setEdges(new ArrayList<>());
        int size =  0;
        if(!MyConfig.getInstance().getStrategy().equals(Strategy.NoCallFilter.toString())){
            size =getsizeOfCaller(relatedMethod.getMethod());
        }
//        log.info(size);
        //filter the related methods with too many caller
        for (Edge edge : Global.v().getAppModel().getCg()) {
            String sig = edge.getTgt().method().getSignature();
            if (sig.equals(relatedMethod.getMethod())){
                SootMethod sourceMtd = edge.getSrc().method();
                if (isLibraryMethod(sourceMtd.getDeclaringClass().getName()))
                    continue;
                boolean findInTrace = false;
                if (size > ConstantUtils.LARGECALLERSET) {
                    for (String className : crashInfo.getClassesInTrace()) {
                        if (sourceMtd.getSignature().contains(className)) {
                            findInTrace = true;
                            break;
                        }
                    }
                    if (!findInTrace) continue;
                }
                crashInfo.add2EdgeMap(0, edge);
                JSONObject reason = new JSONObject(true);
//                reason.put("Explanation", "");
//                reason.put("Trace", relatedMethod.getTrace());
                List<String> keyAPIInvokingList = new ArrayList<>();
                addCallersOfSourceOfEdge(initScore, edge, relatedMethod, crashInfo, sourceMtd, 1,  reason, keyAPIInvokingList);
            }
        }
    }

    private int getsizeOfCaller(String signature) {
        int size =0;
        for (Edge edge : Global.v().getAppModel().getCg()) {
            String tgtSig = edge.getTgt().method().getSignature();
            if (tgtSig.equals(signature)) {
                size++;
            }
        }
        return size;
    }

    private int getBottomNonLibMethod(CrashInfo crashInfo) {
        for(int j=crashInfo.getTrace().size()-1; j>=0; j--){
            String candi = crashInfo.getTrace().get(j);
            if(!isLibraryMethod(candi)){
                return j;
            }
        }
        return crashInfo.getTrace().size()-1;
    }


    private int getTopNonLibMethod(CrashInfo crashInfo) {
        for(int i=0; i<crashInfo.getTrace().size(); i++){
            String candi = crashInfo.getTrace().get(i);
            if(!isLibraryMethod(candi)){
                return i;
            }
        }
        return 0;
    }


    /**
     * get fields the related to the buggy variable passes to framework
     * @param crashMethod
     * @param crashInfo
     * @param faultInducingValue
     * @return
     */
    private List<SootField> getKeySootFields(SootMethod crashMethod, CrashInfo crashInfo, Value faultInducingValue) {
//        if(crashMethod==null || crashInfo.getExceptionInfo()==null) return null;
//        List<String> typeList = crashInfo.getExceptionInfo().getRelatedParamValuesInStr();
//        if(typeList==null) return null;
//
//        List<SootField> fields = new ArrayList<>();
//        for(SootField field: crashMethod.getDeclaringClass().getFields()) {
//            for (String bugParaType : typeList) {
//                if (field.getType().toString().equals(bugParaType)) {
//                    fields.add(field);
//                }
//            }
//
//        }
        if(crashMethod==null || crashInfo.getExceptionInfo()==null) return null;
        List<SootField> fields = new ArrayList<>();
        for(SootField field: crashMethod.getDeclaringClass().getFields()) {
            if (faultInducingValue!=null && field.getType().toString().equals(faultInducingValue.getType().toString())) {
                fields.add(field);
            }

        }
        return  fields;
    }
    /**
     * get the real crash soot method by string
     * @param crashInfo
     * @return
     */
    private Set<SootMethod> getCrashSootMethod(CrashInfo crashInfo) {
        return SootUtils.getSootMethodBySimpleName(crashInfo.getCrashMethod());
    }


    /**
     * OverrideMissing type
     * @param crashInfo*
     */
    private void overrideMissingHandler(int score, CrashInfo crashInfo) {
        log.info("overrideMissingHandler...");
        List buggyClasses = new ArrayList();
        for(SootClass sc: Scene.v().getApplicationClasses()){
            List<String> extendRelation = new ArrayList<>();
            if(!sc.hasSuperclass()) continue;
            if(sc.getSuperclass().getName().equals(crashInfo.getClassName())){
                extendRelation.add(sc.getName() + " extends " + crashInfo.getClassName());
                traceExtendTracesToNotOverrideCrash(crashInfo, sc, score,buggyClasses, extendRelation);
            }
        }
        int score2 = Math.max(crashInfo.maxScore-ConstantUtils.SMALLGAPSCORE, crashInfo.minScore - ConstantUtils.SMALLGAPSCORE);
        addCrashTracesNotOverride(score2, crashInfo, buggyClasses);
    }

    private void traceExtendTracesToNotOverrideCrash(CrashInfo crashInfo, SootClass currentCls, int score, List buggyClasses, List<String> extendRelation) {
        SootMethod sootMethod = null;
        for(SootMethod sm : currentCls.getMethods()){
            if(sm.getName().equals(crashInfo.getSubMethodName()) && sm.hasActiveBody()){
                sootMethod = sm;
            }
        }
        if(sootMethod==null) {
            String candi = currentCls.getName() + "." + crashInfo.getSubMethodName();
            int updateScore = score - getOrderInTrace(crashInfo, candi);
            JSONObject reason = new JSONObject(true);
//            reason.put("Explanation", "Forgets to override the signaler method");
//            JSONArray trace = new JSONArray();
//            reason.put("Trace", trace);
//            trace.add(crashInfo.getMethodName());
            int finalScore = crashInfo.addBuggyCandidates(candi, "",updateScore, reason);
            reason.put("Reason Score", finalScore );
            buggyClasses.add(currentCls.getName());
            GenerateReason.generateReasonForNotOverride(reason, currentCls, candi, crashInfo, extendRelation);

        }
        for(SootClass subCls: Scene.v().getActiveHierarchy().getSubclassesOf(currentCls)) {
            List<String> extendRelationStr2 =  new ArrayList<>(extendRelation);
            extendRelationStr2.add(0,subCls.getName()+ " extends " + currentCls.getName());
            traceExtendTracesToNotOverrideCrash(crashInfo, subCls, score,buggyClasses, extendRelationStr2);
        }
    }

    private void addCrashTracesNotOverride(int initscore, CrashInfo crashInfo, List buggyClasses) {
        int l = 0;
        for(String candi: crashInfo.getCrashMethodList()){
            if(l++>5) return;
            JSONObject reason = new JSONObject(true);

//            reason.put("Explanation", "Not influence the keyVar but in crash trace");
//            JSONArray trace = new JSONArray();
//            reason.put("Trace", trace);
            Set<SootMethod> methods = SootUtils.getSootMethodBySimpleName(candi);
            int finalScore =  initscore;
            for(SootMethod sm: methods) {
//                trace.add(sm.getSignature());
                finalScore = crashInfo.addBuggyCandidates(candi, sm.getSignature(), initscore--, reason);
                break;
            }
            if(methods.size()==0){
                finalScore = crashInfo.addBuggyCandidates(candi, "", initscore--, reason);
            }
            reason.put("Reason Score",finalScore);
            GenerateReason.generateReasonForNotOverrideInStackTrace(reason, candi, crashInfo);
        }
    }


    /**
     * getOrderInTrace
     */
    private int getOrderInTrace(CrashInfo crashInfo, String candi) {
        int order = 0;
        for (String tag : crashInfo.getClassesInTrace()) {
            if(isLibraryMethod(tag)) continue;
            //TODO whether consider $ or not ??
            if (tag.contains("$"))  tag = tag.split("\\$")[0];
            order++;
            if (candi.contains(tag)) {
                crashInfo.findCandidateInTrace = true;
                return order;
            }
        }
        return order+2;
    }

    public void getExceptionOfCrashInfo() {
        log.info("getExceptionOfCrashInfo...");
        for(CrashInfo crashInfo: this.crashInfoList) {
            if(crashInfo.getTrace().size()==0 ) continue;
            String targetVer;
            String targetMethodName;
            if(MyConfig.getInstance().getAndroidOSVersion()==null) {
                String[] versionTypes = new String[versions.length];
                String[] versionTypeCandis = new String[versions.length];
                String[] targetMethodNames = new String[versions.length];
                int i = -1;
                int targetVerId = -1;
                int minDistance2TargetAPI = 100;
                log.info("use versionTypes to match");
                for (String version : versions) {
                    i++;
                    Pair<String, String> pair = getExceptionWithGivenVersion(crashInfo, version, true);
                    versionTypes[i] = pair.getO1();
                    targetMethodNames[i] = pair.getO2();
                    if (versionTypes[i].equals("notFound")) {
                        Pair<String, String> pair2 = getExceptionWithGivenVersion(crashInfo, version, false);
                        versionTypeCandis[i] = pair2.getO1();
                        targetMethodNames[i] = pair2.getO2();
                    }else if (versionTypes[i].equals("noFile")){
                        log.info("version "+ versions[i] +" not exist.");
                    }else{
                        if(APILevels[i]>=appModel.getMinSdkVersion()){
                            if(Math.abs(appModel.getTargetSdkVersion()-APILevels[i])<minDistance2TargetAPI){
                                minDistance2TargetAPI = Math.abs(appModel.getTargetSdkVersion()-APILevels[i]);
                                targetVerId = i;
                            }
                        }
                    }
                }
                log.info("versionTypes: "+PrintUtils.printArray(versionTypes));
                if(targetVerId==-1){
                    log.info("use versionTypeCandis to match");
                    log.info("versionTypeCandis: "+PrintUtils.printArray(versionTypeCandis));
                    for (int j=0; j< versionTypeCandis.length; j++) {
                        if (!(versionTypeCandis[j].equals("notFound") || versionTypeCandis[j].equals("noFile"))){
                            if(APILevels[j]>=appModel.getMinSdkVersion()){
                                if(Math.abs(appModel.getTargetSdkVersion()-APILevels[j])<minDistance2TargetAPI){
                                    minDistance2TargetAPI = Math.abs(appModel.getTargetSdkVersion()-APILevels[j]);
                                    targetVerId = j;
                                }
                            }
                        }
                    }
                }
                if(targetVerId==-1){
                    log.info("use min and target version to match");
                    for (int j=0; j< versionTypeCandis.length; j++) {
                        if(APILevels[j]>=appModel.getMinSdkVersion()){
                            if(Math.abs(appModel.getTargetSdkVersion()-APILevels[j])<minDistance2TargetAPI){
                                minDistance2TargetAPI = Math.abs(appModel.getTargetSdkVersion()-APILevels[j]);
                                targetVerId = j;
                            }
                        }
                    }
                }
                targetVer = versions[targetVerId];
                targetMethodName = targetMethodNames[targetVerId];
                log.info("MinSdkVersion: "+appModel.getMinSdkVersion());
                log.info("TargetSdkVersion: "+appModel.getTargetSdkVersion());
                log.info("version "+ versions[targetVerId] +" is matched.");
            }else{
                targetVer = MyConfig.getInstance().getAndroidOSVersion();
                targetMethodName = crashInfo.getSignaler();
            }
            String androidFolder = MyConfig.getInstance().getExceptionFolderPath()+File.separator+"android"+targetVer+File.separator;
            MyConfig.getInstance().setExceptionFilePath(androidFolder+"exceptionInfo"+File.separator);
            MyConfig.getInstance().setPermissionFilePath(androidFolder+"Permission"+File.separator+"permission.txt");
            MyConfig.getInstance().setAndroidCGFilePath(androidFolder+"CallGraphInfo"+File.separator+"android"+targetVer+"_cg.txt");
            MyConfig.getInstance().setMethodInfoFilePath(androidFolder+"CodeInfo"+File.separator+"methodInfo.json");
            log.info("target is "+ targetVer);
            MyConfig.getInstance().setTargetVersion(targetVer);
            readExceptionSummary(crashInfo, targetMethodName);
        }
    }


    public void getExceptionOfCrashInfo_Back() {
        log.info("getExceptionOfCrashInfo...");
        for(CrashInfo crashInfo: this.crashInfoList) {
            if(crashInfo.getTrace().size()==0 ) continue;
            String targetVer;
            String targetMethodName;
            if(MyConfig.getInstance().getAndroidOSVersion()==null) {
                String str = crashInfo.getId() + "\t" + crashInfo.getSignaler() + "\t";
                String[] versionTypes = new String[versions.length];
                String[] versionTypeCandis = new String[versions.length];
                String[] targetMethodNames = new String[versions.length];
                int i = 0;
                for (String version : versions) {
                    Pair<String, String> pair = getExceptionWithGivenVersion(crashInfo, version, true);
                    versionTypes[i] = pair.getO1();
                    targetMethodNames[i] = pair.getO2();
                    if (versionTypes[i].equals("notFound")) {
                        Pair<String, String> pair2 = getExceptionWithGivenVersion(crashInfo, version, false);
                        versionTypeCandis[i] = pair2.getO1();
                        targetMethodNames[i] = pair2.getO2();
                    }else if (versionTypes[i].equals("noFile")){
                        log.info("version "+ versions[i] +" not exist.");
                    }else{
                        log.info("version "+ versions[i] +" is matched.");
                    }
                    i++;
                }

                int targetVerId = getTargetVersion(versionTypes);
                if (targetVerId == -1) {
                    targetVerId = getTargetVersion(versionTypeCandis);
                    log.info(PrintUtils.printArray(versionTypeCandis));
                } else {
                    log.info(PrintUtils.printArray(versionTypes));
                }
                if (targetVerId == -1)
                    targetVerId = 6;
                targetVer = versions[targetVerId];
                targetMethodName = targetMethodNames[targetVerId];
            }else{
                targetVer = MyConfig.getInstance().getAndroidOSVersion();
                targetMethodName = crashInfo.getSignaler();
            }
            String androidFolder = MyConfig.getInstance().getExceptionFolderPath()+File.separator+"android"+targetVer+File.separator;
            MyConfig.getInstance().setExceptionFilePath(androidFolder+"exceptionInfo"+File.separator);
            MyConfig.getInstance().setPermissionFilePath(androidFolder+"Permission"+File.separator+"permission.txt");
            MyConfig.getInstance().setAndroidCGFilePath(androidFolder+"CallGraphInfo"+File.separator+"android"+targetVer+"_cg.txt");
            MyConfig.getInstance().setMethodInfoFilePath(androidFolder+"CodeInfo"+File.separator+"methodInfo.json");

            log.info("target is "+ targetVer);
            MyConfig.getInstance().setTargetVersion(targetVer);
            readExceptionSummary(crashInfo, targetMethodName);
        }
    }

    private int getTargetVersion(String[] versionType) {
        int paraAndField =0 , fieldOnly =0 ,parameterOnly =0 , overrideMissing = 0;
        log.info(PrintUtils.printArray(versionType));
        for(String relatedVarType: versionType) {
            if(relatedVarType ==null) continue;
            if (relatedVarType.equals(RelatedVarType.ParaAndField.toString())) paraAndField++;
            if (relatedVarType.equals(RelatedVarType.Field.toString())) fieldOnly++;
            if (relatedVarType.equals(RelatedVarType.Parameter.toString())) parameterOnly++;
            if (relatedVarType.equals(RelatedVarType.Empty.toString())) overrideMissing++;
        }
        String choice = RelatedVarType.Unknown.toString();
        if(paraAndField + parameterOnly + fieldOnly + overrideMissing ==0)
            choice = RelatedVarType.Unknown.toString();
        else if(paraAndField >= parameterOnly && paraAndField >= fieldOnly && paraAndField >= overrideMissing)
            choice =  RelatedVarType.ParaAndField.toString();
        else if(parameterOnly >= fieldOnly && parameterOnly >= paraAndField && parameterOnly >= overrideMissing)
            choice = RelatedVarType.Parameter.toString();
        else if(fieldOnly >= parameterOnly && fieldOnly >= paraAndField && fieldOnly >= overrideMissing)
            choice =  RelatedVarType.Field.toString();
        else if(overrideMissing >= parameterOnly && overrideMissing >= paraAndField && overrideMissing >= fieldOnly)
            choice = RelatedVarType.Empty.toString();

        int count=0;
        List<Integer> vers = new ArrayList<>();
        for(int i = 0; i<versionType.length; i++) {
            if(versionType[i]!=null && versionType[i].equals(choice)){
                count++;
                vers.add(i);
            }
        }
        if(count>0){
           return vers.get(count/2);
        }
        return -1;
    }

    /**
     * getExceptionOfCrashInfo from exception.json
     */
    private Pair<String,String> getExceptionWithGivenVersion(CrashInfo crashInfo, String version, boolean classFilter) {
        String androidFolder = MyConfig.getInstance().getExceptionFolderPath()+File.separator+"android"+version+File.separator;
        MyConfig.getInstance().setExceptionFilePath(androidFolder+"exceptionInfo"+File.separator);
        MyConfig.getInstance().setPermissionFilePath(androidFolder+"Permission"+File.separator+"permission.txt");
        MyConfig.getInstance().setAndroidCGFilePath(androidFolder+"CallGraphInfo"+File.separator+"android"+version+"_cg.txt");
        MyConfig.getInstance().setMethodInfoFilePath(androidFolder+"CodeInfo"+File.separator+"methodInfo.json");

        String fn = MyConfig.getInstance().getExceptionFilePath()+"summary"+ File.separator+ "exception.json";
        String jsonString = FileUtils.readJsonFile(fn);
        JSONObject wrapperObject = (JSONObject) JSONObject.parse(jsonString);
        if(wrapperObject==null) return new Pair<>("noFile",crashInfo.getSignaler());
        JSONArray methods = wrapperObject.getJSONArray("exceptions");//构建JSONArray数组
        for (Object method : methods) {
            JSONObject jsonObject = (JSONObject) method;
            ExceptionInfo exceptionInfo = new ExceptionInfo();
            exceptionInfo.setSootMethodName(jsonObject.getString("method"));
            if (!classFilter || crashInfo.getSignaler().equals(exceptionInfo.getSootMethodName())) {
                exceptionInfo.setExceptionMsg(jsonObject.getString("message"));
                if (exceptionInfo.getExceptionMsg() == null) continue;
                Pattern p = Pattern.compile(exceptionInfo.getExceptionMsg());
                Matcher m = p.matcher(crashInfo.getMsg());
                if (m.matches()) {
                    String str = exceptionInfo.getExceptionMsg();
                    str = str.replace("[\\s\\S]*", "");
                    str = str.replace("\\Q", "");
                    str = str.replace("\\E", "");
                    if (str.length() < 3)
                        continue;
                    if (jsonObject.getString("relatedVarType") != null) {
                        return new Pair<>(jsonObject.getString("relatedVarType"), exceptionInfo.getSootMethodName());
                    }else{
                        return new Pair<>("Unknown", exceptionInfo.getSootMethodName());
                    }
                }
            }
        }
        return new Pair<>("notFound",crashInfo.getSignaler());
    }



    /**
     * readExceptionSummary from ExceptionFile
     * @param crashInfo
     * @param targetMethodName
     */
    private void readExceptionSummary(CrashInfo crashInfo, String targetMethodName) {
        if(!crashInfo.getMethodName().equals(targetMethodName)) {
            crashInfo.setMethodName(targetMethodName);
        }
        String fn = MyConfig.getInstance().getExceptionFilePath()+crashInfo.getClassName()+".json";
        if(loadedExceptionSummary.contains(fn)) return;
        loadedExceptionSummary.add(fn);
        log.info("readExceptionSummary::"+fn);
        String jsonString = FileUtils.readJsonFile(fn);
        JSONObject wrapperObject = (JSONObject) JSONObject.parse(jsonString);
        if(wrapperObject==null) {
            log.info( crashInfo.getClassName()+" is not modeled.");
            return;
        }
        JSONArray methods = wrapperObject.getJSONArray("exceptions");//构建JSONArray数组
        boolean flag= false;
        for (Object method : methods) {
            JSONObject jsonObject = (JSONObject) method;
            ExceptionInfo exceptionInfo = new ExceptionInfo();
            exceptionInfo.setSootMethodName(jsonObject.getString("method"));
            exceptionInfo.setExceptionMsg(jsonObject.getString("message"));

            //??????????????????
            //if(info.getField2InitialMethod().size()>0)
            //    jsonObject.put("field2InitialMethod", info.getField2InitialMethod());
            Map fieldMap = jsonObject.getObject("field2InitialMethod",  HashMap.class);
            exceptionInfo.setField2InitialMethod(fieldMap);

            if (exceptionInfo.getSootMethodName().equals(crashInfo.getMethodName())) {
                if (exceptionInfo.getExceptionMsg() == null) continue;
                Pattern p = Pattern.compile(exceptionInfo.getExceptionMsg());
                Matcher m = p.matcher(crashInfo.getMsg());
                String str = exceptionInfo.getExceptionMsg();
                str = str.replace("[\\s\\S]*", "");
                str = str.replace("\\Q", "");
                str = str.replace("\\E", "");
                if (str.length() >= 3) {
                    if (m.matches()) {
                        crashInfo.setExceptionInfo(exceptionInfo);
                        flag = true;
                    } else {
                        continue;
                    }
                }
            }
            readMoreInfo(exceptionInfo, jsonObject);
        }
        if(flag) return;
        for (Object method : methods) {
            JSONObject jsonObject = (JSONObject) method;
            ExceptionInfo exceptionInfo = new ExceptionInfo();
            exceptionInfo.setSootMethodName(jsonObject.getString("method"));
            exceptionInfo.setExceptionMsg(jsonObject.getString("message"));

            if (exceptionInfo.getSootMethodName().equals(crashInfo.getMethodName())) {
                if (exceptionInfo.getExceptionMsg() == null) continue;
                Pattern p = Pattern.compile(exceptionInfo.getExceptionMsg());
                Matcher m = p.matcher(crashInfo.getMsg());
                if (m.matches()) {
                    crashInfo.setExceptionInfo(exceptionInfo);
                } else {
                    continue;
                }

            }
            readMoreInfo(exceptionInfo, jsonObject);
        }
    }

    private void readMoreInfo(ExceptionInfo exceptionInfo, JSONObject jsonObject) {
        exceptionInfo.setExceptionType(jsonObject.getString("type"));
        exceptionInfo.setModifier(jsonObject.getString("modifier"));
        exceptionInfo.setOsVersionRelated(jsonObject.getBoolean("osVersionRelated"));
        exceptionInfo.setResourceRelated(jsonObject.getBoolean("resourceRelated"));
        exceptionInfo.setAssessRelated(jsonObject.getBoolean("assessRelated"));
        exceptionInfo.setHardwareRelated(jsonObject.getBoolean("hardwareRelated"));
        exceptionInfo.setManifestRelated(jsonObject.getBoolean("manifestRelated"));
        exceptionInfo.setConditions(jsonObject.getString("conditions"));
        exceptionInfo.setRelatedParamValuesInStr(jsonObject.getString("paramValues"));
        exceptionInfo.setRelatedFieldValuesInStr(jsonObject.getString("fieldValues"));
        if (jsonObject.getString("relatedCondType") != null)
            exceptionInfo.setRelatedCondType(RelatedCondType.valueOf(jsonObject.getString("relatedCondType")));

        exceptionInfo.setRelatedVarType(RelatedVarType.valueOf(jsonObject.getString("relatedVarType")));

        //strategy NoKeyAPI
        if(!MyConfig.getInstance().getStrategy().equals(Strategy.NoKeyAPI.toString()) ){
            JSONArray sameClsObjs = jsonObject.getJSONArray("keyAPISameClass");
            for (Object obj : sameClsObjs) {
                JSONObject sameClsObj = (JSONObject) obj;
                RelatedMethod relatedMethod = new RelatedMethod();
                relatedMethod.setMethod(sameClsObj.getString("method"));

                relatedMethod.setDepth(sameClsObj.getInteger("depth"));
                relatedMethod.setSource(RelatedMethodSource.valueOf(sameClsObj.getString("source")));
                String trace = sameClsObj.getString("trace");
                String newTrace =  trace.replace("[", "").
                        replace("]", "").replace("\"", "");
                for(String mtd:newTrace.split(",")){
                    relatedMethod.addTrace(mtd);
                }
                relatedMethod.getTrace().remove(relatedMethod.getTrace().size()-1);
                exceptionInfo.addRelatedMethodsInSameClass(relatedMethod);
            }
            JSONArray diffClsObjs = jsonObject.getJSONArray("keyAPIDiffClass");
            for (Object clsObj : diffClsObjs) {
                JSONObject diffClsObj = (JSONObject) clsObj;
                RelatedMethod relatedMethod = new RelatedMethod();
                relatedMethod.setMethod(diffClsObj.getString("method"));
                relatedMethod.setDepth(diffClsObj.getInteger("depth"));
                relatedMethod.setSource(RelatedMethodSource.valueOf(diffClsObj.getString("source")));
                String trace = diffClsObj.getString("trace");
                String newTrace =  trace.replace("[", "").
                        replace("]", "").replace("\"", "").replace(">,", ">, ");
                relatedMethod.addTrace(newTrace);
                exceptionInfo.addRelatedMethodsInDiffClass(relatedMethod);
            }
        }

        //strategy NoParaChain TODO
        if(!MyConfig.getInstance().getStrategy().equals(Strategy.NoParaChain.toString())
                && !MyConfig.getInstance().getStrategy().equals(Strategy.NOParaChainANDDataTrace.toString()) ) {
            JSONObject callerOfSingnlar2SourceVar = jsonObject.getJSONObject("callerOfSingnlar2SourceVar");
            if (callerOfSingnlar2SourceVar != null) {
                for (String key : callerOfSingnlar2SourceVar.keySet()) {
                    String[] ids = ((String) callerOfSingnlar2SourceVar.get(key)).split(", ");
                    for (String id : ids)
                        exceptionInfo.addCallerOfSingnlar2SourceVar(SootUtils.getMethodSimpleNameFromSignature(key), Integer.valueOf(id));
                }
            }
        }
    }

    /**
     * readCrashInfo from CrashInfoFile
     */
    private void readCrashInfo() {
        String fn = MyConfig.getInstance().getCrashInfoFilePath();
        log.info("readCrashInfo::"+fn);
        String jsonString = FileUtils.readJsonFile(fn);
        JSONArray jsonArray = JSONArray.parseArray(jsonString);
        for (Object o : jsonArray) {
            JSONObject jsonObject = (JSONObject) o;
            CrashInfo crashInfo = new CrashInfo();
            crashInfo.setIdentifier(jsonObject.getString("identifier"));
            crashInfo.setId(jsonObject.getString("id"));
            if (Global.v().getAppModel().getAppName().equals(crashInfo.getIdentifier() + "-" + crashInfo.getId())) {
                crashInfoList.add(crashInfo);
                if(jsonObject.getString("package")!=null){
                    Global.v().getAppModel().setPackageName(jsonObject.getString("package"));
                }
                if (Global.v().getAppModel().getPackageName().length() == 0 && Global.v().getAppModel().getAppName().contains(crashInfo.getIdentifier())) {
                    Global.v().getAppModel().setPackageName(crashInfo.getIdentifier());
                }
                crashInfo.setReal(jsonObject.getString("real"));
                crashInfo.setException(jsonObject.getString("exception"));
                crashInfo.setTrace(jsonObject.getString("trace"));
                crashInfo.setBuggyApi(jsonObject.getString("buggyApi"));
                crashInfo.setMsg(jsonObject.getString("msg").trim());
                crashInfo.setRealCate(jsonObject.getString("realCate"));
                crashInfo.setCategory(jsonObject.getString("category"));
                if (jsonObject.getString("fileName") != null)
                    crashInfo.setId(jsonObject.getString("fileName"));
                else
                    crashInfo.setId(crashInfo.getIdentifier() + "-" + jsonObject.getString("id"));
                crashInfo.setReason(jsonObject.getString("reason"));
                crashInfo.setMethodName(crashInfo.getTrace().get(0));
                if(jsonObject.getString("relatedVarType")!=null)
                    crashInfo.setRelatedVarTypeOracle(RelatedVarType.valueOf(jsonObject.getString("relatedVarType")));
                if(jsonObject.getString("relatedCondType")!=null)
                    crashInfo.setRelatedCondTypeOracle(RelatedCondType.valueOf(jsonObject.getString("relatedCondType")));

                JSONObject callerOfSingnlar2SourceVar = jsonObject.getJSONObject("callerOfSingnlar2SourceVar");
                if (callerOfSingnlar2SourceVar != null) {
                    for (String key : callerOfSingnlar2SourceVar.keySet()) {
                        String s = ((String) callerOfSingnlar2SourceVar.get(key)).replace(", ", ",");
                        String[] ids = ((String) callerOfSingnlar2SourceVar.get(key)).split(",");
                        for (String id : ids)
                            crashInfo.addCallerOfSingnlar2SourceVarOracle(SootUtils.getMethodSimpleNameFromSignature(key), Integer.valueOf(id));
                    }
                }

            }
        }
    }

    /**
     * read  method info from json file, CodeInfo/methodInfo.json
     */
    private void readMethodInfo() {
        String fn = MyConfig.getInstance().getMethodInfoFilePath();
        log.info("readMethodInfo::"+fn);
        String jsonString = FileUtils.readJsonFile(fn);
        JSONArray methods = (JSONArray) JSONArray.parse(jsonString);
        for (Object method : methods) {
            JSONObject jsonObject = (JSONObject) method;
            MethodModel methodModel = new MethodModel();
            methodModel.setMethodSignature((String) jsonObject.get("methodSignature"));
            methodModel.setClassName((String) jsonObject.get("className"));
            methodModel.setSimpleName((String) jsonObject.get("simpleName"));
            methodModel.setArguments((List<String>) jsonObject.get("Arguments"));
            androidSimpleName2Signature.put(methodModel.getSimpleName(),methodModel.getMethodSignature());
            appModel.getMethodModelList().add(methodModel);
        }
    }

    private void readAndroidCG() {
        if(androidCGMap.size()>0) return;
        String fn = MyConfig.getInstance().getAndroidCGFilePath();
        log.info("readAndroidCG::"+fn);
        List<String> edges = FileUtils.getListFromFile(fn);
        if(edges==null) return;
        for(String edge: edges){
            if(!edge.contains(" -> ")) continue;
            String src = SootUtils.getMethodSimpleNameFromSignature(edge.split(" -> ")[0]);
//            androidSimpleName2Signature.put(src,edge.split(" -> ")[0]);
            String des = SootUtils.getMethodSimpleNameFromSignature(edge.split(" -> ")[1]);
//            androidSimpleName2Signature.put(des,edge.split(" -> ")[1]);
            if(!androidCGMap.containsKey(src))
                androidCGMap.put(src,new HashSet<>());
            androidCGMap.get(src).add(des);
        }
    }




    /**
     * none code related labels
     * @param crashInfo crash instance
     */
    private void checkIsNoAppRelatedHandler(CrashInfo crashInfo) {
        ExceptionInfo info = crashInfo.getExceptionInfo();
        if(info!=null && info.isOsVersionRelated()){
            crashInfo.addNoneCodeLabel("OS Update");
        }else if(crashInfo.getMsg().contains("version")){
            crashInfo.addNoneCodeLabel("OS Update");
        }
        if(info!=null && info.isAssessRelated()){
            crashInfo.addNoneCodeLabel("Asset");
        }
        if(info!=null && info.isManifestRelated()){
            crashInfo.addNoneCodeLabel("Manifest XML");
        } else if(containPermissionString(crashInfo.getMsg())){
            crashInfo.addNoneCodeLabel("Manifest XML");
        }else if(containmActivityInfo(crashInfo)){
            crashInfo.addNoneCodeLabel("Manifest XML");
        }
        if(info!=null && info.isHardwareRelated()) {
            crashInfo.addNoneCodeLabel("Hardware");
        } else if(crashInfo.getCrashAPI() !=null && (crashInfo.getCrashAPI().contains("hardware") || crashInfo.getCrashAPI().contains("opengl")
                || crashInfo.getCrashAPI().contains("nfc") || crashInfo.getCrashAPI().contains("bluetooth") )) {
            crashInfo.addNoneCodeLabel("Hardware");
        }
        if(info!=null && info.isResourceRelated()){
            crashInfo.addNoneCodeLabel("Resource XML");
        }
    }

    private boolean containmActivityInfo(CrashInfo crashInfo) {
        if(crashInfo.getExceptionInfo() ==null)
            return false;
        for(String field: crashInfo.getExceptionInfo().getRelatedFieldValuesInStr()){
            if(field.contains("android.app.Activity: android.content.pm.ActivityInfo mActivityInfo")){
                return true;
            }
        }
        return false;
    }

    private boolean containPermissionString(String msg) {
        String fn = MyConfig.getInstance().getPermissionFilePath();
        log.info(fn);
        List<String> list = FileUtils.getListFromFile(fn);
        if(list==null) return  false;
        for(String str: list){
            str = str.trim().replace("android.permission.","");
            if(msg.contains(str) && str.length()>0){
                return true;
            }
        }
        return  false;
    }


    private void printCrash2Edges() {
        for(CrashInfo crashInfo : crashInfoList){
            log.info("methodName::"+ crashInfo.getMethodName());
            log.info("msg::"+ crashInfo.getMsg());
            String buggyRanking = getRankingString(crashInfo, 999);
            List<Map.Entry<String, Integer>> treeMapList = CollectionUtils.getTreeMapEntriesSortedByValue(crashInfo.getBuggyCandidates());
            for (int i = 0; i < treeMapList.size(); i++) {
                String buggy = treeMapList.get(i).getKey();
                log.info((i+1)+" @ " +treeMapList.get(i).toString() );
                if(crashInfo.getReal().equals(buggy)){
                    buggyRanking = getRankingString(crashInfo,  i+1);
                }
            }
            log.info(buggyRanking);
            FileUtils.createFolder(MyConfig.getInstance().getResultWarpperFolder());
            FileUtils.writeText2File(MyConfig.getInstance().getResultWarpperFolder() +"BuggyCandidatesRanking.txt", buggyRanking, true);
        }
    }

    private String getRankingString(CrashInfo crashInfo, int location) {
        int sizeAll = crashInfo.getBuggyCandidates().size();
        String size = "/\t"+ sizeAll;
//        if(crashInfo.getExceptionInfo()!=null && crashInfo.getExceptionInfo().getRelatedCondType() == RelatedCondType.Caught){
//            return crashInfo.getRealCate() + "\t" + crashInfo.getId() + "\t" + crashInfo.getMethodName() + "\t"
//                    + "CaughtException" + "\t" + location + "\t" + size + "\t" + PrintUtils.printList(crashInfo.getNoneCodeLabel()) + "\n";
//        }else {
//            return crashInfo.getRealCate() + "\t" + crashInfo.getId() + "\t" + crashInfo.getMethodName() + "\t"
//                    + relatedVarType + "\t" + location + "\t" + size + "\t" + PrintUtils.printList(crashInfo.getNoneCodeLabel()) + "\n";
//        }
        return crashInfo.getRealCate() + "\t" + crashInfo.getId() + "\t" + crashInfo.getMethodName() + "\t"
                + relatedVarType + "\t" + location + "\t" + size + "\t" + PrintUtils.printList(crashInfo.getNoneCodeLabel()) + "\n";
    }


}

