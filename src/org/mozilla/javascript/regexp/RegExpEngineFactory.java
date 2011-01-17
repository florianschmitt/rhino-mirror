package org.mozilla.javascript.regexp;

import java.io.Serializable;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Kit;
import org.mozilla.javascript.RegExpEngine;

/**
 * First looks for system property {@code rhino.regexp.engine}.
 * Possible values are {@code joni}, {@code java.util.regex}, and
 * {@code rhino}.
 * If property not set, then look in classpath to detect joni,
 * or java.util.regex in that order, else fall back on default
 * rhino engine.
 *
 * @author Joel Hockey
 */
public class RegExpEngineFactory implements RegExpEngine.Factory, Serializable {
    private static final long serialVersionUID = 0x275AD2C034392B57L;

    public static final RegExpEngine.Factory INSTANCE;

    static {
        String sysProp = System.getProperty("rhino.regexp.engine");
        // look for system prop
        if ("joni".equalsIgnoreCase(sysProp)) {
            INSTANCE = new REJoni.Factory();
        } else if ("java.util.regex".equalsIgnoreCase(sysProp)) {
            INSTANCE = new REJavaUtilRegex.Factory();
        } else if ("rhino".equalsIgnoreCase(sysProp)) {
            INSTANCE = new RERhino.Factory();

        // look in classpath for joni / java.util.regex
        } else if (Kit.classOrNull("org.joni.Regex") != null) {
            INSTANCE = new REJoni.Factory();
        } else if (Kit.classOrNull("java.util.regex.Pattern") != null) {
            INSTANCE = new REJavaUtilRegex.Factory();

        // default is rhino
        } else {
            INSTANCE = new RERhino.Factory();
        }
    }

    public RegExpEngine create(Context cx, String source, boolean global,
            boolean ignoreCase, boolean multiline, boolean literal,
            boolean bomWs) {

        return INSTANCE.create(cx, source, global, ignoreCase, multiline,
                literal, bomWs);
    }
}
