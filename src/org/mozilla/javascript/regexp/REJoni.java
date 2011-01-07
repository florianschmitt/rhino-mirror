package org.mozilla.javascript.regexp;

import java.io.UnsupportedEncodingException;

import org.jcodings.specific.UTF8Encoding;
import org.joni.Matcher;
import org.joni.Option;
import org.joni.Regex;
import org.joni.Region;

public class REJoni implements RegExpEngine {

    private String source;
    private String joniSource;
    private byte[] sourcebuf;
    private int option;
    private boolean global;
    private boolean ignoreCase;
    private boolean multiline;
    private boolean forceMultiline;
    private Regex regex;
    private Matcher m;
    private Region r;
    private String input;
    private byte[] inputBuf;
    int numMultis = 0;
    int[] multiPos = new int[4];

    public REJoni(String source, boolean global, boolean ignoreCase,
            boolean multiline, boolean incBomWs) {
        this.source = source;
        this.global = global;
        this.ignoreCase = ignoreCase;
        this.multiline = multiline;
        option = option(ignoreCase, multiline);

        try {
            joniSource = js2joni(source, incBomWs);
            sourcebuf = joniSource.getBytes("UTF-8");
            regex = new Regex(sourcebuf, 0, sourcebuf.length,
                    option, UTF8Encoding.INSTANCE);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private int option(boolean ignoreCase, boolean multiline) {
        int option = Option.NONE;
        if (ignoreCase) {
            option |= Option.IGNORECASE;
        }

        // joni is multiline by default, so need to explicitly set singleline
        // note: Option.MULTILINE means '.' match with newline.
        //       It is not opposite or related to Option.SINGLELINE
        if (!multiline) {
            option |= Option.SINGLELINE;
        }
        return option;
    }
    @Override
    public String toString() { return NativeRegExp.toString(this); }
    public String source() { return source; }
    public boolean global() { return global; }
    public boolean ignoreCase() { return ignoreCase; }
    public boolean multiline() { return multiline; }
    public void setInput(String input) {
        this.input = input;
        numMultis = 0;
        try {
            inputBuf = input.getBytes("UTF-8");
            if (inputBuf.length != input.length()) {
                // create index for converting byte pos to char pos
                for (int i = 0; i < inputBuf.length; i++) {
                    if ((inputBuf[i] & 0x80) == 0) { // single byte
                        continue;
                    }
                    byte b = inputBuf[i];
                    while ((b & 0x40) != 0) {
                        addMulti(i++);
                        b <<= 1;
                    }
                }
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        m = regex.matcher(inputBuf);
    }
    public String getInput() { return input; }
    public boolean find(int start, boolean forceMultiline) {
        // if this is not multiline and forceMultiline
        // is different to last time, then recompile pattern
        if (!multiline && this.forceMultiline != forceMultiline) {
            regex = new Regex(sourcebuf, 0, sourcebuf.length,
                    option(ignoreCase, forceMultiline), UTF8Encoding.INSTANCE);
            m = regex.matcher(inputBuf);
        }
        this.forceMultiline = forceMultiline; // save for next time

        int byteStart = c2b(start);
        return findByte(byteStart, forceMultiline);
    }
    private boolean findByte(int byteStart, boolean forceMultiline) {
        int matchOption = option;
        if (forceMultiline) {
            matchOption &= (0xffffffff ^ Option.SINGLELINE);
            matchOption |= Option.MULTILINE;
        }
        int index = m.search(byteStart, inputBuf.length, matchOption);

        r = m.getEagerRegion();
        if (index == -1) {
            return false;
        }

        return true;
    }
    public int start() {
        int byteStart = m.getBegin();
        int charStart = b2c(byteStart);
        return charStart;
    }
    public int end() {
        int byteEnd = m.getEnd();
        int charEnd = b2c(byteEnd);
        return charEnd;
    }
    public int groupCount() { return r.numRegs - 1; }
    public String group(int group) {
        if (r.beg[group] == -1) {
            return null;
        }
        int charStart = b2c(r.beg[group]);
        int charEnd = b2c(r.end[group]);
        return input.substring(charStart, charEnd);
    }

    private void addMulti(int bytePos) {
        multiPos[numMultis++] = bytePos;
        // ensure we always have at least 1 terminating '0' at end
        if (numMultis == multiPos.length) {
            int[] tmp = new int[multiPos.length * 2];
            System.arraycopy(multiPos, 0, tmp, 0, multiPos.length);
            multiPos = tmp;
        }
    }
    private int b2c(int bytePos) {
        int result = bytePos;
        for (int i = 0; i < numMultis && bytePos > multiPos[i]; i++) {
            result--;
        }
        return result;
    }
    private int c2b(int charPos) {
        for (int i = 0; multiPos[i] != 0 && charPos > multiPos[i]; i++) {
            charPos++;
        }
        return charPos;
    }

    private static String js2joni(String regexp, boolean bomWs) {
        // Translate regexp from js to joni.
        // Some symbols have different translations depending on whether
        // they are used as regular characters (c), or inside a character
        // class in either positive (cc+) or negated (cc-) form.
        // 1.*     '\0'     => '\x{00}'
        // 2.*     '\xhh'   => '\x{hh}'
        // 3.*     'uhhhh'  => '\x{hhhh}'
        // 4.*     '[^]'    => '[\s\S]'
        // 5.      '\s':
        //   c)             => '[\s\x{feff}]'
        //   cc+-)          => '\s\x{feff}'
        // 6.      '\S':
        //   c)             => '[\S&&[^\x{feff}]]'
        //   cc+)           => '\S&&[^\x{feff}]'
        //   cc-)           => '[\S&&[^\x{feff}]]'
        // 7.      '[':
        //   cc+-)          => '\['

        StringBuilder joni = new StringBuilder();
        boolean inCC = false; // inside character class
        boolean negCC = false; // negated character class
        for (int i = 0; i < regexp.length(); i++) {
            char c = regexp.charAt(i);
            switch (c) {
            case '\\': // escape-sequence
                if (regexp.length() <= i) { // dangling slash at end of regexp
                    joni.append('\\');
                    break;
                }
                c = regexp.charAt(++i);
                switch (c) {
                case '0': // 1.* '\0' => '\x{00}'
                    joni.append("\\x{00}");
                    break;
                case 'x': // 2.* '\xhh' => '\x{hh}'
                    joni.append("\\x{");
                    try {
                        joni.append(regexp.charAt(++i))
                            .append(regexp.charAt(++i));
                    } catch (IndexOutOfBoundsException e) {
                        // ignore - leave joni parser to complain
                    }
                    joni.append("}");
                    break;
                case 'u': // 3.* 'uhhhh' => '\x{hhhh}'
                    joni.append("\\x{");
                    try {
                        joni.append(regexp.charAt(++i))
                            .append(regexp.charAt(++i))
                            .append(regexp.charAt(++i))
                            .append(regexp.charAt(++i));
                    } catch (IndexOutOfBoundsException e) {
                        // ignore - leave joni parser to complain
                    }
                    joni.append("}");
                    break;
                case 's':
                    if (!bomWs) {
                        joni.append("\\s");
                        break;
                    }
                    if (inCC) { // 5.cc+- '\s' => '\s\x{feff}'
                        joni.append("\\s\\x{feff}");
                    } else {    // 5.c    '\s' => '[\s\ufeff]'
                        joni.append("[\\s\\x{feff}]");
                    }
                    break;
                case 'S':
                    if (!bomWs) {
                        joni.append("\\S");
                        break;
                    }
                    if (inCC) {
                        if (negCC) { // 6.cc- '\S' => '[\S&&[^\x{feff}]]'
                            joni.append("[\\S&&[^\\x{feff}]]");
                        } else {     // 6.cc+ '\S' => '\S&&[^\x{feff}]'
                            joni.append("\\S&&[^\\x{feff}]");
                        }
                    } else {         // 6.c   '\S' => '[\S&&[^\x{feff}]]'
                        joni.append("[\\S&&[^\\x{feff}]]");
                    }
                    break;
                default: // leave the escape 'as-is'
                    joni.append('\\').append(c);
                }
                break;
            case '[':
                if (inCC) { // 7.cc+- '[' => '\['
                    joni.append("\\[");
                    break;
                }

                // start of character class, check if negated
                if (regexp.length() > i+1 && regexp.charAt(i+1) == '^') {
                    // check for special match of '[^]'
                    if (regexp.length() > i+2 && regexp.charAt(i+2) == ']') {
                        // 3.* '[^]' => '[\s\S]'
                        joni.append("[\\s\\S]");
                        i += 2;
                        break;
                    }
                    negCC = true;
                }
                inCC = true;
                joni.append('[');
                break;
            case ']':
                // end of character class
                inCC = false;
                negCC = false;
                joni.append(']');
                break;
            default:
                joni.append(c);
            }
        }
        return joni.toString();
    }

    public static void main(String[] args) throws Exception {

//        js> /(.*?)a(?!(a+)b\2c)\2(.*)/.exec("baaabaac")
//        baaabaac,ba,,abaac
        String source = "(.*?)a(?!(a+)b\\2c)\\2(.*)";
        String input = "baaabaac";

        source = "#~#argjbexybtb#~#";
        input = "Bar Jvaqbjf Yvir VQ trgf lbh vagb <o>Ubgznvy</o>, <o>Zrffratre</o>, <o>Kobk YVIR</o> \u2014 naq bgure cynprf lbh frr #~#argjbexybtb#~#";
        REJoni j = new REJoni(source, false, false, false, true);
        j.setInput(input);
        System.out.println(j.end());
    }
}
