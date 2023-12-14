package com.iscas.crashtracker.client.crash;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.iscas.crashtracker.base.Global;
import com.iscas.crashtracker.base.MyConfig;
import com.iscas.crashtracker.client.exception.ExceptionInfo;
import com.iscas.crashtracker.client.exception.RelatedCondType;
import com.iscas.crashtracker.client.exception.RelatedMethod;
import com.iscas.crashtracker.client.exception.RelatedVarType;
import com.iscas.crashtracker.utils.CollectionUtils;
import com.iscas.crashtracker.utils.PrintUtils;
import com.iscas.crashtracker.utils.SootUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

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
        File file = new File(path);
        try {
            file.createNewFile();
            for (CrashInfo crashInfo : crashInfoList) {
                JSONObject jsonObjectInDataset = new JSONObject(true);
                rootElement.put("Crash Info in Dataset", jsonObjectInDataset);
                addBasicInDataset(jsonObjectInDataset, crashInfo);

                JSONObject jsonObjectGenereated = new JSONObject(true);
                rootElement.put("Fault Localization by CrashTracker", jsonObjectGenereated);
                addResultsByTool(jsonObjectGenereated, crashInfo);

//                addExtendedCG(jsonObject, crashInfo);
//                addRelatedMethods(jsonObjectGenereated, crashInfo);
            }

            PrintWriter printWriter = new PrintWriter(file);
            String jsonString = JSON.toJSONString(rootElement, SerializerFeature.PrettyFormat,
                    SerializerFeature.SortField);
            printWriter.write(jsonString);
            printWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addBasicInDataset(JSONObject jsonObject, CrashInfo crashInfo) {
        jsonObject.put("Apk name", Global.v().getAppModel().getAppName());
        jsonObject.put("Method", crashInfo.getMethodName());
        jsonObject.put("Crash Message", crashInfo.getMsg());
        JSONArray traceArray = new JSONArray();
        for (String  trace: crashInfo.getTrace()) {
            traceArray.add(trace);
        }
        jsonObject.put("stack trace" , traceArray);
        jsonObject.put("Labeled Buggy Method", crashInfo.getReal());
        jsonObject.put("Manifest targetSdkVersion",Global.v().getAppModel().getTargetSdkVersion());
        jsonObject.put("Manifest minSdkVersion",Global.v().getAppModel().getMinSdkVersion());
//        jsonObject.put("Labeled Buggy API", crashInfo.getBuggyApi());
//        jsonObject.put("labeledCategory", crashInfo.getCategory());
//        jsonObject.put("labeledReason", crashInfo.getReason());
    }


    private void addRelatedMethods(JSONObject jsonObject, CrashInfo crashInfo) {
        ExceptionInfo exceptionInfo = crashInfo.getExceptionInfo();
        if(exceptionInfo==null) return;
        jsonObject.put("relatedMethodsInSameClass", exceptionInfo.getRelatedMethodsInSameClass(false).size());
        jsonObject.put("relatedMethodsInDiffClass", exceptionInfo.getRelatedMethodsInDiffClass(false).size());

        JSONArray relatedMethodsSameArray = new JSONArray();
        if (exceptionInfo.getRelatedMethodsInSameClass(false).size() > 0) {
            for (RelatedMethod mtd : exceptionInfo.getRelatedMethodsInSameClass(false)) {
                String mtdString = JSONObject.toJSONString(mtd, SerializerFeature.PrettyFormat,
                        SerializerFeature.SortField);
                JSONObject mtdObject = JSONObject.parseObject(mtdString);  // 转换为json对象
                relatedMethodsSameArray.add(mtdObject);
            }
        }
        jsonObject.put("relatedMethodsInSameClass" , relatedMethodsSameArray);

        JSONArray relatedMethodsDiffArray = new JSONArray();
        if (exceptionInfo.getRelatedMethodsInDiffClass(false).size() > 0) {
            for (RelatedMethod mtd : exceptionInfo.getRelatedMethodsInDiffClass(false)) {
                String mtdString = JSONObject.toJSONString(mtd, SerializerFeature.PrettyFormat,
                        SerializerFeature.SortField);
                JSONObject mtdObject = JSONObject.parseObject(mtdString);  // 转换为json对象
                relatedMethodsDiffArray.add(mtdObject);
            }
        }
        jsonObject.put("relatedMethodsInDiffClass" , relatedMethodsDiffArray);
    }



    private String changeToETSType(RelatedVarType relatedVarType) {
        switch (relatedVarType) {
            case Empty:
                return "NoConditionVar";
            case Parameter:
                return "OnlyKeyVar";
            case Field:
                return "OnlyKeyAPI";
            case ParaAndField:
                return "KeyVarAndKeyAPI";
            case Unknown:
                return "NoOutsideVar";
        }
        return "";
    }

    private void addResultsByTool(JSONObject jsonObject, CrashInfo crashInfo) {

        ExceptionInfo exceptionInfo = crashInfo.getExceptionInfo();
        if(exceptionInfo!=null) {
            jsonObject.put("Target Version of Framework", MyConfig.getInstance().getTargetVersion());
            jsonObject.put("Regression Message", crashInfo.getExceptionInfo().getExceptionMsg());
            jsonObject.put("Related Variable Type", crashInfo.getExceptionInfo().getRelatedVarType());
            jsonObject.put("Fault Inducing Paras", crashInfo.getFaultInducingParas());
            jsonObject.put("Related Condition Type", crashInfo.getExceptionInfo().getRelatedCondType()+"Condition");
            if(!crashInfo.getExceptionInfo().getRelatedCondType().equals(RelatedCondType.Empty))
                jsonObject.put("Conditions", crashInfo.getExceptionInfo().getConditions());
            if(crashInfo.getExceptionInfo().getConditions().size()>0)
                jsonObject.put("Conditions", PrintUtils.printList(crashInfo.getExceptionInfo().getConditions()));
            if(crashInfo.getExceptionInfo().getRelatedFieldValues().size()>0)
                jsonObject.put("Field Values", PrintUtils.printList(crashInfo.getExceptionInfo().getRelatedFieldValues()));
            if(crashInfo.getExceptionInfo().getRelatedParamValues().size()>0)
                jsonObject.put("Param Values", PrintUtils.printList(crashInfo.getExceptionInfo().getRelatedParamValues()));
            jsonObject.put("ETS-related Type", changeToETSType(crashInfo.getExceptionInfo().getRelatedVarType()));
        }
        Map<String, String> refToInvokeStack = new HashMap<>();
        List<String> workList = new ArrayList<>();
        JSONArray buggyArray = new JSONArray();
        JSONArray refMethodList = new JSONArray();
        JSONArray refFieldList = new JSONArray();
        List<Map.Entry<String, Integer>> treeMapList = CollectionUtils.getTreeMapEntriesSortedByValue(crashInfo.getBuggyCandidates());
        for (int i = 0; i < treeMapList.size(); i++) {
            String buggy = treeMapList.get(i).getKey();
            BuggyCandidate bc = crashInfo.getBuggyCandidateObjs().get(buggy);
            buggyArray.add(bc);
            workList.add(bc.getCandidateSig());
        }

        for(int i =0; i<3; i++) {
            List<String> addedInThisStep = new ArrayList<>();
            for (String ref : workList) {
                updateRefList(crashInfo,ref, refMethodList, refFieldList, addedInThisStep,refToInvokeStack);
            }
            workList = addedInThisStep;
        }
        jsonObject.put("Buggy Method Candidates" , buggyArray);
        jsonObject.put("Reference Method List" , refMethodList);
        jsonObject.put("Reference Field List" , refFieldList);


//        JSONArray noneCodeLabelArray = new JSONArray();
//        for (String label: crashInfo.getNoneCodeLabel( )) {
//            noneCodeLabelArray.add(label);
//        }
//        Object put = jsonObject.put("None-Code Labels", noneCodeLabelArray);
    }

    private void updateRefList(CrashInfo crashInfo, String candidateSig, JSONArray refMethodList, JSONArray refFieldList, List<String> addedInThisStep, Map<String, String> refToInvokeStack) {

        for(String usedMethod: SootUtils.getUsedMethodList(crashInfo, candidateSig)){
            String historyStack = "";
            if(refMethodList.contains(usedMethod))continue;
            if(refToInvokeStack.containsKey(candidateSig)){
                historyStack = refToInvokeStack.get(candidateSig) +" --> ";
            }
            refMethodList.add(historyStack +candidateSig +" --> "+usedMethod);
            refToInvokeStack.put(usedMethod, historyStack +candidateSig);
            addedInThisStep.add(usedMethod);
        }
        for(String usedField: SootUtils.getUsedFieldList(crashInfo, candidateSig)){
            String historyStack = "";
            if(refToInvokeStack.containsKey(candidateSig)){
                historyStack = refToInvokeStack.get(candidateSig) +" --> ";
            }
            refFieldList.add(historyStack +candidateSig +" --> "+usedField);
        }
    }

}

