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
 * Options that make TornadoVM report what it does: debug output, generated kernels and bytecodes.
 */
public final class DebugFlags {

    private static final OptionCategory CATEGORY = OptionCategory.DEBUG;

    public static final Option<Boolean> DEBUG = Option.bool("tornado.debug") //
            .defaultValue(false) //
            .description("Print basic debug information.") //
            .category(CATEGORY).build();

    public static final Option<Boolean> FULL_DEBUG = Option.bool("tornado.debug.full") //
            .defaultValue(false) //
            .description("Print detailed debug information; more verbose than `tornado.debug`.") //
            .category(CATEGORY).deprecatedKey("tornado.fullDebug").build();

    public static final Option<Boolean> THREAD_INFO = Option.bool("tornado.thread.info") //
            .defaultValue(false) //
            .description("Print the thread configuration (global and local work sizes) of every kernel launch.") //
            .category(CATEGORY).deprecatedKey("tornado.threadInfo").build();

    public static final Option<Boolean> PRINT_KERNEL = Option.bool("tornado.print.kernel") //
            .defaultValue(false) //
            .description("Print the source of every generated kernel (OpenCL C, CUDA C or Metal Shading Language).") //
            .category(CATEGORY).deprecatedKey("tornado.printKernel").build();

    public static final Option<String> PRINT_KERNEL_DIR = Option.string("tornado.print.kernel.dir") //
            .defaultValue("") //
            .description("File the generated kernels are appended to; empty prints them on standard output.") //
            .category(CATEGORY).build();

    public static final Option<Boolean> PRINT_BYTECODES = Option.bool("tornado.print.bytecodes") //
            .defaultValue(false) //
            .description("Print the TornadoVM bytecodes of every task graph as it runs.") //
            .category(CATEGORY).build();

    public static final Option<String> DUMP_BYTECODES_DIR = Option.string("tornado.dump.bytecodes.dir") //
            .defaultValue("") //
            .description("File the TornadoVM bytecodes are written to; empty does not write them.") //
            .category(CATEGORY).build();

    public static final Option<String> FUZZ_GEN_DIR = Option.string("tornado.fuzz.gen.dir") //
            .noDefault("temporary directory") //
            .description("Directory the TornadoVM fuzzer writes its generated kernel classes to.") //
            .category(CATEGORY).internal().deprecatedKey("tornado.fuzz.genDir").build();

    private DebugFlags() {
    }
}
