package main.java.client.exception;

import soot.SootMethod;

/**
 * @Author hanada
 * @Date 2022/3/22 17:59
 * @Version 1.0
 */


public class RelatedMethod {

    String method;
    RelatedMethodSource source;
    int depth;

    public RelatedMethod(String method, RelatedMethodSource source, int depth) {
        this.method = method;
        this.source = source;
        this.depth = depth;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public RelatedMethodSource getSource() {
        return source;
    }

    public void setSource(RelatedMethodSource source) {
        this.source = source;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }
}
