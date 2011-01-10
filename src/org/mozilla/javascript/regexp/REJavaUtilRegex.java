package org.mozilla.javascript.regexp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class REJavaUtilRegex implements RegExpEngine {

    private String source;
    private String javaUtilRegexSource;
    private int flags;
    private boolean global;
    private boolean ignoreCase;
    private boolean multiline;
    private boolean prevForceMultiline = false;
    private Pattern pattern;
    private Matcher m;
    private String input;

    public REJavaUtilRegex(String source, boolean global,
            boolean ignoreCase, boolean multiline) {
        this.source = source;
        this.global = global;
        this.ignoreCase = ignoreCase;
        this.multiline = multiline;
        flags = 0;
        if (ignoreCase) {
            flags |= Pattern.CASE_INSENSITIVE;
        }
        if (multiline) {
            flags |= Pattern.MULTILINE;
        }
        this.javaUtilRegexSource = js2javaUtilRegex(source);
        this.pattern = Pattern.compile(javaUtilRegexSource, flags);
    }
    @Override
    public String toString() { return NativeRegExp.toString(this); }
    public String source() { return source; }
    public boolean global() { return global; }
    public boolean ignoreCase() { return ignoreCase; }
    public boolean multiline() { return multiline; }
    public void setInput(String input) {
        this.input = input;
        m = pattern.matcher(input);
    }
    public String getInput() { return input; }
    public boolean find(int start, boolean forceMultiline) {
        // if this is not multiline and forceMultiline
        // is different to last time, then recompile pattern
        if (!multiline && prevForceMultiline != forceMultiline) {
            pattern = Pattern.compile(javaUtilRegexSource,
                    flags | (forceMultiline ? Pattern.MULTILINE : 0));
            m = pattern.matcher(input);
        }
        prevForceMultiline = forceMultiline; // save for next time

        // find
        return m.find(start);
    }
    public int start() { return m.start(); }
    public int end() { return m.end(); }
    public int groupCount() { return m.groupCount(); }
    public String group(int group) { return m.group(group); }

    public static String js2javaUtilRegex(String regexp) {
        // Translate regexp from js to java.
        // Some symbols have different translations depending on whether
        // they are used as regular characters (c), or inside a character
        // class in either positive (cc+) or negated (cc-) form.
        // 1.*     '\0'  => '\x00'
        // 2.*     '\v'  => '\x0b'
        // 3.*     '[^]' => '[\s\S]'
        // 4.      '\b':
        //   c)          => '\b'
        //   cc+-)       => '\x08'
        // 5.      '\s':
        //   c)          => '[\s\ufeff]'
        //   cc+-)       => '\s\ufeff'
        // 6.      '\S':
        //   c)          => '[\S&&[^\ufeff]]'
        //   cc+)        => '\S&&[^\ufeff]'
        //   cc-)        => '\S[\ufeff]'
        // 7.      '[':
        //   cc+-)       => '\['

        StringBuilder javaUtilRegex = new StringBuilder();
        boolean inCC = false; // inside character class
        boolean negCC = false; // negated character class
        for (int i = 0; i < regexp.length(); i++) {
            char c = regexp.charAt(i);
            switch (c) {
            case '\\': // escape-sequence
                if (regexp.length() <= i) { // dangling slash at end of regexp
                    javaUtilRegex.append('\\');
                    break;
                }
                c = regexp.charAt(++i);
                switch (c) {
                case '0': // 1.* '\0' => '\x00'
                    javaUtilRegex.append("\\x00");
                    break;
                case 'v': // 2.* '\v' => '\x0b'
                    javaUtilRegex.append("\\x0b");
                    break;
                case 'b':
                    if (inCC) { // 4.cc+- '\b' => '\x08'
                        javaUtilRegex.append("\\x08");
                    } else {    // 4.c    '\b' => '\b'
                        javaUtilRegex.append("\\b");
                    }
                    break;
                case 's':
                    if (inCC) { // 5.cc+- '\s' => '\s\ufeff'
                        javaUtilRegex.append("\\s\\ufeff");
                    } else {    // 5.c    '\s' => '[\s\ufeff]'
                        javaUtilRegex.append("[\\s\\ufeff]");
                    }
                    break;
                case 'S':
                    if (inCC) {
                        if (negCC) { // 6.cc- '\S' => '\S[\ufeff]'
                            javaUtilRegex.append("\\S[\\ufeff]");
                        } else {     // 6.cc+ '\S' => '\S&&[^\ufeff]'
                            javaUtilRegex.append("\\S&&[^\\ufeff]");
                        }
                    } else {         // 6.c   '\S' => '[\S&&[^\ufeff]]'
                        javaUtilRegex.append("[\\S&&[^\\ufeff]]");
                    }
                    break;
                default: // leave the escape 'as-is'
                    javaUtilRegex.append('\\').append(c);
                }
                break;
            case '[':
                if (inCC) { // 7.cc+- '[' => '\['
                    javaUtilRegex.append("\\[");
                    break;
                }

                // start of character class, check if negated
                if (regexp.length() > i+1 && regexp.charAt(i+1) == '^') {
                    // check for special match of '[^]'
                    if (regexp.length() > i+2 && regexp.charAt(i+2) == ']') {
                        // 3.* '[^]' => '[\s\S]'
                        javaUtilRegex.append("[\\s\\S]");
                        i += 2;
                        break;
                    }
                    negCC = true;
                }
                inCC = true;
                javaUtilRegex.append('[');
                break;
            case ']':
                // end of character class
                inCC = false;
                negCC = false;
                javaUtilRegex.append(']');
                break;
            default:
                javaUtilRegex.append(c);
            }
        }
        return javaUtilRegex.toString();
    }
}
