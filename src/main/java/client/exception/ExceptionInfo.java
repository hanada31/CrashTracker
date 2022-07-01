package main.java.client.exception;

import main.java.analyze.utils.SootUtils;
import main.java.analyze.utils.output.FileUtils;
import main.java.analyze.utils.output.PrintUtils;
import soot.*;
import soot.jimple.StringConstant;

import java.util.*;

/**
 * @Author hanada
 * @Date 2022/3/15 10:47
 * @Version 1.0
 */
public class ExceptionInfo {
    private String exceptionType;
    private String exceptionMsg;
    private List<Value> relatedParamValues;
    private List<String> relatedParamValuesInStr;
    private Set<Integer> relatedValueIndex;
    private List<SootField> relatedFieldValues;
    private List<String> relatedFieldValuesInStr;

    private List<Value> caughtedValues;
    private List<RelatedMethod> relatedMethodsInSameClass;
    private List<RelatedMethod> relatedMethodsInDiffClass;
    private List<String> relatedMethods;
    private Map<Integer, ArrayList<RelatedMethod>> relatedMethodsInSameClassMap;
    private Map<Integer, ArrayList<RelatedMethod>> relatedMethodsInDiffClassMap;
    private List<Value> conditions;
    private List<Unit> conditionUnits;
    private String modifier;
    private List<Unit> tracedUnits;
    private SootMethod sootMethod;
    private String sootMethodName;
    private Unit unit;
    private RelatedVarType relatedVarType;
    private RelatedCondType relatedCondType;
    private boolean isOsVersionRelated;
    private boolean isAssessRelated;
    private boolean isManifestRelated;
    private boolean isResourceRelated;
    private boolean isHardwareRelated;

    public ExceptionInfo() {
        this.relatedParamValues = new ArrayList<>();
        this.relatedFieldValues = new ArrayList<>();
        this.caughtedValues = new ArrayList<>();
        this.relatedMethodsInSameClass = new ArrayList<>();
        this.relatedMethodsInDiffClass = new ArrayList<>();
        this.relatedMethods = new ArrayList<>();
        this.conditions = new ArrayList<>();
        this.conditionUnits = new ArrayList<>();
        this.tracedUnits = new ArrayList<>();
        this.relatedParamValuesInStr = new ArrayList<>();
        this.relatedFieldValuesInStr = new ArrayList<>();
        this.relatedValueIndex = new HashSet<>();
        this.relatedMethodsInSameClassMap = new TreeMap<Integer, ArrayList<RelatedMethod>>();
        this.relatedMethodsInDiffClassMap = new TreeMap<Integer, ArrayList<RelatedMethod>>();
        this.relatedCondType = RelatedCondType.Unknown;
    }
    public ExceptionInfo(SootMethod sootMethod, Unit unit, String exceptionType) {
        this();
        this.sootMethod = sootMethod;
        initModifier();
        this.unit = unit;
        this.exceptionType = exceptionType;

    }

    private void initModifier() {
        if(sootMethod.isPublic())
            setModifier("public");
        else if(sootMethod.isPrivate())
            setModifier("private");
        else if(sootMethod.isProtected())
            setModifier("protected");
        else
            setModifier("default");
    }

    public RelatedCondType getRelatedCondType() {
        return relatedCondType;
    }

    public void setRelatedCondType(RelatedCondType relatedCondType) {
        this.relatedCondType = relatedCondType;
    }

    public void setRelatedVarType(RelatedVarType relatedVarType) {
        this.relatedVarType = relatedVarType;
    }

    public RelatedVarType getRelatedVarType() {
        if(relatedVarType == null) {
            if (isOverrideMissing()) return RelatedVarType.OverrideMissing;
            if (isParameterOnly()) return RelatedVarType.ParameterOnly;
            if (isFieldOnly()) return RelatedVarType.FieldOnly;
            if (isParaAndField()) return RelatedVarType.ParaAndField;
        }
        return relatedVarType;
    }

    public boolean isOverrideMissing() {
        if(getRelatedMethods().size() ==0 && getConditions().size() ==0 )
            return true;
        else
            return false;
    }

    public boolean isParameterOnly() {
        if(getRelatedParamValues().size()>0 && getRelatedFieldValues().size()==0 )
            return true;
        else
            return false;
    }

    public boolean isFieldOnly() {
        if(getRelatedParamValues().size()==0 && getRelatedFieldValues().size()>0 )
            return true;
        else
            return false;
    }

    public boolean isParaAndField() {
        if(getRelatedParamValues().size()> 0 && getRelatedFieldValues().size()>0 )
            return true;
        else
            return false;
    }

    public String getModifier() {
        return modifier;
    }

    public void setModifier(String modifier) {
        this.modifier = modifier;
    }

