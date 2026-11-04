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
 * Options of the OpenCL backend. The code-cache and source-dump options also apply to the CUDA backend, which
 * shares their implementation.
 */
public final class OpenCLFlags {

    private static final OptionCategory CATEGORY = OptionCategory.OPENCL;

    public static final Option<String> COMPILER_FLAGS = Option.string("tornado.opencl.compiler.flags") //
            .defaultValue("-cl-mad-enable -cl-fast-relaxed-math -w") //
            .description("OpenCL build options for every kernel. A plan's `withCompilerFlags(...)` is added to them: an option given there replaces "
                    + "a default of the same name.") //
            .category(CATEGORY).build();

    public static final Option<Integer> PRIORITY = Option.integer("tornado.opencl.priority") //
            .defaultValue(10) //
            .description("Priority of the OpenCL backend when choosing the default backend; the highest priority comes first.") //
            .category(CATEGORY).build();

    public static final Option<Boolean> PROFILING = Option.bool("tornado.opencl.profiling.enable") //
            .defaultValue(true) //
            .description("Create OpenCL command queues with profiling enabled, which the TornadoVM profiler needs for kernel times.") //
            .category(CATEGORY).build();

    public static final Option<Boolean> CODE_CACHE = Option.bool("tornado.opencl.codecache.enable") //
            .defaultValue(false) //
            .description("Write the binary of every compiled kernel to `tornado.opencl.codecache.dir` (OpenCL and CUDA backends; not on Apple platforms).") //
            .category(CATEGORY).build();

    public static final Option<Boolean> CODE_CACHE_DUMP = Option.bool("tornado.opencl.codecache.dump") //
            .defaultValue(false) //
            .description("Same as `tornado.opencl.codecache.enable`: write the binary of every compiled kernel to `tornado.opencl.codecache.dir`.") //
            .category(CATEGORY).build();

    public static final Option<String> CODE_CACHE_DIR = Option.string("tornado.opencl.codecache.dir") //
            .defaultValue("/var/opencl-codecache") //
            .description("Directory, under `$TORNADOVM_HOME`, the kernel binaries are written to.") //
            .category(CATEGORY).build();

    public static final Option<Boolean> SOURCE_DUMP = Option.bool("tornado.opencl.source.dump") //
            .defaultValue(false) //
            .description("Write the source of every generated kernel to `tornado.opencl.source.dir` (OpenCL and CUDA backends).") //
            .category(CATEGORY).build();

    public static final Option<String> SOURCE_DIR = Option.string("tornado.opencl.source.dir") //
            .defaultValue("/var/opencl-compiler") //
            .description("Directory, under `$TORNADOVM_HOME`, the generated kernel sources are written to.") //
            .category(CATEGORY).build();

    public static final Option<String> LOG_DIR = Option.string("tornado.opencl.log.dir") //
            .defaultValue("/var/opencl-logs") //
            .description("Directory, under `$TORNADOVM_HOME`, the kernel compiler logs are written to.") //
            .category(CATEGORY).build();

    private OpenCLFlags() {
    }
}
