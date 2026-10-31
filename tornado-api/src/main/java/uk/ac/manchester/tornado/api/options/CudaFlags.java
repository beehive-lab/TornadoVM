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
 * Options of the CUDA backend.
 */
public final class CudaFlags {

    private static final OptionCategory CATEGORY = OptionCategory.CUDA;

    private static final long SIXTEEN_MB = 16L * 1024 * 1024;

    public static final Option<String> COMPILE_PROFILE = Option.string("tornado.cuda.compile.profile") //
            .defaultValue("default") //
            .description("Named NVRTC option bundle: `default` (none), `fast` (`--use_fast_math --extra-device-vectorization`), `debug` (`-lineinfo`, so "
                    + "Nsight can attribute samples to the generated CUDA C) or `repro` (`--fmad=false`).") //
            .category(CATEGORY).build();

    public static final Option<String> COMPILER_FLAGS = Option.string("tornado.cuda.compiler.flags") //
            .defaultValue("") //
            .description("Options passed to NVRTC, added to those of `tornado.cuda.compile.profile` and a plan's `withCompilerFlags(...)`; an option "
                    + "of the same name replaces the profile's (NVRTC rejects an option given twice).") //
            .category(CATEGORY).build();

    public static final Option<Integer> PRIORITY = Option.integer("tornado.cuda.priority") //
            .defaultValue(0) //
            .description("Priority of the CUDA backend when choosing the default backend; the highest priority comes first.") //
            .category(CATEGORY).build();

    public static final Option<Boolean> CODE_CACHE = Option.bool("tornado.cuda.codecache.enable") //
            .defaultValue(true) //
            .description("Keep compiled CUDA modules on disk and reuse them in later runs, so a kernel goes through NVRTC once per "
                    + "source, device, options and toolkit.") //
            .category(CATEGORY).build();

    public static final Option<String> CODE_CACHE_DIR = Option.string("tornado.cuda.codecache.dir") //
            .defaultValue("/var/cuda-codecache") //
            .description("Directory, under `$TORNADOVM_HOME`, of the CUDA module cache.") //
            .category(CATEGORY).build();

    public static final Option<String> SCHEDULING = Option.string("tornado.cuda.sched") //
            .defaultValue("yield") //
            .description("How a host thread waits for the device: `yield`, `spin`, `blocking` (sleeps until signalled) or `auto`.") //
            .category(CATEGORY).build();

    public static final Option<Integer> COMPUTE_STREAMS = Option.integer("tornado.cuda.compute.streams") //
            .defaultValue(4) //
            .description("CUDA streams per execution plan that independent kernels are spread over, so they can run concurrently.") //
            .category(CATEGORY).build();

    public static final Option<Boolean> HOST_PINNING = Option.bool("tornado.cuda.host.pinning") //
            .defaultValue(true) //
            .description("Pin the host memory of buffers transferred on every execution; `false` uses pageable transfers.") //
            .category(CATEGORY).build();

    public static final Option<Boolean> HOST_PINNING_ALL = Option.bool("tornado.cuda.host.pinning.all") //
            .defaultValue(false) //
            .description("Pin the host memory of every device buffer, not only of those transferred on every execution.") //
            .category(CATEGORY).build();

    public static final Option<Long> PAYLOAD_ALIGNMENT = Option.longValue("tornado.cuda.payload.alignment") //
            .defaultValue(32L) //
            .description("Byte boundary the first element of a native array is aligned to in device memory. 32 keeps warp-wide accesses at four "
                    + "32-byte L2 sectors instead of five.") //
            .category(CATEGORY).deprecatedKey("tornado.cuda.payloadAlignment").build();

    public static final Option<String> NVCC = Option.string("tornado.cuda.nvcc") //
            .noDefault("found from the CUDA toolkit") //
            .description("Path of the `nvcc` used to compile CUDA Tile kernels.") //
            .category(CATEGORY).build();

    public static final Option<String> DEVICE_RUNTIME_PATH = Option.string("tornado.cuda.devrt.path") //
            .noDefault("searched under $CUDA_PATH, $CUDA_HOME, $CUDA_ROOT and /usr/local/cuda*") //
            .description("Path of the CUDA device runtime (`libcudadevrt.a`), linked into kernels that launch kernels (`KernelContext.launch`).") //
            .category(CATEGORY).build();

    public static final Option<Long> PENDING_LAUNCH_COUNT = Option.longValue("tornado.cuda.dp.pending.launch.count") //
            .noDefault("driver default (2048)") //
            .description("Maximum device-side kernel launches outstanding at once (`CU_LIMIT_DEV_RUNTIME_PENDING_LAUNCH_COUNT`); a launch beyond it "
                    + "fails. A plan's `withCUDAPendingLaunchCount(n)` takes precedence.") //
            .category(CATEGORY).deprecatedKey("tornado.cuda.dp.pendingLaunchCount").build();

    public static final Option<Boolean> GRAPH_ASYNC_LAUNCH = Option.bool("tornado.cuda.graph.async.launch") //
            .defaultValue(false) //
            .description("Launch a CUDA graph that copies nothing back to the host without waiting for it.") //
            .category(CATEGORY).internal().deprecatedKey("tornado.cuda.graph.asyncLaunch").build();

    public static final Option<Boolean> STAGED_TRANSFERS = Option.bool("tornado.staged.transfers") //
            .defaultValue(false) //
            .description("Experimental: upload large one-shot transfers through a ring of pinned staging buffers, overlapping the host "
                    + "copy with the DMA.") //
            .category(CATEGORY).build();

    public static final Option<Long> STAGED_CHUNK_SIZE = Option.longValue("tornado.staged.chunk.size") //
            .defaultValue(SIXTEEN_MB) //
            .description("Size in bytes of each pinned staging buffer of `tornado.staged.transfers`.") //
            .category(CATEGORY).internal().build();

    public static final Option<Integer> STAGED_RING_DEPTH = Option.integer("tornado.staged.ring.depth") //
            .defaultValue(4) //
            .description("Number of pinned staging buffers of `tornado.staged.transfers`.") //
            .category(CATEGORY).internal().build();

    public static final Option<Long> STAGED_MIN_SIZE = Option.longValue("tornado.staged.min.size") //
            .defaultValue(SIXTEEN_MB) //
            .description("Smallest transfer in bytes that `tornado.staged.transfers` stages.") //
            .category(CATEGORY).internal().build();

    public static final Option<Integer> STAGED_FILL_THREADS = Option.integer("tornado.staged.fill.threads") //
            .defaultValue(() -> Math.min(8, Math.max(1, Runtime.getRuntime().availableProcessors() / 2)), "half the cores, at most 8") //
            .description("Threads that fill a staging buffer of `tornado.staged.transfers`.") //
            .category(CATEGORY).internal().build();

    private CudaFlags() {
    }
}