    public List<Unit> getTracedUnits() {
        return tracedUnits;
    }

    public void setTracedUnits(List<Unit> tracedUnits) {
        this.tracedUnits = tracedUnits;
    }
    public String getExceptionType() {
        return exceptionType;
    }

    public Unit getUnit() {
        return unit;
    }

    public void setUnit(Unit unit) {
        this.unit = unit;
    }

    public String getExceptionMsg() {
        return exceptionMsg;
    }

    public void setExceptionMsg(String exceptionMsg) {
        this.exceptionMsg = exceptionMsg;
    }

    public SootMethod getSootMethod() {
        return sootMethod;
    }

    public Map<Integer, ArrayList<RelatedMethod>> getRelatedMethodsInSameClassMap() {
        return relatedMethodsInSameClassMap;
    }

    public Map<Integer, ArrayList<RelatedMethod>> getRelatedMethodsInDiffClassMap() {
        return relatedMethodsInDiffClassMap;
    }

    public void addRelatedMethodsInSameClassMap(RelatedMethod m) {
        if(!relatedMethodsInSameClassMap.containsKey(m.getDepth()))
            relatedMethodsInSameClassMap.put(m.getDepth(), new ArrayList<>());
        if(!relatedMethods.contains(m.getMethod()))
            relatedMethodsInSameClassMap.get(m.getDepth()).add(m);
    }

    public void addRelatedMethodsInDiffClassMap(RelatedMethod m) {
        if(!relatedMethodsInDiffClassMap.containsKey(m.getDepth()))
            relatedMethodsInDiffClassMap.put(m.getDepth(), new ArrayList<>());
        if(!relatedMethods.contains(m.getMethod()))
            relatedMethodsInDiffClassMap.get(m.getDepth()).add(m);
    }

    public List<RelatedMethod> getRelatedMethodsInSameClass(boolean compute) {
        if(!compute) return  relatedMethodsInSameClass;
        for(Integer depth:relatedMethodsInSameClassMap.keySet()) {
            for (RelatedMethod relatedMethod : relatedMethodsInSameClassMap.get(depth)) {
                addRelatedMethodsInSameClass(relatedMethod);
            }
        }
        return relatedMethodsInSameClass;
    }
    public List<RelatedMethod> getRelatedMethodsInDiffClass(boolean compute) {
        if(!compute) return  relatedMethodsInDiffClass;
        for(Integer depth:relatedMethodsInDiffClassMap.keySet()) {
            for (RelatedMethod relatedMethod : relatedMethodsInDiffClassMap.get(depth)) {
                addRelatedMethodsInDiffClass(relatedMethod);
            }
        }
        return relatedMethodsInDiffClass;
    }
    public void addRelatedMethodsInSameClass(RelatedMethod m) {
        if(!relatedMethodsInSameClass.contains(m))
            relatedMethodsInSameClass.add(m);
    }
    public void addRelatedMethodsInDiffClass(RelatedMethod m) {
        if(!relatedMethodsInDiffClass.contains(m))
            relatedMethodsInDiffClass.add(m);
    }
    public List<Value> getConditions() {
        return conditions;
    }
    public void addRelatedCondition(Value condition) {
        if(!conditions.contains(condition))
            conditions.add(condition);
    }

    public List<SootField> getRelatedFieldValues() {
        return relatedFieldValues;
    }
    public void addRelatedFieldValues(SootField v) {
        if(!relatedFieldValues.contains(v))
            relatedFieldValues.add(v);
    }

    public List<Value> getRelatedParamValues() {
        return relatedParamValues;
    }
    public void addRelatedParamValue(Value v) {
        if(!relatedParamValues.contains(v))
            relatedParamValues.add(v);
    }

    public List<Value> getCaughtedValues() {
        return caughtedValues;
    }
    public void addCaughtedValues(Value v) {
        if(!caughtedValues.contains(v))
            caughtedValues.add(v);
    }

    public List<String> getRelatedMethods() {
        return relatedMethods;
    }



    public void setExceptionType(String exceptionType) {
        this.exceptionType = exceptionType;
    }


    public List<String> getRelatedParamValuesInStr() {
        return relatedParamValuesInStr;
    }

    public List<String> getRelatedFieldValuesInStr() {
        return relatedFieldValuesInStr;
    }

    public void setRelatedParamValuesInStr(String relatedParamValues) {
        if(relatedParamValues == null) return;
        relatedParamValues= relatedParamValues.replace("<","");
        relatedParamValues= relatedParamValues.replace(">","");
        String ss[] = relatedParamValues.split(", ");
        for(String t: ss){
            if(t.contains(": "))
                this.relatedParamValuesInStr.add(t.split(": ")[1]);
        }
    }

