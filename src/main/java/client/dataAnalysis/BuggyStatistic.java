package main.java.client.dataAnalysis;

import main.java.utils.FileUtils;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author hanada
 * @Date 2022/7/5 18:28
 * @Version 1.0
 */
public class BuggyStatistic {
    public void analyze() {
        analyzeAllFiles();
    }

    private void analyzeAllFiles() {
        FileUtils.writeText2File( "Files" + File.separator+"BuggyStatistic.txt", "", false);
        String[] fns = {};
        File dir = new File("Files" + File.separator+"buggyRanking");
        if (dir.isDirectory()) {
            fns = dir.list();
        }
        for(String fn : fns){
            List<String> lines = FileUtils.getListFromFile("Files" + File.separator + "buggyRanking" + File.separator + fn);
            StringBuilder sb = new StringBuilder(fn+ "\tR@1\tR@5\tR@10\tR@1\tR@5\tR@10\n");
            analyzeSingleFile(lines, sb);
            FileUtils.writeText2File( "Files" + File.separator+"BuggyStatistic.txt", sb +"\n\n\n", true);
        }


    }

    private void analyzeSingleFile(List<String> lines, StringBuilder sb) {
        Map<String, Integer> cateAMap = initMap();
        Map<String, Integer> cateBMap = initMap();
        Map<String, Integer> cateCMap = initMap();
        Map<String, Integer> cateAllMap = initMap();

        for(String line: lines){
            String[] ss = line.split("\t");
            if(ss.length>5){
                String type = ss[0];
                int rank = Integer.parseInt(ss[4]);
                if(type.equals("A"))
                    addRanking2Type(cateAMap, rank);
                if(type.equals("B"))
                    addRanking2Type(cateBMap, rank);
                if(type.equals("C"))
                    addRanking2Type(cateCMap, rank);
                addRanking2Type(cateAllMap, rank);

            }
        }
        printInfo2SB("all", cateAllMap, sb);
        printInfo2SB("A", cateAMap, sb);
        printInfo2SB("B", cateBMap, sb);
        printInfo2SB("C", cateCMap, sb);

    }

    private void printInfo2SB(String tag, Map<String, Integer> map, StringBuilder sb) {
        sb.append(tag + "\t");
        sb.append(map.get("Recall@1") + "\t");
        sb.append(map.get("Recall@5") + "\t");
        sb.append(map.get("Recall@10") + "\t");

        sb.append(1.0* map.get("Recall@1")/map.get("Recall@all") + "\t");
        sb.append(1.0* map.get("Recall@5")/map.get("Recall@all") + "\t");
        sb.append(1.0* map.get("Recall@10")/map.get("Recall@all") + "\n");

    }

    private void addRanking2Type(Map<String, Integer> map, int rank) {
        if(rank == 1){
            map.put("Recall@1", map.get("Recall@1")+1);
            map.put("Recall@5", map.get("Recall@5")+1);
            map.put("Recall@10", map.get("Recall@10")+1);
        }else if(rank <=5){
            map.put("Recall@5", map.get("Recall@5")+1);
            map.put("Recall@10", map.get("Recall@10")+1);
        }else if(rank <=10){
            map.put("Recall@10", map.get("Recall@10")+1);
        }
        map.put("Recall@all", map.get("Recall@all")+1);
    }

    private Map initMap() {
        Map map = new HashMap();
        map.put("Recall@1", 0);
        map.put("Recall@5", 0);
        map.put("Recall@10", 0);
        map.put("Recall@all", 0);
        return map;
    }
}
