package com.iscas.crashtracker.client.crash;

import com.alibaba.fastjson.JSONObject;
import com.iscas.crashtracker.utils.PrintUtils;
import soot.SootClass;
import soot.SootField;

import java.util.List;

/**
 * @Author hanada
 * @Date 2023/12/14 17:01
 * @Version 1.0
 */
public class GenerateReason {

    // test with apk com.travelzoo.android-90.apk=
    public static void generateReasonForNotOverride(JSONObject reason, SootClass buggyClass, String buggyCandidate, CrashInfo crashInfo) {
        reason.put("Explanation Bug Type", "without keyCond");
        reason.put("Explanation Crash Message", crashInfo.getMsg());
        if(crashInfo.getExceptionInfo()!=null)
            reason.put("Explanation Exception Message", crashInfo.getExceptionInfo().getExceptionMsg());

        reason.put("Explanation M_frame Info", "when signaler "+ crashInfo.getSignaler()+ " is invoked, an unconditional exception is thrown out");
        reason.put("Explanation M_app Info", "in the application code, class "+ buggyClass + " extends "+crashInfo.getClassName()+", but the signaler is not override");
        reason.put("Explanation M_app Info by LLM", "how is method "+buggyCandidate+ "be invoked before the crashAPI "+ crashInfo.getCrashAPI()+" invocation?");
        generateReasonForNonCodeTag(reason, buggyCandidate, crashInfo);
    }

    // test with apk com.travelzoo.android-90.apk
    public static void generateReasonForExecutedMethodNotOverride(JSONObject reason, String buggyCandidate, List buggyClasses, CrashInfo crashInfo) {
        reason.put("Explanation Bug Type", "without keyCond");
        reason.put("Explanation Crash Message", crashInfo.getMsg());
        if(crashInfo.getExceptionInfo()!=null)
            reason.put("Explanation Exception Message", crashInfo.getExceptionInfo().getExceptionMsg());

        reason.put("Explanation M_frame Info", "when signaler "+ crashInfo.getSignaler()+ " is invoked, an unconditional exception is thrown out");
        reason.put("Explanation M_app Info", "in the application code, a set of classes" + PrintUtils.printList(buggyClasses)+" extends "+crashInfo.getClassName()+", but the signaler is not override");
        reason.put("Explanation M_app Info by LLM", "what is the relationship between method "+buggyCandidate+ " and classes who not override "+ crashInfo.getCrashAPI()+" (if it has)?");
        generateReasonForNonCodeTag(reason, buggyCandidate, crashInfo);
    }


    // test with apk cgeo.geocaching-4450.apk
    public static void generateReasonForKeyAPIRelated(JSONObject reason, String buggyCandidate, String keyAPI, CrashInfo crashInfo, List<?> fieldString) {
        reason.put("Explanation Bug Type", "with keyAPI");
        reason.put("Explanation Crash Message", crashInfo.getMsg());
        if(crashInfo.getExceptionInfo()!=null)
            reason.put("Explanation Exception Message", crashInfo.getExceptionInfo().getExceptionMsg());

        reason.put("Explanation M_frame Info", "method "+buggyCandidate+" call keyAPI "+ keyAPI +" in Android framework code, " +
                "which influence the value of field (" + PrintUtils.printList(fieldString)+")");
        reason.put("Explanation M_frame Info by LLM", "what is the constraints of field ("+ PrintUtils.printList(fieldString)+ ") in signaler "+ crashInfo.getSignaler()+
                "? how does "+ keyAPI +" modify that field?");
        reason.put("Explanation M_app Info",
                "the invocation of " +keyAPI +" in "+buggyCandidate+" is conflicts with the invocation of "+crashInfo.getCrashAPI()+ " in " +crashInfo.getCrashMethod());
        reason.put("Explanation M_app Info by LLM",  "how is keyAPI invoked on unexpected path lead to the invocation conflict?");
        generateReasonForNonCodeTag(reason, buggyCandidate, crashInfo);
    }

    // test with apk cgeo.geocaching-4450.apk
    public static void generateReasonForExecutedMethodKeyAPI(JSONObject reason, String buggyCandidate, CrashInfo crashInfo) {
        reason.put("Explanation Bug Type", "with keyVar");
        reason.put("Explanation Crash Message", crashInfo.getMsg());
        if(crashInfo.getExceptionInfo()!=null)
            reason.put("Explanation Exception Message", crashInfo.getExceptionInfo().getExceptionMsg());

        reason.put("Explanation M_app Info",
                "the invocation of "+crashInfo.getCrashAPI()+ " in " +crashInfo.getCrashMethod() +" may be conflicts with other API invocation. " +
                        "Method "+buggyCandidate+" is in the crash stack trace, ");
        reason.put("Explanation M_app Info by LLM",  "what is the relationship with it and the crash triggering statement "+crashInfo.getCrashAPI() +" (it it has)?");
        generateReasonForNonCodeTag(reason, buggyCandidate, crashInfo);
    }

    // test with apk cgeo.geocaching-4275.apk
    public static void generateReasonForKeyVariableRelatedTerminate(JSONObject reason, String buggyCandidate, List faultInducingParas, CrashInfo crashInfo) {
        reason.put("Explanation Bug Type", "with keyAPI");
        reason.put("Explanation Crash Message", crashInfo.getMsg());
        if(crashInfo.getExceptionInfo()!=null)
            reason.put("Explanation Exception Message", crashInfo.getExceptionInfo().getExceptionMsg());
        reason.put("Explanation M_frame Info", "value of the "+ PrintUtils.printList(faultInducingParas) + " parameter (start from 0) in API "+crashInfo.getCrashAPI()+" may be wrong and trigger crash");
        reason.put("Explanation M_frame Info by LLM", "what are the data constraints of the buggy parameter value?");
        reason.put("Explanation M_app Info", "the buggy parameter value is passed through call chain from "+buggyCandidate+ " to " + crashInfo.crashMethod +".");
        reason.put("Explanation M_app Info by LLM",  "how is unexpected keyVar value assigned in the buggy candidate "+buggyCandidate);
        generateReasonForNonCodeTag(reason, buggyCandidate, crashInfo);
    }

