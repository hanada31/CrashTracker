package main.java.client.crash;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import main.java.Analyzer;
import main.java.Global;
import main.java.MyConfig;
import main.java.analyze.utils.CollectionUtils;
import main.java.analyze.utils.ConstantUtils;
import main.java.analyze.utils.SootUtils;
import main.java.analyze.utils.output.FileUtils;
import main.java.analyze.utils.output.PrintUtils;
import main.java.client.exception.*;
import main.java.client.statistic.model.StatisticResult;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.scalar.Pair;
import soot.toolkits.scalar.UnitValueBoxPair;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static main.java.analyze.utils.SootUtils.getFiledValueAssigns;

/**
 * @Author hanada
 * @Date 2022/3/22 20:20
 * @Version 1.0
 */
public class CrashAnalysis extends Analyzer {
    List<CrashInfo> crashInfoList;
    Set<String> loadedExceptionSummary;
    Map<String, Set<String>> androidCGMap;
    String relatedVarType="";
    String[] versions = {"4.4", "5.0", "6.0", "7.0", "8.0", "9.0", "10.0", "11.0", "12.0"};

    public CrashAnalysis(StatisticResult result) {
        crashInfoList = Global.v().getAppModel().getCrashInfoList();
        loadedExceptionSummary = new HashSet<>();
        androidCGMap = new HashMap<>();
    }

