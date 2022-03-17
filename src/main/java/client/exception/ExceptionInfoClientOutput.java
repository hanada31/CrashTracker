package main.java.client.exception;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import main.java.analyze.utils.output.PrintUtils;
import main.java.client.statistic.model.StatisticResult;
import soot.SootMethod;
import soot.Value;

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

    public void writeToJson(String path, Set<ExceptionInfo> result){
        JSONObject rootElement = new JSONObject(new LinkedHashMap());
        rootElement.put("system", "");
        rootElement.put("version", "");
        File file = new File(path);
        try {
            file.createNewFile();
            JSONArray exceptionListElement  = new JSONArray(new ArrayList<>());
            rootElement.put("methodMap", exceptionListElement);
            for(ExceptionInfo info :result){
                JSONObject jsonObject = new JSONObject(true);
                jsonObject.put("method", info.getSootMethod().getSignature());
                jsonObject.put("modifier", info.getModifier());
                jsonObject.put("type", info.getExceptionType());
                jsonObject.put("message", info.getExceptionMsg());
//                JSONArray conds  = new JSONArray(new ArrayList<>());
//                for(Value condValue: info.getConditions()){
//                    JSONObject jsonObjectTemp = new JSONObject(true);
//                    jsonObjectTemp.put("cond", condValue.getClass().getName()+"-->"+condValue.toString());
//                    conds.add(jsonObjectTemp);
//                }
//                jsonObject.put("conds",conds);

                jsonObject.put("conditions", PrintUtils.printList(info.getConditions()));

                jsonObject.put("paramValues", PrintUtils.printList(info.getRelatedParamValues()));
                jsonObject.put("staticValues", PrintUtils.printList(info.getRelatedStaticValues()));
                jsonObject.put("caughtedValues", PrintUtils.printList(info.getCaughtedValues()));
                jsonObject.put("relatedValues", PrintUtils.printList(info.getRelatedParamValues())+"; "
                        +PrintUtils.printList(info.getRelatedStaticValues()) +"; "+ PrintUtils.printList(info.getCaughtedValues()));
                exceptionListElement.add(jsonObject);
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