    public void setRelatedFieldValuesInStr(String relatedFieldValues) {
        if(relatedFieldValues == null) return;
        relatedFieldValues= relatedFieldValues.replace("<","");
        relatedFieldValues= relatedFieldValues.replace(">","");
        String ss[] = relatedFieldValues.split(", ");
        for(String t: ss){
            this.relatedFieldValuesInStr.add(t);
        }
    }

    public void setCaughtedValues(List<Value> caughtedValues) {
        this.caughtedValues = caughtedValues;
    }

    public void setRelatedMethodsInSameClass(List<RelatedMethod> relatedMethodsInSameClass) {
        this.relatedMethodsInSameClass = relatedMethodsInSameClass;
    }

    public void setRelatedMethodsInDiffClass(List<RelatedMethod> relatedMethodsInDiffClass) {
        this.relatedMethodsInDiffClass = relatedMethodsInDiffClass;
    }

    public void setRelatedMethods(List<String> relatedMethods) {
        this.relatedMethods = relatedMethods;
    }

    public void setConditions(String conditions) {
        if(conditions == null) return;
        conditions= conditions.replace("\"","");
        conditions= conditions.replace("[","").replace("]","");
        conditions= conditions.replace("at ","");
        String ss[] = conditions.split(", ");
        for(String t: ss){
            this.conditions.add(StringConstant.v(t));
        }

    }

    public void setSootMethod(SootMethod sootMethod) {
        this.sootMethod = sootMethod;
    }

    @Override
    public String toString() {
        return "ExceptionInfo{" +
                "exceptionType='" + exceptionType + '\'' +
                ", exceptionMsg='" + exceptionMsg + '\'' +
                ", relatedParamValues=" + relatedParamValues +
                ", relatedFieldValues=" + relatedFieldValues +
                ", caughtedValues=" + caughtedValues +
                ", relatedMethodsInSameClass=" + relatedMethodsInSameClass +
                ", relatedMethodsInDiffClass=" + relatedMethodsInDiffClass +
                ", relatedMethods=" + relatedMethods +
                ", conditions=" + conditions +
                ", modifier='" + modifier + '\'' +
                ", tracedUnits=" + tracedUnits +
                ", sootMethod=" + sootMethod +
                ", unit=" + unit +
                '}';
    }

    public String getSootMethodName() {
        return sootMethodName;
    }

    public void setSootMethodName(String sootMethodName) {
        //			"method":"<android.database.sqlite.SQLiteClosable: void acquireReference()>",
        String ss[] = sootMethodName.split(" ");
        String prefix = ss[0].replace("<","").replace(":",".");
        String suffix = ss[2].split("\\(")[0];
        this.sootMethodName = prefix+suffix;
    }

    public String printRelatedMethodsInSameClass() {
        return  PrintUtils.printList(relatedMethodsInSameClass,"\n");
    }

    public String printRelatedMethodsInDiffClass() {
        return  PrintUtils.printList(relatedMethodsInDiffClass,"\n");
    }

    public Set<Integer> getRelatedValueIndex() {
        return relatedValueIndex;
    }

    public void setRelatedValueIndex(Set<Integer> relatedValueIndex) {
        this.relatedValueIndex = relatedValueIndex;
    }

    public boolean isOsVersionRelated() {
        return isOsVersionRelated;
    }

    public void setOsVersionRelated(boolean osVersionRelated) {
        isOsVersionRelated = osVersionRelated;
    }

    public boolean isAssessRelated() {
        return isAssessRelated;
    }

    public void setAssessRelated(boolean assessRelated) {
        isAssessRelated = assessRelated;
    }

    public boolean isManifestRelated() {
        return isManifestRelated;
    }

    public void setManifestRelated(boolean manifestRelated) {
        isManifestRelated = manifestRelated;
    }

    public boolean isResourceRelated() {
        return isResourceRelated;
    }

    public void setResourceRelated(boolean resourceRelated) {
        isResourceRelated = resourceRelated;
    }

    public boolean isHardwareRelated() {
        return isHardwareRelated;
    }

    public void setHardwareRelated(boolean hardwareRelated) {
        isHardwareRelated = hardwareRelated;
    }

    public List<Unit> getConditionUnits() {
        return conditionUnits;
    }

    public void setConditionUnits(List<Unit> conditionUnits) {
        this.conditionUnits = conditionUnits;
    }



    public void addRelatedMethods(String signature) {
//        if (SootUtils.getSootMethodBySignature(signature) != null) {
//            addRelatedMethods(SootUtils.getSootMethodBySignature(signature), signature);
//        } else
            if (!relatedMethods.contains(signature))
            relatedMethods.add(signature);
    }
}

