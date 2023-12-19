package com.iscas.crashtracker.client.crash;

import com.alibaba.fastjson.JSONObject;
import com.iscas.crashtracker.client.exception.RelatedMethod;
import com.iscas.crashtracker.utils.PrintUtils;
import soot.SootClass;
import soot.SootField;
import soot.toolkits.scalar.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author hanada
 * @Date 2023/12/14 17:01
 * @Version 1.0
 */
public class GenerateReason {

    // test with apk com.streema.podcast-22.apk
    public static void generateReasonForNotOverride(JSONObject reason, SootClass buggyClass, String buggyCandidate, CrashInfo crashInfo, String extendRelationStr) {
        JSONObject explanation = new JSONObject(true);
        reason.put("Crash Message", crashInfo.getMsg());
        if(crashInfo.getExceptionInfo()!=null)
            reason.put("Exception Message", crashInfo.getExceptionInfo().getExceptionMsg());

        reason.put("Extra Explanation",explanation);
        explanation.put("Bug Type", "without keyCond 1");

        explanation.put("M_frame Info", "when signaler "+ crashInfo.getSignaler()+ " is invoked, an unconditional exception is thrown out");
        explanation.put("M_frame Unconditional Exception Method", crashInfo.getSignaler());

        explanation.put("M_app Info", "in the application code, class "+ buggyClass + " extends "+crashInfo.getClassName()+", but the signaler is not override");
        explanation.put("M_app Extend Relationship", extendRelationStr);

        explanation.put("M_app Info by LLM", "how is method "+buggyCandidate+ "be invoked before the crashAPI "+ crashInfo.getCrashAPI()+" invocation?");
        generateReasonForNonCodeTag(explanation, buggyCandidate, crashInfo);
    }

    // test with apk com.streema.podcast-22.apk
    public static void generateReasonForNotOverrideInStackTrace(JSONObject reason, String buggyCandidate, CrashInfo crashInfo) {
        JSONObject explanation = new JSONObject(true);
        reason.put("Crash Message", crashInfo.getMsg());
        if(crashInfo.getExceptionInfo()!=null)
            reason.put("Exception Message", crashInfo.getExceptionInfo().getExceptionMsg());

        reason.put("Extra Explanation",explanation);
        explanation.put("Bug Type", "without keyCond 2");

        explanation.put("M_frame Info", "when signaler "+ crashInfo.getSignaler()+ " is invoked, an unconditional exception is thrown out");

        explanation.put("M_app Info", "in the application code, there are some classes extend class "+crashInfo.getClassName()+" but not override its method "+ crashInfo.getSignaler()+ ". It cause an unconditional exception thrown out. Method "+ buggyCandidate+ " exists in the crash stack trace.");

        explanation.put("M_app Info by LLM", "what is the relationship between method "+buggyCandidate+ " and classes who not override "+ crashInfo.getCrashAPI()+" (if it has)?");
        generateReasonForNonCodeTag(explanation, buggyCandidate, crashInfo);
    }


    // test with apk cgeo.geocaching-4450.apk
    public static void generateReasonForKeyAPIRelated(JSONObject reason, String buggyCandidate, RelatedMethod relatedMethod, CrashInfo crashInfo, List<?> fieldString, String keyAPIInvokingStr) {
        String keyAPI = relatedMethod.getMethod();
        JSONObject explanation = new JSONObject(true);
        reason.put("Crash Message", crashInfo.getMsg());
        if(crashInfo.getExceptionInfo()!=null)
            reason.put("Exception Message", crashInfo.getExceptionInfo().getExceptionMsg());

        reason.put("Extra Explanation",explanation);
        explanation.put("Bug Type", "with keyAPI 1");

        explanation.put("M_frame Info", "method "+buggyCandidate+" call keyAPI "+ keyAPI +" in Android framework code, " +
                "which influence the value of field (" + PrintUtils.printList(fieldString)+")");
        explanation.put("M_frame Trace Info", PrintUtils.printList(relatedMethod.getTrace()));

        explanation.put("M_frame Info by LLM", "what is the constraints of field ("+ PrintUtils.printList(fieldString)+ ") in signaler "+ crashInfo.getSignaler()+
                "? how does "+ keyAPI +" modify that field?");
       
        explanation.put("M_app Info",
                "the invocation of " +keyAPI +" in "+buggyCandidate+" conflicts with the invocation of "+crashInfo.getCrashAPI()+ " in " +crashInfo.getCrashMethod());
        explanation.put("M_app Trace Info", keyAPIInvokingStr);

        explanation.put("M_app Info by LLM",  "how is keyAPI invoked on unexpected path lead to the invocation conflict?");
        generateReasonForNonCodeTag(explanation, buggyCandidate, crashInfo);
    }

