/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.tc.runtime;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/**
 * Options of a TC compilation, read from {@code -Dtornado.tc.*} system properties.
 *
 * <ul>
 * <li>{@code tornado.tc.dump}: {@code none} (default), {@code all}, or a comma-separated list of
 * stage names to write (for example {@code abi,emit}).</li>
 * <li>{@code tornado.tc.dumpDir}: directory for dumps and reports (default {@code tc-dumps}).</li>
 * <li>{@code tornado.tc.verify}: {@code end} (default) runs the verifier once, before structuring
 * and emission; {@code each} after every pass and stage, which names the pass that broke the IR
 * and costs about 15% of the compile time. {@link #DEFAULTS}, used by TC's own tests and tools,
 * verifies after each.</li>
 * <li>{@code tornado.tc.only}: compile-and-dump only functions whose name contains this text.</li>
 * <li>{@code tornado.tc.report}: {@code html} writes a self-contained HTML report per compilation.</li>
 * <li>{@code tornado.enable.fma} (the runtime's switch, default on): fused multiply-adds; a plan
 * with {@code withStrictFloatingPoint()} turns them off for its tasks.</li>
 * <li>{@code tornado.tc.javaMath} (default off): Java-exact math. {@code Math.sin}, {@code exp},
 * {@code pow} and the other functions {@code StrictMath} specifies are compiled from the JDK's own
 * FdLibm code instead of the device library, so their results are bit-identical to
 * {@code StrictMath}'s. Implies strict floating point (no fused multiply-adds). See
 * {@link #withJavaMath}.</li>
 * </ul>
 */
public record TcOptions(Set<String> dump, Path dumpDir, boolean verifyEach, Optional<String> only, boolean htmlReport, boolean mathOptimizations, String task,
        boolean fuseMultiplyAdd, boolean javaMath) {

    /** No Java-exact math. */
    public TcOptions(Set<String> dump, Path dumpDir, boolean verifyEach, Optional<String> only, boolean htmlReport, boolean mathOptimizations, String task, boolean fuseMultiplyAdd) {
        this(dump, dumpDir, verifyEach, only, htmlReport, mathOptimizations, task, fuseMultiplyAdd, false);
    }

    /** Fused multiply-adds on. */
    public TcOptions(Set<String> dump, Path dumpDir, boolean verifyEach, Optional<String> only, boolean htmlReport, boolean mathOptimizations, String task) {
        this(dump, dumpDir, verifyEach, only, htmlReport, mathOptimizations, task, true);
    }

    /** Math optimisations (rsqrt) on, as on the Graal path by default; no task id. */
    public TcOptions(Set<String> dump, Path dumpDir, boolean verifyEach, Optional<String> only, boolean htmlReport) {
        this(dump, dumpDir, verifyEach, only, htmlReport, true, "?");
    }

    public static final TcOptions DEFAULTS = new TcOptions(Set.of(), Path.of("tc-dumps"), true, Optional.empty(), false);

    public static TcOptions fromSystemProperties() {
        var dump = System.getProperty("tornado.tc.dump", "none");
        var stages = switch (dump) {
            case "none", "" -> Set.<String>of();
            case "all" -> Set.of("all");
            default -> Set.of(dump.split(","));
        };
        return new TcOptions(stages, Path.of(System.getProperty("tornado.tc.dumpDir", "tc-dumps")), "each".equals(System.getProperty("tornado.tc.verify", "end")),
                Optional.ofNullable(System.getProperty("tornado.tc.only")), "html".equals(System.getProperty("tornado.tc.report")),
                // The runtime's own switch for math optimisations (default on), honoured by TC too.
                Boolean.parseBoolean(System.getProperty("tornado.enable.mathOptimizations", "true")), "?",
                Boolean.parseBoolean(System.getProperty("tornado.enable.fma", "true"))).withJavaMath(Boolean.getBoolean("tornado.tc.javaMath"));
    }

    public boolean dumps(String stage) {
        return dump.contains("all") || dump.contains(stage);
    }

    public boolean selects(String functionName) {
        return only.map(functionName::contains).orElse(true);
    }

    public TcOptions withReport(boolean html, Path dir) {
        return new TcOptions(dump, dir, verifyEach, only, html, mathOptimizations, task, fuseMultiplyAdd, javaMath);
    }

    /** The task being compiled, as the runtime names it (device-launch reports print it, as on the Graal path). */
    public TcOptions withTask(String id) {
        return new TcOptions(dump, dumpDir, verifyEach, only, htmlReport, mathOptimizations, id, fuseMultiplyAdd, javaMath);
    }

    /** A plan with strict floating point: the multiply and the add round separately, as on the host. */
    public TcOptions withStrictFloatingPoint(boolean strict) {
        return strict ? new TcOptions(dump, dumpDir, verifyEach, only, htmlReport, mathOptimizations, task, false, javaMath) : this;
    }

    /**
     * Java-exact math: the functions {@code StrictMath} specifies ({@code sin}, {@code cos}, {@code tan},
     * {@code asin}, {@code acos}, {@code atan}, {@code atan2}, {@code exp}, {@code log}, {@code log10},
     * {@code pow}, {@code cbrt}, {@code sinh}, {@code cosh}, {@code tanh}), called through {@code Math}
     * or {@code StrictMath}, are compiled from the JDK's FdLibm code rather than mapped to the device
     * library, so each result is bit-identical to {@code StrictMath}'s. The FdLibm code is exact only
     * when every multiply and add rounds separately, so this also turns fused multiply-adds off, as
     * {@link #withStrictFloatingPoint} does. {@code sqrt} stays the device's, which is correctly
     * rounded as Java's is.
     */
    public TcOptions withJavaMath(boolean on) {
        return on ? new TcOptions(dump, dumpDir, verifyEach, only, htmlReport, mathOptimizations, task, false, true) : this;
    }
}
