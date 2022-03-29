package main.java.client.crash;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import main.java.Analyzer;
import main.java.Global;
import main.java.MyConfig;
import main.java.analyze.utils.output.FileUtils;
import main.java.client.exception.ExceptionInfo;
import main.java.client.exception.RelatedMethod;
import main.java.client.exception.RelatedMethodSource;
import main.java.client.exception.RelatedVarType;
import main.java.client.statistic.model.StatisticResult;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.Edge;

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

    public CrashAnalysis(StatisticResult result) {
        crashInfoList = Global.v().getAppModel().getCrashInfoList();
        exceptionInfoMap = new HashMap<>();
        loadedExceptionSummary = new HashSet<>();
    }

    @Override
    public void analyze() {
        readCrashInfo();
        getExceptionOfCrashInfo();
        getCandidateBuggyMethods();
        printCrash2Edges();
    }

    private void printCrash2Edges() {
        for(CrashInfo crashInfo : crashInfoList){
            if(crashInfo.getExceptionInfo()==null) continue;
            System.out.println("methodName::"+ crashInfo.getMethodName());
            System.out.println("msg::"+ crashInfo.getMsg());
            int i = 0;
            for(String buggy : crashInfo.getBuggyMethods()) {
                System.out.println("Buggy "+ (++i)+"::"+buggy);
            }

            for(String buggy : crashInfo.getBuggyMethods_weak()) {
                System.out.println("Buggy weak "+ (++i)+"::"+buggy);
            }
        }
    }

    /**
     * find candidates according to the type of corresponding exception
     */
    private void getCandidateBuggyMethods() {
        List<String> buggyCandidates = new ArrayList<>();
        for(CrashInfo crashInfo : crashInfoList){
            ExceptionInfo exceptionInfo = crashInfo.getExceptionInfo();
            if(exceptionInfo == null) continue;
            switch (exceptionInfo.getRelatedVarType()) {
                case OverrideMissing:
                    overrideMissingHandler(crashInfo, buggyCandidates);
                    sortBuggyMethodsByTrace(crashInfo, buggyCandidates, false);
                    break;
                case ParameterOnly:
                    parameterOnlyHandler(crashInfo, buggyCandidates);
                    sortBuggyMethodsByTrace(crashInfo, buggyCandidates, true);
                    break;
                case FieldOnly:
                case ParaAndField:
                    parameterOnlyHandler(crashInfo, buggyCandidates);
                    sortBuggyMethodsByTrace(crashInfo, buggyCandidates, false);
                    break;
            }
        }
    }

    /**
     * FieldOnly type
     * @param crashInfo
     * @param buggyCandidates
     */
    private void fieldOnlyHandler(CrashInfo crashInfo, List<String> buggyCandidates) {
    }

    /**
     * ParameterOnly type
     * @param crashInfo
     * @param buggyCandidates
     */
    private void parameterOnlyHandler(CrashInfo crashInfo, List<String> buggyCandidates) {
        ExceptionInfo exceptionInfo = crashInfo.getExceptionInfo();
        for(RelatedMethod method: exceptionInfo.getRelatedMethodsInSameClass()){
            getBuggyFromRelatedMethods(crashInfo, method);
        }
        for(RelatedMethod method: exceptionInfo.getRelatedMethodsInDiffClass()){
            getBuggyFromRelatedMethods(crashInfo, method);
        }
        for(Integer depth: crashInfo.getEdgeMap().keySet()){
            for(Edge edge: crashInfo.getEdgeMap().get(depth)) {
                buggyCandidates.add(edge.getSrc().method().getDeclaringClass().getName() + "." + edge.getSrc().method().getName());
            }
        }
    }

    /**
     * OverrideMissing type
     * @param crashInfo
     * @param buggyCandidates
     */
    private void overrideMissingHandler(CrashInfo crashInfo, List<String> buggyCandidates) {
        for(SootClass sc: Scene.v().getApplicationClasses()){
            if(!sc.hasSuperclass()) continue;
            if(sc.getSuperclass().getName().equals(crashInfo.getClssName())){
                for(SootClass sub: Scene.v().getActiveHierarchy().getSubclassesOfIncluding(sc)){
                    boolean hasMethod = false;
                    for(SootMethod sm : sub.getMethods()){
                        if(sm.getName().equals(crashInfo.getSubMethodName())){
                            hasMethod = true;
                        }
                    }
                    if(!hasMethod)
                        buggyCandidates.add(sub.getName() + "." + crashInfo.getSubMethodName());
                }
            }
        }
    }

    /**
     * sort buggy methods by the order in trace
     * @param crashInfo
     * @param buggyCandidates
     */
    private void sortBuggyMethodsByTrace(CrashInfo crashInfo, List<String> buggyCandidates, boolean requireEqual) {
        for(String methodOnTrace: crashInfo.getClassesInTrace()) {
            for(String candi: buggyCandidates) {
                if (requireEqual && candi.equals(methodOnTrace)) {
                    crashInfo.addBuggyMethods(candi);
                }
                if (!requireEqual && candi.contains(methodOnTrace)) {
                    crashInfo.addBuggyMethods(candi);
                }
            }
        }
        for(String candi: buggyCandidates) {
            if (!crashInfo.getBuggyMethods().contains(candi)){
                crashInfo.addBuggyMethods_weak(candi);
            }
        }
    }

    private void getBuggyFromRelatedMethods(CrashInfo crashInfo, RelatedMethod method) {
        if(method.getMethod().contains("android.content.ContextWrapper: void unbindService"))
            System.out.println();
        for (Iterator<Edge> it = Global.v().getAppModel().getCg().iterator(); it.hasNext(); ) {
            Edge edge = it.next();
//            if(edge.getTgt().method().getSignature().contains("android.content.ContextWrapper: void unbindService") )
//                System.err.println(edge.getSrc().method());
            if(edge.getTgt().method().getSignature().equals(method.getMethod())){
                crashInfo.add2EdgeMap(0, edge);
                addCallersOfSourceOfEdge(crashInfo, edge.getSrc().method(), 1);
            }
        }
    }


    private void addCallersOfSourceOfEdge(CrashInfo crashInfo, SootMethod sootMethod, int depth) {
        for (Iterator<Edge> it = Global.v().getAppModel().getCg().edgesInto(sootMethod); it.hasNext(); ) {
            Edge edge = it.next();
            if(!crashInfo.getEdges().contains(edge) && !edge.toString().contains("dummyMainMethod")){
                crashInfo.add2EdgeMap(depth,edge);
                addCallersOfSourceOfEdge(crashInfo, edge.getSrc().method(), depth+1);
            }
        }
    }

    private void getExceptionOfCrashInfo() {
        for(CrashInfo crashInfo : crashInfoList){
            if(crashInfo.getTrace().size()==0 ) continue;
            readExceptionSummary(crashInfo.getClssName());
            if(!exceptionInfoMap.containsKey(crashInfo.getMethodName())) continue;
            for(ExceptionInfo exceptionInfo : exceptionInfoMap.get(crashInfo.getMethodName())){
                if(exceptionInfo.getExceptionMsg() == null) continue;
                Pattern p = Pattern.compile(exceptionInfo.getExceptionMsg().replace("()",""));
                Matcher m = p.matcher(crashInfo.getMsg().replace("()",""));
//                System.out.println("exception::" + p);
//                System.out.println("crash::" +crashInfo.getMsg());
//                System.out.println("matches::" +m.matches());
                if(exceptionInfo.getExceptionMsg().equals(crashInfo.getMsg()) || m.matches()){
                    crashInfo.setExceptionInfo(exceptionInfo);
                }

            }
        }
    }

    private void readExceptionSummary(String sootclass) {
        String fn = MyConfig.getInstance().getExceptionSummaryFilePath()+sootclass+".json";
        if(loadedExceptionSummary.contains(fn)) return;
        loadedExceptionSummary.add(fn);

        String jsonString = FileUtils.readJsonFile(fn);
        JSONObject wrapperObject = (JSONObject) JSONObject.parse(jsonString);
        JSONArray methods = wrapperObject.getJSONArray("methodMap");//构建JSONArray数组
        for (int i = 0 ; i < methods.size();i++){
            JSONObject jsonObject = (JSONObject)methods.get(i);
            ExceptionInfo exceptionInfo = new ExceptionInfo();
            exceptionInfo.setExceptionType(jsonObject.getString("type"));
            exceptionInfo.setExceptionMsg(jsonObject.getString("message"));
            exceptionInfo.setModifier(jsonObject.getString("modifier"));
            exceptionInfo.setConditions(jsonObject.getString("conditions"));
            exceptionInfo.setSootMethodName(jsonObject.getString("method"));
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

    private void readCrashInfo() {
        String fn = MyConfig.getInstance().getCrashInfoFilePath();
        String jsonString = FileUtils.readJsonFile(fn);
        JSONArray jsonArray = JSONArray.parseArray(jsonString);
        for (int i = 0 ; i < jsonArray.size();i++){
            JSONObject jsonObject = (JSONObject)jsonArray.get(i);
            CrashInfo crashInfo = new CrashInfo();
            crashInfo.setReal(jsonObject.getString("real"));
            crashInfo.setException(jsonObject.getString("exception"));
            crashInfo.setTrace(jsonObject.getString("trace"));
            crashInfo.setBuggyApi(jsonObject.getString("buggyApi"));
            crashInfo.setMsg(jsonObject.getString("msg").trim());
            crashInfo.setRealCate(jsonObject.getString("realCate"));
            crashInfo.setCategory(jsonObject.getString("category"));
            crashInfo.setIdentifier(jsonObject.getString("identifier"));
            crashInfo.setReason(jsonObject.getString("reason"));
            crashInfo.setMethodName(crashInfo.getTrace().get(0));
            if(!crashInfo.getIdentifier().equals(Global.v().getAppModel().getPackageName())) continue;
            crashInfoList.add(crashInfo);
        }
//        System.out.println(PrintUtils.printList(crashInfoList));
    }
}
