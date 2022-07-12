package main.java.client.dataAnalysis;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import main.java.base.MyConfig;
import main.java.client.crash.CrashInfo;
import main.java.client.exception.ExceptionInfo;
import main.java.utils.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author hanada
 * @Date 2022/6/24 10:22
 * @Version 1.0
 */
public class ExceptionMather {
    List<CrashInfo> crashInfoList = new ArrayList<>();
    String[] versions = {"2.3", "4.4", "5.0", "6.0", "7.0", "8.0", "9.0", "10.0", "11.0", "12.0"};
    Map<String, JSONObject> version2JsonStr = new HashMap<String,JSONObject>();

    public void analyze() {
        readAllCrashInfo();
        System.out.println("readCrashInfo Finish...");
        getExceptionOfCrashInfo();
        System.out.println("getExceptionOfCrashInfo Finish...");

    }

    private void getExceptionOfCrashInfo() {
        FileUtils.writeText2File(MyConfig.getInstance().getResultFolder() +"exceptionMatch.txt", "", false);
        System.out.println("write to "+ MyConfig.getInstance().getResultFolder() +"exceptionMatch.txt");
        for(CrashInfo crashInfo: crashInfoList) {
            String str= crashInfo.getId()+"\t"+crashInfo.getSignaler()+"\t";
            System.out.println("Analysis crash "+ str);
            for (String version : versions) {
                String relatedVarType = getExceptionWithGivenVersion(crashInfo, version);
                str+=relatedVarType+"\t";
            }
//            System.out.println(str);
            FileUtils.writeText2File(MyConfig.getInstance().getResultFolder() +"exceptionMatch.txt", str+"\n", true);
        }
    }

    /**
     * getExceptionOfCrashInfo from exception.json
     */
    private String getExceptionWithGivenVersion(CrashInfo crashInfo, String version) {
        String jsonString = "";
        JSONObject wrapperObject = null;
        if(version2JsonStr.containsKey(version)){
            wrapperObject = version2JsonStr.get(version);
        }else {
            MyConfig.getInstance().setExceptionFilePath("Files\\android" + version + "\\exceptionInfo\\");
            String fn = MyConfig.getInstance().getExceptionFilePath() + "summary" + File.separator + "exception.json";
            jsonString = FileUtils.readJsonFile(fn);
            wrapperObject = (JSONObject) JSONObject.parse(jsonString);
            version2JsonStr.put(version,wrapperObject);
        }

        if(wrapperObject==null) return "noFile";
        JSONArray methods = wrapperObject.getJSONArray("exceptions");//构建JSONArray数组
        for (int i = 0 ; i < methods.size();i++){
            JSONObject jsonObject = (JSONObject)methods.get(i);
            ExceptionInfo exceptionInfo = new ExceptionInfo();
            exceptionInfo.setSootMethodName(jsonObject.getString("method"));
            if(crashInfo.getSignaler().equals(exceptionInfo.getSootMethodName())){
                exceptionInfo.setExceptionMsg(jsonObject.getString("message"));
                if (exceptionInfo.getExceptionMsg() == null) continue;
                Pattern p = Pattern.compile(exceptionInfo.getExceptionMsg());
                Matcher m = p.matcher(crashInfo.getMsg());
                if (exceptionInfo.getExceptionMsg().equals(crashInfo.getMsg()) || m.matches()) {
                    crashInfo.setExceptionInfo(exceptionInfo);
                    if(exceptionInfo!=null && jsonObject.getString("relatedVarType")!=null) {
                        return jsonObject.getString("relatedVarType");
                    }
                }
            }
        }
        return "unknown";
    }



    /**
     * readCrashInfo from CrashInfoFile
     */
    private void readAllCrashInfo() {
        String fn = MyConfig.getInstance().getCrashInfoFilePath();
        System.out.println("readCrashInfo::"+fn);
        String jsonString = FileUtils.readJsonFile(fn);
        JSONArray jsonArray = JSONArray.parseArray(jsonString);
        for (int i = 0 ; i < jsonArray.size();i++){
            JSONObject jsonObject = (JSONObject)jsonArray.get(i);
            CrashInfo crashInfo = new CrashInfo();
            crashInfoList.add(crashInfo);
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
        }
    }
}
