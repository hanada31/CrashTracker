package main.java.client.dataAnalysis;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import main.java.base.MyConfig;
import main.java.client.crash.CrashInfo;
import main.java.utils.FileUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author hanada
 * @Date 2022/6/24 10:22
 * @Version 1.0
 */
public class CrashInfoCounter {
    List<CrashInfo> crashInfoList = new ArrayList<>();

    public void analyze() {
        readAllCrashInfo();
        System.out.println("readCrashInfo Finish...");
        writeCrashInfo();
        System.out.println("writeCrashInfo Finish...");

    }

    private void writeCrashInfo() {
        FileUtils.writeText2File(MyConfig.getInstance().getResultFolder() +"crashLength.txt", "", false);
        FileUtils.writeText2File(MyConfig.getInstance().getResultFolder() +"crashLength.txt", "app\ttraceSize\tcrashMethodListSize\n", true);
        FileUtils.writeText2File(MyConfig.getInstance().getResultFolder() +"C-tag.txt", "", false);

        for(CrashInfo crashInfo: crashInfoList) {
            String str= crashInfo.getId()+"\t"+ crashInfo.getTrace().size()+"\t" +crashInfo.getCrashMethodList().size();
            FileUtils.writeText2File(MyConfig.getInstance().getResultFolder() +"crashLength.txt", str+"\n", true);
            if(crashInfo.getNoneCodeLabel().get(0)!=null && crashInfo.getNoneCodeLabel().get(0)!=null) {
                str = crashInfo.getRealCate() + "\t" + crashInfo.getId() + "\t" + crashInfo.getNoneCodeLabel().get(0);
                FileUtils.writeText2File(MyConfig.getInstance().getResultFolder() + "C-tag.txt", str + "\n", true);
            }else{
                str = crashInfo.getRealCate() + "\t" + crashInfo.getId() + "\t";
                FileUtils.writeText2File(MyConfig.getInstance().getResultFolder() + "C-tag.txt", str + "\n", true);
            }
        }
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
            crashInfo.addNoneCodeLabel(jsonObject.getString("tag"));
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
