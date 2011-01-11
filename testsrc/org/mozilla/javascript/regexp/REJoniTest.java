package org.mozilla.javascript.regexp;

import junit.framework.TestCase;

/**
 * Tests joni RegExp engine
 * @author Joel Hockey
 */
public class REJoniTest extends TestCase {
   private static String[][] TESTS = {
       {"(.*?)a(?!(a+)b\\2c)\\2(.*)", "(.*?)a(?!(a+)b\\2c)(.*)"},
       {"(.)x\\2(?:.)(?!(.(?!x))\\1x\\2)(g3)\\1\\2\\3\\(?!(.)\\2\\)",
           "(.)x\\2(?:.)(?!(.(?!x))\\1x\\2)(g3)\\1\\3\\(?!(.)\\)"},
   };


   public void testJsToJoni() {
       for (int i = 0; i < TESTS.length; i++) {
           assertEquals("js2joni test: " + (i+1),
                   TESTS[i][1], REJoni.js2joni(TESTS[i][0], true));
       }
   }
}
