/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
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

package org.mozilla.javascript;

/**
 * RegExp engine interface to allow pluggable implementations.  Interface
 * located in <code>org.mozilla.javascript</code> directory so regexp
 * package can be loaded optionally.
 *
 * This interface is modelled closely on
 * {@link java.util.regex.Matcher} with similar semantics.
 * @author Joel Hockey
 */
public interface RegExpEngine {
    /**
     * Factory to create RegExpEngine instances
     */
    public interface Factory {
        /**
         * Create RegExp engine
         * @param cx current context
         * @param source regexp source <code>/&lt;source>/</code>
         * @param global true if global flag set
         * @param ignoreCase true if ignoreCase flag set
         * @param multiline true if multiline flag set
         * @param literal true if source to be treated as literal
         * @param bomWs if true, \s must match unicode byte-order-mark
         * @return RegExp engine instance
         */
        RegExpEngine create(Context cx, String source, boolean global,
                boolean ignoreCase, boolean multiline, boolean literal,
                boolean bomWs);
    }

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
