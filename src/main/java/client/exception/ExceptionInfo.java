package main.java.client.exception;

import soot.SootMethod;
import soot.Unit;
import soot.Value;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author hanada
 * @Date 2022/3/15 10:47
 * @Version 1.0
 */
public class ExceptionInfo {

    private  String exceptionType;
    private  String exceptionMsg;
    private List<Value> relatedParamValues;
    private List<Value> relatedStaticValues;
    private List<Value> caughtedValues;
    private List<SootMethod> relatedMethods;
    private List<Value> conditions;
    private String modifier;


    private List<Unit> tracedUnits;

    private  SootMethod sootMethod;
    private  Unit unit;

    public ExceptionInfo(SootMethod sootMethod, Unit unit, String exceptionType) {
        this.sootMethod = sootMethod;
        this.unit = unit;
        this.exceptionType = exceptionType;
        this.relatedParamValues = new ArrayList<Value>();
        this.relatedStaticValues = new ArrayList<Value>();
        this.caughtedValues = new ArrayList<Value>();
        this.relatedMethods = new ArrayList<SootMethod>();
        this.conditions = new ArrayList<Value>();
        this.tracedUnits = new ArrayList<Unit>();
        initModifier();
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





    public List<SootMethod> getRelatedMethods() {
        return relatedMethods;
    }

    public void setRelatedMethods(List<SootMethod> relatedMethods) {
        this.relatedMethods = relatedMethods;
    }

    public List<Value> getConditions() {
        return conditions;
    }

    public void setConditions(List<Value> conditions) {
        this.conditions = conditions;
    }

    public List<Value> getRelatedStaticValues() {
        return relatedStaticValues;
    }

    public void addRelatedStaticValues(Value v) {
        if(!relatedStaticValues.contains(v))
            relatedStaticValues.add(v);
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
}

