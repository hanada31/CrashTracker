package main.java.client.crash;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import main.java.client.exception.ExceptionInfo;
import main.java.client.exception.RelatedMethod;
import main.java.client.statistic.model.StatisticResult;
import soot.jimple.toolkits.callgraph.Edge;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;

/**
 * @Author hanada
 * @Date 2022/3/22 20:04
 * @Version 1.0
 */
public class CrashAnalysisClientOutput {

    public CrashAnalysisClientOutput(StatisticResult result) {

    }

    public void writeToJson(String path, List<CrashInfo> crashInfoList) {
        JSONObject rootElement = new JSONObject(new LinkedHashMap());
        rootElement.put("system", "");
        rootElement.put("version", "");
        File file = new File(path);
        try {
            file.createNewFile();
            JSONArray arrayElement = new JSONArray(new ArrayList<>());
            rootElement.put("methodMap", arrayElement);
            for (CrashInfo crashInfo : crashInfoList) {
                JSONObject jsonObject = new JSONObject(true);
                arrayElement.add(jsonObject);

                addBasic(jsonObject, crashInfo);
                addBuggyTraces(jsonObject, crashInfo);

                addBuggyMethods(jsonObject, crashInfo);
                addEdges(jsonObject, crashInfo);
                addRelatedMethods(jsonObject, crashInfo);
            }

            PrintWriter printWriter = new PrintWriter(file);
            String jsonString = JSON.toJSONString(rootElement, SerializerFeature.PrettyFormat,
                    SerializerFeature.DisableCircularReferenceDetect);
            printWriter.write(jsonString.toString());
            printWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addRelatedMethods(JSONObject jsonObject, CrashInfo crashInfo) {
        ExceptionInfo exceptionInfo = crashInfo.getExceptionInfo();
        if(exceptionInfo==null) return;
        jsonObject.put("relatedMethodsInSameClass", exceptionInfo.getRelatedMethodsInSameClass().size());
        jsonObject.put("relatedMethodsInDiffClass", exceptionInfo.getRelatedMethodsInDiffClass().size());

        JSONArray relatedMethodsSameArray = new JSONArray();
        if (exceptionInfo.getRelatedMethodsInSameClass().size() > 0) {
            for (RelatedMethod mtd : exceptionInfo.getRelatedMethodsInSameClass()) {
                String mtdString = JSONObject.toJSONString(mtd);
                JSONObject mtdObject = JSONObject.parseObject(mtdString);  // 转换为json对象
                relatedMethodsSameArray.add(mtdObject);
            }
        }
        jsonObject.put("relatedMethodsInSameClass" , relatedMethodsSameArray);

        JSONArray relatedMethodsDiffArray = new JSONArray();
        if (exceptionInfo.getRelatedMethodsInDiffClass().size() > 0) {
            for (RelatedMethod mtd : exceptionInfo.getRelatedMethodsInDiffClass()) {
                String mtdString = JSONObject.toJSONString(mtd);
                JSONObject mtdObject = JSONObject.parseObject(mtdString);  // 转换为json对象
                relatedMethodsDiffArray.add(mtdObject);
            }
        }
        jsonObject.put("relatedMethodsInDiffClass" , relatedMethodsDiffArray);
    }

    private void addBasic(JSONObject jsonObject, CrashInfo crashInfo) {
        jsonObject.put("method", crashInfo.getMethodName());
        jsonObject.put("crash message", crashInfo.getMsg());
        ExceptionInfo exceptionInfo = crashInfo.getExceptionInfo();
        if(exceptionInfo!=null) {
            jsonObject.put("reg message", crashInfo.getExceptionInfo().getExceptionMsg());
            jsonObject.put("relatedVarType", crashInfo.getExceptionInfo().getRelatedVarType());
        }
        jsonObject.put("labeledBuggyAPI", crashInfo.getBuggyApi());
        jsonObject.put("labeledRealBuggy", crashInfo.getReal());
        jsonObject.put("labeledCategory", crashInfo.getCategory());
        jsonObject.put("labeledReason", crashInfo.getReason());

    }

    private void addBuggyTraces(JSONObject jsonObject, CrashInfo crashInfo) {
        JSONArray traceArray = new JSONArray();
        for (String  trace: crashInfo.getTrace()) {
            traceArray.add(trace);
        }
        jsonObject.put("stack trace" , traceArray);
    }

    private void addBuggyMethods(JSONObject jsonObject, CrashInfo crashInfo) {
        JSONArray buggyArray = new JSONArray();
        for (String  buggy: crashInfo.getBuggyMethods()) {
            buggyArray.add(buggy);
        }
        jsonObject.put("locatedBuggyMethod" , buggyArray);

        JSONArray buggyweakArray = new JSONArray();
        for (String  buggy: crashInfo.getBuggyMethods_weak()) {
            buggyweakArray.add(buggy);
        }
        jsonObject.put("locatedBuggyMethod_weak" , buggyweakArray);
    }

    private void addEdges(JSONObject jsonObject, CrashInfo crashInfo) {
        JSONArray edgeArray = new JSONArray();
        for (Map.Entry entry: crashInfo.getEdgeMap().entrySet()) {
            for(Edge edge :(List<Edge>)entry.getValue()){
                edgeArray.add(edge.getSrc().method().getSignature());
            }
        }
        jsonObject.put("edges" , edgeArray);
    }
}

