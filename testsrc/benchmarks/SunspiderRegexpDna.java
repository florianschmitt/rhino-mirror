package benchmarks;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SunspiderRegexpDna {
    public static void main(String[] args) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        FileInputStream fis = new FileInputStream("RegexpDna.txt");
        for (int len = 0; (len = fis.read(buf)) != -1; ) {
            baos.write(buf, 0, len);
        }
        String dnaInput = baos.toString();

        int ITS = 10;
        for (int i = 0; i < ITS; i++) {
            long start = System.currentTimeMillis();
            regexpdna(dnaInput);
            long timeTaken = System.currentTimeMillis() - start;
            System.out.println(timeTaken);
        }
    }

    static void regexpdna(String dnaInput) throws Exception {
        // The Computer Language Shootout
        // http://shootout.alioth.debian.org/
        //
        // contributed by Jesse Millikan
        // Base on the Ruby version by jose fco. gonzalez

        dnaInput = dnaInput + dnaInput + dnaInput;

        // var ilen, clen,
        String[] seqs = {
                "agggtaaa|tttaccct",
                "[cgt]gggtaaa|tttaccc[acg]",
                "a[act]ggtaaa|tttacc[agt]t",
                "ag[act]gtaaa|tttac[agt]ct",
                "agg[act]taaa|ttta[agt]cct",
                "aggg[acg]aaa|ttt[cgt]ccct",
                "agggt[cgt]aa|tt[acg]accct",
                "agggta[cgt]a|t[acg]taccct",
                "agggtaa[cgt]|[acg]ttaccct",
        };
        Map<String, String> subs = new HashMap<String, String>();
        subs.put("B", "(c|g|t)");
        subs.put("D", "(a|g|t)");
        subs.put("H", "(a|c|t)");
        subs.put("K", "(g|t)");
        subs.put("M", "(a|c)");
        subs.put("N", "(a|c|g|t)");
        subs.put("R", "(a|g)");
        subs.put("S", "(c|t)");
        subs.put("V", "(a|c|g)");
        subs.put("W", "(a|t)");
        subs.put("Y", "(c|t)");

        int ilen = dnaInput.length();

        dnaInput = Pattern.compile(">.*\\n|\\n").matcher(dnaInput).replaceAll("");
        int clen = dnaInput.length();

        StringBuilder dnaOutputString = new StringBuilder();

        for (String i : seqs) {
            dnaOutputString.append(i).append(" ");
            dnaOutputString.append(match(i, dnaInput).size()).append("\n");
            // match returns null if no matches, so replace with empty
        }

        for (Map.Entry<String, String> k : subs.entrySet()) {
            dnaInput = dnaInput.replaceFirst(k.getKey(), k.getValue());
        }

//        System.out.println(dnaOutputString);
//        System.out.println(ilen);
//        System.out.println(clen);
//        System.out.println(dnaInput.length());
    }

    static List<String> match(String regexp, String s) throws Exception {
        List<String> result = new ArrayList<String>();
        Matcher m = Pattern.compile(regexp).matcher(s);
        while (m.find()) {
            result.add(m.group());
        }
        return result;
    }
}
