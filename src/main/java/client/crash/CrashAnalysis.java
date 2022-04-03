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
            System.out.println("methodName::"+ crashInfo.getMethodName());
            System.out.println("msg::"+ crashInfo.getMsg());
            String buggyRanking = getRankingString(crashInfo, "XXX", crashInfo.getReal(), -999);
            List<Map.Entry<String, Integer>> treeMapList = CollectionUtils.getTreeMapEntriesSortedByValue(crashInfo.getBuggyCandidates());
            for (int i = 0; i < treeMapList.size(); i++) {
                String buggy = treeMapList.get(i).getKey();
                System.out.println((i+1)+" @ " +treeMapList.get(i).toString() );
                if(crashInfo.getReal().equals(buggy)){
                    buggyRanking = getRankingString(crashInfo, "Buggy", buggy, i+1);
                }
            }
            System.out.println(buggyRanking);
            FileUtils.writeText2File(MyConfig.getInstance().getResultFolder() +"buggyRanking.txt", buggyRanking, true);
        }
    }

    private String getRankingString(CrashInfo crashInfo, String tag, String method, int location) {
        int sizeAll = crashInfo.getBuggyCandidates().size();
        String size = "/ "+ sizeAll;
        return  crashInfo.getRealCate() + "\t" + crashInfo.getId() +"\t" + tag + "\t" + method  +"\t@\t"+ location + "\t" +size + "\n";
    }

    /**
     * find candidates according to the type of corresponding exception
     */
    private void getCandidateBuggyMethods() {
        for(CrashInfo crashInfo : crashInfoList){
            ExceptionInfo exceptionInfo = crashInfo.getExceptionInfo();
            if(exceptionInfo != null && exceptionInfo.getRelatedVarType()!=null) {
                switch (exceptionInfo.getRelatedVarType()) {
                    case OverrideMissing:
                        overrideMissingHandler(crashInfo);
                        noExceptionHandler(ConstantUtils.NOEXCEPTIONSCORE, crashInfo);
                        break;
                    case ParameterOnly:
                        withParameterHandler(crashInfo);
                        getBuggyFromUserCode(crashInfo);
                        break;
                    case FieldOnly:
                        withFieldHandler(crashInfo);
                        getBuggyFromUserCode(crashInfo);
                        noExceptionHandler(ConstantUtils.NOEXCEPTIONSCORE, crashInfo);
                        break;
                    case ParaAndField:
                        withFieldHandler(crashInfo);
                        withParameterHandler(crashInfo);
                        getBuggyFromUserCode(crashInfo);
                        break;

                }
            }else {
                noExceptionHandler(ConstantUtils.NOEXCEPTIONSCORE, crashInfo);
            }
        }
    }

    /**
     * parameterOnlyHandler
     * @param crashInfo
     */
    private void withParameterHandler(CrashInfo crashInfo) {
        for(String candi : crashInfo.getCrashMethodList()){
            boolean isParaPassed = false;
            SootMethod sm = getSootMethodBySimpleName(candi);
            if(sm == null) continue;
            for(String paraTye: crashInfo.getExceptionInfo().getRelatedParamValuesInStr()){
                if(sm.getSignature().contains(paraTye)){
                    isParaPassed = true;
                }
            }
            if(!isParaPassed) {
                crashInfo.addBuggyCandidates(candi, ConstantUtils.INITSCORE);
                break;
            }
        }
        //TODO delete or ??
        int score = ConstantUtils.INITSCORE-20;
        for(String candi : crashInfo.getCrashMethodList()){
            if(!crashInfo.buggyCandidates.containsKey(candi))
                crashInfo.addBuggyCandidates(candi, score--);
        }
    }

    /**
     * ParameterOnly type
     * @param crashInfo
     *
     */
    private void withFieldHandler(CrashInfo crashInfo) {
        System.out.println("parameterOrFieldHandler...");
        ExceptionInfo exceptionInfo = crashInfo.getExceptionInfo();
        for(RelatedMethod method: exceptionInfo.getRelatedMethodsInSameClass(false)){
            getBuggyFromRelatedMethods(crashInfo, method);
        }
        if(crashInfo.getEdgeMap().size()==0) {
            //add diff class results, when the same class results returns nothing
            for (RelatedMethod method : exceptionInfo.getRelatedMethodsInDiffClass(false)) {
                getBuggyFromRelatedMethods(crashInfo, method);
            }
        }
    }
    private void noExceptionHandler(int initScore, CrashInfo crashInfo) {
        System.out.println("noExceptionHandler...");
        for(String candi : crashInfo.getCrashMethodList()){
            crashInfo.addBuggyCandidates(candi, initScore--);
        }
//            boolean isParaPassed = false;
//            SootMethod sm = getSootMethodBySimpleName(candi);
//            for(String paraTye: crashInfo.getExceptionInfo().getRelatedParamValuesInStr()){
//                if(sm.getSignature().contains(paraTye)){
//                    isParaPassed = true;
//                }
//            }
//            if(!isParaPassed)
//                crashInfo.addBuggyCandidates(candi, initScore--);
//        }
    }


    /**
     * getBuggyFromRelatedMethods
     * @param crashInfo
     * @param method
     */
    private void getBuggyFromRelatedMethods(CrashInfo crashInfo, RelatedMethod method ) {
        for (Iterator<Edge> it = Global.v().getAppModel().getCg().iterator(); it.hasNext(); ) {
            Edge edge = it.next();
            if(edge.getTgt().method().getSignature().equals(method.getMethod())){
                crashInfo.add2EdgeMap(0, edge);
                SootMethod sourceMtd = edge.getSrc().method();
                if(sourceMtd.getDeclaringClass().getName().startsWith("java") || sourceMtd.getDeclaringClass().getName().startsWith("android"))
                    continue;
                addCallersOfSourceOfEdge(edge, method, crashInfo, sourceMtd, 1);
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
     */
    private void addCallersOfSourceOfEdge(Edge edge, RelatedMethod method, CrashInfo crashInfo, SootMethod sootMethod, int depth ) {
        //TODO
        String candi = sootMethod.getDeclaringClass().getName()+ "." + sootMethod.getName();
        int score = ConstantUtils.INITSCORE - getOrderInTrace(crashInfo, candi)*5 - method.getDepth()*2 - depth;
        System.out.println(candi +" " +score +" " + " 5*" +getOrderInTrace(crashInfo, candi) + " 2*" +method.getDepth()+ " 1*" +depth);
        crashInfo.addBuggyCandidates(candi, score);

        //if the buggy type is not passed by parameter, do not find its caller
        Set<Integer> paramIndexCaller = SootUtils.getIndexesFromMethod(edge, crashInfo.exceptionInfo.getRelatedValueIndex());
        System.out.println(sootMethod.getSignature() +" "+ paramIndexCaller.size() );
        if(paramIndexCaller.size() == 0) return;

        for (Iterator<Edge> it = Global.v().getAppModel().getCg().edgesInto(sootMethod); it.hasNext(); ) {
            Edge edge2 = it.next();
            if(!crashInfo.getEdges().contains(edge2) && !edge2.toString().contains("dummyMainMethod")){
                crashInfo.add2EdgeMap(depth,edge2);
                addCallersOfSourceOfEdge(edge2, method, crashInfo, edge2.getSrc().method(), depth+1);
            }
        }
    }

    /**
     * getBuggyFromUserCode
     * @param crashInfo
     */
    private void getBuggyFromUserCode(CrashInfo crashInfo) {
        System.out.println("getBuggyFromUserCode......");
        SootMethod crashMethod = getCrashSootMethod(crashInfo);
        List<SootField> keyFields = getKeySootFields(crashMethod, crashInfo);
        for(SootField field: keyFields) {
            for (SootMethod otherMethod : crashMethod.getDeclaringClass().getMethods()) {
                if (!otherMethod.hasActiveBody()) continue;
                if (SootUtils.fieldIsChanged(field, otherMethod)) {
                    //TODO
                    int score = ConstantUtils.INITSCORE - ConstantUtils.USERFIELDMTDSCORE;
                    crashInfo.addBuggyCandidates(otherMethod.getDeclaringClass().getName() + "." + otherMethod.getName(), score);
                }
            }
        }
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
        return getSootMethodBySimpleName(crashInfo.getCrashMethod());
    }

    /**
     * getSootMethodBySimpleName
     * @param simpleName
     * @return
     */
    private SootMethod getSootMethodBySimpleName(String simpleName){
        for(SootClass sc: Scene.v().getApplicationClasses()) {
            for(SootMethod method: sc.getMethods()){
                String name = method.getDeclaringClass().getName()+"."+ method.getName();
                if(name.equals(simpleName)){
                    return method;
                }
            }
        }
        return null;
    }
    /**
     * OverrideMissing type
     * @param crashInfo
     *
     */
    private void overrideMissingHandler(CrashInfo crashInfo) {
        System.out.println("overrideMissingHandler...");
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
                    if(!hasMethod) {
                        String candi = sub.getName() + "." + crashInfo.getSubMethodName();
                        int score = ConstantUtils.INITSCORE - getOrderInTrace(crashInfo, candi);
                        //TODO
                        crashInfo.addBuggyCandidates(candi, score);
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
            if(tag.startsWith("android") ||tag.startsWith("java")) continue;
            if (tag.contains("$"))   tag = tag.split("\\$")[0];
            order++;
            if (candi.contains(tag)) {
                return order;
            }
        }

        return order+2;
    }


    /**
     * getExceptionOfCrashInfo
     */
    private void getExceptionOfCrashInfo() {
        for(CrashInfo crashInfo : crashInfoList){
            if(crashInfo.getTrace().size()==0 ) continue;
            readExceptionSummary(crashInfo.getClssName());
            if(!exceptionInfoMap.containsKey(crashInfo.getMethodName())) continue;
            for(ExceptionInfo exceptionInfo : exceptionInfoMap.get(crashInfo.getMethodName())){
                if(exceptionInfo.getExceptionMsg() == null) continue;
                Pattern p = Pattern.compile(exceptionInfo.getExceptionMsg().replace("()",""));
                Matcher m = p.matcher(crashInfo.getMsg().replace("()",""));
                if(exceptionInfo.getExceptionMsg().equals(crashInfo.getMsg()) || m.matches()){
                    crashInfo.setExceptionInfo(exceptionInfo);
                }

            }
        }
    }

    /**
     * readExceptionSummary
     * @param sootclass
     */
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
    }
}
