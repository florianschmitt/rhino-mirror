package org.mozilla.javascript.regexp;

import org.mozilla.javascript.regexp.NativeRegExp;

import junit.framework.TestCase;

/**
 * Test reegxp escaping, and detection of
 * test-only regexps that can use fastest REIndexOf engine.
 * @author Joel Hockey
 */
public class RegExpTest extends TestCase {

   public void testCompileEscape() {
       String[][] tests = {
               // escaped chars: \0cfnrtuxv
               {"", "", ""},
               {"\\\\", "\\", "\\\\\\\\"},
               {"\\0", "\u0000", "\\\\0"},
               {"\\1", null, "\\\\1"},
               {"\\cM", "\r", "\\\\cM"},
               {"\\cz", "\u001a", "\\\\cz"},
               {"\\c[", null, "\\\\c\\["},
               {"\\f\\n\\r\\t\\v", "\f\n\r\t\u000b",
                   "\\\\f\\\\n\\\\r\\\\t\\\\v"},
               {"abc\\", null, "abc\\\\"},
               {"\\x01", "\u0001", "\\\\x01"},
               {"\\xAf", "\u00af", "\\\\xAf"},
               {"\\x3h", null, "\\\\x3h"},
               {"\\u0060", "\u0060", "\\\\u0060"},
               {"\\u2fFC", "\u2ffc", "\\\\u2fFC"},
               {"\\s", null, "\\\\s"},
               // meta chars: ^$*+?[]()|{}
               {"\\^\\$\\*\\+\\?\\[\\]\\(\\)\\|\\{\\}",
                   "^$*+?[]()|{}",
                   "\\\\\\^\\\\\\$\\\\\\*\\\\\\+\\\\\\?\\\\\\[\\\\]\\\\\\(\\\\\\)\\\\\\|\\\\\\{\\\\}"},
               {"abc", "abc", "abc"},
               {"abc^abc", null, "abc\\^abc"},
               {"abc$abc", null, "abc\\$abc"},
       };

       for (int i = 0; i < tests.length; i++) {
           String textOnly = NativeRegExp.compileTextOnly(tests[i][0]);
           String escaped = NativeRegExp.escRe(tests[i][0]);
           assertEquals("text-only test: " + i, tests[i][1], textOnly);
           assertEquals("escape test: " + i, tests[i][2], escaped);
       }
   }

}