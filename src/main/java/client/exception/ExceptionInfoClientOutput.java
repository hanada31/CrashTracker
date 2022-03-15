package main.java.client.exception;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import main.java.client.statistic.model.StatisticResult;
import soot.SootMethod;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * @Author hanada
 * @Date 2022/3/11 15:06
 * @Version 1.0
 */
public class ExceptionInfoClientOutput {

    public ExceptionInfoClientOutput(StatisticResult result) {

    }


    public void writeToTxt(String path, Map<SootMethod, Map<String, Set<String>>> result){
        File file = new File(path);
        try {
            file.createNewFile();
            StringBuffer stringBuffer = new StringBuffer();
            for (Map.Entry<SootMethod, Map<String, Set<String>>> sootMethodMap : result.entrySet()) {
                SootMethod method = sootMethodMap.getKey();
                Map<String, Set<String>> value = sootMethodMap.getValue();
                stringBuffer.append(method.getSignature() + "\n");
                for (Map.Entry<String, Set<String>> exceptionMap : value.entrySet()) {
                    String name = exceptionMap.getKey();
                    Set<String> message = exceptionMap.getValue();
                    stringBuffer.append("    ").append(name).append("\n");
                    for (String s : message) {
                        stringBuffer.append("        ").append(s).append("\n");
                    }
                }
            }
            PrintWriter printWriter = new PrintWriter(file);
            printWriter.write(stringBuffer.toString());
            printWriter.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeToJson(String path, Map<SootMethod, Map<String, Set<String>>> result){
        JSONObject rootElement = new JSONObject(new LinkedHashMap());
        rootElement.put("system", "");
        rootElement.put("version", "");
        JSONObject methodMapElement  = new JSONObject(new LinkedHashMap<>());
        rootElement.put("methodMap", methodMapElement);

        File file = new File(path);
        try {
            file.createNewFile();
            for (Map.Entry<SootMethod, Map<String, Set<String>>> sootMethodMap : result.entrySet()) {
                SootMethod method = sootMethodMap.getKey();
                Map<String, Set<String>> value = sootMethodMap.getValue();
                JSONArray exceptionListElement  = new JSONArray(new ArrayList<>());
                methodMapElement.put(method.getSignature(), exceptionListElement);

                for (Map.Entry<String, Set<String>> exceptionMap : value.entrySet()) {
                    String name = exceptionMap.getKey();
                    Set<String> message = exceptionMap.getValue();
                    if(message.size() ==0){
                        message.add("");
                    }
                    for (String s : message) {
                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put("name", name);
                        jsonObject.put("message", s);
                        exceptionListElement.add(jsonObject);
                    }

                }
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
}