    // test with apk cgeo.geocaching-4450.apk
    public static void generateReasonForKeyAPIInStackTrace(JSONObject reason, String buggyCandidate, CrashInfo crashInfo) {
        JSONObject explanation = new JSONObject(true);
        reason.put("Crash Message", crashInfo.getMsg());
        if(crashInfo.getExceptionInfo()!=null)
            reason.put("Exception Message", crashInfo.getExceptionInfo().getExceptionMsg());

        reason.put("Extra Explanation",explanation);
        explanation.put("Bug Type", "with keyAPI 2");

        explanation.put("M_app Info",
                        "the invocation of "+crashInfo.getCrashAPI()+ " in " +crashInfo.getCrashMethod() +" may conflict with other framework API invocation. " +
                        "Method "+ buggyCandidate+ " exists in the crash stack trace.");
//        explanation.put("M_app Trace Info", "");

        explanation.put("M_app Info by LLM",  "what is the relationship with it and the crash triggering statement "+crashInfo.getCrashAPI() +" (it it has)?");
        generateReasonForNonCodeTag(explanation, buggyCandidate, crashInfo);
    }

    // test with apk cgeo.geocaching-3541.apk
    public static void generateReasonForKeyVariableRelatedTerminate(JSONObject reason, String buggyCandidate, List faultInducingParas, CrashInfo crashInfo) {
        JSONObject explanation = new JSONObject(true);
        reason.put("Crash Message", crashInfo.getMsg());
        if(crashInfo.getExceptionInfo()!=null)
            reason.put("Exception Message", crashInfo.getExceptionInfo().getExceptionMsg());

        reason.put("Extra Explanation",explanation);
        explanation.put("Bug Type", "with keyVar 1");

        if(faultInducingParas.size()==0) faultInducingParas.add(-999);
        explanation.put("M_frame Info", "value of the "+ PrintUtils.printList(faultInducingParas) + " parameter (start from 0) in API "+crashInfo.getCrashAPI()+" may be wrong and trigger crash.");
        explanation.put("M_frame Trace Info", printTrackingList(crashInfo.getFrameworkParamIdTrackingList()));
        explanation.put("M_frame Info by LLM", "what are the data constraints of the buggy parameter value?");

        explanation.put("M_app Info", "the buggy parameter value is passed through call chain from "+buggyCandidate+ " to " + crashInfo.crashMethod+", this is the terminate method.");
        explanation.put("M_app Trace Info", printTrackingList(crashInfo.getAppParamIdTrackingList()));
        explanation.put("M_app Info by LLM",  "how is unexpected keyVar value assigned in the buggy candidate "+buggyCandidate);
        generateReasonForNonCodeTag(explanation, buggyCandidate, crashInfo);
    }

    // test with apk cgeo.geocaching-3541.apk
    public static void generateReasonForKeyVariableRelatedNotTerminate(JSONObject reason, String buggyCandidate, List faultInducingParas, CrashInfo crashInfo) {
        JSONObject explanation = new JSONObject(true);
        reason.put("Crash Message", crashInfo.getMsg());
        if(crashInfo.getExceptionInfo()!=null)
            reason.put("Exception Message", crashInfo.getExceptionInfo().getExceptionMsg());

        reason.put("Extra Explanation",explanation);
        explanation.put("Bug Type", "with keyVar 2");

        if(faultInducingParas.size()==0) faultInducingParas.add(-999);
        explanation.put("M_frame Info", "value of the "+ PrintUtils.printList(faultInducingParas) + " parameter (start from 0) in API "+crashInfo.getCrashAPI()+" may be wrong and trigger crash.");
        explanation.put("M_frame Info by LLM", "what are the data constraints of the buggy parameter value?");
        explanation.put("M_frame Trace Info", printTrackingList(crashInfo.getFrameworkParamIdTrackingList()));

        explanation.put("M_app Info", "the buggy parameter value is passed through call chain to " + crashInfo.crashMethod+", this is not the terminate method." );
        explanation.put("M_app Trace Info", printTrackingList(crashInfo.getAppParamIdTrackingList()));

        explanation.put("M_app Info by LLM",  "how is unexpected keyVar value assigned in the buggy candidate "+buggyCandidate +
                ", and then influence the crashMethod "+crashInfo.getCrashMethod()+"?");
        generateReasonForNonCodeTag(explanation, buggyCandidate, crashInfo);
    }

