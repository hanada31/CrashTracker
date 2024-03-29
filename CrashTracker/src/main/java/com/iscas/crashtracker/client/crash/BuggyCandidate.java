package com.iscas.crashtracker.client.crash;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.annotation.JSONField;
import com.iscas.crashtracker.utils.SootUtils;
import soot.SootMethod;

import java.util.HashSet;
import java.util.Set;

public class BuggyCandidate {
    @JSONField(name = "Candidate Name")
    private String candidateName;
    @JSONField(name = "Candidate Signature")
    private String candidateSig;
    @JSONField(name = "Candidate Score")
    private final int candidateScore;
    @JSONField(name = "Reasons")
    private JSONArray reasons = new JSONArray();

    private Set<String> reasonTrace = new HashSet<String>();

    public int getcandidateScore() {
        return candidateScore;
    }

    public String getCandidateName() {
        return candidateName;
    }

    public JSONArray getReasons() {
        return reasons;
    }

    public BuggyCandidate(String candi, String candidateSig, int score){
        this.candidateName = candi;
        this.candidateSig = candidateSig;
        this.candidateScore = score;
    }

    public void addReasonTrace(JSONObject reason) {
//
        reasonTrace.add(reason.toJSONString());
        reasons.add(reason);
    }

    public String getCandidateSig() {
        return SootUtils.getSignatureBySimpleName(candidateSig);
    }

}
