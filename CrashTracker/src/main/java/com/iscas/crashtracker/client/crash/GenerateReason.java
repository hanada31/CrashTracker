package com.iscas.crashtracker.client.crash;

import com.alibaba.fastjson.JSONObject;
import com.iscas.crashtracker.client.exception.RelatedMethod;
import com.iscas.crashtracker.utils.PrintUtils;
import com.iscas.crashtracker.utils.SootUtils;
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
    public static void generateReasonForNotOverride(JSONObject reason, SootClass buggyClass, String buggyCandidate, CrashInfo crashInfo, List<String> extendRelationStr) {
        reason.put("Explanation Type", "Not Override Method 1");
        reason.put("M_frame Unconditional Exception Method", crashInfo.getSignaler());
        reason.put("M_app NotOverride Class", buggyClass.toString());
        reason.put("M_app NotOverride Class Extend M_frame Class", crashInfo.getClassName());
        reason.put("M_app Extend Relationship", extendRelationStr);
        reason.put("Explanation Info", "When signaler "+ crashInfo.getSignaler()+ " is invoked, an unconditional exception is thrown out. "+
                "In the application code, class "+ buggyClass + " extends "+crashInfo.getClassName()+", but the signaler is not override.");

//        reason.put("M_frame Info", "when signaler "+ crashInfo.getSignaler()+ " is invoked, an unconditional exception is thrown out");
//        reason.put("M_frame Unconditional Exception Method", crashInfo.getSignaler());

//        reason.put("M_app Info", "in the application code, class "+ buggyClass + " extends "+crashInfo.getClassName()+", but the signaler is not override");
//        reason.put("M_app Exte nd Relationship", extendRelationStr);
//        reason.put("M_app Info by LLM", "how is method "+buggyCandidate+ "be invoked before the crashAPI "+ crashInfo.getCrashAPI()+" invocation?");

        generateReasonForNonCodeTag(reason, buggyCandidate, crashInfo);
    }

    // test with apk com.streema.podcast-22.apk
    public static void generateReasonForNotOverrideInStackTrace(JSONObject reason, String buggyCandidate, CrashInfo crashInfo) {
        reason.put("Explanation Type", "Not Override Method 2 (Executed)");
        reason.put("M_app Trace to Crash API", getMethodListBetween(crashInfo.getTrace(), buggyCandidate, crashInfo.crashAPI) );
        reason.put("Explanation Info", "When signaler "+ crashInfo.getSignaler()+ " is invoked, an unconditional exception is thrown out. "+
                "In the application code, there are some classes extend class "+crashInfo.getClassName()+" but not override its method "+ crashInfo.getSignaler()+
                        ". It cause an unconditional exception thrown out. Method "+ buggyCandidate+ " exists in the crash stack trace.");

//        reason.put("M_frame Info", "when signaler "+ crashInfo.getSignaler()+ " is invoked, an unconditional exception is thrown out");
//        reason.put("M_app Info", "in the application code, there are some classes extend class "+crashInfo.getClassName()+" but not override its method "+ crashInfo.getSignaler()+ ". It cause an unconditional exception thrown out. Method "+ buggyCandidate+ " exists in the crash stack trace.");
//        reason.put("M_app Info by LLM", "what is the relationship between method "+buggyCandidate+ " and classes who not override "+ crashInfo.getCrashAPI()+" (if it has)?");
        generateReasonForNonCodeTag(reason, buggyCandidate, crashInfo);
    }

    // test with apk cgeo.geocaching-3541.apk
    public static void generateReasonForKeyVariableRelatedTerminate(JSONObject reason, String buggyCandidate, List faultInducingParas, CrashInfo crashInfo) {
        reason.put("Explanation Type", "Key Variable Related 1");
        List<String> res1 = printTrackingList(crashInfo.getFrameworkParamIdTrackingList());
        List<String> res2 = printTrackingList(crashInfo.getAppParamIdTrackingList());
        reason.put("KeyVar PassChain Info", mergeList(res1,res2));
        reason.put("M_app Is Terminate?", true);
        reason.put("M_app Trace to Crash API", getMethodListBetween(crashInfo.getTrace(), buggyCandidate, crashInfo.crashAPI) );

        reason.put("Explanation Info","The buggy parameter value is passed through call chain from the terminate method "+buggyCandidate+ " to " + crashInfo.crashAPI);

//        ---------------------------
//        reason.put("M_app Info", "the buggy parameter value is passed through call chain to " + crashInfo.crashMethod );
//        if(faultInducingParas.size()==0) faultInducingParas.add(-999);
//        reason.put("M_frame Info", "value of the "+ PrintUtils.printList(faultInducingParas) + " parameter (start from 0) in API "+crashInfo.getCrashAPI()+" may be wrong and trigger crash.");
////        reason.put("M_frame Trace Info", printTrackingList(crashInfo.getFrameworkParamIdTrackingList()));
//        reason.put("M_frame Info by LLM", "what are the data constraints of the buggy parameter value?");
//        reason.put("M_app Info", "the buggy parameter value is passed through call chain from "+buggyCandidate+ " to " + crashInfo.crashAPI+", this is the terminate method.");
////        reason.put("M_app Trace Info", printTrackingList(crashInfo.getAppParamIdTrackingList()));
//        reason.put("M_app Info by LLM",  "how is unexpected keyVar value assigned in the buggy candidate "+buggyCandidate);
        generateReasonForNonCodeTag(reason, buggyCandidate, crashInfo);
    }

    // test with apk cgeo.geocaching-3541.apk
    public static void generateReasonForKeyVariableRelatedNotTerminate(JSONObject reason, String buggyCandidate, List faultInducingParas, CrashInfo crashInfo) {
        reason.put("Explanation Type", "Key Variable Related 2");
        List<String> res1 = printTrackingList(crashInfo.getFrameworkParamIdTrackingList());
        List<String> res2 = printTrackingList(crashInfo.getAppParamIdTrackingList());
        reason.put("KeyVar PassChain Info", mergeList(res1,res2));
        reason.put("M_app Is Terminate?", false);
        reason.put("M_app Trace to Crash API", getMethodListBetween(crashInfo.getTrace(), buggyCandidate, crashInfo.crashAPI) );

        reason.put("Explanation Info","The buggy parameter value is passed through call chain from the non-terminate method "+buggyCandidate+ " to " + crashInfo.crashAPI);


//        ---------------------------
//        if(faultInducingParas.size()==0) faultInducingParas.add(-999);
//        reason.put("M_frame Info", "value of the "+ PrintUtils.printList(faultInducingParas) + " parameter (start from 0) in API "+crashInfo.getCrashAPI()+" may be wrong and trigger crash.");
//        reason.put("M_frame Info by LLM", "what are the data constraints of the buggy parameter value?");
////        reason.put("M_frame Trace Info", printTrackingList(crashInfo.getFrameworkParamIdTrackingList()));
//        reason.put("M_app Info", "the buggy parameter value is passed through call chain to " + crashInfo.crashAPI+", this is not the terminate method." );
//        reason.put("M_app Trace Info", printTrackingList(crashInfo.getAppParamIdTrackingList()));
//        reason.put("M_app Info by LLM",  "how is unexpected keyVar value assigned in the buggy candidate "+buggyCandidate +
//                ", and then influence the crashMethod "+crashInfo.getCrashMethod()+"?");
        generateReasonForNonCodeTag(reason, buggyCandidate, crashInfo);
    }

    // test with apk cgeo.geocaching-3541.apk
    public static void generateReasonForKeyVariableRelatedPreviousCall(JSONObject reason, String buggyCandidate, List faultInducingParas, CrashInfo crashInfo) {
        reason.put("Explanation Type", "Key Variable Related 3");
        List<String> res1 = printTrackingList(crashInfo.getFrameworkParamIdTrackingList());
        List<String> res2 = printTrackingList(crashInfo.getAppParamIdTrackingList());
        reason.put("KeyVar PassChain Info", mergeList(res1,res2));
        reason.put("M_app Is Terminate?", false);
        reason.put("Explanation Info", "Value of the "+ PrintUtils.printList(faultInducingParas) + " parameter (start from 0) in API "+
                crashInfo.getCrashAPI()+" may be wrong and trigger crash. " +
                "The buggy parameter value is data related to the buggy candidate "+buggyCandidate+".");

//        if(faultInducingParas.size()==0) faultInducingParas.add(-999);
//        reason.put("M_frame Info", "value of the "+ PrintUtils.printList(faultInducingParas) + " parameter (start from 0) in API "+crashInfo.getCrashAPI()+" may be wrong and trigger crash");
//        reason.put("M_frame Trace Info", printTrackingList(crashInfo.getFrameworkParamIdTrackingList()));
//        reason.put("M_frame Info by LLM", "what are the data constraints of the buggy parameter value?");

//        reason.put("M_app Info", "the buggy parameter value is data related to the buggy candidate "+buggyCandidate+".");
//        reason.put("M_app Trace Info", printTrackingList(crashInfo.getAppParamIdTrackingList()));
//        reason.put("M_app Info by LLM",  "how is unexpected keyVar value is modified in "+buggyCandidate +
//                ", and then influence the crashMethod "+crashInfo.getCrashMethod()+"?");
        generateReasonForNonCodeTag(reason, buggyCandidate, crashInfo);
    }

    // test with apk be.thomashermine.prochainbus-96.apk
    public static void generateReasonForKeyVariableRelatedModifySameField(JSONObject reason, String buggyCandidate, int faultInducingParaId, SootField field, CrashInfo crashInfo) {

        reason.put("Explanation Type", "Key Variable Related 4");
        List<String> res1 = printTrackingList(crashInfo.getFrameworkParamIdTrackingList());
        List<String> res2 = printTrackingList(crashInfo.getAppParamIdTrackingList());
        reason.put("KeyVar PassChain Info", mergeList(res1,res2));
        reason.put("M_app Is Terminate?", false);
        reason.put("Explanation Info", "Value of the "+ faultInducingParaId + " parameter (start from 0) in API "+crashInfo.getCrashAPI()+" may be wrong and trigger crash. " +
                "Method "+buggyCandidate +" modify the field variable "+ field.toString()+", which may influence the buggy parameter value.");

//        reason.put("M_frame Info", "value of the "+ faultInducingParaId + " parameter (start from 0) in API "+crashInfo.getCrashAPI()+" may be wrong and trigger crash.");
//        reason.put("M_frame Info by LLM", "what are the data constraints of the buggy parameter value?");
//        reason.put("M_frame Trace Info", printTrackingList(crashInfo.getFrameworkParamIdTrackingList()));
//
//        reason.put("M_app Info", "method "+buggyCandidate +" modify the field variable "+ field.toString()+", which may influence the buggy parameter value.");
//        reason.put("M_app Trace Info", printTrackingList(crashInfo.getAppParamIdTrackingList()));
//        reason.put("M_app Info by LLM",  "how can "+buggyCandidate +" influence the the buggy parameter value in crashMethod "+crashInfo.getCrashMethod()+"?");
        generateReasonForNonCodeTag(reason, buggyCandidate, crashInfo);
    }


    // test with apk cgeo.geocaching-4450.apk
    public static void generateReasonForKeyAPIRelated(JSONObject reason, String buggyCandidate, RelatedMethod relatedMethod,
                                                      CrashInfo crashInfo, List<?> fieldString, List<String> keyAPIInvokingList) {
        reason.put("Explanation Type", "Key API Related 1");
        reason.put("M_frame Triggered KeyAPI", relatedMethod.getMethod());
        reason.put("M_frame Influenced Field", fieldString);
        reason.put("KeyAPI Invocation Info", mergeList(keyAPIInvokingList,relatedMethod.getTrace()));

        reason.put("Explanation Info", "Method "+buggyCandidate+" call keyAPI in Android framework code, " +
                "which influence the value of field (" + PrintUtils.printList(fieldString)+"). " +
                "The invocation of " +relatedMethod.getMethod() +" in  conflicts with the invocation of "+crashInfo.getCrashAPI()+ " in " +crashInfo.getCrashMethod());


//        reason.put("M_frame Trace Info", PrintUtils.printList(relatedMethod.getTrace()));
//        reason.put("M_frame Info by LLM", "what is the constraints of field ("+ PrintUtils.printList(fieldString)+ ") in signaler "+ crashInfo.getSignaler()+
//                "? how does "+ keyAPI +" modify that field?");
//        reason.put("M_app Info",
//                "the invocation of " +keyAPI +" in "+buggyCandidate+" conflicts with the invocation of "+crashInfo.getCrashAPI()+ " in " +crashInfo.getCrashMethod());
//        reason.put("M_app Trace Info", keyAPIInvokingStr);
//        reason.put("M_app Info by LLM",  "how is keyAPI invoked on unexpected path lead to the invocation conflict?");
        generateReasonForNonCodeTag(reason, buggyCandidate, crashInfo);
    }

    // test with apk cgeo.geocaching-4450.apk
    public static void generateReasonForKeyAPIInStackTrace(JSONObject reason, String buggyCandidate, CrashInfo crashInfo) {
        reason.put("Explanation Type", "Key API Related 2 (Executed)");
        reason.put("M_app Trace to Crash API", getMethodListBetween(crashInfo.getTrace(), buggyCandidate, crashInfo.crashAPI) );

        reason.put("Explanation Info",
                "The invocation of "+crashInfo.getCrashAPI()+ " in " +crashInfo.getCrashMethod() +" may conflict with other framework API invocation. " +
                        "Method "+ buggyCandidate+ " exists in the crash stack trace.");


//        reason.put("M_app Trace Info", "");
//        reason.put("M_app Info by LLM",  "what is the relationship with it and the crash triggering statement "+crashInfo.getCrashAPI() +" (it it has)?");
        generateReasonForNonCodeTag(reason, buggyCandidate, crashInfo);
    }


    public static void generateReasonForFrameworkRecallMethod(JSONObject reason, String buggyCandidate, CrashInfo crashInfo, List<String> invokingList) {
        reason.put("Explanation Type", "Framework Recall");
        reason.put("M_app Trace to Crash API", mergeList(invokingList,getMethodListBetween(crashInfo.getTrace(), buggyCandidate, crashInfo.crashAPI)));

        reason.put("Explanation Info", "Method "+buggyCandidate+" is not in the crash stack, it is a recall method invoked by framework method.");

//        reason.put("M_app Info by LLM",  "what is the relationship between "+buggyCandidate+" and the crash triggering statement "+crashInfo.getCrashAPI() +" (if it has)?");
        generateReasonForNonCodeTag(reason, buggyCandidate, crashInfo);
    }

    public static void generateReasonForNonCodeTag(JSONObject reason, String buggyCandidate, CrashInfo crashInfo) {
        if(crashInfo.getNoneCodeLabel().size()>0){
            reason.put("Non-Code Label",  crashInfo.getNoneCodeLabel());
//            reason.put("Non-Code Info",  "this crash may have relationship with non-code reasons, e.g., " + PrintUtils.printList(crashInfo.getNoneCodeLabel())+".");
//            reason.put("Non-Code Info by LLM",  "what is the relationship between between "+buggyCandidate+" and "+PrintUtils.printList(crashInfo.getNoneCodeLabel())+ " (if it has)?");
        }
    }

    private static Object mergeList(List res1, List res2) {
        List<Object> res = new ArrayList<>(res1);
        for(Object str: res2){
            if(!res.contains(str))
                res.add(str);
        }
        return res;
    }
    public static List<String> printTrackingList(List<Pair<String, List>> paramIdTrackingList) {
        List<String> res = new ArrayList<>();
        if (paramIdTrackingList == null)
            return res;
        String split = "， ";
        for (Pair<String, List> pair : paramIdTrackingList) {
            if(pair!=null) 
                res.add(pair.getO1() + " @ " +pair.getO2());
        }
        return res;
    }

    public static List<String> getMethodListBetween(List<String> trace, String start, String end){
        List<String> mtdList = new ArrayList<>();
        boolean flag = false;
        for(int i = trace.size()-1; i>=0; i--){
            String mtd = trace.get(i);
            if(mtd.equals(start)) {
                flag = true;
            }
            if(mtd.equals(end)) {
                flag = false;
            }
            if(flag){
                mtdList.add(SootUtils.getSignatureBySimpleName(mtd));
            }
        }
        mtdList.add(SootUtils.getSignatureBySimpleName(end));
        return mtdList;
    }
}
