package com.iscas.crashtracker.client.dataAnalysis;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.iscas.crashtracker.base.MyConfig;
import com.iscas.crashtracker.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * @Author hanada
 * @Date 2023/12/28 14:10
 * @Version 1.0
 */
public class ReasonTypeStatistic {
    Map<String, Set<String>> reasonType2Apk = new HashMap<>();

    public void analyze() {
        readReasonFiles();
        write2JsonFile();
    }

    private void readReasonFiles() {
        String folder = MyConfig.getInstance().getResultFolder() + "output" + File.separator;
        String[] fns = {};
        File dir = new File(folder);
        if (dir.isDirectory()) {
            fns = dir.list();
        }
        for(String fn : fns) {
            List<String> lines = FileUtils.getListFromFile(folder+ fn +File.separator+ fn+".json");
            for(String line : lines){
                if(line.contains("Bug Type")){
                    String type = line.split(":")[1].replace("\"","").strip();
                    reasonType2Apk.putIfAbsent(type, new HashSet<>());
                    reasonType2Apk.get(type).add(fn);
                }
            }
        }
    }

    public void write2JsonFile() {
        String folder = MyConfig.getInstance().getResultFolder() + "outputAnalysis" + File.separator;
        FileUtils.createFolder(folder);
        File file = new File(folder+ "ReasonTypeToApk.json");
        try {
            file.createNewFile();
            PrintWriter printWriter = new PrintWriter(file);
            String jsonString = JSON.toJSONString(reasonType2Apk, SerializerFeature.PrettyFormat,
                    SerializerFeature.SortField, SerializerFeature.DisableCircularReferenceDetect);
            printWriter.write(jsonString);
            printWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
