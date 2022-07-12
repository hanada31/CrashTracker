package main.java.client.crash;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import main.java.client.exception.ExceptionInfo;
import main.java.client.exception.RelatedMethod;
import main.java.utils.CollectionUtils;
import main.java.utils.PrintUtils;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author hanada
 * @Date 2022/3/22 20:04
 * @Version 1.0
 */
public class CrashAnalysisClientOutput {

    public CrashAnalysisClientOutput() {

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
//                addExtendedCG(jsonObject, crashInfo);
                addBuggyTraces(jsonObject, crashInfo);

                addBuggyMethods(jsonObject, crashInfo);
                addRelatedMethods(jsonObject, crashInfo);
            }

            PrintWriter printWriter = new PrintWriter(file);
            String jsonString = JSON.toJSONString(rootElement, SerializerFeature.PrettyFormat,
                    SerializerFeature.DisableCircularReferenceDetect);
            printWriter.write(jsonString);
            printWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    private void addRelatedMethods(JSONObject jsonObject, CrashInfo crashInfo) {
        ExceptionInfo exceptionInfo = crashInfo.getExceptionInfo();
        if(exceptionInfo==null) return;
        jsonObject.put("relatedMethodsInSameClass", exceptionInfo.getRelatedMethodsInSameClass(false).size());
        jsonObject.put("relatedMethodsInDiffClass", exceptionInfo.getRelatedMethodsInDiffClass(false).size());

        JSONArray relatedMethodsSameArray = new JSONArray();
        if (exceptionInfo.getRelatedMethodsInSameClass(false).size() > 0) {
            for (RelatedMethod mtd : exceptionInfo.getRelatedMethodsInSameClass(false)) {
                String mtdString = JSONObject.toJSONString(mtd);
                JSONObject mtdObject = JSONObject.parseObject(mtdString);  // 转换为json对象
                relatedMethodsSameArray.add(mtdObject);
            }
        }
        jsonObject.put("relatedMethodsInSameClass" , relatedMethodsSameArray);

        JSONArray relatedMethodsDiffArray = new JSONArray();
        if (exceptionInfo.getRelatedMethodsInDiffClass(false).size() > 0) {
            for (RelatedMethod mtd : exceptionInfo.getRelatedMethodsInDiffClass(false)) {
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
            jsonObject.put("relatedCondType", crashInfo.getExceptionInfo().getRelatedCondType());
            jsonObject.put("conditions", crashInfo.getExceptionInfo().getConditions());
            if(crashInfo.getExceptionInfo().getConditions().size()>0)
                jsonObject.put("conditions", PrintUtils.printList(crashInfo.getExceptionInfo().getConditions()));
            if(crashInfo.getExceptionInfo().getRelatedFieldValues().size()>0)
                jsonObject.put("fieldValues", PrintUtils.printList(crashInfo.getExceptionInfo().getRelatedFieldValues()));
            if(crashInfo.getExceptionInfo().getRelatedParamValues().size()>0)
                jsonObject.put("paramValues", PrintUtils.printList(crashInfo.getExceptionInfo().getRelatedParamValues()));
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
        List<Map.Entry<String, Integer>> treeMapList = CollectionUtils.getTreeMapEntriesSortedByValue(crashInfo.getBuggyCandidates());
        for (int i = 0; i < treeMapList.size(); i++) {
            String buggy = treeMapList.get(i).toString();
            BuggyCandidate bc = crashInfo.getBuggyCandidateObjs().get(treeMapList.get(i).getKey());
            buggyArray.add(buggy +"    Reason: "+ bc.getReason() +"  @  " +bc.getTrace());
        }
        jsonObject.put("locatedBuggyMethod" , buggyArray);


        JSONArray noneCodeLabelArray = new JSONArray();
        for (String label: crashInfo.getNoneCodeLabel()) {
            noneCodeLabelArray.add(label);
        }
        jsonObject.put("noneCodeLabels" , noneCodeLabelArray);
    }
}

