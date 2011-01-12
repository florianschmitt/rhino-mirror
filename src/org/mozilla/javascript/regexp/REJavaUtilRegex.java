package org.mozilla.javascript.regexp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RegExp engine using java.util.regex.
 *
 * @author Joel Hockey
 */
public class REJavaUtilRegex implements RegExpEngine {

    // original regexp source
    private String source;
    // source compiled to java.util.regex syntax
    private String javaUtilRegexSource;
    // flags for java.util.regex
    private int flags;
    private boolean global;
    private boolean ignoreCase;
    private boolean multiline;

    // true if \s matches unicode byte-order-mark
    private boolean bomWs;

    // java.util.regexp must be recompiled if RegExp.multiline
    // is different to multiline
    private boolean prevForceMultiline = false;

    // java.util.regex objects
    private Pattern pattern;
    private Matcher matcher;

    // input to match against
    private String input;

    // JavaScript requires capture groups to be cleared when inside
    // a repeating atom.  JUR will only overwrite groups if they match again
    // JUR also (maybe bug) matches capture groups in a negative lookahead
    // To fix this, we look at capture groups and even if JUR has a match,
    // we return null if we detect that capture index is before previous,
    // or within neg lookahead
    private boolean[] validCapture;
    // bitset showing which capture groups are in neg lookahead
    private int negLookCapBs;

    /**
     * engine constructor
     * @param source regexp source
     * @param global global flag
     * @param ignoreCase ignoreCase flag
     * @param multiline multiline flat
     * @param literal if true, source is literal
     * @param bomWs true if \s matches unicode byte-order-mark
     */
    public REJavaUtilRegex(String source, boolean global,
            boolean ignoreCase, boolean multiline, boolean literal,
            boolean bomWs) {
        this.source = source;
        this.global = global;
        this.ignoreCase = ignoreCase;
        this.multiline = multiline;
        this.bomWs = bomWs;
        flags = 0;

        // set flags
        if (literal) {
            flags |= Pattern.LITERAL;
            this.javaUtilRegexSource = source;
        } else {
            // compile to java.util.regex syntax
            this.javaUtilRegexSource = js2javaUtilRegex();
        }

        if (ignoreCase) {
            flags |= Pattern.CASE_INSENSITIVE;
        }
        if (multiline) {
            flags |= Pattern.MULTILINE;
        }
        this.pattern = Pattern.compile(javaUtilRegexSource, flags);
    }

    @Override
    public String toString() {
        return NativeRegExp.toString(this);
    }

    public String source() {
        return source;
    }

    public boolean global() {
        return global;
    }

    public boolean ignoreCase() {
        return ignoreCase;
    }

    public boolean multiline() {
        return multiline;
    }

    public void setInput(String input) {
        this.input = input;
        matcher = pattern.matcher(input);
    }

    public String getInput() {
        return input;
    }

    public boolean find(int start, boolean forceMultiline) {
        // if this is not multiline and forceMultiline
        // is different to last time, then recompile pattern
        if (!multiline && prevForceMultiline != forceMultiline) {
            pattern = Pattern.compile(javaUtilRegexSource,
                    flags | (forceMultiline ? Pattern.MULTILINE : 0));
            matcher = pattern.matcher(input);
        }
        prevForceMultiline = forceMultiline; // save for next time

        // clear valid capture groups
        validCapture = null;

        // find
        return matcher.find(start);
    }

    public int start() {
        return matcher.start();
    }

    public int end() {
        return matcher.end();
    }

    public int groupCount() {
        return matcher.groupCount();
    }

    public String group(int group) {
        // remove any capture groups that were not cleared
        // in a repetition, or any capture groups within
        // a negative lookahead
        if (validCapture == null) {
            validCapture = new boolean[matcher.groupCount() + 1];
            int lastCaptureStart = 0;
            for (int i = 0; i < validCapture.length; i++) {
                if ((negLookCapBs & (1 << i)) == 0
                        &&  matcher.start(i) >= lastCaptureStart) {
                    validCapture[i] = true;
                    lastCaptureStart = matcher.start(i);
                }
            }
        }
        return validCapture[group] ? matcher.group(group) : null;
    }

    public String js2javaUtilRegex() {
        // Translate regexp from js to java.
        // Some symbols have different translations depending on whether
        // they are used as regular characters (c), or inside a character
        // class in either positive (cc+) or negated (cc-) form.
        // 1.*     '\0'  => '\x00' unless \nnn is valid octal
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
        boolean inNegLook = false; // negative lookahead

        int negLookBrackets = 0;
        int captureCount = 0;

        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            switch (c) {
            case '\\': // escape-sequence
                if (source.length() <= i) { // dangling slash at end of regexp
                    javaUtilRegex.append('\\');
                    break;
                }
                c = source.charAt(++i);
                switch (c) {
                case '0': // 1.* '\0' => '\x00' unless \nnn is valid octal
                    if (i + 2 < source.length() && isOct(source.charAt(i + 1))
                            && isOct(source.charAt(i + 2))) {
                        javaUtilRegex.append("\\0"); // leave as-is
                    } else {
                        javaUtilRegex.append("\\x00");
                    }
                    break;
                case '1': case '2': case '3': case '4': case '5':
                case '6': case '7': case '8': case '9':
                    // 5. Remove invalid backreferences to negative lookaheads
                    if (inNegLook || (negLookCapBs & (1 << (c-'0'))) == 0) {
                        javaUtilRegex.append('\\').append(c);
                    }
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
                    if (!bomWs) {
                        javaUtilRegex.append("\\s");
                        break;
                    }
                    if (inCC) { // 5.cc+- '\s' => '\s\ufeff'
                        javaUtilRegex.append("\\s\\ufeff");
                    } else {    // 5.c    '\s' => '[\s\ufeff]'
                        javaUtilRegex.append("[\\s\\ufeff]");
                    }
                    break;
                case 'S':
                    if (!bomWs) {
                        javaUtilRegex.append("\\S");
                        break;
                    }
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
                if (source.length() > i+1 && source.charAt(i+1) == '^') {
                    // check for special match of '[^]'
                    if (source.length() > i+2 && source.charAt(i+2) == ']') {
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
            case '(':
                if (i + 1 < source.length() && source.charAt(i + 1) == '?') {
                    if (i + 2 < source.length() && source.charAt(i + 2) == '!') {
                        inNegLook = true;
                        negLookBrackets++;
                    }
                } else {
                    captureCount++;
                    if (inNegLook) {
                        // set neg look bitset
                        negLookCapBs |= (1 << captureCount);
                        negLookBrackets++;
                    }
                }
                javaUtilRegex.append('(');
                break;
            case ')':
                if (inNegLook) {
                    negLookBrackets--;
                    inNegLook = negLookBrackets > 0;
                }
                javaUtilRegex.append(')');
                break;
            default:
                javaUtilRegex.append(c);
            }
        }
        return javaUtilRegex.toString();
    }

    private static boolean isOct(char c) {
        return c >= '0' && c <= '7';
    }
}
