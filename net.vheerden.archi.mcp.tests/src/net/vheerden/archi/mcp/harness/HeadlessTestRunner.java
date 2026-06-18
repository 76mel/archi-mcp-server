package net.vheerden.archi.mcp.harness;

import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Headless JUnit4 runner for the build+test harness ({@code tools/run-tests.sh}).
 *
 * <p>This is NOT a test class (it has no {@code @Test} methods and does not match the
 * {@code *Test.java} scan glob, so the harness class-list scan never picks it up). It lives in
 * the <b>tests</b> project — never in production {@code net.vheerden.archi.mcp/src}
 * (tooling-only, zero production-code edits).</p>
 *
 * <p>Two things {@code org.junit.runner.JUnitCore} alone does not give us, and the reason this
 * class exists:</p>
 * <ol>
 *   <li><b>{@code testsRun > 0} postcondition.</b> For every class it runs, it computes the
 *       <i>effective</i> run count = tests that actually executed, <b>excluding</b> assumption-skipped
 *       tests ({@code org.junit.Assume}), {@code @Ignore}d tests, and the synthetic
 *       {@code initializationError} pseudo-test JUnit emits when a class fails to load/initialise.
 *       If a non-excluded class contributes an effective run count of 0, the harness
 *       <b>fails loudly</b> (non-zero exit) — the silent-stale guard {@code AllPluginTestsRunner}
 *       never had.</li>
 *   <li><b>Surefire-style JUnit XML.</b> One {@code TEST-<fqcn>.xml} per class to
 *       {@code -Dharness.xmlDir}, so GitHub Actions CI can publish it.</li>
 * </ol>
 *
 * <p>Usage: {@code java -cp <cp> net.vheerden.archi.mcp.harness.HeadlessTestRunner <fqcn> [<fqcn> ...]}</p>
 *
 * <p>System properties:</p>
 * <ul>
 *   <li>{@code -Dharness.xmlDir=<dir>} — emit surefire {@code TEST-*.xml} here (omit to skip XML).</li>
 *   <li>{@code -Dharness.classify=true} — classification mode: never fails the
 *       build; prints one {@code CLASSIFY <bucket> <fqcn> ...} line per class so each class can be
 *       sorted into the junit bucket vs the {@code tools/osgi-excluded-tests.txt} manifest. In this
 *       mode the {@code testsRun>0} guard is reported, not enforced.</li>
 * </ul>
 *
 * <p>Exit code: 0 only if every class passed AND every class satisfied {@code effectiveRan > 0}
 * (the latter enforced only outside classify mode). Non-zero otherwise.</p>
 */
public final class HeadlessTestRunner {

    private HeadlessTestRunner() {
    }

    private enum Status {
        PASSED, FAILED, ERROR, ASSUMPTION_SKIPPED, IGNORED
    }

    /** Synthetic method name JUnit uses for a class that fails to load/initialise. */
    private static final String INIT_ERROR = "initializationError";

    private static final class TestRecord {
        final String className;
        final String methodName;
        Status status = Status.PASSED;
        String failureMessage;
        String failureType;
        String failureTrace;
        long timeMs;
        long startNanos;

        TestRecord(String className, String methodName) {
            this.className = className;
            this.methodName = methodName;
        }

        boolean isInitError() {
            return INIT_ERROR.equals(methodName);
        }
    }

    private static final class RecordingListener extends RunListener {
        final Map<String, TestRecord> records = new LinkedHashMap<>();

        private static String methodOf(Description d) {
            String m = d.getMethodName();
            return m != null ? m : d.getDisplayName();
        }

        private static String classOf(Description d) {
            String c = d.getClassName();
            return c != null ? c : "UNKNOWN";
        }

        @Override
        public void testStarted(Description d) {
            TestRecord r = new TestRecord(classOf(d), methodOf(d));
            r.startNanos = System.nanoTime();
            records.put(d.getDisplayName(), r);
        }

        @Override
        public void testFinished(Description d) {
            TestRecord r = records.get(d.getDisplayName());
            if (r != null) {
                r.timeMs = (System.nanoTime() - r.startNanos) / 1_000_000L;
            }
        }

