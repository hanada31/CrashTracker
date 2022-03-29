package main.java.client.exception;

import main.java.analyze.utils.output.PrintUtils;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.StringConstant;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @Author hanada
 * @Date 2022/3/15 10:47
 * @Version 1.0
 */
public class ExceptionInfo {
    private String exceptionType;
    private String exceptionMsg;
    private List<Value> relatedParamValues;
    private Set<Integer> relatedValueIndex;
    private List<SootField> relatedFieldValues;
    private List<Value> caughtedValues;
    private List<RelatedMethod> relatedMethodsInSameClass;
    private List<RelatedMethod> relatedMethodsInDiffClass;
    private List<String> relatedMethods;
    private List<Value> conditions;
    private String modifier;
    private List<Unit> tracedUnits;
    private SootMethod sootMethod;
    private String sootMethodName;
    private Unit unit;
    private RelatedVarType relatedVarType;

    public ExceptionInfo() {
        this.relatedParamValues = new ArrayList<>();
        this.relatedFieldValues = new ArrayList<>();
        this.caughtedValues = new ArrayList<>();
        this.relatedMethodsInSameClass = new ArrayList<>();
        this.relatedMethodsInDiffClass = new ArrayList<>();
        this.relatedMethods = new ArrayList<>();
        this.conditions = new ArrayList<>();
        this.tracedUnits = new ArrayList<>();
        this.relatedValueIndex = new HashSet<>();

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

    public void setRelatedVarType(RelatedVarType relatedVarType) {
        this.relatedVarType = relatedVarType;
    }

    public RelatedVarType getRelatedVarType() {
        if(isOverrideMissing()) return  RelatedVarType.OverrideMissing;
        if(isParameterOnly()) return  RelatedVarType.ParameterOnly;
        if(isFieldOnly()) return  RelatedVarType.FieldOnly;
        if(isParaAndField()) return  RelatedVarType.ParaAndField;
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

    public List<RelatedMethod> getRelatedMethodsInSameClass() {
        return relatedMethodsInSameClass;
    }
    public void addRelatedMethodsInSameClass(RelatedMethod m) {
        if(!relatedMethods.contains(m.getMethod()))
            relatedMethodsInSameClass.add(m);
    }

    public List<RelatedMethod> getRelatedMethodsInDiffClass() {
        return relatedMethodsInDiffClass;
    }

    public void addRelatedMethodsInDiffClass(RelatedMethod m) {
        if(!relatedMethods.contains(m.getMethod()))
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

    public void addRelatedMethods(String sm) {
        if(!relatedMethods.contains(sm))
            relatedMethods.add(sm);
    }

    public void setExceptionType(String exceptionType) {
        this.exceptionType = exceptionType;
    }

    public void setRelatedParamValues(List<Value> relatedParamValues) {
        this.relatedParamValues = relatedParamValues;
    }

    public void setRelatedFieldValues(List<SootField> relatedFieldValues) {
        this.relatedFieldValues = relatedFieldValues;
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
}

