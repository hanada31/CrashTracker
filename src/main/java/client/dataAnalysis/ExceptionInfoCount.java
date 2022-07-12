package main.java.client.dataAnalysis;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import main.java.base.MyConfig;
import main.java.utils.FileUtils;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @Author hanada
 * @Date 2022/6/24 10:22
 * @Version 1.0
 */
public class ExceptionInfoCount {
    String[] versions = {"2.3", "4.4", "5.0", "6.0", "7.0", "8.0", "9.0", "10.0", "11.0", "12.0"};

    public void analyze() {
        getExceptionOfCrashInfo();
        System.out.println("getExceptionOfCrashInfo Finish...");

    }

    private void getExceptionOfCrashInfo() {
        FileUtils.writeText2File(MyConfig.getInstance().getResultFolder() +"exceptionInfoCount.txt", "", false);
        System.out.println("write to "+ MyConfig.getInstance().getResultFolder() +"exceptionInfoCount.txt");

        StringBuilder sb = new StringBuilder();
        sb.append("version\t" + "exceptionNum\t" + "exceptionTypeNum\t" + "uniqueMethodNum\t");
        sb.append("msgWithsS\t" + "msgWithoutsS\t" + "notNullMsg\t");
        sb.append("null"  + "\t");
        sb.append("OverrideMissing"  + "\t");
        sb.append("ParameterOnly"  + "\t");
        sb.append("FieldOnly"  + "\t");
        sb.append("ParaAndField"  + "\t");

        sb.append("Direct"  + "\t");
        sb.append("Multiple"  + "\t");
        sb.append("NotReturn"  + "\t");
        sb.append("Caught"  + "\t");
        sb.append("Unknown"  + "\n");

        for (String version : versions) {
            String str = getExceptionWithGivenVersion(version);
            sb.append(str);
        }
        FileUtils.writeText2File(MyConfig.getInstance().getResultFolder() +"exceptionInfoCount.txt", sb.toString(), true);

    }

    /**
     * getExceptionOfCrashInfo from exception.json
     * @return
     */
    private String getExceptionWithGivenVersion(String version) {
        MyConfig.getInstance().setExceptionFilePath("Files\\android" + version + "\\exceptionInfo\\");
        String fn = MyConfig.getInstance().getExceptionFilePath() + "summary" + File.separator + "exception.json";
        String jsonString = FileUtils.readJsonFile(fn);
        JSONObject wrapperObject = (JSONObject) JSONObject.parse(jsonString);
        if(wrapperObject==null) return "";

        int exceptionNum = 0;
        Set<String> exceptionType = new HashSet<String>();
        Set<String> uniqueMethod = new HashSet<String>();
        Map<String, Integer> relatedVarTypeMap = new HashMap<String, Integer>();
        Map<String, Integer> relatedCondTypeMap = new HashMap<String, Integer>();
        int extractedMsgWithsS = 0;
        int extractedMsgWithoutsS = 0;
        JSONArray methods = wrapperObject.getJSONArray("exceptions");//构建JSONArray数组
        exceptionNum = methods.size();
        for (int i = 0 ; i < methods.size();i++){
            JSONObject jsonObject = (JSONObject)methods.get(i);
            uniqueMethod.add(jsonObject.getString("method"));
            exceptionType.add(jsonObject.getString("type"));
            String message = jsonObject.getString("message");
            if(!message.equals("[\\s\\S]*")){
                if(message.contains("[\\s\\S]*"))
                    extractedMsgWithsS+=1;
                else
                    extractedMsgWithoutsS+=1;
            }

            String relatedVarType = jsonObject.getString("relatedVarType");
            if(!relatedVarTypeMap.containsKey(relatedVarType)){
                relatedVarTypeMap.put(relatedVarType, 1);
            }
            relatedVarTypeMap.put(relatedVarType, relatedVarTypeMap.get(relatedVarType)+1);

            String relatedCondType = jsonObject.getString("relatedCondType");
            if(!relatedCondTypeMap.containsKey(relatedCondType)){
                relatedCondTypeMap.put(relatedCondType, 1);
            }
            relatedCondTypeMap.put(relatedCondType, relatedCondTypeMap.get(relatedCondType)+1);
        }
        StringBuilder sb = new StringBuilder(version + "\t" + exceptionNum +"\t" + exceptionType.size()+"\t" + uniqueMethod.size()+"\t");

        sb.append(extractedMsgWithsS+ "\t");
        sb.append(extractedMsgWithoutsS+ "\t");
        sb.append(extractedMsgWithsS+extractedMsgWithoutsS+ "\t");
        sb.append(relatedVarTypeMap.get(null)+ "\t");
        sb.append(relatedVarTypeMap.get("OverrideMissing") + "\t");
        sb.append(relatedVarTypeMap.get("ParameterOnly") + "\t");
        sb.append(relatedVarTypeMap.get("FieldOnly") + "\t");
        sb.append(relatedVarTypeMap.get("ParaAndField") + "\t");


        sb.append(relatedCondTypeMap.get("Direct") + "\t");
        sb.append(relatedCondTypeMap.get("Multiple") + "\t");
        sb.append(relatedCondTypeMap.get("NotReturn") + "\t");
        sb.append(relatedCondTypeMap.get("Caught") + "\t");
        sb.append(relatedCondTypeMap.get("Unknown") + "\t");
        return  sb+"\n";
    }
}
