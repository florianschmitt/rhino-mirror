package org.mozilla.javascript.regexp;

import java.io.Serializable;

import org.mozilla.javascript.RegExpEngine;

public class REIndexOf implements RegExpEngine, Serializable {
    private static final long serialVersionUID = 0x2BF4F0CFB9A0DB91L;

    private String source;
    private String compiled;
    private boolean global;
    private boolean multiline;
    private String input;
    private int matchStart;
    private int matchEnd;

    public REIndexOf(String source, String compiled, boolean global,
            boolean multiline) {
        this.source = source;
        this.compiled = compiled;
        this.global = global;
        this.multiline = multiline;
    }
    @Override
    public String toString() { return NativeRegExp.toString(this); }
    public String source() { return source; }
    public boolean global() { return global; }
    public boolean ignoreCase() { return false; }
    public boolean multiline() { return multiline; }
    public void setInput(String input) { this.input = input; }
    public String getInput() { return input; }
    public boolean find(int start, boolean forceMultiline) {
        matchEnd = -1;
        matchStart = input.indexOf(compiled, start);
        if (matchStart >= 0) {
            matchEnd = matchStart + compiled.length();
            return true;
        }
        return false;
    }
    public int start() { return matchStart; }
    public int end() { return matchEnd; }
    public int groupCount() { return 0; }
    public String group(int group) {
        if (group != 0) {
            return null;
        }
        return input.substring(matchStart, matchEnd);
    }
}
