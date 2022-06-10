package main.java.client.exception;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import main.java.Global;
import main.java.MyConfig;
import main.java.analyze.utils.output.FileUtils;
import main.java.analyze.utils.output.PrintUtils;
import main.java.client.crash.CrashInfo;
import main.java.client.statistic.model.StatisticResult;
import soot.SootClass;
import soot.SootMethod;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;

/**
 * @Author hanada
 * @Date 2022/3/11 15:06
 * @Version 1.0
 */
public class ExceptionInfoClientOutput {

    public ExceptionInfoClientOutput(StatisticResult result) {

    }



    /**
     * write to Json File after each class is Analyzed
     * @param sootClass
     */
    public static void writeJsonForCurrentClass(SootClass sootClass, List<ExceptionInfo> exceptionInfoList, JSONArray exceptionListElement) {
        String path = MyConfig.getInstance().getExceptionFilePath();
        FileUtils.createFolder(path);
        if(exceptionInfoList.size()>0) {
            System.out.println("writeToJson "+path + sootClass.getName() + ".json");
            ExceptionInfoClientOutput.writeToJson(path + sootClass.getName() + ".json", exceptionInfoList);
        }
    }


    public static void writeJsonForFramework(JSONArray exceptionListElement) {
        String path = MyConfig.getInstance().getExceptionFilePath()+ "summary"+ File.separator;
        FileUtils.createFolder(path);
        JSONObject rootElement = new JSONObject(new LinkedHashMap());
        File file = new File(path+ "exception.json");
        try {
            file.createNewFile();
            rootElement.put("exceptions", exceptionListElement);
            PrintWriter printWriter = new PrintWriter(file);
            String jsonString = JSON.toJSONString(rootElement, SerializerFeature.PrettyFormat,
                    SerializerFeature.DisableCircularReferenceDetect);
            printWriter.write(jsonString);
            printWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void writeToJson(String path, List<ExceptionInfo> result){
        JSONObject rootElement = new JSONObject(new LinkedHashMap());
        File file = new File(path);
        try {
            file.createNewFile();
            JSONArray exceptionListElement  = new JSONArray(new ArrayList<>());
            rootElement.put("exceptions", exceptionListElement);
            for(ExceptionInfo info :result){
                JSONObject jsonObject = new JSONObject(true);
                exceptionListElement.add(jsonObject);
                addBasic1(jsonObject, info);
                addBasic2(jsonObject, info);
                addConditions(jsonObject, info);
                addRelatedValues(jsonObject, info);
                addRelatedMethods(jsonObject, info);
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

    public static void addBasic1(JSONObject jsonObject, ExceptionInfo info) {
        jsonObject.put("method", info.getSootMethod().getSignature());
        jsonObject.put("message", info.getExceptionMsg());
    }

    public static void addBasic2(JSONObject jsonObject, ExceptionInfo info) {
        jsonObject.put("relatedVarType", info.getRelatedVarType());
        jsonObject.put("modifier", info.getModifier());
        jsonObject.put("type", info.getExceptionType());
        jsonObject.put("osVersionRelated", info.isOsVersionRelated());
        jsonObject.put("resourceRelated", info.isResourceRelated());
        jsonObject.put("assessRelated", info.isAssessRelated());
        jsonObject.put("hardwareRelated", info.isHardwareRelated());
        jsonObject.put("manifestRelated", info.isManifestRelated());
    }

    public static void addConditions(JSONObject jsonObject, ExceptionInfo info) {
//        JSONArray conds  = new JSONArray(new ArrayList<>());
//        for(Value condValue: info.getConditions()){
//            JSONObject jsonObjectTemp = new JSONObject(true);
//            jsonObjectTemp.put("cond", condValue.getClass().getName()+"-->"+condValue.toString());
//            conds.add(jsonObjectTemp);
//        }
//        jsonObject.put("conds",conds);

        if(info.getConditions().size()>0)
            jsonObject.put("conditions", PrintUtils.printList(info.getConditions()));
    }

    public static void addRelatedValues(JSONObject jsonObject, ExceptionInfo info) {
        if(info.getRelatedParamValues().size()>0)
            jsonObject.put("paramValues", PrintUtils.printList(info.getRelatedParamValues()));
        if(info.getRelatedFieldValues().size()>0)
            jsonObject.put("fieldValues", PrintUtils.printList(info.getRelatedFieldValues()));
        if(info.getCaughtedValues().size()>0)
            jsonObject.put("caughtValues", PrintUtils.printList(info.getCaughtedValues()));
        if(info.getRelatedParamValues().size() + info.getRelatedFieldValues().size() + info.getCaughtedValues().size()>0)
            jsonObject.put("relatedValues", PrintUtils.printList(info.getRelatedParamValues())+"; "
                    +PrintUtils.printList(info.getRelatedFieldValues()) +"; "+ PrintUtils.printList(info.getCaughtedValues()));
    }


    public static void addRelatedMethods(JSONObject jsonObject, ExceptionInfo exceptionInfo) {
        if(exceptionInfo==null) return;
        jsonObject.put("relatedMethodsInSameClass", exceptionInfo.getRelatedMethodsInSameClass(true).size());
        jsonObject.put("relatedMethodsInDiffClass", exceptionInfo.getRelatedMethodsInDiffClass(true).size());

        JSONArray relatedMethodsSameArray = new JSONArray();
        if (exceptionInfo.getRelatedMethodsInSameClass(false).size() > 0) {
            for (RelatedMethod mtd : exceptionInfo.getRelatedMethodsInSameClass(false)) {
                String mtdString = JSONObject.toJSONString(mtd);
                JSONObject mtdObject = JSONObject.parseObject(mtdString);  // 转换为json对象
                relatedMethodsSameArray.add(mtdObject);
            }
        }
        jsonObject.put("relatedMethodSameClass" , relatedMethodsSameArray);

        JSONArray relatedMethodsDiffArray = new JSONArray();
        if (exceptionInfo.getRelatedMethodsInDiffClass(false).size() > 0) {
            for (RelatedMethod mtd : exceptionInfo.getRelatedMethodsInDiffClass(false)) {
                String mtdString = JSONObject.toJSONString(mtd);
                JSONObject mtdObject = JSONObject.parseObject(mtdString);  // 转换为json对象
                relatedMethodsDiffArray.add(mtdObject);
            }
        }
        jsonObject.put("relatedMethodDiffClass" , relatedMethodsDiffArray);
    }

}
