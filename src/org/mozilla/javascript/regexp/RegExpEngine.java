package org.mozilla.javascript.regexp;

/**
 * Interface for RegExp engine.  Allows any regexp engine to be used
 * by rhino.
 * This interface is modelled closely on
 * {@link java.util.regex.Matcher} with similar semantics.
 * @author Joel Hockey
 */
public interface RegExpEngine {

    /** @return true if global flag (/.../g)specified */
    boolean global();

    /** @return true if ignoreCase flag (/.../i)specified */
    boolean ignoreCase();

    /** @return true if multiline flag (/.../m)specified */
    boolean multiline();

    /** @return source of regex /&lt;source>/, new RegExp(source) */
    String source();

    /** @param input input to match against */
    void setInput(String input);

    /** @param input to match against, or null if no input set */
    String getInput();

    /**
     * Return true if match found from specified position.
     * @param start position to start search at
     * @param forceMultiline current value of RegExp.multiline
     * @return true if match found from specified position
     */
    boolean find(int start, boolean forceMultiline);

    /**
     * @return start of match.  Only valid after calling
     * {@link #find(int, boolean)} and getting a successful match.
     * @see RegExpEngine#find(int, boolean)
     */
    int start();

    /**
     * @return 1 character after end of match.  Only valid after calling
     * {@link #find(int, boolean)} and getting a successful match.
     * @see RegExpEngine#find(int, boolean)
     */
    int end();

    /**
     * @return number of capturing groups in regexp.  Only valid after calling
     * {@link #find(int, boolean)} and getting a successful match.
     * @see RegExpEngine#find(int, boolean)
     */
    int groupCount();

    /**
     * Get specified group.
     * @param group 0 will return entire regexp match.  1, 2, 3, ... match
     * captured groups.
     * @return group or null if no group matched.  Only valid after calling
     * {@link #find(int, boolean)} and getting a successful match.
     * @see RegExpEngine#find(int, boolean)
     */
    String group(int group);
}
