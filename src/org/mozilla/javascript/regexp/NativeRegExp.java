/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1998.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Norris Boyd
 *   Igor Bukanov
 *   Brendan Eich
 *   Matthias Radestock
 *   Joel Hockey
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */
package org.mozilla.javascript.regexp;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.IdFunctionObject;
import org.mozilla.javascript.IdScriptableObject;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;


/**
 * This class implements the RegExp native object.
 *
 * Revision History:
 * Implementation in C by Brendan Eich
 * Initial port to Java by Norris Boyd from jsregexp.c version 1.36
 * Merged up to version 1.38, which included Unicode support.
 * Merged bug fixes in version 1.39.
 * Merged JSFUN13_BRANCH changes up to 1.32.2.13
 * Split regexp engine to use interface and allow other implementations
 *  such as java.util.regex and joni
 *
 * @author Brendan Eich
 * @author Norris Boyd
 * @author Joel Hockey
 */
public class NativeRegExp extends IdScriptableObject implements Function {
    static final long serialVersionUID = 4965263491464903264L;
    private static final Object REGEXP_TAG = new Object();
    private static final boolean[] REGEXP_LKP_BS_META = new boolean[128];
    private static final String REGEXP_BS_META = "\\^$*+?[()|.{";

    //type of match to perform
    public static final int TEST = 0;
    public static final int MATCH = 1;
    public static final int PREFIX = 2;

    static {
        for (int i = 0; i < REGEXP_BS_META.length(); i++) {
            REGEXP_LKP_BS_META[REGEXP_BS_META.charAt(i)] = true;
        }
    }

    // #string_id_map#
    private static final int Id_lastIndex = 1;

    // #string_id_map#
    private static final int Id_source = 2;

    // #string_id_map#
    private static final int Id_global = 3;

    // #string_id_map#
    private static final int Id_ignoreCase = 4;

    // #string_id_map#
    private static final int Id_multiline = 5;

    // #string_id_map#
    private static final int MAX_INSTANCE_ID = 5;
    private static final int Id_compile = 1;
    private static final int Id_toString = 2;
    private static final int Id_toSource = 3;
    private static final int Id_exec = 4;
    private static final int Id_test = 5;
    private static final int Id_prefix = 6;
    private static final int MAX_PROTOTYPE_ID = 6;

    // #/string_id_map#
    RegExpEngine re;
    double lastIndex; /* index after last match, for //g iterator */

    NativeRegExp(Scriptable scope, Object regexpEngine) {
        this.re = (RegExpEngine) regexpEngine;
        this.lastIndex = 0;
        ScriptRuntime.setObjectProtoAndParent(this, scope);
    }

    NativeRegExp() {
    }

    public static void init(Context cx, Scriptable scope, boolean sealed) {
        NativeRegExp proto = new NativeRegExp();
        proto.re = (RegExpEngine) compileRE(cx, "", null, false);
        proto.activatePrototypeMap(MAX_PROTOTYPE_ID);
        proto.setParentScope(scope);
        proto.setPrototype(getObjectPrototype(scope));

        NativeRegExpCtor ctor = new NativeRegExpCtor();
        // Bug #324006: ECMA-262 15.10.6.1 says "The initial value of
        // RegExp.prototype.constructor is the builtin RegExp constructor."
        proto.defineProperty("constructor", ctor, ScriptableObject.DONTENUM);

        ScriptRuntime.setFunctionProtoAndParent(ctor, scope);

        ctor.setImmunePrototypeProperty(proto);

        if (sealed) {
            proto.sealObject();
            ctor.sealObject();
        }

        defineProperty(scope, "RegExp", ctor, ScriptableObject.DONTENUM);
    }

    @Override
    public String getClassName() {
        return "RegExp";
    }

    /**
     * Gets the value to be returned by the typeof operator called on this object.
     * @see org.mozilla.javascript.ScriptableObject#getTypeOf()
     * @return "object"
     */
    @Override
    public String getTypeOf() {
        return "object";
    }

