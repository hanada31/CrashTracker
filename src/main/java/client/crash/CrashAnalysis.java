package main.java.client.crash;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import main.java.Analyzer;
import main.java.Global;
import main.java.MyConfig;
import main.java.analyze.utils.CollectionUtils;
import main.java.analyze.utils.ConstantUtils;
import main.java.analyze.utils.SootUtils;
import main.java.analyze.utils.StringUtils;
import main.java.analyze.utils.output.FileUtils;
import main.java.client.exception.*;
import main.java.client.statistic.model.StatisticResult;
import soot.*;
import soot.jimple.InvokeExpr;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.scalar.UnitValueBoxPair;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author hanada
 * @Date 2022/3/22 20:20
 * @Version 1.0
 */
public class CrashAnalysis extends Analyzer {
    List<CrashInfo> crashInfoList;
    Map<String, Set<ExceptionInfo>> exceptionInfoMap;
    Set<String> loadedExceptionSummary;
    Map<String, Set<String>> androidCGMap;
    Map<String, Set<String>> message2Methods;

    public CrashAnalysis(StatisticResult result) {
        crashInfoList = Global.v().getAppModel().getCrashInfoList();
        exceptionInfoMap = new HashMap<>();
        loadedExceptionSummary = new HashSet<>();
        androidCGMap = new HashMap<>();
        message2Methods = new HashMap<>();
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
                    case OverrideMissing:
                        System.out.println("@@@ OverrideMissing");
                        overrideMissingHandler(ConstantUtils.INITSCORE,crashInfo, false); //OMA
//                        withParameterHandler(crashInfo.minScore-1, crashInfo, false); //TMA'
//                        appFieldCallHandler(crashInfo, crashInfo.minScore-1, true);
                        break;
                    case ParameterOnly:
                        System.out.println("@@@ ParameterOnly");
//                        if(crashMehthodHasParamter(crashInfo)){
                        withParameterHandler(ConstantUtils.INITSCORE, crashInfo, false); //TMA
                        appFieldCallHandler(crashInfo, crashInfo.minScore-1, true);
                        break;
                    case FieldOnly:
                        System.out.println("@@@ FieldOnly");
                        withFieldHandler(ConstantUtils.INITSCORE, crashInfo, false); //FCA
                        addCrashTraces(crashInfo.minScore-1,crashInfo,false);
//                        withParameterHandler(crashInfo.minScore-1, crashInfo, false);//TMA'
//                        appFieldCallHandler(crashInfo, crashInfo.minScore-1, true);
                        break;
                    case ParaAndField:
                        System.out.println("@@@ ParaAndField");
//                        if(crashMehthodHasParamter(crashInfo)){
                        withParameterHandler(ConstantUtils.INITSCORE, crashInfo, false); //TMA
                        appFieldCallHandler(crashInfo, crashInfo.minScore-1, true);
                        withFieldHandler(ConstantUtils.INITSCORE, crashInfo, true); //FCA

                        System.out.println(crashInfo.minScore);
                        break;
                }
            }else {
                // native and other no exception.
                //database related
                //non-database related
                System.out.println("@@@ other");
                withParameterHandler(ConstantUtils.NOEXCEPTIONSCORE, crashInfo, true);
            }
        }
    }

    private void addCrashTraces(int initscore, CrashInfo crashInfo, boolean filterExtendCG) {
        for(String candi: crashInfo.getCrashMethodList()){
            crashInfo.addBuggyCandidates(candi,initscore--,filterExtendCG);
        }
    }

    private boolean crashMehthodHasParamter(CrashInfo crashInfo) {
        for(SootMethod crashMethod: getCrashSootMethod(crashInfo)){
            for(Unit u : crashMethod.getActiveBody().getUnits()){
                InvokeExpr invoke = SootUtils.getInvokeExp(u);
                if(invoke==null) continue;
                int index = crashInfo.getCrashAPI().lastIndexOf(".");
                String e = crashInfo.getCrashAPI().substring(index+1, crashInfo.getCrashAPI().length()-1);
                if(invoke.toString().contains(e)){
                    if(invoke.getMethod().getParameterCount()>0)
                        return  true;
                }
            }
        }
        return  false;
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
            Set<SootMethod> methods = getSootMethodBySimpleName(candi);
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

    //add callback related edges
    private void getExtendedCallTraceWithLibrary(CrashInfo crashInfo) {
        int start = getTopNonLibMethod(crashInfo);
        int end = getBottomNonLibMethod(crashInfo);
        crashInfo.setEdges(new ArrayList<>());
        SootClass superCls = null;
        String sub ="";
        List<String> history = new ArrayList<>();
        for(int k=start; k<=end; k++){
            String candi = crashInfo.getTrace().get(k);
            if(!isLibraryMethod(candi)){
                Set<SootMethod> methods = getSootMethodBySimpleName(candi);
                for(SootMethod sm: methods) {
                    if (sm == null) continue;
                    addEntryMethods2ExtendedCG(sm, crashInfo);
                    String last = (k==start)?crashInfo.getCrashAPI(): crashInfo.getCrashMethodList().get(k-1);
                    addPredCallersOfMethodsInStack(last,sm,crashInfo);
                    sub = sm.getDeclaringClass().getName();
                    superCls = Scene.v().getActiveHierarchy().getSuperclassesOf(sm.getDeclaringClass()).get(0);
                    for (Iterator<Edge> it = Global.v().getAppModel().getCg().edgesOutOf(sm); it.hasNext(); ) {
                        SootMethod callee = it.next().getTgt().method();
                        if (callee.getSignature().contains(superCls.getName())) {
                            getCalleeOfAndroidMethods(crashInfo, getMethodSimpleNameFromSignature(callee.getSignature()), sub, history, 2);
                        }
                    }
                }
            }else{
                if(candi.contains(superCls.getName() )){
                    getCalleeOfAndroidMethods(crashInfo, candi , sub, history,2);
                }
            }
        }
    }



    private void getCalleeOfAndroidMethods(CrashInfo crashInfo, String candi, String sub, List<String> history, int depth) {
        if(history.contains(candi)) return;
        history.add(candi);
        readAndroidCG();
        if(!androidCGMap.containsKey(candi)) return;
        String candiClassName = candi.substring(0,candi.lastIndexOf("."));
        for(String callee: androidCGMap.get(candi)){
            if(callee.contains(candiClassName)) {
                String realCallee = callee.replace(candiClassName, sub);
                Set <SootMethod > methods = getSootMethodBySimpleName(realCallee);
                for (SootMethod realSootMethod : methods) {
                    if (realSootMethod != null) {
                        addAllCallee2ExtendedCG(realSootMethod, crashInfo, depth);
                    }
                }
                getCalleeOfAndroidMethods(crashInfo, callee, sub, history, depth+1);
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
    private void appFieldCallHandler(CrashInfo crashInfo, int score, boolean filterExtendCG) {
        System.out.println("getBuggyFromUserCode......");
        Set<SootMethod> crashMethods = getCrashSootMethod(crashInfo);
        for(SootMethod crashMethod:crashMethods) {
            List<SootField> keyFields = getKeySootFields(crashMethod, crashInfo);
            for (SootField field : keyFields) {
                for (SootMethod otherMethod : crashMethod.getDeclaringClass().getMethods()) {
                    if (!otherMethod.hasActiveBody()) continue;
                    if (SootUtils.fieldIsChanged(field, otherMethod)) {
                        crashInfo.addBuggyCandidates(otherMethod.getDeclaringClass().getName() + "." + otherMethod.getName(), score, filterExtendCG);
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
    }

    private int getParameterTerminateMethod(int score, CrashInfo crashInfo, boolean filterExtendCG) {
        int count =0;
        boolean find = false;
        for(String candi : crashInfo.getCrashMethodList()){
            Set<SootMethod> methods = getSootMethodBySimpleName(candi);
            for(SootMethod sm: methods) {
                boolean isParaPassed = false;
                if (sm == null) break;
                for (String paraTye : crashInfo.getExceptionInfo().getRelatedParamValuesInStr()) {
                    if (sm.getSignature().contains(paraTye)) {
                        isParaPassed = true;
                    }
                }
                if (!isParaPassed) {
                    crashInfo.addBuggyCandidates(candi,score, filterExtendCG);
                    count++;
                    find = true;
                }
            }
            if(find) break;
        }
        return count;
    }


    private void noParameterPassingMethodScore(int initScore, CrashInfo crashInfo, boolean filterExtendCG) {
        System.out.println("noExceptionHandler...");
        int start = getTopNonLibMethod(crashInfo);
        int end = getBottomNonLibMethod(crashInfo);
        crashInfo.setEdges(new ArrayList<>());
        SootClass superCls = null;
        String sub ="";
        List<String> history = new ArrayList<>();
        for(int k=start; k<=end; k++){
            String candi = crashInfo.getTrace().get(k);
            if(!isLibraryMethod(candi)){
                crashInfo.addBuggyCandidates(candi, initScore--, filterExtendCG);
                Set<SootMethod> methods = getSootMethodBySimpleName(candi);
                for(SootMethod sm: methods) {
                    if (sm == null) continue;
                    sub = sm.getDeclaringClass().getName();
                    superCls = Scene.v().getActiveHierarchy().getSuperclassesOf(sm.getDeclaringClass()).get(0);
                    for (Iterator<Edge> it = Global.v().getAppModel().getCg().edgesOutOf(sm); it.hasNext(); ) {
                        Edge outEdge = it.next();
                        SootMethod callee = outEdge.getTgt().method();
                        if (callee.getSignature().contains(superCls.getName())) {
                            getCalleeOfAndroidMethods(initScore, crashInfo, getMethodSimpleNameFromSignature(callee.getSignature()), sub, history, filterExtendCG);
                        }
                    }
                }
            }else{
                System.out.println(candi);
                System.out.println(superCls.getName());
                if(candi.contains(superCls.getName() )){
                    getCalleeOfAndroidMethods(initScore,crashInfo, candi , sub, history, filterExtendCG);
                }
                initScore--;
            }
        }
    }



    private void getCalleeOfAndroidMethods(int initScore, CrashInfo crashInfo, String candi, String sub, List<String> history, boolean filterExtendCG) {
        if(history.contains(candi)) return;
        history.add(candi);
        readAndroidCG();
        if(!androidCGMap.containsKey(candi)) return;
        String candiClassName = candi.substring(0,candi.lastIndexOf("."));
        for(String callee: androidCGMap.get(candi)){
            if(callee.contains(candiClassName)) {
                String realCallee = callee.replace(candiClassName, sub);
                Set <SootMethod > methods = getSootMethodBySimpleName(realCallee);
                for (SootMethod realSootMethod : methods) {
                    if (realSootMethod != null) {
                        addCalleesOfSourceOfEdge(initScore, crashInfo, realSootMethod, 0, filterExtendCG);
                    }
                }
                getCalleeOfAndroidMethods(initScore, crashInfo, callee, sub, history, filterExtendCG);
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
    private void addCalleesOfSourceOfEdge(int initScore, CrashInfo crashInfo, SootMethod sootMethod, int depth, boolean filterExtendCG) {
        String candi = sootMethod.getDeclaringClass().getName()+ "." + sootMethod.getName();
        if(isLibraryMethod(candi)) return;
        int score = initScore - getOrderInTrace(crashInfo, candi)  - depth;
//        System.out.println(candi +" " +initScore + " - 5*" +getOrderInTrace(crashInfo, candi) + " - 1*" +depth);
        crashInfo.addBuggyCandidates(candi, score, filterExtendCG);

        for (Iterator<Edge> it = Global.v().getAppModel().getCg().edgesOutOf(sootMethod); it.hasNext(); ) {
            Edge edge2 = it.next();
            if(!crashInfo.getEdges().contains(edge2) && !edge2.toString().contains("dummyMainMethod")){
                crashInfo.add2EdgeMap(depth,edge2);
                addCalleesOfSourceOfEdge(initScore, crashInfo, edge2.getTgt().method(), depth+1, filterExtendCG);
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
    private void addCallersOfSourceOfEdge(int initScore, Edge edge, RelatedMethod method, CrashInfo crashInfo, SootMethod sootMethod, int depth, boolean filterExtendedCG) {
        String candi = sootMethod.getDeclaringClass().getName()+ "." + sootMethod.getName();
        int score = initScore - getOrderInTrace(crashInfo, candi) - method.getDepth() - depth;
//        System.out.println(candi +" " +score +" " + " 5*" +getOrderInTrace(crashInfo, candi) + " 2*" +method.getDepth()+ " 1*" +depth);
        crashInfo.addBuggyCandidates(candi, score, filterExtendedCG);

        //if the buggy type is not passed by parameter, do not find its caller
        Set<Integer> paramIndexCaller = SootUtils.getIndexesFromMethod(edge, crashInfo.exceptionInfo.getRelatedValueIndex());
        if(paramIndexCaller.size() == 0) return;

        for (Iterator<Edge> it = Global.v().getAppModel().getCg().edgesInto(sootMethod); it.hasNext(); ) {
            Edge edge2 = it.next();
            if(edge2.toString().contains("dummyMainMethod")) continue;
            if( crashInfo.getEdges().contains(edge2) ) continue;
            crashInfo.add2EdgeMap(depth,edge2);
            addCallersOfSourceOfEdge(initScore, edge2, method, crashInfo, edge2.getSrc().method(), depth+1, filterExtendedCG);
        }
    }

    /**
     * getBuggyFromRelatedMethods
     * @param crashInfo
     * @param method
     * @param filterExtendedCG
     */
    private void getBuggyFromRelatedMethods(CrashInfo crashInfo, RelatedMethod method, int initScore, boolean filterExtendedCG) {
        crashInfo.setEdges(new ArrayList<>());
        for (Iterator<Edge> it = Global.v().getAppModel().getCg().iterator(); it.hasNext(); ) {
            Edge edge = it.next();
            if(edge.getTgt().method().getSignature().equals(method.getMethod())){
                SootMethod sourceMtd = edge.getSrc().method();
                if(isLibraryMethod(sourceMtd.getDeclaringClass().getName()))
                    continue;
                crashInfo.add2EdgeMap(0, edge);
                addCallersOfSourceOfEdge(initScore, edge, method, crashInfo, sourceMtd, 1, filterExtendedCG);
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
        List<SootField> fields = new ArrayList<>();
        if(crashMethod==null) return fields;
        for(SootField field: crashMethod.getDeclaringClass().getFields()) {
            for (String bugParaType : crashInfo.getExceptionInfo().getRelatedParamValuesInStr()) {
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
        return getSootMethodBySimpleName(crashInfo.getCrashMethod());
    }

    /**
     * getSootMethodBySimpleName
     * @param simpleName
     * @return
     */
    private Set<SootMethod> getSootMethodBySimpleName(String simpleName){
        Set<SootMethod> methods = new HashSet<SootMethod>();
        for(SootClass sc: Scene.v().getApplicationClasses()) {
            for(SootMethod method: sc.getMethods()){
                String name = method.getDeclaringClass().getName()+"."+ method.getName();
                if(name.equals(simpleName)){
                    methods.add(method);
                }
            }
        }
        return methods;
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
                        //TODO
                        crashInfo.addBuggyCandidates(candi, updateScore, filterExtendCG);
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


    /**
     * getExceptionOfCrashInfo from exception.json
     */
    private void getExceptionOfCrashInfo() {
        getMessage2ExceptionClass();
        for(CrashInfo crashInfo : crashInfoList){//
            if(crashInfo.getTrace().size()==0 ) continue;
            readExceptionSummary(crashInfo.getClassName());
            if(exceptionInfoMap.containsKey(crashInfo.getMethodName())) {
                for (ExceptionInfo exceptionInfo : exceptionInfoMap.get(crashInfo.getMethodName())) {
                    updateExceptionInCls2CrashInfo(crashInfo, exceptionInfo);
                }
            }
            if(crashInfo.getExceptionInfo() == null) {
                for (Map.Entry<String, Set<String>> entry : message2Methods.entrySet()) {
                    String message = entry.getKey();
                    Pattern p = Pattern.compile(StringUtils.filterRegex(message));
                    Matcher m = p.matcher(crashInfo.getMsg());
                    if (message.equals(crashInfo.getMsg()) || m.matches()) {
                        for (String method : entry.getValue()) {
                            String className =  method.split(" ")[0].replace("<","").replace(":","");
                            if(StringUtils.getPkgPrefix(crashInfo.getClassName(),2).equals(StringUtils.getPkgPrefix(className,2))) {
                                readExceptionSummary(className);
                            }
                        }
                    }
                }
                for (String key : exceptionInfoMap.keySet()) {
                    for (ExceptionInfo exceptionInfo : exceptionInfoMap.get(key)) {
                        updateExceptionInCls2CrashInfo(crashInfo, exceptionInfo);
                    }
                }
            }
        }
    }

    private void updateExceptionInCls2CrashInfo(CrashInfo crashInfo, ExceptionInfo exceptionInfo) {
;        if (exceptionInfo.getExceptionMsg() == null) return;
        Pattern p = Pattern.compile(StringUtils.filterRegex(exceptionInfo.getExceptionMsg()));
        Matcher m = p.matcher(crashInfo.getMsg());
        if (exceptionInfo.getExceptionMsg().equals(crashInfo.getMsg()) || m.matches()) {
            crashInfo.setExceptionInfo(exceptionInfo);
            return;
        }
    }

    private void getMessage2ExceptionClass() {
        String fn = MyConfig.getInstance().getExceptionFilePath()+"summary"+ File.separator+ "exception.json";
        String jsonString = FileUtils.readJsonFile(fn);
        JSONObject wrapperObject = (JSONObject) JSONObject.parse(jsonString);
        if(wrapperObject==null) return;
        JSONArray methods = wrapperObject.getJSONArray("exceptions");//构建JSONArray数组
        for (int i = 0 ; i < methods.size();i++) {
            JSONObject jsonObject = (JSONObject) methods.get(i);
            String key =jsonObject.getString("message");
            String value = jsonObject.getString("method");
//            if(jsonObject.getString("conditions")!=null && jsonObject.getString("relatedValues")==null){
//                System.out.println(value +"  " +key);
//            }
            if(key==null || value ==null) continue;
            if(!message2Methods.containsKey(key))
                message2Methods.put(key, new HashSet<>());
            message2Methods.get(key).add(value);
        }
    }

    /**
     * readExceptionSummary
     * @param sootclass
     */
    private void readExceptionSummary(String sootclass) {
        String fn = MyConfig.getInstance().getExceptionFilePath()+sootclass+".json";
        if(loadedExceptionSummary.contains(fn)) return;
        loadedExceptionSummary.add(fn);
        System.out.println("readExceptionSummary::"+fn);
        String jsonString = FileUtils.readJsonFile(fn);
        JSONObject wrapperObject = (JSONObject) JSONObject.parse(jsonString);
        if(wrapperObject==null) {
            System.err.println(sootclass+" is not modeled.");
            return;
        }
        JSONArray methods = wrapperObject.getJSONArray("exceptions");//构建JSONArray数组
        for (int i = 0 ; i < methods.size();i++){
            JSONObject jsonObject = (JSONObject)methods.get(i);
            ExceptionInfo exceptionInfo = new ExceptionInfo();
            exceptionInfo.setExceptionType(jsonObject.getString("type"));
            exceptionInfo.setExceptionMsg(jsonObject.getString("message"));
            exceptionInfo.setModifier(jsonObject.getString("modifier"));
            exceptionInfo.setOsVersionRelated(jsonObject.getBoolean("osVersionRelated"));
            exceptionInfo.setResourceRelated(jsonObject.getBoolean("resourceRelated"));
            exceptionInfo.setAssessRelated(jsonObject.getBoolean("assessRelated"));
            exceptionInfo.setHardwareRelated(jsonObject.getBoolean("hardwareRelated"));
            exceptionInfo.setManifestRelated(jsonObject.getBoolean("manifestRelated"));
            exceptionInfo.setConditions(jsonObject.getString("conditions"));
            exceptionInfo.setSootMethodName(jsonObject.getString("method"));
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
                exceptionInfo.addRelatedMethodsInSameClass(relatedMethod);
            }
            JSONArray diffClsObjs = jsonObject.getJSONArray("relatedMethodDiffClass");
            for (Iterator<Object> it = diffClsObjs.iterator(); it.hasNext(); ) {
                JSONObject sameClsObj = (JSONObject) it.next();
                RelatedMethod relatedMethod = new RelatedMethod();
                relatedMethod.setMethod(sameClsObj.getString("method"));
                relatedMethod.setDepth(sameClsObj.getInteger("depth"));
                relatedMethod.setSource(RelatedMethodSource.valueOf(sameClsObj.getString("source")));
                exceptionInfo.addRelatedMethodsInDiffClass(relatedMethod);
            }

            if(!exceptionInfoMap.containsKey(exceptionInfo.getSootMethodName()))
                exceptionInfoMap.put(exceptionInfo.getSootMethodName(), new HashSet<>());
            exceptionInfoMap.get(exceptionInfo.getSootMethodName()).add(exceptionInfo);

        }
    }

    /**
     * readCrashInfo
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
            if(Global.v().getAppModel().getPackageName().length()==0 && Global.v().getAppModel().getAppName().contains(crashInfo.getIdentifier()))
                Global.v().getAppModel().setPackageName(crashInfo.getIdentifier());
            if(crashInfo.getIdentifier().equals(Global.v().getAppModel().getPackageName())) {
                crashInfoList.add(crashInfo);
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
            String src = getMethodSimpleNameFromSignature(edge.split(" -> ")[0]);
            String des = getMethodSimpleNameFromSignature(edge.split(" -> ")[1]);
            if(!androidCGMap.containsKey(src))
                androidCGMap.put(src,new HashSet<>());
            androidCGMap.get(src).add(des);
        }
    }

    private String getMethodSimpleNameFromSignature(String str) {
        //<android.database.sqlite.SQLiteOpenHelper: android.database.sqlite.SQLiteDatabase getDatabaseLocked(boolean)>
        String ss[] = str.split(" ");
        String res1 = ss[0].replace("<","").replace(":","");
        String res2 = ss[2].split("\\(")[0];
        return res1 + "." + res2;
    }



    //TODO
    private void checkIsNoAppRelatedHandler(CrashInfo crashInfo) {
        int score = ConstantUtils.INITSCORE + ConstantUtils.OUTOFPKGSCORE;
        boolean filterExtendCG = false;
        ExceptionInfo info = crashInfo.getExceptionInfo();
        if(info!=null && info.isOsVersionRelated()){
            crashInfo.addBuggyCandidates("OS Update", score, filterExtendCG);
        }

        if(info!=null && info.isAssessRelated()){
            crashInfo.addBuggyCandidates("Asset", score, filterExtendCG);
        }

        if(info!=null && info.isManifestRelated()){
            crashInfo.addBuggyCandidates("Manifest XML", score, filterExtendCG);
        } else if(containPermissionString(crashInfo.getMsg())){
            crashInfo.addBuggyCandidates("Manifest XML", score, filterExtendCG);
        }

        if(info!=null && info.isHardwareRelated()) {
            crashInfo.addBuggyCandidates("Hardware", score, filterExtendCG);
        } else if(crashInfo.getCrashAPI() !=null && (crashInfo.getCrashAPI().contains("hardware") || crashInfo.getCrashAPI().contains("opengl")
                || crashInfo.getCrashAPI().contains("nfc") || crashInfo.getCrashAPI().contains("bluetooth") )) {
            crashInfo.addBuggyCandidates("Hardware", score, filterExtendCG);
        }

        if(info!=null && info.isResourceRelated()){
            crashInfo.addBuggyCandidates("Resource XML", score, filterExtendCG);
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
        return  crashInfo.getRealCate() + "\t" + crashInfo.getId() +"\t" + crashInfo.getMethodName() + "\t" + crashInfo.getReal()  +"\t"+ location + "\t" +size + "\n";
    }

}
