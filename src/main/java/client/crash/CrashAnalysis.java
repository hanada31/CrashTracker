package main.java.client.crash;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import main.java.Analyzer;
import main.java.Global;
import main.java.MyConfig;
import main.java.analyze.utils.output.FileUtils;
import main.java.client.exception.*;
import main.java.client.statistic.model.StatisticResult;
import soot.*;
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
        System.out.println("readCrashInfo Finish...");
        getExceptionOfCrashInfo();
        System.out.println("getExceptionOfCrashInfo Finish...");
        getCandidateBuggyMethods();
        System.out.println("getCandidateBuggyMethods Finish...");
        printCrash2Edges();
    }

    private void printCrash2Edges() {
        for(CrashInfo crashInfo : crashInfoList){
//            if(crashInfo.getExceptionInfo()==null) continue;
            System.out.println("methodName::"+ crashInfo.getMethodName());
            System.out.println("msg::"+ crashInfo.getMsg());
            int i = 0;

            String buggyRanking = getRankingString(crashInfo, "XXX", crashInfo.getReal(), -999);
            for(String buggy : crashInfo.getBuggyMethods()) {
                System.out.println("Buggy "+ (++i)+"::"+buggy);
                if(crashInfo.getReal().equals(buggy)){
                    buggyRanking = getRankingString(crashInfo, "Buggy", buggy, i);
                }
            }

            for(String buggy : crashInfo.getBuggyMethods_weak()) {
                System.out.println("Buggy weak "+ (++i)+"::"+buggy);
                if(crashInfo.getReal().equals(buggy)){
                    buggyRanking = getRankingString(crashInfo, "WeakBuggy", buggy, i);
                }
            }
            FileUtils.writeText2File(MyConfig.getInstance().getResultFolder() +"buggyRanking.txt", buggyRanking, true);
        }
    }

    private String getRankingString(CrashInfo crashInfo, String tag, String method, int location) {
        int size1 = crashInfo.getBuggyMethods().size();
        int size2 = size1 + crashInfo.getBuggyMethods_weak().size();
        String size = "/ "+ size1+ " / "+size2;
        return crashInfo.getRealCate() + "\t" + crashInfo.getId() +"\t" + tag + "\t" + method  +"\t@\t"+ location + "\t" +size + "\n" ;
    }

    /**
     * find candidates according to the type of corresponding exception
     */
    private void getCandidateBuggyMethods() {
        List<String> buggyCandidates = new ArrayList<>();
        for(CrashInfo crashInfo : crashInfoList){
            ExceptionInfo exceptionInfo = crashInfo.getExceptionInfo();
            if(exceptionInfo != null && exceptionInfo.getRelatedVarType()!=null) {
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
            }else{
                sortBuggyMethodsByTrace(crashInfo, buggyCandidates, false);
            }
        }
    }

    private void addCrashMethodsAsBuggy(CrashInfo crashInfo, List<String> buggyCandidates) {
        for(String traceMethod: crashInfo.getCrashMethodList()){
            if(!buggyCandidates.contains(traceMethod))
                buggyCandidates.add(traceMethod);
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
                SootMethod method = edge.getSrc().method();
                buggyCandidates.add(method.getDeclaringClass().getName() + "." + method.getName());
            }
        }
        getBuggyFromUserCode(crashInfo, buggyCandidates);
    }

    private void getBuggyFromUserCode(CrashInfo crashInfo, List<String> buggyCandidates) {
        SootMethod crashMethod = getCrashSootMethod(crashInfo);
        List<SootField> keyFields = getKeySootFields(crashMethod, crashInfo);
        List<SootMethod> fieldRelatedMethods = getMethodsUsedField(crashMethod, keyFields,buggyCandidates);
        for(SootMethod method :fieldRelatedMethods)
            buggyCandidates.add(method.getDeclaringClass().getName()+"."+method.getName());

    }


    private List<SootMethod>  getMethodsUsedField(SootMethod sootMethod, List<SootField> keyFields, List<String> buggyCandidates) {
        List<SootMethod> fieldRelatedMethods = new ArrayList<>();
        if(sootMethod==null) return fieldRelatedMethods;
        for(SootField field: keyFields) {
            for (SootMethod otherMethod : sootMethod.getDeclaringClass().getMethods()) {
                if (!otherMethod.hasActiveBody()) continue;
                if (ExceptionAnalyzer.fieldIsChanged(field, otherMethod)) {
                    fieldRelatedMethods.add(otherMethod);
                }
            }
        }
        return fieldRelatedMethods;
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
    private SootMethod getCrashSootMethod(CrashInfo crashInfo) {
        for(SootClass sc: Scene.v().getApplicationClasses()) {
            for(SootMethod method: sc.getMethods()){
                String name = method.getDeclaringClass().getName()+"."+ method.getName();
                if(name.equals(crashInfo.getCrashMethod())){
                    return method;
                }
            }
        }
        return null;
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
        addCrashMethodsAsBuggy(crashInfo,buggyCandidates);
        for(int i = 0; i< crashInfo.getTrace().size(); i++) {
            if (requireEqual) {
                String tag = crashInfo.getTrace().get(i);
                for (String candi : buggyCandidates) {
                    if (candi.equals(tag)) {
                        crashInfo.addBuggyMethods(candi);
                    }
                }
            }
        }
        for(int i = 0; i< crashInfo.getClassesInTrace().size(); i++) {
            String tag = crashInfo.getClassesInTrace().get(i);
            for (String candi : buggyCandidates) {
                if (candi.contains(tag)) {
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
        for (Iterator<Edge> it = Global.v().getAppModel().getCg().iterator(); it.hasNext(); ) {
            Edge edge = it.next();
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
        System.out.println(fn);
        String jsonString = FileUtils.readJsonFile(fn);
        JSONObject wrapperObject = (JSONObject) JSONObject.parse(jsonString);
        if(wrapperObject==null) {
            System.err.println(sootclass+" is not modeled.");
            return;
        }
        JSONArray methods = wrapperObject.getJSONArray("methodMap");//构建JSONArray数组
        for (int i = 0 ; i < methods.size();i++){
            JSONObject jsonObject = (JSONObject)methods.get(i);
            ExceptionInfo exceptionInfo = new ExceptionInfo();
            exceptionInfo.setExceptionType(jsonObject.getString("type"));
            exceptionInfo.setExceptionMsg(jsonObject.getString("message"));
            exceptionInfo.setModifier(jsonObject.getString("modifier"));
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

//            JSONArray diffClsObjs = jsonObject.getJSONArray("relatedMethodDiffClass");
//            for (Iterator<Object> it = diffClsObjs.iterator(); it.hasNext(); ) {
//                JSONObject sameClsObj = (JSONObject) it.next();
//                RelatedMethod relatedMethod = new RelatedMethod();
//                relatedMethod.setMethod(sameClsObj.getString("method"));
//                relatedMethod.setDepth(sameClsObj.getInteger("depth"));
//                relatedMethod.setSource(RelatedMethodSource.valueOf(sameClsObj.getString("source")));
//                exceptionInfo.addRelatedMethodsInDiffClass(relatedMethod);
//            }

            if(!exceptionInfoMap.containsKey(exceptionInfo.getSootMethodName()))
                exceptionInfoMap.put(exceptionInfo.getSootMethodName(), new HashSet<>());
            exceptionInfoMap.get(exceptionInfo.getSootMethodName()).add(exceptionInfo);
        }
    }

    private void readCrashInfo() {
        String fn = MyConfig.getInstance().getCrashInfoFilePath();
        System.out.println(fn);
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
            if(crashInfo.getIdentifier().equals(Global.v().getAppModel().getPackageName())) {
                crashInfoList.add(crashInfo);
            }else{
                if(Global.v().getAppModel().getPackageName().length()==0 && Global.v().getAppModel().getAppName().contains(crashInfo.getIdentifier()))
                    crashInfoList.add(crashInfo);
            }
        }
//        System.out.println(PrintUtils.printList(crashInfoList));
    }
}
