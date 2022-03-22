package main.java.client.crash;

import main.java.analyze.utils.output.PrintUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author hanada
 * @Date 2022/3/22 20:16
 * @Version 1.0
 */
public class CrashInfo {
    String real;
    String exception;
    List<String> trace = new ArrayList<>();
    String buggyApi;
    String msg;
    String realCate;
    String category;
    String identifier;
    String reason;

    public String getReal() {
        return real;
    }

    public void setReal(String real) {
        this.real = real;
    }

    public String getException() {
        return exception;
    }

    public void setException(String exception) {
        this.exception = exception;
    }

    public List<String> getTrace() {
        return trace;
    }

    public void setTrace(String trace) {
        trace= trace.replace("\"","");
        trace= trace.replace("[","").replace("]","");
        trace= trace.replace("at ","");
        String ss[] = trace.split(",");
        for(String t: ss){
            this.trace.add(t);
        }
        this.trace.toString();
    }

    public String getBuggyApi() {
        return buggyApi;
    }

    public void setBuggyApi(String buggyApi) {
        this.buggyApi = buggyApi;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getRealCate() {
        return realCate;
    }

    public void setRealCate(String realCate) {
        this.realCate = realCate;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }


    @Override
    public String toString() {
        return "CrashInfo{" +
                "real='" + real + '\'' +
                ", exception='" + exception + '\'' +
                ", trace='" + PrintUtils.printList(trace) + '\'' +
                ", buggyApi='" + buggyApi + '\'' +
                ", msg='" + msg + '\'' +
                ", realCate='" + realCate + '\'' +
                ", category='" + category + '\'' +
                ", identifier='" + identifier + '\'' +
                ", reason='" + reason + '\'' +
                '}';
    }
}