    @Override
    public void analyze() {
        readCrashInfo();
        System.out.println("readCrashInfo Finish...");
        getExceptionOfCrashInfo();
        System.out.println("getExceptionOfCrashInfo Finish...");
        getCandidateBuggyMethods();
        System.out.println("getCandidateBuggyMethods Finish...");
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
            getExtendedCallTrace(crashInfo);
            if(exceptionInfo!=null && exceptionInfo.getRelatedVarType()!=null) {
                switch (exceptionInfo.getRelatedVarType()) {
                    //first choice filterExtendedCG false, second choice true
                    case OverrideMissing:
                        relatedVarType="OverrideMissing";
                        overrideMissingHandler(ConstantUtils.INITSCORE,crashInfo, false); //OMA
                        break;
                    case ParameterOnly:
                        relatedVarType="ParameterOnly";
                        withParameterHandler(ConstantUtils.INITSCORE, crashInfo, false); //TMA
                        break;
                    case FieldOnly:
                        relatedVarType="FieldOnly";
                        withFieldHandler(ConstantUtils.INITSCORE, crashInfo, false); //FCA
                        int score = Math.max(crashInfo.maxScore-ConstantUtils.SMALLGAPSCORE, crashInfo.minScore - ConstantUtils.SMALLGAPSCORE);
                        addCrashTraces(score, crashInfo,true);

//                        for(String method: exceptionInfo.getRelatedMethods()) {
//                            if (exceptionInfo.getSootMethod().getSignature().equals(method)){
//                                if(exceptionInfo.getRelatedVarType().equals(RelatedVarType.FieldOnly)){
//                                    int score = Math.max(crashInfo.maxScore-ConstantUtils.SMALLGAPSCORE, crashInfo.minScore - ConstantUtils.SMALLGAPSCORE);
//                                    addCrashTraces(score, crashInfo,true);
//                                    break;
//                                }
//                            }
//                        }
                        break;
                    case ParaAndField:
                        relatedVarType="ParaAndField";
                        withFieldHandler(ConstantUtils.INITSCORE, crashInfo, false); //FCA
                        withParameterHandler(ConstantUtils.INITSCORE, crashInfo, false); //TMA
                        break;
                }
            }else {
                relatedVarType="unknown"; // native and other no exception.
                withParameterHandler(ConstantUtils.INITSCORE, crashInfo, false);
            }
            System.out.println("### relatedVarType is " + relatedVarType);
        }
    }



    private void addCrashTraces(int initscore, CrashInfo crashInfo, boolean filterExtendCG) {
        for(String candi: crashInfo.getCrashMethodList()){
            List<String> trace = new ArrayList<>();
            trace.add(candi);
            crashInfo.addBuggyCandidates(candi,initscore--,filterExtendCG, "crash_trace_method", trace);
        }
    }


    /**
     * use the same strategy as CrashLocator, extend cg, remove control flow and data flow unrelated edges
     * @param crashInfo
     */
    private void getExtendedCallTrace(CrashInfo crashInfo) {
        for(int index = crashInfo.getCrashMethodList().size()-1; index>=0; index--) {
            String candi = crashInfo.getCrashMethodList().get(index);
            crashInfo.addExtendedCallDepth(candi, 1);

            //all function in the last method
            //methods that preds of the next one in call stack
            Set<SootMethod> methods = SootUtils.getSootMethodBySimpleName(candi);
            for(SootMethod sm: methods) {
                addEntryMethods2ExtendedCG(sm, crashInfo);
                String last = (index == 0)?crashInfo.getCrashAPI(): crashInfo.getCrashMethodList().get(index-1);
                addPredCallersOfMethodsInStack(last,sm,crashInfo);
            }
        }
    }

    private void addPredCallersOfMethodsInStack(String last, SootMethod sm, CrashInfo crashInfo) {
        if(!sm.hasActiveBody())return;
        for(Unit u : sm.getActiveBody().getUnits()){
            InvokeExpr invoke = SootUtils.getSingleInvokedMethod(u);
            if (invoke != null) { // u is invoke stmt
                String callee = invoke.getMethod().getDeclaringClass().getName()+ "." + invoke.getMethod().getName();
                if(callee.equals(last)){
                    addPredsOfUnit2ExtendedCG(u, sm, crashInfo, 2);
                }
            }
        }
    }

    private void addEntryMethods2ExtendedCG(SootMethod sm, CrashInfo crashInfo) {
        if(!sm.hasActiveBody())return;
        for(SootMethod entry: sm.getDeclaringClass().getMethods()) {
            if(appModel.getEntryMethods().contains(entry) || entry.getName().startsWith("on")){
                String callee = entry.getDeclaringClass().getName()+ "." + entry.getName();
                if(crashInfo.addExtendedCallDepth(callee, 2)) {
                    addAllCallee2ExtendedCG(entry, crashInfo, 3);
                }
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
     */
    private void addPredsOfUnit2ExtendedCG(Unit u, SootMethod sm, CrashInfo crashInfo, int depth) {
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
        Set<Unit> dataSlice = new HashSet<Unit>();
        getDataSliceOfUnit(dataSlice, sm, u, predUnits);
        for(Unit pred: predUnits){
            if(!dataSlice.contains(pred)) continue;
            Set<SootMethod> calleeMethod = SootUtils.getInvokedMethodSet(sm, pred);
            for(SootMethod method: calleeMethod){
                String callee = method.getDeclaringClass().getName()+ "." + method.getName();
                if(crashInfo.addExtendedCallDepth(callee, depth)){
                    addAllCallee2ExtendedCG(method, crashInfo, depth+1);
                }
            }
        }
    }

    private void getDataSliceOfUnit(Set<Unit> dataSlice, SootMethod sm, Unit u, List<Unit> predUnits) {
        InvokeExpr invokeExpr = SootUtils.getInvokeExp(u);
        if(invokeExpr==null) return;
        for (Value val : invokeExpr.getArgs()){
            List<Unit> defs = SootUtils.getDefOfLocal(sm.getSignature(), val, u);
            for(Unit def: defs) {
                List<UnitValueBoxPair> uses = SootUtils.getUseOfLocal(sm.getSignature(), def);
                for(UnitValueBoxPair pair : uses){
                    if(predUnits.contains(pair.getUnit()) && !dataSlice.contains(pair.getUnit()) ) {
                        dataSlice.add(pair.getUnit());
                        getDataSliceOfUnit(dataSlice, sm, pair.getUnit(), predUnits);
                    }
                }
            }
        }
    }

    private void addAllCallee2ExtendedCG(SootMethod sm, CrashInfo crashInfo, int depth) {
        if(depth> ConstantUtils.EXTENDCGDEPTH) return;
        for (Iterator<Edge> it = Global.v().getAppModel().getCg().edgesOutOf(sm); it.hasNext(); ) {
            Edge edge = it.next();
            String callee = edge.getTgt().method().getDeclaringClass().getName()+ "." + edge.getTgt().method().getName();
            if(crashInfo.addExtendedCallDepth(callee, depth)){
                addAllCallee2ExtendedCG(edge.getTgt().method(), crashInfo, depth+1);
            }
        }
    }




    /**
     * getBuggyFromUserCode
     * @param crashInfo
     * @param filterExtendCG
     */
    private void withAppFieldCallHandler(int score, CrashInfo crashInfo, boolean filterExtendCG) {
        System.out.println("getBuggyFromUserCode......");
        Set<SootMethod> crashMethods = getCrashSootMethod(crashInfo);
        for(SootMethod crashMethod:crashMethods) {
            List<SootField> keyFields = getKeySootFields(crashMethod, crashInfo);
            for (SootField field : keyFields){
                for (SootMethod otherMethod : crashMethod.getDeclaringClass().getMethods()) {
                    if (!otherMethod.hasActiveBody()) continue;
                    if (SootUtils.fieldIsChanged(field, otherMethod)) {
                        String candi = otherMethod.getDeclaringClass().getName() + "." + otherMethod.getName();
                        List<String> trace = new ArrayList<>();
                        trace.add(otherMethod.getSignature());
                        trace.add("key field: " + field.toString());
                        trace.add(crashMethod.getSignature());
                        trace.add("key field: " + field.toString());
                        crashInfo.addBuggyCandidates(candi, score, filterExtendCG,"modify_fields_in_app_crash_method", trace);
                    }
                }
            }
        }
    }

    /**
     * ParameterOnly type
     * @param crashInfo
     * @param filterExtendCG
     *
     */
    private void withFieldHandler(int score, CrashInfo crashInfo, boolean filterExtendCG) {
        System.out.println("withFieldHandler...");
        ExceptionInfo exceptionInfo = crashInfo.getExceptionInfo();
        for(RelatedMethod method: exceptionInfo.getRelatedMethodsInSameClass(false)){
            getBuggyFromRelatedMethods(crashInfo, method, score, filterExtendCG);
        }
        if(!crashInfo.findCandidateInTrace) {
            //add diff class results, when the same class results returns nothing
            for (RelatedMethod method : exceptionInfo.getRelatedMethodsInDiffClass(false)) {
                getBuggyFromRelatedMethods(crashInfo, method, score-ConstantUtils.DIFFCLASS, filterExtendCG);
            }
        }
    }
    //according to the parameter send into framework API
    private void withCrashAPIParaHandler(int score, CrashInfo crashInfo, boolean filterExtendCG) {
        for(SootMethod crashMethod: getCrashSootMethod(crashInfo)){
            for(Unit unit : crashMethod.getActiveBody().getUnits()){
                InvokeExpr invoke = SootUtils.getInvokeExp(unit);
                if(invoke==null) continue;
                int index = crashInfo.getCrashAPI().lastIndexOf(".");
                String e = crashInfo.getCrashAPI().substring(index+1, crashInfo.getCrashAPI().length()-1);
                if(invoke.toString().contains(e)){
                    for(Value param: invoke.getArgs()){
                        findMethodsWhoModifyParam(score, crashInfo, filterExtendCG, crashMethod, unit, param);
                    }
                }
            }
        }
    }

    private void findMethodsWhoModifyParam(int score, CrashInfo crashInfo, boolean filterExtendCG, SootMethod crashMethod, Unit unit, Value value) {
        List<Unit> allPreds = new ArrayList<>();
        SootUtils.getAllPredsofUnit(crashMethod, unit,allPreds);
        HashSet<SootField> fields = new HashSet<>();
        extendRelatedValues(crashMethod, allPreds, unit, value, new ArrayList<>(), fields);

        List<SootField> keyFields = getKeySootFields(crashMethod, crashInfo);
        for (SootField field : fields) {
            if(keyFields!=null && !keyFields.contains(field))
                continue;
            for (SootMethod otherMethod : crashMethod.getDeclaringClass().getMethods()) {
                if (!otherMethod.hasActiveBody()) continue;
                if (SootUtils.fieldIsChanged(field, otherMethod)) {
                    String candi = otherMethod.getDeclaringClass().getName() + "." + otherMethod.getName();
                    List<String> trace = new ArrayList<>();
                    trace.add(otherMethod.getSignature());
                    trace.add("key field: " + field.toString());
                    trace.add(crashMethod.getSignature());
                    trace.add("key field: " + field.toString());
                    crashInfo.addBuggyCandidates(candi, score, filterExtendCG,"modify_fields_send_to_framework", trace);
                }
            }
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
                //if the define unit is under a check
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
                            List<Value> rightValues = getFiledValueAssigns(baseF, field, allPreds);
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
                    }else if (rightOp instanceof JInstanceFieldRef) {
                        JInstanceFieldRef jInstanceFieldRef = (JInstanceFieldRef) rightOp;
                        extendRelatedValues(crashMethod, allPreds, defUnit, jInstanceFieldRef.getBase(), valueHistory, sootFields);
                    }
                } else {
                    System.out.println(defUnit.getClass().getName() + "::" + defUnit);
                }
            }
        }
        return "";
    }
    /**
     * parameterOnlyHandler
     * @param score
     * @param crashInfo
     * @param filterExtendCG
     */
    private void withParameterHandler(int score, CrashInfo crashInfo, boolean filterExtendCG) {
        int n = 0;
        if(crashInfo.getExceptionInfo()!=null && crashInfo.getExceptionInfo().getRelatedVarType()!=null) {
            n = getParameterTerminateMethod(score, crashInfo, filterExtendCG);
        }
        noParameterPassingMethodScore(score-n,crashInfo, filterExtendCG);
        int score2 = Math.max(crashInfo.maxScore-ConstantUtils.SMALLGAPSCORE, crashInfo.minScore - ConstantUtils.SMALLGAPSCORE);
        withCrashAPIParaHandler(score2, crashInfo, false);
    }

    private int getParameterTerminateMethod(int score, CrashInfo crashInfo, boolean filterExtendCG) {
        int count =0;
        boolean find = false;
        for(String candi : crashInfo.getCrashMethodList()){
            Set<SootMethod> methods = SootUtils.getSootMethodBySimpleName(candi);
            String signature = null;
            for(SootMethod sm: methods) {
                boolean isParaPassed = false;
                if (sm == null) break;
                signature = sm.getSignature();
                for (String paraTye : crashInfo.getExceptionInfo().getRelatedParamValuesInStr()) {
                    if (signature.contains(paraTye)) {
                        isParaPassed = true;
                    }
                }
                if (!isParaPassed) {
                    List<String> trace = new ArrayList<>();
                    if(signature != null){
                        trace.add(signature);
                    }else{
                        trace.add(candi);
                    }
                    crashInfo.addBuggyCandidates(candi,score, filterExtendCG,"best_match_crash_trace_ method", trace);
                    count++;
                    find = true;
                }
            }
            if(find) break;
        }
        return count;
    }


    private void noParameterPassingMethodScore(int initScore, CrashInfo crashInfo, boolean filterExtendCG) {
        System.out.println("noParameterPassingMethodScore...");
        int start = getTopNonLibMethod(crashInfo);
        int end = getBottomNonLibMethod(crashInfo);
        crashInfo.setEdges(new ArrayList<>());
        SootClass superCls = null;
        String sub ="";
        List<String> history = new ArrayList<>();
        for(int k=start; k<=end; k++){
            String candi = crashInfo.getTrace().get(k);
            if(!isLibraryMethod(candi)){
                List<String> trace = new ArrayList<>();
                trace.add(candi);
                crashInfo.addBuggyCandidates(candi, initScore--, filterExtendCG, "crash_trace_method", trace);
                Set<SootMethod> methods = SootUtils.getSootMethodBySimpleName(candi);
                for(SootMethod sm: methods) {
                    if (sm == null) continue;
                    sub = sm.getDeclaringClass().getName();
                    superCls = Scene.v().getActiveHierarchy().getSuperclassesOf(sm.getDeclaringClass()).get(0);
                    for (Iterator<Edge> it = Global.v().getAppModel().getCg().edgesOutOf(sm); it.hasNext(); ) {
                        Edge outEdge = it.next();
                        SootMethod callee = outEdge.getTgt().method();
                        if (callee.getSignature().contains(superCls.getName())) {
                            List<String> trace2 = new ArrayList<>();
                            trace2.add(0, sm.getSignature());
                            trace2.add(0, callee.getSignature());
                            getCalleeOfAndroidMethods(initScore, crashInfo, SootUtils.getMethodSimpleNameFromSignature(callee.getSignature()), sub, history, filterExtendCG, trace);
                        }
                    }
                }
            }else{
                if(superCls!= null && candi.contains(superCls.getName() )){
                    List<String> trace = new ArrayList<>();
                    trace.add(0, candi);
                    getCalleeOfAndroidMethods(initScore,crashInfo, candi , sub, history, filterExtendCG, trace);
                }
                initScore--;
            }
        }
    }



    private void getCalleeOfAndroidMethods(int initScore, CrashInfo crashInfo, String candi,
                                           String sub, List<String> history, boolean filterExtendCG, List<String> trace) {
        if(history.contains(candi)) return;
        history.add(candi);
        readAndroidCG();
        if(!androidCGMap.containsKey(candi)) return;
        String candiClassName = candi.substring(0,candi.lastIndexOf("."));
        for(String callee: androidCGMap.get(candi)){
            if(callee.contains(candiClassName)) {
                String realCallee = callee.replace(candiClassName, sub);
                Set <SootMethod > methods = SootUtils.getSootMethodBySimpleName(realCallee);
                for (SootMethod realSootMethod : methods) {
                    if (realSootMethod != null) {
                        List<String> newTrace = new ArrayList<>(trace);
                        newTrace.add(realSootMethod.getSignature());
                        addCalleesOfSourceOfEdge(initScore, crashInfo, realSootMethod, 0, filterExtendCG, newTrace);
                    }
                }
                List<String> newTrace = new ArrayList<>(trace);
                newTrace.add(callee);
                getCalleeOfAndroidMethods(initScore, crashInfo, callee, sub, history, filterExtendCG, newTrace);
            }
        }
    }


    /**
     * addCalleesOfSourceOfEdge
     * @param crashInfo
     * @param sootMethod
     * @param depth
     * @param filterExtendCG
     */
    private void addCalleesOfSourceOfEdge(int initScore, CrashInfo crashInfo, SootMethod sootMethod, int depth, boolean filterExtendCG, List<String> trace ) {
        String candi = sootMethod.getDeclaringClass().getName()+ "." + sootMethod.getName();
        if(isLibraryMethod(candi)) return;
        int score = initScore - getOrderInTrace(crashInfo, candi)  - depth;
//        System.out.println(candi +" " +initScore + " - 5*" +getOrderInTrace(crashInfo, candi) + " - 1*" +depth);
        crashInfo.addBuggyCandidates(candi, score, filterExtendCG,"extended_callee_from_crash_trace", trace);

        for (Iterator<Edge> it = Global.v().getAppModel().getCg().edgesOutOf(sootMethod); it.hasNext(); ) {
            Edge edge2 = it.next();
            if(!crashInfo.getEdges().contains(edge2) && !edge2.toString().contains("dummyMainMethod")){
                crashInfo.add2EdgeMap(depth,edge2);
                List<String> newTrace = new ArrayList<>(trace);
                newTrace.add(edge2.getTgt().method().getSignature());
                addCalleesOfSourceOfEdge(initScore, crashInfo, edge2.getTgt().method(), depth+1, filterExtendCG, newTrace);
            }
        }
    }
    /**
     * addCallersOfSourceOfEdge
     * @param edge
     * @param method
     * @param crashInfo
     * @param sootMethod
     * @param depth
     * @param filterExtendedCG
     */
    private void addCallersOfSourceOfEdge(int initScore, Edge edge, RelatedMethod method,
                                          CrashInfo crashInfo, SootMethod sootMethod, int depth, boolean filterExtendedCG, List<String> trace) {
        String candi = sootMethod.getDeclaringClass().getName()+ "." + sootMethod.getName();
        trace.add(0,sootMethod.getSignature());
        int score = initScore - getOrderInTrace(crashInfo, candi) - method.getDepth() - depth;
        if(crashInfo.getTrace().contains(candi)) score += ConstantUtils.METHODINTACE;
        crashInfo.addBuggyCandidates(candi, score, filterExtendedCG,"related_method_caller", trace);

        //if the buggy type is not passed by parameter, do not find its caller
        Set<Integer> paramIndexCaller = SootUtils.getIndexesFromMethod(edge, crashInfo.exceptionInfo.getRelatedValueIndex());
        if(paramIndexCaller.size() == 0) return;

        for (Iterator<Edge> it = Global.v().getAppModel().getCg().edgesInto(sootMethod); it.hasNext(); ) {
            Edge edge2 = it.next();
            if(edge2.toString().contains("dummyMainMethod")) continue;
            if( crashInfo.getEdges().contains(edge2) ) continue;
            crashInfo.add2EdgeMap(depth,edge2);
            List<String> newTrace = new ArrayList<>(trace);
            addCallersOfSourceOfEdge(initScore, edge2, method, crashInfo, edge2.getSrc().method(), depth+1, filterExtendedCG, newTrace);
        }
    }

    /**
     * getBuggyFromRelatedMethods
     * @param crashInfo
     * @param filterExtendedCG
     */
    private void getBuggyFromRelatedMethods(CrashInfo crashInfo, RelatedMethod relatedMethod, int initScore, boolean filterExtendedCG) {
        crashInfo.setEdges(new ArrayList<>());
        for (Iterator<Edge> it = Global.v().getAppModel().getCg().iterator(); it.hasNext(); ) {
            Edge edge = it.next();
            if(edge.getTgt().method().getSignature().equals(relatedMethod.getMethod())){
                SootMethod sourceMtd = edge.getSrc().method();
                if(isLibraryMethod(sourceMtd.getDeclaringClass().getName()))
                    continue;
                crashInfo.add2EdgeMap(0, edge);
                List<String> trace = new ArrayList<>();
                trace.addAll(relatedMethod.getTrace());
                addCallersOfSourceOfEdge(initScore, edge, relatedMethod, crashInfo, sourceMtd, 1, filterExtendedCG, trace);
            }
        }
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
    private boolean isLibraryMethod(String candi) {
        return candi.startsWith("android.")  || candi.startsWith("com.android.") || candi.startsWith("java.");
    }

    /**
     * get fields the related to the buggy variable passes to framework
     * @param crashMethod
     * @param crashInfo
     * @return
     */
    private List<SootField> getKeySootFields(SootMethod crashMethod, CrashInfo crashInfo) {
        if(crashMethod==null || crashInfo.getExceptionInfo()==null) return null;
        List<String> typeList = crashInfo.getExceptionInfo().getRelatedParamValuesInStr();
        if(typeList==null) return null;

        List<SootField> fields = new ArrayList<>();
        for(SootField field: crashMethod.getDeclaringClass().getFields()) {
            for (String bugParaType : typeList) {
                if (field.getType().toString().equals(bugParaType)) {
                    fields.add(field);
                }
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
     * @param crashInfo
     * @param filterExtendCG
     *
     */
    private void overrideMissingHandler(int score, CrashInfo crashInfo, boolean filterExtendCG) {
        System.out.println("overrideMissingHandler...");
        for(SootClass sc: Scene.v().getApplicationClasses()){
            if(!sc.hasSuperclass()) continue;
            if(sc.getSuperclass().getName().equals(crashInfo.getClassName())){
                for(SootClass sub: Scene.v().getActiveHierarchy().getSubclassesOfIncluding(sc)){
                    boolean hasMethod = false;
                    for(SootMethod sm : sub.getMethods()){
                        if(sm.getName().equals(crashInfo.getSubMethodName()) && sm.hasActiveBody()){
                            hasMethod = true;
                        }
                    }
                    if(!hasMethod) {
                        String candi = sub.getName() + "." + crashInfo.getSubMethodName();
                        int updateScore = score - getOrderInTrace(crashInfo, candi);

                        List<String> trace = new ArrayList<>();
                        trace.add(crashInfo.getMethodName());
                        crashInfo.addBuggyCandidates(candi, updateScore, filterExtendCG, "not_override_method", trace);
                    }
                }
            }
        }
    }

    /**
     * getOrderInTrace
     * @param crashInfo
     * @param candi
     * @return
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

    private void getExceptionOfCrashInfo() {
        for(CrashInfo crashInfo: crashInfoList) {
            if(crashInfo.getTrace().size()==0 ) continue;
            String str= crashInfo.getId()+"\t"+crashInfo.getSignaler()+"\t";
            System.out.println("Analysis crash "+ str);
            String[] versionTypes = new String[versions.length];
            String[] versionTypeCandis = new String[versions.length];
            String[] targetMethodNames = new String[versions.length];
            int i =0;
            for (String version : versions) {
                Pair pair = getExceptionWithGivenVersion(crashInfo, version, true);
                versionTypes[i]  = (String) pair.getO1();
                targetMethodNames[i]  = (String) pair.getO2();
                if(versionTypes[i].equals("unknown")){
                    Pair pair2 = getExceptionWithGivenVersion(crashInfo, version, false);
                    versionTypeCandis[i]  = (String) pair2.getO1();
                    targetMethodNames[i]  = (String) pair2.getO2();
                }
                i++;
            }

            int targetVerId = getTargetVersion(versionTypes);
            if(targetVerId==-1) {
                targetVerId = getTargetVersion(versionTypeCandis);
                System.out.println(PrintUtils.printArray(versionTypeCandis));
            }else{
                System.out.println(PrintUtils.printArray(versionTypes));
            }
            if(targetVerId==-1)
                targetVerId = 6;
            String targetVer = versions[targetVerId];
            String targetMethodName = targetMethodNames[targetVerId];
            MyConfig.getInstance().setExceptionFilePath("Files"+File.separator+"android"+targetVer+File.separator+"exceptionInfo"+File.separator);
            MyConfig.getInstance().setPermissionFilePath("Files"+File.separator+"android"+targetVer+File.separator+"Permission"+File.separator+"permission.txt");
            MyConfig.getInstance().setAndroidCGFilePath("Files"+File.separator+"android"+targetVer+File.separator+
                    "CallGraphInfo" + File.separator + "android"+targetVer + "_cg.txt");
            System.out.println("target is "+ targetVer);

            readExceptionSummary(crashInfo, targetMethodName);

        }

    }

    private int getTargetVersion(String[] versionType) {
        int paraAndField =0 , fieldOnly =0 ,parameterOnly =0 , overrideMissing = 0;
        for(String relatedVarType: versionType) {
            if (relatedVarType.equals("ParaAndField")) paraAndField++;
            if (relatedVarType.equals("FieldOnly")) fieldOnly++;
            if (relatedVarType.equals("ParameterOnly")) parameterOnly++;
            if (relatedVarType.equals("OverrideMissing")) overrideMissing++;
        }
        String choice = "";
        if(paraAndField + parameterOnly + fieldOnly + overrideMissing ==0)
            return -1;
        else if(paraAndField >= parameterOnly && paraAndField >= fieldOnly && paraAndField >= overrideMissing)
            choice =  "ParaAndField";
        else if(parameterOnly >= fieldOnly && parameterOnly >= paraAndField && parameterOnly >= overrideMissing)
            choice = "ParameterOnly";
        else if(fieldOnly >= parameterOnly && fieldOnly >= paraAndField && fieldOnly >= overrideMissing)
            choice = "FieldOnly";
        else if(overrideMissing >= parameterOnly && overrideMissing >= paraAndField && overrideMissing >= fieldOnly)
            choice = "OverrideMissing";

        for(int i = versionType.length-1; i>=0; i--) {
            if(versionType[i].equals(choice)){
                return i;
            }
        }
        return -1;
    }

    /**
     * getExceptionOfCrashInfo from exception.json
     */
    private Pair<String,String> getExceptionWithGivenVersion(CrashInfo crashInfo, String version, boolean classFilter) {
        MyConfig.getInstance().setExceptionFilePath("Files"+File.separator+"android"+version+File.separator+"exceptionInfo"+File.separator);
        String fn = MyConfig.getInstance().getExceptionFilePath()+"summary"+ File.separator+ "exception.json";
        String jsonString = FileUtils.readJsonFile(fn);
        JSONObject wrapperObject = (JSONObject) JSONObject.parse(jsonString);
        if(wrapperObject==null) return new Pair<>("noFile",crashInfo.getSignaler());
        JSONArray methods = wrapperObject.getJSONArray("exceptions");//构建JSONArray数组
        for (int i = 0 ; i < methods.size();i++){
            JSONObject jsonObject = (JSONObject)methods.get(i);
            ExceptionInfo exceptionInfo = new ExceptionInfo();
            exceptionInfo.setSootMethodName(jsonObject.getString("method"));
            if(classFilter== false || crashInfo.getSignaler().equals(exceptionInfo.getSootMethodName())){
                exceptionInfo.setExceptionMsg(jsonObject.getString("message"));
                if (exceptionInfo.getExceptionMsg() == null) continue;
                Pattern p = Pattern.compile(exceptionInfo.getExceptionMsg());
                Matcher m = p.matcher(crashInfo.getMsg());
                if (m.matches()) {
                    String str =exceptionInfo.getExceptionMsg();
                    str = str.replace("[\\s\\S]*","");
                    str = str.replace("\\Q","");
                    str = str.replace("\\E","");
                    if(str.length()<3)
                        continue;
                    if(exceptionInfo!=null && jsonObject.getString("relatedVarType")!=null) {
                        return new Pair<>(jsonObject.getString("relatedVarType"), exceptionInfo.getSootMethodName());
                    }
                }
            }
        }
        return new Pair<>("unknown",crashInfo.getSignaler());
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
        System.out.println("readExceptionSummary::"+fn);
        String jsonString = FileUtils.readJsonFile(fn);
        JSONObject wrapperObject = (JSONObject) JSONObject.parse(jsonString);
        if(wrapperObject==null) {
            System.out.println( crashInfo.getClassName()+" is not modeled.");
            return;
        }
        JSONArray methods = wrapperObject.getJSONArray("exceptions");//构建JSONArray数组
        for (int i = 0 ; i < methods.size();i++){
            JSONObject jsonObject = (JSONObject)methods.get(i);
            ExceptionInfo exceptionInfo = new ExceptionInfo();
            exceptionInfo.setSootMethodName(jsonObject.getString("method"));
            exceptionInfo.setExceptionMsg(jsonObject.getString("message"));

            if(exceptionInfo.getSootMethodName().equals(crashInfo.getMethodName())) {
                if (exceptionInfo.getExceptionMsg() == null) continue;
                Pattern p = Pattern.compile(exceptionInfo.getExceptionMsg());
                Matcher m = p.matcher(crashInfo.getMsg());
                if (m.matches()) {
                    crashInfo.setExceptionInfo(exceptionInfo);
                }else{
                    continue;
                }
            }
            exceptionInfo.setExceptionType(jsonObject.getString("type"));
            exceptionInfo.setModifier(jsonObject.getString("modifier"));
            exceptionInfo.setOsVersionRelated(jsonObject.getBoolean("osVersionRelated"));
            exceptionInfo.setResourceRelated(jsonObject.getBoolean("resourceRelated"));
            exceptionInfo.setAssessRelated(jsonObject.getBoolean("assessRelated"));
            exceptionInfo.setHardwareRelated(jsonObject.getBoolean("hardwareRelated"));
            exceptionInfo.setManifestRelated(jsonObject.getBoolean("manifestRelated"));
            exceptionInfo.setConditions(jsonObject.getString("conditions"));
            exceptionInfo.setRelatedFieldValuesInStr(jsonObject.getString("fieldValues"));
            exceptionInfo.setRelatedParamValuesInStr(jsonObject.getString("paramValues"));
            if(jsonObject.getString("relatedVarType")!=null)
                exceptionInfo.setRelatedVarType(RelatedVarType.valueOf(jsonObject.getString("relatedVarType")));
            JSONArray sameClsObjs = jsonObject.getJSONArray("relatedMethodSameClass");
            for (Iterator<Object> it = sameClsObjs.iterator(); it.hasNext(); ) {
                JSONObject sameClsObj = (JSONObject) it.next();
                RelatedMethod relatedMethod = new RelatedMethod();
                relatedMethod.setMethod(sameClsObj.getString("method"));

                relatedMethod.setDepth(sameClsObj.getInteger("depth"));
                relatedMethod.setSource(RelatedMethodSource.valueOf(sameClsObj.getString("source")));
                String trace = sameClsObj.getString("trace");
                String newTrace = "fw: " + trace.replace("[", "").
                        replace("]", "").replace("\"", "").replace(">,", ">, ");
                relatedMethod.addTrace(newTrace);
                exceptionInfo.addRelatedMethodsInSameClass(relatedMethod);
            }
            JSONArray diffClsObjs = jsonObject.getJSONArray("relatedMethodDiffClass");
            for (Iterator<Object> it = diffClsObjs.iterator(); it.hasNext(); ) {
                JSONObject diffClsObj = (JSONObject) it.next();
                RelatedMethod relatedMethod = new RelatedMethod();
                relatedMethod.setMethod(diffClsObj.getString("method"));
                relatedMethod.setDepth(diffClsObj.getInteger("depth"));
                relatedMethod.setSource(RelatedMethodSource.valueOf(diffClsObj.getString("source")));
                String trace = diffClsObj.getString("trace");
                String newTrace = "fw: " + trace.replace("[", "").
                        replace("]", "").replace("\"", "").replace(">,", ">, ");
                relatedMethod.addTrace(newTrace);
                exceptionInfo.addRelatedMethodsInDiffClass(relatedMethod);
            }
        }
    }

    /**
     * readCrashInfo from CrashInfoFile
     */
    private void readCrashInfo() {
        String fn = MyConfig.getInstance().getCrashInfoFilePath();
        System.out.println("readCrashInfo::"+fn);
        String jsonString = FileUtils.readJsonFile(fn);
        JSONArray jsonArray = JSONArray.parseArray(jsonString);
        for (int i = 0 ; i < jsonArray.size();i++){
            JSONObject jsonObject = (JSONObject)jsonArray.get(i);
            CrashInfo crashInfo = new CrashInfo();
            crashInfo.setIdentifier(jsonObject.getString("identifier"));
            crashInfo.setId(jsonObject.getString("id"));
            if(Global.v().getAppModel().getPackageName().length()==0 && Global.v().getAppModel().getAppName().contains(crashInfo.getIdentifier()))
                Global.v().getAppModel().setPackageName(crashInfo.getIdentifier());
            if(Global.v().getAppModel().getAppName().equals(crashInfo.getIdentifier()+"-"+crashInfo.getId())){
                crashInfoList.add(crashInfo);
                crashInfo.setReal(jsonObject.getString("real"));
                crashInfo.setException(jsonObject.getString("exception"));
                crashInfo.setTrace(jsonObject.getString("trace"));
                crashInfo.setBuggyApi(jsonObject.getString("buggyApi"));
                crashInfo.setMsg(jsonObject.getString("msg").trim());
                crashInfo.setRealCate(jsonObject.getString("realCate"));
                crashInfo.setCategory(jsonObject.getString("category"));
                if(jsonObject.getString("fileName")!=null)
                    crashInfo.setId(jsonObject.getString("fileName"));
                else
                    crashInfo.setId(crashInfo.getIdentifier()+"-"+ jsonObject.getString("id"));
                crashInfo.setReason(jsonObject.getString("reason"));
                crashInfo.setMethodName(crashInfo.getTrace().get(0));
            }
        }
    }
    private void readAndroidCG() {
        if(androidCGMap.size()>0) return;
        String fn = MyConfig.getInstance().getAndroidCGFilePath();
        System.out.println("readAndroidCG::"+fn);
        List<String> edges = FileUtils.getListFromFile(fn);
        for(String edge: edges){
            if(!edge.contains(" -> ")) continue;
            String src = SootUtils.getMethodSimpleNameFromSignature(edge.split(" -> ")[0]);
            String des = SootUtils.getMethodSimpleNameFromSignature(edge.split(" -> ")[1]);
            if(!androidCGMap.containsKey(src))
                androidCGMap.put(src,new HashSet<>());
            androidCGMap.get(src).add(des);
        }
    }




    /**
     * none code related labels
     * @param crashInfo
     */
    private void checkIsNoAppRelatedHandler(CrashInfo crashInfo) {
        ExceptionInfo info = crashInfo.getExceptionInfo();
        if(info!=null && info.isOsVersionRelated()){
            crashInfo.addNoneCodeLabel("OS Update");
        }
        if(info!=null && info.isAssessRelated()){
            crashInfo.addNoneCodeLabel("Asset");
        }
        if(info!=null && info.isManifestRelated()){
            crashInfo.addNoneCodeLabel("Manifest XML");
        } else if(containPermissionString(crashInfo.getMsg())){
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

    private boolean containPermissionString(String msg) {
        String fn = MyConfig.getInstance().getPermissionFilePath();
        System.out.println(fn);
        List<String> list = FileUtils.getListFromFile(fn);
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
            System.out.println("methodName::"+ crashInfo.getMethodName());
            System.out.println("msg::"+ crashInfo.getMsg());
            String buggyRanking = getRankingString(crashInfo, 999);
            List<Map.Entry<String, Integer>> treeMapList = CollectionUtils.getTreeMapEntriesSortedByValue(crashInfo.getBuggyCandidates());
            for (int i = 0; i < treeMapList.size(); i++) {
                String buggy = treeMapList.get(i).getKey();
                System.out.println((i+1)+" @ " +treeMapList.get(i).toString() );
                if(crashInfo.getReal().equals(buggy)){
                    buggyRanking = getRankingString(crashInfo,  i+1);
                }
            }
            System.out.println(buggyRanking);
            FileUtils.writeText2File(MyConfig.getInstance().getResultFolder() +"buggyRanking.txt", buggyRanking, true);
        }
    }

    private String getRankingString(CrashInfo crashInfo, int location) {
        int sizeAll = crashInfo.getBuggyCandidates().size();
        String size = "/\t"+ sizeAll;
        return  crashInfo.getRealCate()+"\t" + crashInfo.getId()+"\t" + crashInfo.getMethodName()+"\t"
                + relatedVarType +"\t" + location+"\t" +size+"\t" + PrintUtils.printList(crashInfo.getNoneCodeLabel())+"\n";
    }

}