        @Override
        public void testFailure(Failure f) {
            Description d = f.getDescription();
            TestRecord r = records.get(d.getDisplayName());
            if (r == null) {
                // initializationError (and other class-level failures) arrive with no prior testStarted.
                r = new TestRecord(classOf(d), methodOf(d));
                records.put(d.getDisplayName(), r);
            }
            Throwable t = f.getException();
            r.status = (t instanceof AssertionError) ? Status.FAILED : Status.ERROR;
            r.failureMessage = f.getMessage();
            r.failureType = t != null ? t.getClass().getName() : "java.lang.Throwable";
            r.failureTrace = f.getTrace();
        }

        @Override
        public void testAssumptionFailure(Failure f) {
            TestRecord r = records.get(f.getDescription().getDisplayName());
            if (r != null) {
                r.status = Status.ASSUMPTION_SKIPPED;
                r.failureMessage = f.getMessage();
            }
        }

        @Override
        public void testIgnored(Description d) {
            TestRecord r = new TestRecord(classOf(d), methodOf(d));
            r.status = Status.IGNORED;
            records.put(d.getDisplayName(), r);
        }
    }

    /** Per-class outcome rolled up from the listener records. */
    private static final class ClassResult {
        final String fqcn;
        int effectiveRan;          // PASSED + FAILED + ERROR, excluding the initializationError pseudo
        int passed;
        int failures;             // assertion failures
        int errors;               // non-assertion errors (incl. initializationError)
        int assumptionSkipped;
        int ignored;
        boolean loadError;        // Class.forName / class-init blew up
        String loadErrorDetail;
        final List<TestRecord> records = new ArrayList<>();

        ClassResult(String fqcn) {
            this.fqcn = fqcn;
        }

        boolean hasInitError() {
            for (TestRecord r : records) {
                if (r.isInitError()) {
                    return true;
                }
            }
            return loadError;
        }

        String bucket() {
            if (hasInitError()) {
                return "EXCLUDED";   // class cannot load/initialise headlessly (OSGi/Eclipse singleton)
            }
            if (effectiveRan == 0) {
                return "EXCLUDED";   // entirely assumption-skipped / no real test ran
            }
            return "JUNIT";
        }

        String reason() {
            if (loadError) {
                return "load error: " + loadErrorDetail;
            }
            if (hasInitError()) {
                return "class-init error (OSGi/Eclipse singleton not available headlessly)";
            }
            if (effectiveRan == 0) {
                return "no test executed headlessly (assumption-skipped=" + assumptionSkipped
                        + ", ignored=" + ignored + ")";
            }
            return "ran " + effectiveRan + " headless";
        }
    }

