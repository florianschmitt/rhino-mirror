package org.mozilla.javascript.regexp;

import junit.framework.TestCase;

/**
 * Tests joni RegExp engine
 * @author Joel Hockey
 */
public class REJoniTest extends TestCase {
   private static String[][] TESTS = {
       // remove invalid backrefs
       {"(.*?)a(?!(a+)b\\2c)\\2(.*)", "(.*?)a(?!(a+)b\\2c)(.*)"},
       {"(.)x\\2(?:.)(?!(.(?!x))\\1x\\2)(g3)\\1\\2\\3\\(?!(.)\\2\\)",
           "(.)x\\u0002(?:.)(?!(.(?!x))\\1x\\2)(g3)\\1\\3\\(?!(.)\\)"},
       // octal escapes
       {"\\0", "\\u0000"},
       {"\\7", "\\u0007"},
       {"\\8", "\\\\8"},
       {"a(.)\\1\\2", "a(.)\\1\\u0002"},
       {"\\400", "\\u00200"},
       {"\\0\\0", "\\u0000\\u0000"},
   };


   public void testJsToJoni() {
       for (int i = 0; i < TESTS.length; i++) {
           REJoni joni = new REJoni(TESTS[i][0], false, false, false,
                   false, true);
           assertEquals("js2joni test: " + (i+1),
                   TESTS[i][1], joni.js2joni());
       }
   }
}
