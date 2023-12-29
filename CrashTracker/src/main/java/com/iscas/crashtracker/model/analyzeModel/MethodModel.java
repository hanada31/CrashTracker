package com.iscas.crashtracker.model.analyzeModel;

import com.alibaba.fastjson.JSONArray;
import com.iscas.crashtracker.utils.SootUtils;
import soot.Type;

import java.util.List;

/**
 * @Author hanada
 * @Date 2023/12/28 11:16
 * @Version 1.0
 */
public class MethodModel {
    String methodSignature;
    String simpleName;
    String className;
    List<String > Arguments;

    public String getMethodSignature() {
        return methodSignature;
    }

    public void setMethodSignature(String methodSignature) {
        this.methodSignature = methodSignature;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public void setSimpleName(String simpleName) {
        this.simpleName = simpleName;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public List<String> getArguments() {
        return Arguments;
    }

    public void setArguments(List<String> arguments) {
        Arguments = arguments;
    }

}
