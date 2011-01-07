package org.mozilla.javascript.regexp;

public interface RegExpEngine {
    boolean global();
    boolean ignoreCase();
    boolean multiline();
    String source();
    void setInput(String input);
    String getInput();
    boolean find(int start, boolean forceMultiline);
    int start();
    int end();
    int groupCount();
    String group(int group);
}
