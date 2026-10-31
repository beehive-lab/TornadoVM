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
 * Options of a TC compilation. The compiler reads no system properties: the drivers build these options from the
 * {@code tornado.tc.*} flags of {@code uk.ac.manchester.tornado.api.options.TcFlags}, where each is documented.
 *
 * @param dump
 *     stage names whose IR is written to {@code dumpDir}; {@code all} for every stage
 * @param dumpDir
 *     directory for dumps and reports
 * @param verifyEach
 *     run the verifier after every pass and stage, which names the pass that broke the IR and costs about 15% of
 *     the compile time, instead of once before structuring; {@link #DEFAULTS}, used by TC's own tests and tools,
 *     verifies after each
 * @param only
 *     compile-and-dump only functions whose name contains this text
 * @param htmlReport
 *     write a self-contained HTML report per compilation
 * @param mathOptimizations
 *     rewrite math calls into faster equivalents (rsqrt)
 * @param task
 *     the runtime's task id, printed by reports
 * @param fuseMultiplyAdd
 *     fused multiply-adds; a plan with {@code withStrictFloatingPoint()} turns them off for its tasks
 * @param nativeHalf
 *     half-precision arithmetic in half rather than through float
 * @param signedOverflowUndefined
 *     print int and long arithmetic as plain C, where signed overflow is undefined, instead of wrapping as Java does
 */
public record TcOptions(Set<String> dump, Path dumpDir, boolean verifyEach, Optional<String> only, boolean htmlReport, boolean mathOptimizations, String task,
        boolean fuseMultiplyAdd, boolean nativeHalf, boolean signedOverflowUndefined) {

    /** Native half arithmetic, and int and long arithmetic that wraps as in Java. */
    public TcOptions(Set<String> dump, Path dumpDir, boolean verifyEach, Optional<String> only, boolean htmlReport, boolean mathOptimizations, String task,
            boolean fuseMultiplyAdd) {
        this(dump, dumpDir, verifyEach, only, htmlReport, mathOptimizations, task, fuseMultiplyAdd, true, false);
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

    /**
     * @param stages
     *     {@code none} (or blank), {@code all}, or a comma-separated list of stage names
     * @return the stages to dump
     */
    public static Set<String> dumpStages(String stages) {
        return switch (stages) {
            case "none", "" -> Set.of();
            case "all" -> Set.of("all");
            default -> Set.of(stages.split(","));
        };
    }

    public boolean dumps(String stage) {
        return dump.contains("all") || dump.contains(stage);
    }

    public boolean selects(String functionName) {
        return only.map(functionName::contains).orElse(true);
    }

    public TcOptions withReport(boolean html, Path dir) {
        return new TcOptions(dump, dir, verifyEach, only, html, mathOptimizations, task, fuseMultiplyAdd, nativeHalf, signedOverflowUndefined);
    }

    /** The task being compiled, as the runtime names it (device-launch reports print it, as on the Graal path). */
    public TcOptions withTask(String id) {
        return new TcOptions(dump, dumpDir, verifyEach, only, htmlReport, mathOptimizations, id, fuseMultiplyAdd, nativeHalf, signedOverflowUndefined);
    }

    /** A plan with strict floating point: the multiply and the add round separately, as on the host. */
    public TcOptions withStrictFloatingPoint(boolean strict) {
        return strict ? new TcOptions(dump, dumpDir, verifyEach, only, htmlReport, mathOptimizations, task, false, nativeHalf, signedOverflowUndefined) : this;
    }
}
