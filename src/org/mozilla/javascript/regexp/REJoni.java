package org.mozilla.javascript.regexp;

import java.io.UnsupportedEncodingException;

import org.jcodings.Config;
import org.jcodings.specific.UTF8Encoding;
import org.joni.Matcher;
import org.joni.Option;
import org.joni.Regex;
import org.joni.Region;
import org.joni.Syntax;
import org.joni.Syntax.MetaCharTable;
import org.joni.WarnCallback;
import org.joni.constants.MetaChar;

/**
 * RegExp engine using joni.
 *
 * @author Joel Hockey
 */
public class REJoni implements RegExpEngine {
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final boolean[] IS_HEX = new boolean[128];
    static {
        char[] hex = "0123456789ABCDEFabcdef".toCharArray();
        for (int i = 0; i < hex.length; i++) {
            IS_HEX[hex[i]] = true;
        }
    }

    // original regexp source
    private String source;
    // source compiled to joni syntax
    String joniSource;
    // joniSource.getBytes("UTF-8")
    private byte[] sourcebuf;

    private boolean global;
    private boolean ignoreCase;
    private boolean multiline;

    // true if \s matches unicode byte-order-mark
    private boolean bomWs;

    // joni regexp must be recompiled if RegExp.multiline
    // is different to multiline
    private boolean prevForceMultiline;

    // joni regex objects
    private Regex regex;
    private Matcher matcher;
    private Region region;

    // input to match against
    private String input;
    // input.getBytes("UTF-8")
    private byte[] inputBuf;

    // UTF-8 multi-byte index helps for fast converstion between
    // char position in 'input' and byte pos in 'inputbuf'
    private int numMultis = 0;
    private int[] multiPos = new int[4];

    // JavaScript requires capture groups to be cleared when inside
    // a repeating atom.  Joni doesn't do this, but we can do this after
    // joni has finished matching by looking at start index of each group
    private boolean capturesCleared;

    // bitset of negative lookahead captures
    long negLookCapBs = 0;

    /** best fit JavaScript syntax for joni */
    public static final Syntax JavaScript = new Syntax(
        ((Syntax.GNU_REGEX_OP | Syntax.OP_QMARK_NON_GREEDY
        | Syntax.OP_ESC_CONTROL_CHARS | Syntax.OP_ESC_C_CONTROL)
        & ~Syntax.OP_ESC_LTGT_WORD_BEGIN_END),

        (Syntax.OP2_QMARK_GROUP_EFFECT | Syntax.OP2_CCLASS_SET_OP
        | Syntax.OP2_ESC_U_HEX4 | Syntax.OP_ESC_X_HEX2 | Syntax.OP2_ESC_V_VTAB),

        (Syntax.GNU_REGEX_BV | Syntax.DIFFERENT_LEN_ALT_LOOK_BEHIND),

        Option.SINGLELINE,

        new MetaCharTable('\\', /* esc */
            MetaChar.INEFFECTIVE_META_CHAR, /* anychar '.' */
            MetaChar.INEFFECTIVE_META_CHAR, /* anytime '*' */
            MetaChar.INEFFECTIVE_META_CHAR, /* zero or one time '?' */
            MetaChar.INEFFECTIVE_META_CHAR, /* one or more time '+' */
            MetaChar.INEFFECTIVE_META_CHAR /* anychar anytime */
        )
    );

