/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
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
package uk.ac.manchester.tornado.api.options;

/**
 * Options of the TornadoVM compiler (TC). The compiler modules do not read them: the drivers read them here and
 * pass them to TC.
 */
public final class TcFlags {

    private static final OptionCategory CATEGORY = OptionCategory.COMPILER;

    public static final Option<Boolean> ENABLE_FMA = Option.bool("tornado.enable.fma") //
            .defaultValue(true) //
            .description("Fuse multiplies and adds into `fma`. `false` keeps the two roundings of the host, also in NVRTC (`--fmad=false`). For one "
                    + "execution plan only, use `TornadoExecutionPlan.withStrictFloatingPoint()`.") //
            .category(CATEGORY).build();

    public static final Option<Boolean> MATH_OPTIMIZATIONS = Option.bool("tornado.enable.math.optimizations") //
            .defaultValue(true) //
            .description("Rewrite math calls into faster equivalents, such as `1/sqrt(x)` into `rsqrt(x)`.") //
            .category(CATEGORY).deprecatedKey("tornado.enable.mathOptimizations").build();

    public static final Option<Boolean> PARTIAL_UNROLL = Option.bool("tornado.experimental.partial.unroll") //
            .defaultValue(false) //
            .description("Experimental: partially unroll loops.") //
            .category(CATEGORY).dynamic().build();

    public static final Option<String> DUMP = Option.string("tornado.tc.dump") //
            .defaultValue("none") //
            .description("Compiler stages whose IR is written to `tornado.tc.dump.dir`: `none`, `all` or a comma-separated list.") //
            .category(CATEGORY).build();

    public static final Option<String> DUMP_DIR = Option.string("tornado.tc.dump.dir") //
            .defaultValue("tc-dumps") //
            .description("Directory of the compiler IR dumps and reports.") //
            .category(CATEGORY).deprecatedKey("tornado.tc.dumpDir").build();

    public static final Option<String> VERIFY = Option.string("tornado.tc.verify") //
            .defaultValue("end") //
            .description("When the compiler verifies its IR: `end` of the pipeline or after `each` stage.") //
            .category(CATEGORY).build();

    public static final Option<String> ONLY = Option.string("tornado.tc.only") //
            .defaultValue("") //
            .description("Dump and report only the functions whose name contains this text; empty selects every function.") //
            .category(CATEGORY).build();

    public static final Option<String> REPORT = Option.string("tornado.tc.report") //
            .defaultValue("") //
            .description("`html` writes an HTML report of every compilation to `tornado.tc.dump.dir`.") //
            .category(CATEGORY).build();

    public static final Option<Boolean> VERBOSE = Option.bool("tornado.tc.verbose") //
            .defaultValue(false) //
            .description("Print one line per compiled kernel.") //
            .category(CATEGORY).build();

    public static final Option<Boolean> STATS = Option.bool("tornado.tc.stats") //
            .defaultValue(false) //
            .description("Print a summary of the compilations at exit.") //
            .category(CATEGORY).build();

    public static final Option<Boolean> COMPILE_TIMES = Option.bool("tornado.tc.compile.times") //
            .defaultValue(false) //
            .description("With the profiler on, print the compiler's and the driver's compilation time of every task.") //
            .category(CATEGORY).deprecatedKey("tornado.tc.compileTimes").build();

    public static final Option<Boolean> MEMOIZE = Option.bool("tornado.tc.memoize") //
            .defaultValue(true) //
            .description("Reuse a compilation for a task with the same method and facts as an earlier one.") //
            .category(CATEGORY).internal().build();

    public static final Option<Integer> VARYING_SCALARS = Option.integer("tornado.tc.varying.scalars") //
            .defaultValue(3) //
            .description("A scalar argument that took this many different values across the tasks of one kernel is compiled as a "
                    + "runtime value from then on; 0 specialises every value.") //
            .category(CATEGORY).internal().deprecatedKey("tornado.tc.varyingScalars").build();

    public static final Option<Boolean> DYNAMIC_SHAPES = Option.bool("tornado.tc.dynamic.shapes") //
            .defaultValue(false) //
            .description("Compile every task as with `withDynamicShapes()`: a kernel that never reads an array length is shared by tasks "
                    + "over arrays of any size.") //
            .category(CATEGORY).internal().deprecatedKey("tornado.tc.dynamicShapes").build();

    public static final Option<Boolean> NATIVE_HALF = Option.bool("tornado.tc.native.half") //
            .defaultValue(true) //
            .description("Compute half-precision arithmetic in half; `false` computes it through float.") //
            .category(CATEGORY).internal().deprecatedKey("tornado.tc.nativeHalf").build();

    public static final Option<Boolean> SIGNED_OVERFLOW_UNDEFINED = Option.bool("tornado.tc.signed.overflow.undefined") //
            .defaultValue(false) //
            .description("Emit int and long arithmetic as plain C, where signed overflow is undefined: faster where the driver "
                    + "compiler exploits it, wrong where Java code relies on wrapping.") //
            .category(CATEGORY).internal().deprecatedKey("tornado.tc.signedOverflowUndefined").build();

    private TcFlags() {
    }
}