    // test with apk cgeo.geocaching-3541.apk
    public static void generateReasonForKeyVariableRelatedPreviousCall(JSONObject reason, String buggyCandidate, List faultInducingParas, CrashInfo crashInfo) {
        JSONObject explanation = new JSONObject(true);
        reason.put("Crash Message", crashInfo.getMsg());
        if(crashInfo.getExceptionInfo()!=null)
            reason.put("Exception Message", crashInfo.getExceptionInfo().getExceptionMsg());

        reason.put("Extra Explanation",explanation);
        explanation.put("Bug Type", "with keyVar 3");

        if(faultInducingParas.size()==0) faultInducingParas.add(-999);
        explanation.put("M_frame Info", "value of the "+ PrintUtils.printList(faultInducingParas) + " parameter (start from 0) in API "+crashInfo.getCrashAPI()+" may be wrong and trigger crash");
//        explanation.put("M_frame Trace Info", crashInfo.getCrashAPI()+" @ "+ PrintUtils.printList(faultInducingParas));
        explanation.put("M_frame Trace Info", printTrackingList(crashInfo.getFrameworkParamIdTrackingList()));
        explanation.put("M_frame Info by LLM", "what are the data constraints of the buggy parameter value?");

        explanation.put("M_app Info", "the buggy parameter value is data related to the buggy candidate "+buggyCandidate+".");
        explanation.put("M_app Trace Info", printTrackingList(crashInfo.getAppParamIdTrackingList()));
        explanation.put("M_app Info by LLM",  "how is unexpected keyVar value is modified in "+buggyCandidate +
                ", and then influence the crashMethod "+crashInfo.getCrashMethod()+"?");
        generateReasonForNonCodeTag(explanation, buggyCandidate, crashInfo);
    }

    // test with apk be.thomashermine.prochainbus-96.apk
    public static void generateReasonForKeyVariableRelatedModifySameField(JSONObject reason, String buggyCandidate, int faultInducingParaId, SootField field, CrashInfo crashInfo) {
        JSONObject explanation = new JSONObject(true);
        reason.put("Crash Message", crashInfo.getMsg());
        if(crashInfo.getExceptionInfo()!=null)
            reason.put("Exception Message", crashInfo.getExceptionInfo().getExceptionMsg());

        reason.put("Extra Explanation",explanation);
        explanation.put("Bug Type", "with keyVar 4");

        explanation.put("M_frame Info", "value of the "+ faultInducingParaId + " parameter (start from 0) in API "+crashInfo.getCrashAPI()+" may be wrong and trigger crash.");
        explanation.put("M_frame Info by LLM", "what are the data constraints of the buggy parameter value?");
        explanation.put("M_frame Trace Info", printTrackingList(crashInfo.getFrameworkParamIdTrackingList()));

        explanation.put("M_app Info", "method "+buggyCandidate +" modify the field variable "+field.toString()+", which may influence the buggy parameter value.");
//        explanation.put("M_app Trace Info", buggyCandidate +" @ "+field);
        explanation.put("M_app Trace Info", printTrackingList(crashInfo.getAppParamIdTrackingList()));
        explanation.put("M_app Info by LLM",  "how can "+buggyCandidate +" influence the the buggy parameter value in crashMethod "+crashInfo.getCrashMethod()+"?");
        generateReasonForNonCodeTag(explanation, buggyCandidate, crashInfo);
    }


    public static void generateReasonForExecutedMethodNotInTrace(JSONObject reason, String buggyCandidate, CrashInfo crashInfo) {
        JSONObject explanation = new JSONObject(true);
        reason.put("Crash Message", crashInfo.getMsg());
        if(crashInfo.getExceptionInfo()!=null)
            reason.put("Exception Message", crashInfo.getExceptionInfo().getExceptionMsg());

        reason.put("Extra Explanation",explanation);
        explanation.put("Bug Type", "other");

        explanation.put("M_app Info", "method "+buggyCandidate+" is not in the crash stack, but it has been executed before crash.");
        explanation.put("M_app Info by LLM",  "what is the relationship between "+buggyCandidate+" and the crash triggering statement "+crashInfo.getCrashAPI() +" (if it has)?");
        generateReasonForNonCodeTag(explanation, buggyCandidate, crashInfo);
    }

    public static void generateReasonForNonCodeTag(JSONObject explanation, String buggyCandidate, CrashInfo crashInfo) {
        if(crashInfo.getNoneCodeLabel().size()>0){
            explanation.put("Non-Code Info",  "this crash may have relationship with non-code reasons, e.g., " + PrintUtils.printList(crashInfo.getNoneCodeLabel())+".");
            explanation.put("Non-Code Info by LLM",  "what is the relationship between between "+buggyCandidate+" and "+PrintUtils.printList(crashInfo.getNoneCodeLabel())+ " (if it has)?");
        }
    }

    public static String printTrackingList(List<Pair<String, List>> paramIdTrackingList) {
        if (paramIdTrackingList == null)
            return "";
        String split = "， ";
        String res = "";
        for (Pair<String, List> pair : paramIdTrackingList) {
            System.out.println(pair.getO1());
            System.out.println(pair.getO2());
            if(pair!=null) res += pair.getO1() + " @ " +pair.getO2() + split;
        }
        if (res.length() > 0)
            res = res.substring(0, res.length() - split.length());
        return res;
    }
}
