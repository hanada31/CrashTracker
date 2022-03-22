package main.java.client.crash;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import main.java.Analyzer;
import main.java.MyConfig;
import main.java.analyze.utils.output.FileUtils;
import main.java.client.exception.ExceptionInfo;
import main.java.client.exception.RelatedMethod;
import main.java.client.statistic.model.StatisticResult;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author hanada
 * @Date 2022/3/22 20:20
 * @Version 1.0
 */
public class CrashAnalysis extends Analyzer {
    List<CrashInfo> crashInfoList;
    List<ExceptionInfo> exceptionInfoList;

    public CrashAnalysis(StatisticResult result) {
        crashInfoList = new ArrayList<>();
        exceptionInfoList = new ArrayList<>();
    }

    @Override
    public void analyze() {
        readCrashInfo();
        readExceptionSummary();
        analyzeCrashInApp();
    }

    private void analyzeCrashInApp() {
    }

    private void readExceptionSummary() {
        String fn = MyConfig.getInstance().getExceptionSummaryFilePath();
        String jsonString = FileUtils.readJsonFile(fn);
        JSONObject warpperObject = (JSONObject) JSONObject.parse(jsonString);
        JSONArray methods = warpperObject.getJSONArray("methodMap");//构建JSONArray数组
        for (int i = 0 ; i < methods.size();i++){
            JSONObject jsonObject = (JSONObject)methods.get(i);
            ExceptionInfo exceptionInfo = new ExceptionInfo();

            exceptionInfo.setExceptionType(jsonObject.getString("type"));
            exceptionInfo.setExceptionMsg(jsonObject.getString("message"));
            exceptionInfo.setModifier(jsonObject.getString("modifier"));

//            exceptionInfo.setSootMethod(jsonObject.getString("method"));
//            exceptionInfo.setTracedUnits(jsonObject.getString("real"));
////            exceptionInfo.setUnit(jsonObject.getString("unit"));
//            exceptionInfo.setRelatedFieldValues(jsonObject.getString("fieldValues"));
//            exceptionInfo.setRelatedParamValues(jsonObject.getString("paramValues"));
//            exceptionInfo.setCaughtedValues(jsonObject.getString("caughtValues"));
//            exceptionInfo.setRelatedMethodsInSameClass(jsonObject.getString("relatedMethodsInSameClass"));
//            exceptionInfo.setRelatedMethodsInDiffClass(jsonObject.getString("relatedMethodsInDiffClass"));
//            exceptionInfo.setConditions(jsonObject.getString("conditions"));


            System.out.println(exceptionInfo.toString());

            exceptionInfoList.add(exceptionInfo);
        }
    }

    private void readCrashInfo() {
        String fn = MyConfig.getInstance().getCrashInfoFilePath();
        String jsonString = FileUtils.readJsonFile(fn);
        JSONArray jsonArray = JSONArray.parseArray(jsonString);
        for (int i = 0 ; i < jsonArray.size();i++){
            JSONObject jsonObject = (JSONObject)jsonArray.get(i);
            CrashInfo crashInfo = new CrashInfo();
            crashInfo.setReal(jsonObject.getString("real"));
            crashInfo.setException(jsonObject.getString("exception"));
            crashInfo.setTrace(jsonObject.getString("trace"));
            crashInfo.setBuggyApi(jsonObject.getString("buggyApi"));
            crashInfo.setMsg(jsonObject.getString("msg"));
            crashInfo.setRealCate(jsonObject.getString("realCate"));
            crashInfo.setCategory(jsonObject.getString("category"));
            crashInfo.setIdentifier(jsonObject.getString("identifier"));
            crashInfo.setReason(jsonObject.getString("reason"));
            crashInfoList.add(crashInfo);
        }
    }
}
