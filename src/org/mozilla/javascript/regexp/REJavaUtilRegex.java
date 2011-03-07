package org.mozilla.javascript.regexp;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.RegExpEngine;

/**
 * RegExp engine using java.util.regex.
 *
 * @author Joel Hockey
 */
public class REJavaUtilRegex implements RegExpEngine, Serializable {
    private static final long serialVersionUID = 0x9DD472F6171676CDL;

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    // original regexp source
    private String source;
    // source compiled to java.util.regex syntax
    private String javaUtilRegexSource;
    // flags for java.util.regex
    private int flags;
    private boolean global;
    private boolean ignoreCase;
    private boolean multiline;

    // whitespace character class - may also get bom ufeff
    private String wscc = "\\u0009-\\u000d\\u0020\\u0085\\u00a0\\u1680"
        + "\\u180e\\u2000\\u2028\\u2029\\u202f\\u205f\\u3000";

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
    private long negLookCapBs;

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
        if (bomWs) {
            wscc += "\\ufeff";
        }
        flags = 0;

        // set flags
        if (literal) {
            flags |= Pattern.LITERAL;
            javaUtilRegexSource = source;
        } else {
            // compile to java.util.regex syntax
            javaUtilRegexSource = js2javaUtilRegex();
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
        // remove any capture groups that were not cleared in a repetition,
        // also remove any groups that are inside neglookaheads
        if (validCapture == null) {
            validCapture = new boolean[matcher.groupCount() + 1];
            validCapture[0] = true;
            int lastCaptureStart = 0;
            for (int i = 1; i < validCapture.length; i++) {
                if ((negLookCapBs & (1 << i)) == 0
                        && matcher.start(i) >= lastCaptureStart) {
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
        // 1.      octal => 'u00hh'
        // 2.*     '\v'  => '\x0b'
        // 3.*     '[^]' => '[\s\S]'
        // 4.      '\b':
        //   c)          => '\b'
        //   cc+-)       => '\x08'
        // 5.      '\s':
        //   c)          => '[u...]'
        //   cc+-)       => 'u...'
        // 6.      '\S':
        //   c)          => '[^u...]
        //   cc+)        => '&&[^u...]'
        //   cc-)        => '[u...]'
        // 7.      '[':
        //   cc+-)       => '\['
        // 8.      '{'   => '\{' if not matching '{n}', '{n,}' or '{n,n}'

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
                case '0': case '1': case '2': case '3': case '4':
                case '5': case '6': case '7': case '8': case '9':
                    // it is tricky to detect difference between backref and oct
                    // if c between 1 and 'captureCount' it is a bref
                    // if c > '7', it is literal '\\c'
                    // else octal escape.  Parse 1 or 2 more octal chars
                    int cdec = c - '0';
                    // backref
                    if ((cdec >= 1 && cdec <= captureCount)) {
                        // 6. Remove invalid backreferences to neg lookaheads
                        if (inNegLook || (negLookCapBs & (1 << cdec)) == 0) {
                            javaUtilRegex.append('\\').append(c);
                        }

                    // literal '\\c'
                    } else if (cdec > 7) {
                        javaUtilRegex.append('\\').append('\\').append(c);

                    // octal   => 'u00hh'
                    } else {
                        int num = cdec;
                        // if first char 0, 1, 2, 3, parse 2 more, else 1 more
                        int oneOrTwo = c > '3' ? 1 : 2;
                        for (int octIndex = 0; octIndex < oneOrTwo
                                && i+1 < source.length(); octIndex++) {

                            cdec = source.charAt(++i) - '0';
                            // if non-octal char, then just use what we have
                            if (cdec < 0 || cdec > 7) {
                                i--; // backup main index 'i'
                                break;
                            }
                            num = (num << 3) | cdec;
                        }
                        // convert num to unicode escape
                        char[] u4 = new char[4];
                        for (int u4pos = 3; u4pos >= 0; u4pos--) {
                            u4[u4pos] = HEX[num & 0xf];
                            num >>= 4;
                        }
                        javaUtilRegex.append("\\u").append(u4);
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
                    if (inCC) { // 5.cc+-) '\s' => 'u...'
                        javaUtilRegex.append(wscc);
                    } else { // 5.c) '\s' => '[u...]'
                        javaUtilRegex.append('[').append(wscc).append(']');
                    }
                    break;
                case 'S':
                    if (inCC) {
                        if (negCC) { // 6.cc-) '\S' => '[u...]'
                            javaUtilRegex.append('[').append(wscc).append(']');
                        } else { // 6.cc+) '\S' => '&&[^u...]'
                            javaUtilRegex.append("&&[^").append(wscc).append(']');
                        }
                    } else { // 6.c) '\S' => '[^u...]'
                        javaUtilRegex.append('[').append('^').append(wscc).append(']');
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
                if (i+1 < source.length() && source.charAt(i+1) == '?') {
                    if (i+2 < source.length() && source.charAt(i+2) == '!') {
                        inNegLook = true;
                        negLookBrackets++;
                    }
                } else {
                    captureCount++;
                    if (inNegLook) {
                        // set neg look bitset, only support 63 captures
                        if (captureCount < 64) {
                            negLookCapBs |= (1L << captureCount);
                        }
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
            case '{':
                // 8.      '{'   => '\{' if not matching '{n}', '{n,}' or '{n,n}'
                if (inCC) {
                    javaUtilRegex.append(c); // leave cc as-is
                    break;
                }

                int j = i + 1;
                int numDigits = numDigits(j);
                j += numDigits; // j points to char after digits
                if (numDigits == 0 || j >= source.length()) {
                    // no digits, or at end of source - escape
                    javaUtilRegex.append("\\{");
                    break;
                }

                if (source.charAt(j) == '}') {
                    javaUtilRegex.append(c); // valid format '{n}'
                    break;
                } else if (source.charAt(j) != ',') {
                    // bad format - escape
                    javaUtilRegex.append("\\{");
                    break;
                }

                // we have parsed '{n,' - now read zero or more digits and '}'
                j++; // move past ','
                j += numDigits(j); // j points to char after digits
                if (j < source.length() && source.charAt(j) == '}') {
                    javaUtilRegex.append('{'); // valid format '{n,n}'
                } else {
                    // at end of source, or not closing bracket - escape
                    javaUtilRegex.append("\\{");
                }

                break;
            default:
                javaUtilRegex.append(c);
            }
        }
        return javaUtilRegex.toString();
    }

    private int numDigits(int i) {
        int start = i;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c < '0' || c > '9') {
                break;
            }
            i++;
        }
        return i - start;
    }

    public static class Factory implements RegExpEngine.Factory {
        public RegExpEngine create(Context cx, String source, boolean global,
                boolean ignoreCase, boolean multiline, boolean literal,
                boolean bomWs) {

            return new REJavaUtilRegex(source, global, ignoreCase, multiline,
                    literal, bomWs);
        }
    }

    public static void main(String[] args) throws Exception {
        String source = "[^\\S]";
        String input = "n\u0101ïve\ufeff";

        REJavaUtilRegex jur = new REJavaUtilRegex(source, false, false, false, false, true);
        jur.setInput(input);
        boolean found = jur.find(0, false);
        System.out.println("java.util.regex source: " + jur.javaUtilRegexSource);
        System.out.println(found + " at: " + (found ? jur.start() : -1));
        for (int i = 0; found && i <= jur.groupCount(); i++) {
            System.out.println("group " + i + ": " + jur.group(i) + " : " + jur.matcher.start(i));
        }
    }
}
