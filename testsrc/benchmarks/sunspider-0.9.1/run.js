var tests = [
    "3d-cube", "3d-morph", "3d-raytrace",
    "access-binary-trees", "access-fannkuch", "access-nbody", "access-nsieve",
    "bitops-3bit-bits-in-byte", "bitops-bits-in-byte", "bitops-bitwise-and", "bitops-nsieve-bits",
    "controlflow-recursive", "crypto-aes", "crypto-md5", "crypto-sha1",
    "date-format-tofte", "date-format-xparb",
    "math-cordic", "math-partial-sums", "math-spectral-norm",
    "regexp-dna",
    "string-base64", "string-fasta", "string-tagcloud", "string-unpack-code", "string-validate-input"
];

var repeatCount = 10;

var results = { times: [], categories: {}};
for (var i = 0; i < tests.length; i++) {
    var test = tests[i];
    var dash = test.indexOf('-');
    var category = test.substring(0, dash);
    var testName = test.substring(dash + 1);
    results.categories[category] = results.categories[category] || { name: category, times: [], tests: {}};
    results.categories[category].tests[testName] = {name: testName, times: []};
}

for (var currentRepeat = -1; currentRepeat < repeatCount; currentRepeat++) {
    var totalTime = 0;
    for (var p in results.categories) {
        if (!results.categories.hasOwnProperty(p)) continue;
        var category = results.categories[p];
        var categoryTime = 0;
        for (var q in category.tests) {
            if (!category.tests.hasOwnProperty(q)) continue;
            var test = category.tests[q];
            print('iteration ' + lpad('' + currentRepeat, 2) + ' : loading ' + category.name + '-' + test.name + '.js')
            var start = new Date();
            load(category.name + "-" + test.name + ".js");
            var time = new Date() - start;
            totalTime += time;
            categoryTime += time;
            test.times.push(time);
        }
        category.times.push(categoryTime);
    }
    results.times.push(totalTime);
}

function lpad(s, n) { while (s.length < n) s = ' ' + s; return s; }
function rpad(s, n) { while (s.length < n) s += ' '; return s; }
function timeDisplay(times) {
    // skip first since it was warmup
    var sum = 0;
    var sumsq = 0;
    for (var i = 1; i < times.length; i++) {
        sum += times[i];
        sumsq += (times[i] * times[i]);
    }
    var mean = sum / repeatCount;
    var variance = (sumsq - (sum * mean)) / (repeatCount - 1);
    var stddev = Math.sqrt(variance);
    var percent = (2 * stddev) / mean * 100;
    return lpad(mean.toFixed(1), 8) + "ms +- " + lpad(percent.toFixed(1), 4) + "%";
}

// calculate mean, variance and display 95% CI (2 * stddev)

print("============================================");
print("RESULTS (means and 95% confidence intervals)");
print("--------------------------------------------");
print(rpad("Total:", 22) + timeDisplay(results.times));
print("--------------------------------------------");

for (var p in results.categories) {
    if (!results.categories.hasOwnProperty(p)) continue;
    var category = results.categories[p];
    print(rpad("  " + category.name + ":", 22) + timeDisplay(category.times));
    for (var q in category.tests) {
        if (!category.tests.hasOwnProperty(q)) continue;
        var test = category.tests[q];
        print(rpad("    " + test.name + ":", 22) + timeDisplay(test.times));
    }
}
