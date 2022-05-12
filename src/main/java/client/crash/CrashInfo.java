package main.java.client.crash;
import main.java.Global;
import main.java.analyze.utils.ConstantUtils;
import main.java.analyze.utils.StringUtils;
import main.java.client.exception.ExceptionInfo;
import soot.jimple.toolkits.callgraph.Edge;
import soot.util.Cons;

import java.util.*;

/**
 * @Author hanada
 * @Date 2022/3/22 20:16
 * @Version 1.0
 */
public class CrashInfo {

    String id;
    String methodName;
    String className;
    String real;
    String exception;
    List<String> trace = new ArrayList<>();
    String buggyApi;
    String msg;
    String realCate;
    String category;
    String identifier;
    String reason;
    String signaler;
    String crashAPI;
    String crashMethod;
    List<String> crashMethodList = new ArrayList<>();
    String crashCallBack;
    ExceptionInfo exceptionInfo;
    List<Edge> edges = new ArrayList<>();
    Map<Integer, ArrayList<Edge>> edgeMap = new TreeMap<Integer, ArrayList<Edge>>();
    List<String> classesInTrace= new ArrayList<>();
    Map<String, Integer> buggyCandidates = new TreeMap<>();
    Map<String, Integer> extendedCallDepth = new HashMap<String, Integer>();
    boolean findCandidateInTrace = false;
    int minScore = ConstantUtils.INITSCORE;

    public Map<String, Integer> getExtendedCallDepth() {
        return extendedCallDepth;
    }

    public boolean addExtendedCallDepth(String key, int value) {
        if(key.startsWith("java") || key.startsWith("android") || key.startsWith("com.android")) return false;
        if(!extendedCallDepth.containsKey(key) || extendedCallDepth.get(key)>value ) {
            extendedCallDepth.put(key, value);
            return true;
        }
        return false;
    }



    public Map<String, Integer> getBuggyCandidates() {
        return buggyCandidates;
    }

    public void addBuggyCandidates(String candi, int score, boolean filterByExtendedCG) {
        if(filterByExtendedCG && !extendedCallDepth.containsKey(candi))
            return;
//        if(!extendedCallDepth.containsKey(candi))
//            score = score - ConstantUtils.NOTINEXTENDEDCG;
        String pkgPrefix = StringUtils.getPkgPrefix(Global.v().getAppModel().getPackageName(),2);
        if(!candi.contains(pkgPrefix)) score = score - ConstantUtils.OUTOFPKGSCORE;
        if(this.buggyCandidates.containsKey(candi) && this.buggyCandidates.get(candi) > score)
            return;
        if(score > ConstantUtils.BOTTOMSCORE) {
            this.buggyCandidates.put(candi, score);
            if(score< minScore) minScore = score;
        }
    }


    public List<String> getCrashMethodList() {
        return crashMethodList;
    }

    public String getSignaler() {
        return signaler;
    }

    public void setSignaler(String signaler) {
        this.signaler = signaler;
    }

    public String getCrashAPI() {
        return crashAPI;
    }

    public void setCrashAPI(String crashAPI) {
        this.crashAPI = crashAPI;
    }

    public String getCrashMethod() {
        return crashMethod;
    }

    public void setCrashMethod(String crashMethod) {
        this.crashMethod = crashMethod;
    }

    public String getCrashCallBack() {
        return crashCallBack;
    }

    public void setCrashCallBack(String crashCallBack) {
        this.crashCallBack = crashCallBack;
    }


    public String getReal() {
        return real;
    }

    public void setReal(String real) {
        this.real = real;
    }

    public String getException() {
        return exception;
    }

    public void setException(String exception) {
        this.exception = exception;
    }

    public List<String> getTrace() {
        return trace;
    }

    public void setTrace(String trace) {
        trace= trace.replace("\"","");
        trace= trace.replace("[","").replace("]","");
        trace= trace.replace("at ","");
        String ss[] = trace.split(",");
        boolean firstUserAPI= true;
        for(int i=0; i< ss.length;i++){
            String method = ss[i];
            if(method.contains(" ") || !method.contains(".")) continue;
            this.trace.add(method);
            if(method.lastIndexOf(".")>=0)
                this.classesInTrace.add(method.substring(0, method.lastIndexOf(".")));
            if(i==0)
                setSignaler(method);
            else if(getCrashAPI()==null && ss[i-1].startsWith("android") && !method.startsWith("android")){
                setCrashAPI(ss[i-1]);
                setCrashMethod(method);
            }else if(getCrashAPI()!=null && getCrashCallBack()==null
                    && (method.startsWith("android.")|| method.startsWith("com.android.")|| method.startsWith("java")) ) {
                setCrashCallBack(method);
            }
            if(!method.startsWith("android.") &&!method.startsWith("com.android.") && !method.startsWith("java")) {
                if(firstUserAPI)
                    crashMethodList.add(method);
            }else{
                if(crashMethodList.size()>0)  firstUserAPI = false;
            }

        }
    }
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBuggyApi() {
        return buggyApi;
    }

    public void setBuggyApi(String buggyApi) {
        this.buggyApi = buggyApi;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getRealCate() {
        return realCate;
    }

    public void setRealCate(String realCate) {
        this.realCate = realCate;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public ExceptionInfo getExceptionInfo() {
        return exceptionInfo;
    }

    public void setExceptionInfo(ExceptionInfo exceptionInfo) {
        this.exceptionInfo = exceptionInfo;
    }

    public List<Edge> getEdges() {
        return edges;
    }

    public void setEdges(List<Edge> edges) {
        this.edges = edges;
    }

    public String getMethodName() {
        return methodName;
    }
    public String getSubMethodName() {
        int index = methodName.lastIndexOf(".");
        if(index>=0 && index+1 < methodName.length()){
            return methodName.substring(index+1);
        }
        return null;
    }


    public String getClassName() {
        return className;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
        if(methodName.lastIndexOf(".")>=0)
            this.className = methodName.substring(0,methodName.lastIndexOf("."));
    }

    public List<String> getClassesInTrace() {
        return classesInTrace;
    }

    public Map<Integer, ArrayList<Edge>> getEdgeMap() {
        return edgeMap;
    }

    public void setEdgeMap(Map<Integer, ArrayList<Edge>> edgeMap) {
        this.edgeMap = edgeMap;
    }
    public void add2EdgeMap(Integer depth, Edge e) {
        if(!edgeMap.containsKey(depth)){
            edgeMap.put(depth,new ArrayList<Edge>());
        }
        if(!edgeMap.get(depth).contains(e))
            edgeMap.get(depth).add(e);
        edges.add(e);
    }

    @Override
    public String toString() {
        return "CrashInfo{" +
                "methodName='" + methodName + '\'' +
                ", real='" + real + '\'' +
                ", exception='" + exception + '\'' +
                ", trace=" + trace +
                ", buggyApi='" + buggyApi + '\'' +
                ", msg='" + msg + '\'' +
                ", realCate='" + realCate + '\'' +
                ", category='" + category + '\'' +
                ", identifier='" + identifier + '\'' +
                ", reason='" + reason + '\'' +
                ", exceptionInfo=" + exceptionInfo +
                ", edges=" + edges +
                '}';
    }

}