    // test with apk cgeo.geocaching-4275.apk
    public static void generateReasonForKeyVariableRelatedNotTerminate(JSONObject reason, String buggyCandidate, List faultInducingParas, CrashInfo crashInfo) {
        reason.put("Explanation Bug Type", "with keyVar");
        reason.put("Explanation Crash Message", crashInfo.getMsg());
        if(crashInfo.getExceptionInfo()!=null)
            reason.put("Explanation Exception Message", crashInfo.getExceptionInfo().getExceptionMsg());
        reason.put("Explanation M_frame Info", "value of the "+ PrintUtils.printList(faultInducingParas) + " parameter (start from 0) in API "+crashInfo.getCrashAPI()+" may be wrong and trigger crash");
        reason.put("Explanation M_frame Info by LLM", "what are the data constraints of the buggy parameter value?");
        reason.put("Explanation M_app Info", "the buggy parameter value is passed through call chain to " + crashInfo.crashMethod +".");
        reason.put("Explanation M_app Info by LLM",  "how is unexpected keyVar value assigned in the buggy candidate "+buggyCandidate +
                ", and then influence the crashMethod "+crashInfo.getCrashMethod()+"?");
        generateReasonForNonCodeTag(reason, buggyCandidate, crashInfo);
    }

    // test with apk cgeo.geocaching-4275.apk
    public static void generateReasonForKeyVariableRelatedPreviousCall(JSONObject reason, String buggyCandidate, List faultInducingParas, CrashInfo crashInfo) {
        reason.put("Explanation Bug Type", "with keyVar");
        reason.put("Explanation Crash Message", crashInfo.getMsg());
        if(crashInfo.getExceptionInfo()!=null)
            reason.put("Explanation Exception Message", crashInfo.getExceptionInfo().getExceptionMsg());
        reason.put("Explanation M_frame Info", "value of the "+ PrintUtils.printList(faultInducingParas) + " parameter (start from 0) in API "+crashInfo.getCrashAPI()+" may be wrong and trigger crash");
        reason.put("Explanation M_frame Info by LLM", "what are the data constraints of the buggy parameter value?");
        reason.put("Explanation M_app Info", "the buggy parameter value is data related to the buggy candidate "+buggyCandidate);
        reason.put("Explanation M_app Info by LLM",  "how is unexpected keyVar value is modified in "+buggyCandidate +
                ", and then influence the crashMethod "+crashInfo.getCrashMethod()+"?");
        generateReasonForNonCodeTag(reason, buggyCandidate, crashInfo);
    }

    // test with apk be.thomashermine.prochainbus-96.apk
    public static void generateReasonForKeyVariableRelatedModifySameField(JSONObject reason, String buggyCandidate, int faultInducingParaId, SootField field, CrashInfo crashInfo) {
        reason.put("Explanation Bug Type", "with keyVar");
        reason.put("Explanation Crash Message", crashInfo.getMsg());
        if(crashInfo.getExceptionInfo()!=null)
            reason.put("Explanation Exception Message", crashInfo.getExceptionInfo().getExceptionMsg());
        reason.put("Explanation M_frame Info", "value of the "+ faultInducingParaId + " parameter (start from 0) in API "+crashInfo.getCrashAPI()+" may be wrong and trigger crash.");
        reason.put("Explanation M_frame Info by LLM", "what are the data constraints of the buggy parameter value?");
        reason.put("Explanation M_app Info", "method "+buggyCandidate +" modify the field variable "+field.toString()+", which may influence the buggy parameter value");
        reason.put("Explanation M_app Info by LLM",  "how can "+buggyCandidate +" influence the the buggy parameter value in crashMethod "+crashInfo.getCrashMethod()+"?");
        generateReasonForNonCodeTag(reason, buggyCandidate, crashInfo);
    }


    public static void generateReasonForExecutedMethodNotInTrace(JSONObject reason, String buggyCandidate, CrashInfo crashInfo) {
        reason.put("Explanation Bug Type", "other");
        reason.put("Explanation Crash Message", crashInfo.getMsg());
        if(crashInfo.getExceptionInfo()!=null)
            reason.put("Explanation Exception Message", crashInfo.getExceptionInfo().getExceptionMsg());

        reason.put("Explanation M_app Info", "method "+buggyCandidate+" is not in the crash stack, but it has been executed before crash");
        reason.put("Explanation M_app Info by LLM",  "what is the relationship between "+buggyCandidate+" and the crash triggering statement "+crashInfo.getCrashAPI() +" (if it has)?");
        generateReasonForNonCodeTag(reason, buggyCandidate, crashInfo);
    }

    public static void generateReasonForNonCodeTag(JSONObject reason, String buggyCandidate, CrashInfo crashInfo) {
        if(crashInfo.getNoneCodeLabel().size()>0){
            reason.put("Explanation Non-Code Info",  "this crash may have relationship with non-code reasons, e.g., " + PrintUtils.printList(crashInfo.getNoneCodeLabel()));
            reason.put("Explanation Non-Code Info by LLM",  "what is the relationship between between "+buggyCandidate+" and "+PrintUtils.printList(crashInfo.getNoneCodeLabel())+ " (if it has)?");
        }
    }
}