    public static void main(String[] args) {
        boolean classify = "true".equalsIgnoreCase(System.getProperty("harness.classify"));
        String xmlDir = System.getProperty("harness.xmlDir");
        File outDir = null;
        if (xmlDir != null && !xmlDir.isBlank()) {
            outDir = new File(xmlDir);
            //noinspection ResultOfMethodCallIgnored
            outDir.mkdirs();
        }
        // Known-failing quarantine (tools/known-failing-tests.txt): these classes are RUN and
        // their results emitted to XML (visible, tracked), but their failures do NOT gate the
        // build — they are pre-existing, tracked defects, not new regressions. A NEW
        // failure in any other class still turns the build red.
        Set<String> knownFailing = loadClassList(System.getProperty("harness.knownFailingFile"));

        if (args.length == 0) {
            System.err.println("HeadlessTestRunner: no test classes supplied");
            System.exit(2);
        }

        List<ClassResult> results = new ArrayList<>();
        int totalEffective = 0;
        int totalFailures = 0;   // gating failures (excludes known-failing)
        int totalErrors = 0;     // gating errors (excludes known-failing)
        int violations = 0;      // gating empty-class violations (excludes known-failing)
        int knownFailClasses = 0;
        int knownFailTests = 0;

        for (String fqcn : args) {
            ClassResult cr = runOne(fqcn);
            results.add(cr);
            totalEffective += cr.effectiveRan;

            if (outDir != null) {
                writeXml(outDir, cr);
            }

            boolean known = knownFailing.contains(fqcn);

            if (classify) {
                System.out.printf("CLASSIFY %-8s %s | effectiveRan=%d pass=%d fail=%d err=%d assumeSkip=%d ignored=%d | %s%n",
                        cr.bucket(), cr.fqcn, cr.effectiveRan, cr.passed, cr.failures, cr.errors,
                        cr.assumptionSkipped, cr.ignored, cr.reason());
                continue;
            }

            boolean guardViolated = cr.effectiveRan == 0;
            boolean classFailed = cr.failures > 0 || cr.errors > 0 || cr.loadError;

            if (known) {
                knownFailClasses++;
                knownFailTests += cr.failures + cr.errors;
                System.out.printf("[KNOWN] %-69s ran=%d fail=%d err=%d skip=%d  (quarantined — not gating)%n",
                        cr.fqcn, cr.effectiveRan, cr.failures, cr.errors,
                        cr.assumptionSkipped + cr.ignored);
                // A quarantined class is still expected to RUN ≥1 test. If it contributes zero
                // (all assumption-skipped / cannot init headlessly) it belongs in the OSGi-excluded
                // manifest, not here — surface that loudly even though it doesn't gate the build.
                if (guardViolated) {
                    System.out.printf("    !! quarantined class ran 0 tests — move it to tools/osgi-excluded-tests.txt, not known-failing (%s)%n",
                            cr.reason());
                }
                continue;
            }

            totalFailures += cr.failures;
            totalErrors += cr.errors;
            if (guardViolated) {
                violations++;
            }
            String mark = classFailed ? "FAIL" : (guardViolated ? "EMPTY" : "ok  ");
            System.out.printf("[%s] %-70s ran=%d fail=%d err=%d skip=%d%n",
                    mark, cr.fqcn, cr.effectiveRan, cr.failures, cr.errors,
                    cr.assumptionSkipped + cr.ignored);
            if (guardViolated) {
                System.out.printf("    !! testsRun>0 VIOLATION: %s (%s)%n", cr.fqcn, cr.reason());
            }
        }

        // Aggregate summary (mirrors AllPluginTestsRunner's ergonomic, plus the run-count guard it lacked).
        long junitBucket = results.stream().filter(r -> "JUNIT".equals(r.bucket())).count();
        long excludedBucket = results.size() - junitBucket;
        System.out.println();
        System.out.printf("SUMMARY: %d classes | %d tests run | %d failed | %d errors | %d empty-class violations%n",
                results.size(), totalEffective, totalFailures, totalErrors, violations);
        if (classify) {
            System.out.printf("CLASSIFY-SUMMARY: junit=%d excluded=%d (of %d classes)%n",
                    junitBucket, excludedBucket, results.size());
            System.exit(0); // classification never fails the build
        }
        if (knownFailClasses > 0) {
            System.out.printf("KNOWN-FAILING (quarantined, not gating): %d classes / %d failing tests — see tools/known-failing-tests.txt%n",
                    knownFailClasses, knownFailTests);
        }

        boolean ok = totalFailures == 0 && totalErrors == 0 && violations == 0;
        System.out.println(ok ? "RESULT: PASS" : "RESULT: FAIL");
        System.exit(ok ? 0 : 1);
    }

