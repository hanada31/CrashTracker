package main.java.client.crash;

import main.java.client.exception.ExceptionInfo;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.*;

/**
 * @Author hanada
 * @Date 2022/3/22 20:16
 * @Version 1.0
 */
public class CrashInfo {
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
    ExceptionInfo exceptionInfo;
    List<Edge> edges = new ArrayList<>();
    Map<Integer, ArrayList<Edge>> edgeMap = new TreeMap<Integer, ArrayList<Edge>>();
    List<String> classesInTrace= new ArrayList<>();
    List<String> buggyMethods = new ArrayList<>();
    List<String> buggyMethods_weak = new ArrayList<>();


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
        for(String t: ss){
            this.trace.add(t);
//            System.out.println(t);
            if(t.lastIndexOf(".")>=0)
                this.classesInTrace.add(t.substring(0, t.lastIndexOf(".")));
        }
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


    public String getClssName() {
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


    public List<String> getBuggyMethods() {
        return buggyMethods;
    }

    public void setBuggyMethods(List<String> buggyMethods) {
        this.buggyMethods = buggyMethods;
    }

    public void addBuggyMethods(String buggyMethod) {
        if(!buggyMethods.contains(buggyMethod))
            this.buggyMethods.add(buggyMethod);
    }

    public List<String> getBuggyMethods_weak() {
        return buggyMethods_weak;
    }

    public void setBuggyMethods_weak(List<String> buggyMethods_weak) {
        this.buggyMethods_weak = buggyMethods_weak;
    }

    public void addBuggyMethods_weak(String buggyMethod) {
        this.buggyMethods_weak.add(buggyMethod);
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