    /**
     * engine constructor
     * @param source regexp source
     * @param global global flag
     * @param ignoreCase ignoreCase flag
     * @param multiline multiline flat
     * @param escape if true, source is literal and should be escaped
     * @param bomWs true if \s matches unicode byte-order-mark
     */
    public REJoni(String source, boolean global, boolean ignoreCase,
            boolean multiline, boolean escape, boolean bomWs) {
        this.source = source;
        this.global = global;
        this.ignoreCase = ignoreCase;
        this.multiline = multiline;
        this.bomWs = bomWs;

        try {
            // escape or compile to joni syntax
            if (escape) {
                joniSource = NativeRegExp.escRe(source);
            } else {
                joniSource = js2joni();
            }
            sourcebuf = joniSource.getBytes("UTF-8");
            regex = regex(sourcebuf, ignoreCase, multiline);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private Regex regex(byte[] sourcebuf,
            boolean ignoreCase, boolean multiline) {

        int option = Option.NONE;
        if (ignoreCase) {
            option |= Option.IGNORECASE;
        }
        // note: we must use NEGATE_SINGLELINE and SINGLELINE.
        // Option.MULTILINE means dot-matches-newline
        option |= (multiline ? Option.NEGATE_SINGLELINE : Option.SINGLELINE);

        // special new jcodings flag for JavaScript compliance
        int foldCaseFlag = Config.ENC_CASE_FOLD_NO_UNICODE_TO_ASCII;

        return new Regex(sourcebuf, 0, sourcebuf.length, option, foldCaseFlag,
                UTF8Encoding.INSTANCE, JavaScript, WarnCallback.DEFAULT);
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
        numMultis = 0;
        try {
            inputBuf = input.getBytes("UTF-8");
            if (inputBuf.length != input.length()) {
                // create index for converting byte pos to char pos
                for (int i = 0; i < inputBuf.length; i++) {
                    if ((inputBuf[i] & 0x80) == 0) { // single byte
                        continue;
                    }
                    // store index of every byte with high-order bit set
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
        matcher = regex.matcher(inputBuf);
    }

    public String getInput() {
        return input;
    }

    public boolean find(int start, boolean forceMultiline) {
        // if this is not multiline and forceMultiline
        // is different to last time, then recompile pattern
        if (!multiline && prevForceMultiline != forceMultiline) {
            regex = regex(sourcebuf, ignoreCase, forceMultiline);
            matcher = regex.matcher(inputBuf);
        }
        prevForceMultiline = forceMultiline; // save for next time

        int byteStart = c2b(start);
        return findByte(byteStart);
    }

    private boolean findByte(int byteStart) {
        int index = matcher.search(byteStart, inputBuf.length, Option.NONE);
        region = matcher.getEagerRegion();
        if (index == -1) {
            return false;
        }
        // mark captures to be cleared lazily on first access to groups
        capturesCleared = false;

        return true;
    }

    public int start() {
        // convert between byte-pos and char-pos
        int byteStart = matcher.getBegin();
        int charStart = b2c(byteStart);
        return charStart;
    }

    public int end() {
        // convert between byte-pos and char-pos
        int byteEnd = matcher.getEnd();
        int charEnd = b2c(byteEnd);
        return charEnd;
    }

    public int groupCount() {
        return region.numRegs - 1;
    }

    public String group(int group) {
        // ECMA 262 15.10.2.5 RepeatMatcher Step 4 - clear captures
        // if the start of any capture group is before the start of the prev
        // capture group, then clear it (set beg=-1)
        if (!capturesCleared) {
            int lastCaptureStart = 0;
            for (int i = 1; i < region.numRegs; i++) {
                if (region.beg[i] < lastCaptureStart) {
                    region.beg[i] = -1;
                } else {
                    lastCaptureStart = region.beg[i];
                }
            }
            capturesCleared = true;
        }

        // check for non-matching group
        if (region.beg[group] == -1) {
            return null;
        }

        // convert between byte-pos and char-pos and return input.substr
        int charStart = b2c(region.beg[group]);
        int charEnd = b2c(region.end[group]);
        return input.substring(charStart, charEnd);
    }

    private void addMulti(int bytePos) {
        // expand array if needed
        if (numMultis == multiPos.length) {
            int[] tmp = new int[multiPos.length * 2];
            System.arraycopy(multiPos, 0, tmp, 0, multiPos.length);
            multiPos = tmp;
        }
        multiPos[numMultis++] = bytePos;
    }

    // convert between byte-pos and char-pos using index
    private int b2c(int bytePos) {
        int result = bytePos;
        for (int i = 0; i < numMultis && bytePos > multiPos[i]; i++) {
            result--;
        }
        return result;
    }

    // convert between byte-pos and char-pos using index
    private int c2b(int charPos) {
        for (int i = 0; i < numMultis && charPos > multiPos[i]; i++) {
            charPos++;
        }
        return charPos;
    }

    /**
     * Compile javascript regexp to joni syntax
     * @param regexp regexp to compile
     * @param bomWs true if \s matches unicode byte-order-mark
     * @return equivalent regexp to be used by joni
     */
    private String js2joni() {
        // Translate regexp from js to joni.
        // Some symbols have different translations depending on whether
        // they are used as regular characters (c), or inside a character
        // class in either positive (cc+) or negated (cc-) form.
        // 1.      octal      => 'u00hh'
        // 2.      '\d':      => '[0-9]'
        // 3.      '\D':      => '[^0-9]'
        // 4.      '\s':
        //   c)               => '[\s\ufeff]'
        //   cc+-)            => '\s\ufeff'
        // 5.      '\S':
        //   c)               => '[\S&&[^\ufeff]]'
        //   cc+)             => '\S&&[^\ufeff]'
        //   cc-)             => '[\S&&[^\ufeff]]'
        // 6.      '\xhh'     => 'u00hh'
        // 7.      '\ u<bad>' => 'u<bad>'
        // 8.cc+-) '['        => '\['
        // 9.c)    '[^]'      => '[\s\S]'
        // 10.     Remove invalid backreferences to negative lookaheads

        StringBuilder joni = new StringBuilder();
        boolean inCC = false; // inside character class
        boolean negCC = false; // negated character class
        boolean inNegLook = false; // negative lookahead

        int negLookBrackets = 0;
        int captureCount = 0;
        negLookCapBs = 0;

        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            switch (c) {
            case '\\': // escape-sequence
                if (source.length() <= i) { // dangling slash at end of regexp
                    joni.append('\\');
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
                            joni.append('\\').append(c);
                        }

                    // literal '\\c'
                    } else if (cdec > 7) {
                        joni.append('\\').append('\\').append(c);

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
                        joni.append("\\u").append(u4);
                    }
                    break;
                case 'd':
                    joni.append("[0-9]"); // 2) '\d' => '[0-9]'
                    break;
                case 'D':
                    joni.append("[^0-9]"); // 3) '\D' => '[^0-9]'
                    break;
                case 's':
                    if (!bomWs) {
                        joni.append("\\s");
                        break;
                    }
                    if (inCC) { // 4.cc+- '\s' => '\s\ufeff'
                        joni.append("\\s\\ufeff");
                    } else { // 4.c '\s' => '[\s\ufeff]'
                        joni.append("[\\s\\ufeff]");
                    }
                    break;
                case 'S':
                    if (!bomWs) {
                        joni.append("\\S");
                        break;
                    }
                    if (inCC) {
                        if (negCC) { // 5.cc- '\S' => '[\S&&[^\ufeff]]'
                            joni.append("[\\S&&[^\\ufeff]]");
                        } else { // 5.cc+ '\S' => '\S&&[^\ufeff]'
                            joni.append("\\S&&[^\\ufeff]");
                        }
                    } else { // 5.c '\S' => '[\S&&[^\ufeff]]'
                        joni.append("[\\S&&[^\\ufeff]]");
                    }
                    break;
                case 'x': // 6.* '\xhh' => 'u00hh'
                    joni.append("\\u00");
                    break;
                case 'u': // 7. '\ u<bad unicode>' => 'u<bad unicode>'
                    boolean baduhex = i + 4 >= source.length();
                    if (!baduhex) {
                        int stop = i + 5;
                        for (int j = i + 1; j < stop; j++) {
                            char h = source.charAt(j);
                            if (h > 127 || !IS_HEX[h]) {
                                baduhex = true;
if (h > 127) System.out.println("bigger than 127: " + (int) h + " : " + source);
                                break;
                            }
                        }
                    }
                    if (!baduhex) {
                        joni.append('\\');
                    }
                    joni.append('u');
                    break;
                default: // leave the escape 'as-is'
                    joni.append('\\').append(c);
                }
                break;
            case '[':
                if (inCC) { // 8.cc+- '[' => '\['
                    joni.append("\\[");
                    break;
                }

                // start of character class, check if negated
                if (source.length() > i + 1 && source.charAt(i + 1) == '^') {
                    // check for special match of '[^]'
                    if (source.length() > i + 2 && source.charAt(i + 2) == ']') {
                        // 9. '[^]' => '[\s\S]'
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
            case '(':
                // 10. Remove invalid backreferences to negative lookaheads
                // count groups and take note of groups in neg lookahead
                if (i+1 < source.length() && source.charAt(i + 1) == '?') {
                    if (i+2 < source.length() && source.charAt(i+2) == '!') {
                        inNegLook = true;
                        negLookBrackets++;
                    }
                } else {
                    captureCount++;
                    // set pos/neg look bitset
                    // count brackets inside lookahead so we
                    // know when lookahead finishes
                    if (inNegLook) {
                        negLookCapBs |= (1 << captureCount);
                        negLookBrackets++;
                    }
                }
                joni.append('(');
                break;
            case ')':
                // check if neg lookahead is finished
                if (inNegLook) {
                    negLookBrackets--;
                    inNegLook = negLookBrackets > 0;
                }
                joni.append(')');
                break;
            case '\n':
                joni.append("\\n");
                break;
            case '\r':
                joni.append("\\r");
                break;
            default:
                joni.append(c);
            }
        }
        return joni.toString();
    }

    public static void main(String[] args) throws Exception {
        String source = "(a|b*)*";
        String input = "a";
        REJoni j = new REJoni(source, false, false, false, false, false);
        j.setInput(input);
        boolean found = j.find(0, false);
        System.out.println("joni source: " + j.joniSource);
        System.out.println(found + " at: " + j.start());
        for (int i = 0; i <= j.groupCount(); i++) {
            System.out.println("group " + i + ": " + j.group(i));
        }
//        Regex regex = new Regex(source.getBytes(), 0, source.length(),
//                Option.NONE, ASCIIEncoding.INSTANCE);
//        Matcher matcher = regex.matcher(input.getBytes());
//        int index = matcher.search(0, input.length(), Option.NONE);
//        System.out.println("found at: " + index);
//        Region region = matcher.getEagerRegion();
//        for (int i = 0; i < region.numRegs; i++) {
//            String group = region.beg[i] != -1 ? input.substring(region.beg[i], region.end[i]) :  null;
//            System.out.println("region: " + i + ": [" + group + "], " + region.beg[i] + "-" + region.end[i]);
//        }
    }
}
