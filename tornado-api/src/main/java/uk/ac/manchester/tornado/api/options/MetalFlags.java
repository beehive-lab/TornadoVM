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
 * Options of the Metal backend.
 */
public final class MetalFlags {

    private static final OptionCategory CATEGORY = OptionCategory.METAL;

    public static final Option<String> COMPILER_FLAGS = Option.string("tornado.metal.compiler.flags") //
            .defaultValue("") //
            .description("Options passed to the Metal compiler.") //
            .category(CATEGORY).build();

    public static final Option<Integer> PRIORITY = Option.integer("tornado.metal.priority") //
            .defaultValue(0) //
            .description("Priority of the Metal backend when choosing the default backend; the highest priority comes first.") //
            .category(CATEGORY).build();

    public static final Option<Boolean> PROFILING = Option.bool("tornado.metal.profiling.enable") //
            .defaultValue(true) //
            .description("Record Metal command-buffer timestamps, which the TornadoVM profiler needs for kernel times.") //
            .category(CATEGORY).build();

    public static final Option<Boolean> FAST_MATH = Option.bool("tornado.metal.fast.math") //
            .defaultValue(false) //
            .description("Compile Metal kernels with fast, relaxed math (`MTLMathModeFast`), trading some floating-point precision for speed.") //
            .category(CATEGORY).deprecatedKey("tornado.metal.fastmath").build();

    public static final Option<Boolean> CODE_CACHE = Option.bool("tornado.metal.codecache.enable") //
            .defaultValue(false) //
            .description("Load a kernel's Metal library from `tornado.metal.codecache.dir` when it is there instead of compiling it, and write the compiled ones there.") //
            .category(CATEGORY).build();

    public static final Option<Boolean> CODE_CACHE_DUMP = Option.bool("tornado.metal.codecache.dump") //
            .defaultValue(false) //
            .description("Write the Metal library of every compiled kernel to `tornado.metal.codecache.dir`, without loading from it.") //
            .category(CATEGORY).build();

    public static final Option<String> CODE_CACHE_DIR = Option.string("tornado.metal.codecache.dir") //
            .defaultValue("/var/metal-codecache") //
            .description("Directory, under `$TORNADOVM_HOME`, of the Metal library cache.") //
            .category(CATEGORY).build();

    public static final Option<Boolean> SOURCE_DUMP = Option.bool("tornado.metal.source.dump") //
            .defaultValue(false) //
            .description("Write the generated Metal Shading Language sources to `tornado.metal.source.dir`.") //
            .category(CATEGORY).build();

    public static final Option<String> SOURCE_DIR = Option.string("tornado.metal.source.dir") //
            .defaultValue("/var/metal-compiler") //
            .description("Directory, under `$TORNADOVM_HOME`, the generated Metal sources are written to.") //
            .category(CATEGORY).build();

    public static final Option<String> LOG_DIR = Option.string("tornado.metal.log.dir") //
            .defaultValue("/var/metal-logs") //
            .description("Directory, under `$TORNADOVM_HOME`, the Metal compiler logs are written to.") //
            .category(CATEGORY).build();

    public static final Option<Boolean> DISPATCH_BATCH = Option.bool("tornado.metal.dispatch.batch") //
            .defaultValue(true) //
            .description("Encode kernels into shared command buffers committed in batches, instead of committing and waiting for every kernel.") //
            .category(CATEGORY).internal().build();

    public static final Option<Integer> DISPATCH_BATCH_SIZE = Option.integer("tornado.metal.dispatch.batch.size") //
            .defaultValue(2) //
            .description("Kernels per command buffer before it is committed, with `tornado.metal.dispatch.batch`.") //
            .category(CATEGORY).internal().deprecatedKey("tornado.metal.dispatch.batchSize").build();

    public static final Option<Boolean> DISPATCH_SPIN_WAIT = Option.bool("tornado.metal.dispatch.spin.wait") //
            .defaultValue(true) //
            .description("Wait for a command buffer by polling the shared event it signals, instead of `waitUntilCompleted`.") //
            .category(CATEGORY).internal().deprecatedKey("tornado.metal.dispatch.spinWait").build();

    private MetalFlags() {
    }
}
