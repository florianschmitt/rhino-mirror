package org.mozilla.javascript.regexp;

import junit.framework.TestCase;

/**
 * Tests java.util.regex RegExp engine
 * @author Joel Hockey
 */
public class REJavaUtilRegexTest extends TestCase {
   private static String[][] TESTS = {
       {"(.*?)a(?!(a+)b\\2c)\\2(.*)", "(.*?)a(?!(a+)b\\2c)(.*)"},
       {"(.)x\\2(?:.)(?!(.(?!x))\\1x\\2)(g3)\\1\\2\\3\\(?!(.)\\2\\)",
           "(.)x\\2(?:.)(?!(.(?!x))\\1x\\2)(g3)\\1\\3\\(?!(.)\\)"},
   };


   public void testJsToJavaUtilRegex() {
       for (int i = 0; i < TESTS.length; i++) {
           REJavaUtilRegex r = new REJavaUtilRegex(TESTS[i][0],
                   false, false, false, false, true);
           assertEquals("js2joni test: " + (i+1), TESTS[i][1],
                   r.js2javaUtilRegex());
       }
   }
}
