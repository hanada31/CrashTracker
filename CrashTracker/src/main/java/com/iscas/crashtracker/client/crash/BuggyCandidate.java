package com.iscas.crashtracker.client.crash;

import com.iscas.crashtracker.utils.PrintUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BuggyCandidate {
    private String candi;
    private List<String> reasons = new ArrayList<>();
    private List<List<String>> traces = new ArrayList<List<String>>();
    private final int score;
    private Set<String> reasonTrace = new HashSet<String>();

    public BuggyCandidate(String candi, int score){
        this.candi = candi;
        this.score = score;
    }

    public String getCandi() {
        return candi;
    }

    public List<String> getReason() {
        return reasons;
    }

    public void addReasonTrace(String reason, List<String> trace) {
        if(reasonTrace.contains(reason+PrintUtils.printList(trace)))
            return;
        reasonTrace.add(reason+PrintUtils.printList(trace));
        reasons.add(reason);
        traces.add(trace);
    }
    public List<List<String>> getTrace() {
        return traces;
    }


    public int getScore() {
        return score;
    }
}
