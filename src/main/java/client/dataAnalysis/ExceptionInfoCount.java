package main.java.client.dataAnalysis;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import main.java.MyConfig;
import main.java.analyze.utils.output.FileUtils;
import main.java.client.crash.CrashInfo;
import main.java.client.exception.ExceptionInfo;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        for (String version : versions) {
            getExceptionWithGivenVersion(version);
        }
//            System.out.println(str);
        FileUtils.writeText2File(MyConfig.getInstance().getResultFolder() +"exceptionInfoCount.txt", "", true);

    }

    /**
     * getExceptionOfCrashInfo from exception.json
     */
    private void getExceptionWithGivenVersion(String version) {
        MyConfig.getInstance().setExceptionFilePath("Files\\android" + version + "\\exceptionInfo\\");
        String fn = MyConfig.getInstance().getExceptionFilePath() + "summary" + File.separator + "exception.json";
        String jsonString = FileUtils.readJsonFile(fn);
        JSONObject wrapperObject = (JSONObject) JSONObject.parse(jsonString);


        if(wrapperObject==null) return;
        JSONArray methods = wrapperObject.getJSONArray("exceptions");//构建JSONArray数组
        for (int i = 0 ; i < methods.size();i++){
            JSONObject jsonObject = (JSONObject)methods.get(i);
            ExceptionInfo exceptionInfo = new ExceptionInfo();
            exceptionInfo.setSootMethodName(jsonObject.getString("method"));
            //TODO
            //count 1
            //count 1
            //count 1
            //count 1
            //count 1

        }
        return ;
    }
}