    /** Loads a newline-delimited class list (ignoring blank lines and {@code #} comments). */
    private static Set<String> loadClassList(String path) {
        Set<String> out = new java.util.HashSet<>();
        if (path == null || path.isBlank()) {
            return out;
        }
        File f = new File(path);
        if (!f.isFile()) {
            return out;
        }
        try {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                int hash = line.indexOf('#');
                if (hash >= 0) {
                    line = line.substring(0, hash);
                }
                line = line.trim();
                if (!line.isEmpty()) {
                    out.add(line);
                }
            }
        } catch (IOException e) {
            System.err.println("WARN: could not read class list " + path + ": " + e.getMessage());
        }
        return out;
    }

    private static ClassResult runOne(String fqcn) {
        ClassResult cr = new ClassResult(fqcn);
        Class<?> clazz;
        try {
            clazz = Class.forName(fqcn);
        } catch (Throwable t) {
            cr.loadError = true;
            cr.loadErrorDetail = t.getClass().getSimpleName() + ": " + t.getMessage();
            cr.errors = 1;
            TestRecord r = new TestRecord(fqcn, INIT_ERROR);
            r.status = Status.ERROR;
            r.failureType = t.getClass().getName();
            r.failureMessage = String.valueOf(t.getMessage());
            cr.records.add(r);
            return cr;
        }

        JUnitCore core = new JUnitCore();
        RecordingListener listener = new RecordingListener();
        core.addListener(listener);
        try {
            core.run(clazz);
        } catch (Throwable t) {
            // Defensive: JUnitCore normally routes failures to the listener, but a catastrophic
            // class-init can escape. Treat as a load/init error.
            cr.loadError = true;
            cr.loadErrorDetail = t.getClass().getSimpleName() + ": " + t.getMessage();
        }

        for (TestRecord r : listener.records.values()) {
            cr.records.add(r);
            if (r.isInitError()) {
                cr.errors++;   // count the synthetic init failure as an error, but NOT as effectiveRan
                continue;
            }
            switch (r.status) {
                case PASSED -> {
                    cr.effectiveRan++;
                    cr.passed++;
                }
                case FAILED -> {
                    cr.effectiveRan++;
                    cr.failures++;
                }
                case ERROR -> {
                    cr.effectiveRan++;
                    cr.errors++;
                }
                case ASSUMPTION_SKIPPED -> cr.assumptionSkipped++;
                case IGNORED -> cr.ignored++;
            }
        }
        return cr;
    }

    private static void writeXml(File outDir, ClassResult cr) {
        int tests = cr.records.size();
        int skipped = cr.assumptionSkipped + cr.ignored;
        double timeSec = cr.records.stream().mapToLong(r -> r.timeMs).sum() / 1000.0;

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<testsuite name=\"").append(esc(cr.fqcn)).append("\"")
                .append(" tests=\"").append(tests).append("\"")
                .append(" failures=\"").append(cr.failures).append("\"")
                .append(" errors=\"").append(cr.errors).append("\"")
                .append(" skipped=\"").append(skipped).append("\"")
                .append(" time=\"").append(String.format(java.util.Locale.ROOT, "%.3f", timeSec)).append("\">\n");

        for (TestRecord r : cr.records) {
            sb.append("  <testcase classname=\"").append(esc(r.className)).append("\"")
                    .append(" name=\"").append(esc(r.methodName)).append("\"")
                    .append(" time=\"").append(String.format(java.util.Locale.ROOT, "%.3f", r.timeMs / 1000.0)).append("\"");
            switch (r.status) {
                case FAILED -> {
                    sb.append(">\n");
                    sb.append("    <failure message=\"").append(esc(r.failureMessage)).append("\"")
                            .append(" type=\"").append(esc(r.failureType)).append("\">")
                            .append(esc(r.failureTrace)).append("</failure>\n");
                    sb.append("  </testcase>\n");
                }
                case ERROR -> {
                    sb.append(">\n");
                    sb.append("    <error message=\"").append(esc(r.failureMessage)).append("\"")
                            .append(" type=\"").append(esc(r.failureType)).append("\">")
                            .append(esc(r.failureTrace)).append("</error>\n");
                    sb.append("  </testcase>\n");
                }
                case ASSUMPTION_SKIPPED -> {
                    sb.append(">\n");
                    sb.append("    <skipped message=\"").append(esc(r.failureMessage)).append("\"/>\n");
                    sb.append("  </testcase>\n");
                }
                case IGNORED -> {
                    sb.append(">\n");
                    sb.append("    <skipped/>\n");
                    sb.append("  </testcase>\n");
                }
                case PASSED -> sb.append("/>\n");
            }
        }
        sb.append("</testsuite>\n");

        File f = new File(outDir, "TEST-" + cr.fqcn + ".xml");
        try {
            Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("WARN: could not write " + f + ": " + e.getMessage());
        }
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> {
                    // Strip XML-1.0-illegal control chars (keep tab/newline/carriage-return).
                    if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                        out.append(' ');
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
