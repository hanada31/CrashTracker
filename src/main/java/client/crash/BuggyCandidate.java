package main.java.client.crash;

import java.util.ArrayList;
import java.util.List;

public class BuggyCandidate {
    private final String candi;
    private final String reason;
    private  List trace = new ArrayList<String>();
    private final int score;

    public BuggyCandidate(String candi, int score, String reason, List trace){
        this.candi = candi;
        this.score = score;
        this.reason = reason;
        this.trace = trace;
    }

    public String getCandi() {
        return candi;
    }

    public String getReason() {
        return reason;
    }

    public List getTrace() {
        return trace;
    }

    public int getScore() {
        return score;
    }
}