    public Object call(Context cx, Scriptable scope, Scriptable thisObj,
        Object[] args) {
        return execSub(cx, scope, args, MATCH);
    }

    public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
        return (Scriptable) execSub(cx, scope, args, MATCH);
    }

    Scriptable compile(Context cx, Scriptable scope, Object[] args) {
        if ((args.length > 0) && args[0] instanceof NativeRegExp) {
            if ((args.length > 1) && (args[1] != Undefined.instance)) {
                // report error
                throw ScriptRuntime.typeError0("msg.bad.regexp.compile");
            }

            NativeRegExp thatObj = (NativeRegExp) args[0];
            this.re = thatObj.re;
            this.lastIndex = thatObj.lastIndex;

            return this;
        }

        String s = (args.length == 0) ? "" : ScriptRuntime.toString(args[0]);
        String global = ((args.length > 1) && (args[1] != Undefined.instance))
            ? ScriptRuntime.toString(args[1]) : null;
        this.re = (RegExpEngine) compileRE(cx, s, global, false);
        this.lastIndex = 0;

        return this;
    }

    @Override
    public String toString() {
        return toString(re);
    }

    public static String toString(RegExpEngine re) {
        StringBuilder buf = new StringBuilder();
        buf.append('/');

        if (re.source().length() != 0) {
            buf.append(re.source());
        } else {
            // See bugzilla 226045
            buf.append("(?:)");
        }

        buf.append('/');

        if (re.global()) {
            buf.append('g');
        }

        if (re.ignoreCase()) {
            buf.append('i');
        }

        if (re.multiline()) {
            buf.append('m');
        }

        return buf.toString();
    }

    private static RegExpImpl getImpl(Context cx) {
        return (RegExpImpl) ScriptRuntime.getRegExpProxy(cx);
    }

    private Object execSub(Context cx, Scriptable scopeObj, Object[] args,
        int matchType) {
        RegExpImpl reImpl = getImpl(cx);

        if (args.length == 0) {
            reportError("msg.no.re.input.for", toString());
        }

        String str = ScriptRuntime.toString(args[0]);

        double d = re.global() ? lastIndex : 0;

        Object rval;

        if ((d < 0) || (str.length() < d)) {
            lastIndex = 0;
            rval = null;
        } else {
            int[] indexp = { (int) d };
            rval = executeRegExp(cx, scopeObj, reImpl, str, indexp, matchType);

            if (re.global()) {
                lastIndex = ((rval == null) || (rval == Undefined.instance))
                    ? 0 : indexp[0];
            }
        }

        return rval;
    }

    public static RegExpEngine compileRE(Context cx, String source,
        String flags, boolean escape) {
        if (flags == null) {
            flags = "";
        }

        boolean global = false;
        boolean ignoreCase = false;
        boolean multiline = false;

        for (int i = 0; i < flags.length(); i++) {
            char c = flags.charAt(i);

            if (c == 'g') {
                global = true;
            } else if (c == 'i') {
                ignoreCase = true;
            } else if (c == 'm') {
                multiline = true;
            } else {
                reportError("msg.invalid.re.flag", String.valueOf(c));
            }
        }

        // TODO: only set this if version >= 1.8.5
        boolean bomWs = true;

        // check if we can use REIndexOf engine which is fastest
        // or if we have to escape pattern, depends on escape and ignoreCase

        //  escape  ignoreCase    action
        // -----------------------------------------------
        //   T        T         escape, use standard engine
        //   T        F         use IndexOf engine
        //   F        T         use standard engine
        //   F        F         if compileTextOnly, use IndexOf else standard

        if (escape) {
            if (ignoreCase) {
return new REJoni(escRe(source), global, ignoreCase, multiline, bomWs);
//return new RERhino(cx, source, global, ignoreCase, multiline, escape);
//return new REJavaUtilRegex(source, global, ignoreCase, multiline, escape, bomWs);
            } else {
                return new REIndexOf(source, source, global, multiline);
            }
        } else {
            if (ignoreCase) {
return new REJoni(source, global, ignoreCase, multiline, bomWs);
//return new RERhino(cx, source, global, ignoreCase, multiline, escape);
//return new REJavaUtilRegex(source, global, ignoreCase, multiline, false, bomWs);
            } else {
                String compiled = compileTextOnly(source);

                if (compiled != null) {
                    return new REIndexOf(source, compiled, global, multiline);
                } else {
return new REJoni(source, global, ignoreCase, multiline, bomWs);
//return new RERhino(cx, source, global, ignoreCase, multiline, escape);
//return new REJavaUtilRegex(source, global, ignoreCase, multiline, false, bomWs);
                }
            }
        }
    }

    /*
     * indexp is assumed to be an array of length 1
     */
    Object executeRegExp(Context cx, Scriptable scopeObj, RegExpImpl res,
        String str, int[] indexp, int matchType) {

        int start = indexp[0];
        int end = str.length();

        if (start > end) {
            start = end;
        }

        // only set the input if it has changed, this has big perf gain
        // when regexp engine converts string to char or byte array
        if (!str.equals(re.getInput())) {
            re.setInput(str);
        }

        // Call the engine matcher to do the real work.
        boolean matches = re.find(start, res.multiline);
        if (!matches) {
            if (matchType != PREFIX) {
                return null;
            }

            return Undefined.instance;
        }

        indexp[0] = re.end();
        Object result;
        Scriptable obj;

        if (matchType == TEST) {
            /*
             * Testing for a match and updating cx.regExpImpl: don't allocate
             * an array object, do return true.
             */
            result = Boolean.TRUE;
            obj = null;
        } else {
            /*
             * The array returned on match has element 0 bound to the matched
             * string, elements 1 through re.parenCount bound to the paren
             * matches, an index property telling the length of the left context,
             * and an input property referring to the input string.
             */
            Scriptable scope = getTopLevelScope(scopeObj);
            result = ScriptRuntime.newObject(cx, scope, "Array", null);
            obj = (Scriptable) result;

            String matchstr = re.group(0);
            obj.put(0, obj, matchstr);
        }

        res.input = str;

        if (re.groupCount() == 0) {
            res.parens = null;
            res.lastParen = "";
        } else {
            res.parens = new String[re.groupCount()];

            for (int group = 1; group <= re.groupCount(); group++) {
                String cap = re.group(group);
                res.parens[group - 1] = re.group(group);

                if (matchType != TEST) {
                    obj.put(group, obj,
                            cap != null ? cap : Undefined.instance);
                }
            }
            res.lastParen = res.parens[re.groupCount() - 1];
        }

        if (!(matchType == TEST)) {
            /*
             * Define the index and input properties last for better for/in loop
             * order (so they come after the elements).
             */
            obj.put("index", obj, Integer.valueOf(re.start()));

            obj.put("input", obj, str);
        }

        if (res.lastMatch == null) {
            res.lastMatch = "";
            res.leftContext = "";
            res.rightContext = "";
        }

        res.lastMatchStart = re.start();
        res.lastMatch = re.group(0);

        if (cx.getLanguageVersion() == Context.VERSION_1_2) {
            /*
             * JS1.2 emulated Perl4.0.1.8 (patch level 36) for global regexps used
             * in scalar contexts, and unintentionally for the string.match "list"
             * psuedo-context.  On "hi there bye", the following would result:
             *
             * Language     while(/ /g){print("$`");}   s/ /$`/g
             * perl4.036    "hi", "there"               "hihitherehi therebye"
             * perl5        "hi", "hi there"            "hihitherehi therebye"
             * js1.2        "hi", "there"               "hihitheretherebye"
             *
             * Insofar as JS1.2 always defined $` as "left context from the last
             * match" for global regexps, it was more consistent than perl4.
             */

            res.leftContext = str.substring(start, re.start());
        } else {
            /*
             * For JS1.3 and ECMAv2, emulate Perl5 exactly:
             *
             * js1.3        "hi", "hi there"            "hihitherehi therebye"
             */

            res.leftContext = str.substring(0, re.start());
        }

        res.rightContext = str.substring(re.end(), end);

        return result;
    }

    private static void reportWarning(Context cx, String messageId, String arg) {
        if (cx.hasFeature(Context.FEATURE_STRICT_MODE)) {
            String msg = ScriptRuntime.getMessage1(messageId, arg);
            Context.reportWarning(msg);
        }
    }

    static void reportError(String messageId, String arg) {
        String msg = ScriptRuntime.getMessage1(messageId, arg);
        throw ScriptRuntime.constructError("SyntaxError", msg);
    }

    @Override
    protected int getMaxInstanceId() {
        return MAX_INSTANCE_ID;
    }

    @Override
    protected int findInstanceIdInfo(String s) {
        int id;

// #generated# Last update: 2007-05-09 08:16:24 EDT
L0:  {
            id = 0;

            String X = null;
            int c;
            int s_length = s.length();

            if (s_length == 6) {
                c = s.charAt(0);

                if (c == 'g') {
                    X = "global";
                    id = Id_global;
                } else if (c == 's') {
                    X = "source";
                    id = Id_source;
                }
            } else if (s_length == 9) {
                c = s.charAt(0);

                if (c == 'l') {
                    X = "lastIndex";
                    id = Id_lastIndex;
                } else if (c == 'm') {
                    X = "multiline";
                    id = Id_multiline;
                }
            } else if (s_length == 10) {
                X = "ignoreCase";
                id = Id_ignoreCase;
            }

            if ((X != null) && (X != s) && !X.equals(s)) {
                id = 0;
            }

            break L0;
        }

        // #/generated#
        // #/string_id_map#
        if (id == 0) {
            return super.findInstanceIdInfo(s);
        }

        int attr;

        switch (id) {
        case Id_lastIndex:
            attr = PERMANENT | DONTENUM;

            break;

        case Id_source:
        case Id_global:
        case Id_ignoreCase:
        case Id_multiline:
            attr = PERMANENT | READONLY | DONTENUM;

            break;

        default:
            throw new IllegalStateException();
        }

        return instanceIdInfo(attr, id);
    }

    @Override
    protected String getInstanceIdName(int id) {
        switch (id) {
        case Id_lastIndex:
            return "lastIndex";

        case Id_source:
            return "source";

        case Id_global:
            return "global";

        case Id_ignoreCase:
            return "ignoreCase";

        case Id_multiline:
            return "multiline";
        }

        return super.getInstanceIdName(id);
    }

    @Override
    protected Object getInstanceIdValue(int id) {
        switch (id) {
        case Id_lastIndex:
            return ScriptRuntime.wrapNumber(lastIndex);

        case Id_source:
            return re.source();

        case Id_global:
            return ScriptRuntime.wrapBoolean(re.global());

        case Id_ignoreCase:
            return ScriptRuntime.wrapBoolean(re.ignoreCase());

        case Id_multiline:
            return ScriptRuntime.wrapBoolean(re.multiline());
        }

        return super.getInstanceIdValue(id);
    }

    @Override
    protected void setInstanceIdValue(int id, Object value) {
        switch (id) {
        case Id_lastIndex:
            lastIndex = ScriptRuntime.toNumber(value);

            return;

        case Id_source:
        case Id_global:
        case Id_ignoreCase:
        case Id_multiline:
            return;
        }

        super.setInstanceIdValue(id, value);
    }

    @Override
    protected void initPrototypeId(int id) {
        String s;
        int arity;

        switch (id) {
        case Id_compile:
            arity = 1;
            s = "compile";

            break;

        case Id_toString:
            arity = 0;
            s = "toString";

            break;

        case Id_toSource:
            arity = 0;
            s = "toSource";

            break;

        case Id_exec:
            arity = 1;
            s = "exec";

            break;

        case Id_test:
            arity = 1;
            s = "test";

            break;

        case Id_prefix:
            arity = 1;
            s = "prefix";

            break;

        default:
            throw new IllegalArgumentException(String.valueOf(id));
        }

        initPrototypeMethod(REGEXP_TAG, id, s, arity);
    }

    @Override
    public Object execIdCall(IdFunctionObject f, Context cx, Scriptable scope,
        Scriptable thisObj, Object[] args) {
        if (!f.hasTag(REGEXP_TAG)) {
            return super.execIdCall(f, cx, scope, thisObj, args);
        }

        int id = f.methodId();

        switch (id) {
        case Id_compile:
            return realThis(thisObj, f).compile(cx, scope, args);

        case Id_toString:
        case Id_toSource:
            return realThis(thisObj, f).toString();

        case Id_exec:
            return realThis(thisObj, f).execSub(cx, scope, args, MATCH);

        case Id_test: {
            Object x = realThis(thisObj, f).execSub(cx, scope, args, TEST);

            return Boolean.TRUE.equals(x) ? Boolean.TRUE : Boolean.FALSE;
        }

        case Id_prefix:
            return realThis(thisObj, f).execSub(cx, scope, args, PREFIX);
        }

        throw new IllegalArgumentException(String.valueOf(id));
    }

    private static NativeRegExp realThis(Scriptable thisObj, IdFunctionObject f) {
        if (!(thisObj instanceof NativeRegExp)) {
            throw incompatibleCallError(f);
        }

        return (NativeRegExp) thisObj;
    }

    // #string_id_map#
    @Override
    protected int findPrototypeId(String s) {
        int id;

// #generated# Last update: 2007-05-09 08:16:24 EDT
L0:  {
            id = 0;

            String X = null;
            int c;
L:
            switch (s.length()) {
            case 4:
                c = s.charAt(0);

                if (c == 'e') {
                    X = "exec";
                    id = Id_exec;
                } else if (c == 't') {
                    X = "test";
                    id = Id_test;
                }

                break L;

            case 6:
                X = "prefix";
                id = Id_prefix;

                break L;

            case 7:
                X = "compile";
                id = Id_compile;

                break L;

            case 8:
                c = s.charAt(3);

                if (c == 'o') {
                    X = "toSource";
                    id = Id_toSource;
                } else if (c == 't') {
                    X = "toString";
                    id = Id_toString;
                }

                break L;
            }

            if ((X != null) && (X != s) && !X.equals(s)) {
                id = 0;
            }

            break L0;
        }

        // #/generated#
        return id;
    }

    boolean js_test(Context cx, Scriptable scope, String input) {
        //        // only set input if different to avoid possible charset conversion
        //        if (!input.equals(re.getInput())) {
        //            re.setInput(input);
        //        }
        //
        //        // start form lastIndex
        //        if (lastIndex >= re.getInput().length() || !re.find(lastIndex)) {
        //            lastIndex = 0;
        //            return false;
        //        }
        //
        //        // update static RegExp properties when we find a match
        //        RegExpImpl reImpl = getImpl(cx);
        //        reImpl.setStaticProps(cx, re, lastIndex);
        //
        //        // update local lastIndex if global
        //        if (re.global()) {
        //            lastIndex = re.end();
        //        }
        return true;
    }

    Scriptable js_exec(Context cx, Scriptable scope, String input) {
        //        // only set input if different to avoid possible charset conversion
        //        if (!input.equals(re.getInput())) {
        //            re.setInput(input);
        //        }
        //
        //        // start from lastIndex
        //        if (lastIndex >= re.getInput().length() || !re.find(lastIndex)) {
        //            lastIndex = 0;
        //            return null;
        //        }
        //
        //        // update static RegExp properties when we find a match
        //        RegExpImpl reImpl = getImpl(cx);
        //        reImpl.setStaticProps(cx, re, lastIndex);
        //
        //        // update local lastIndex if global
        //        if (re.global()) {
        //            lastIndex = re.end();
        //        }

        // put results into array
        // a[0] = match, a[n] = captured groups
        Scriptable result = cx.newObject(scope, "Array");

        //        for (int i = 0; i <= re.groupCount(); i++) {
        //            result.put(i, result, re.group(i));
        //        }
        //
        //        // a.index = leftContext.length, a.input = input
        //        result.put("index", result, re.start());
        //        result.put("input", result, re.getInput());
        return result;
    }

    private String getInput(Object[] args) {
        if (args.length == 0) {
            reportError("msg.no.re.input.for", toString());
        }

        return ScriptRuntime.toString(args[0]);
    }

    /**
     * If possible, compile regexp into text-only string
     * that can be used with String.indexOf matching, else return null
     * @param regexp regexp to parse
     * @return compiled text-only string or null
     */
    public static String compileTextOnly(String regexp) {
        StringBuilder result = new StringBuilder(regexp.length());

        try {
            for (int i = 0; i < regexp.length(); i++) {
                char c = regexp.charAt(i);

                // no special handling for chars > 127
                if (c > 127) {
                    result.append(c);

                    continue;
                }

                // compiling not required for non bslashMeta
                if (!REGEXP_LKP_BS_META[c]) {
                    result.append(c);

                    continue;
                }

                // we have bslash or meta, compile fails if meta
                if (c != '\\') {
                    return null;
                }

                // we got a backslash - allow escape sequences that can
                // be converted to literal chars '\0cfnrtuxv',
                // fail if backref '123456789' or char class 'bBdDsSwW'
                // else, treat as literal char
                c = regexp.charAt(++i);

                int xusize = 0; // this will be either 2 or 4

                switch (c) {
                case '\\':
                    result.append('\\');

                    break;

                case '0':
                    result.append((char) 0);

                    break;

                case 'c':
                    c = regexp.charAt(++i);

                    if (((c >= 'A') && (c <= 'Z')) ||
                            ((c >= 'a') && (c <= 'z'))) {
                        // take last 5-bits for ctrl char
                        result.append((char) (c & 0x1f));
                    } else {
                        return null;
                    }

                    break;

                case 'f':
                    result.append('\f');

                    break;

                case 'n':
                    result.append('\n');

                    break;

                case 'r':
                    result.append('\r');

                    break;

                case 't':
                    result.append('\t');

                    break;

                case 'v':
                    result.append((char) 0x0b);

                    break;

                case 'u':
                    // set xusize=2 and fall through, xusize will end up as 4
                    xusize = 2;

                case 'x':
                    xusize += 2; // add 2 more to xusize

                    char xu = 0;

                    for (int j = 0; j < xusize; j++) {
                        c = regexp.charAt(++i);
                        xu <<= 4;

                        if ((c >= '0') && (c <= '9')) {
                            xu |= (c - '0');
                        } else if ((c >= 'A') && (c <= 'F')) {
                            xu |= (c - 'A' + 10);
                        } else if ((c >= 'a') && (c <= 'f')) {
                            xu |= (c - 'a' + 10);
                        } else {
                            return null;
                        }
                    }

                    result.append(xu);

                    break;

                // fail if backref '123456789' or char class 'bBdDsSwW'
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                case 'b':
                case 'B':
                case 'd':
                case 'D':
                case 's':
                case 'S':
                case 'w':
                case 'W':
                    return null;

                // else, treat as literal char
                default:
                    result.append(c);
                }
            }
        } catch (Exception e) {
            return null;
        }

        return result.toString();
    }

    /**
     * Escape regexp.  Escape backslash and meta characters '\^$*+?[()|.{';
     * @param regexp regexp to escape
     * @return escaped regexp
     */
    public static String escRe(String regexp) {
        StringBuilder result = new StringBuilder(regexp.length() * 2);
        for (int i = 0; i < regexp.length(); i++) {
            char c = regexp.charAt(i);
            if ((c <= 127) && REGEXP_LKP_BS_META[c]) {
                result.append('\\');
            }
            result.append(c);
        }
        return result.toString();
    }
}
