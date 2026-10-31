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
 * Options set per task graph or per task, as {@code <taskGraph>.<task>.<suffix>}, and the scheduling options
 * that apply to every task.
 */
public final class TaskFlags {

    private static final OptionCategory CATEGORY = OptionCategory.THREAD_SCHEDULING;

    public static final TaskOption<String> DEVICE = TaskOption.string("device") //
            .description("Device a task graph or task runs on, as `<backend>:<device>`, e.g. `-Ds0.t0.device=1:0`.") //
            .category(CATEGORY).build();

    public static final TaskOption<String> GLOBAL_WORK_SIZE = TaskOption.string("global.workgroup.size") //
            .description("Global work size of a task, comma-separated per dimension, e.g. `-Ds0.t0.global.workgroup.size=1024,1024`.") //
            .category(CATEGORY).build();

    public static final TaskOption<String> LOCAL_WORK_SIZE = TaskOption.string("local.workgroup.size") //
            .description("Local work size of a task, comma-separated per dimension, e.g. `-Ds0.t0.local.workgroup.size=16,16`.") //
            .category(CATEGORY).build();

    public static final TaskOption<String> COARSENESS = TaskOption.string("coarseness") //
            .description("Thread coarsening factors of a task, comma-separated per dimension.") //
            .category(CATEGORY).build();

    public static final TaskOption<Integer> OPENCL_GPU_BLOCK_X = TaskOption.integer("opencl.gpu.block.x") //
            .defaultValue(256) //
            .description("OpenCL work-group size of one-dimensional kernels on GPUs.") //
            .category(CATEGORY).withGlobalFallback().build();

    public static final TaskOption<Integer> OPENCL_GPU_BLOCK_2D_X = TaskOption.integer("opencl.gpu.block2d.x") //
            .defaultValue(4) //
            .description("OpenCL work-group size in x of two-dimensional kernels on GPUs.") //
            .category(CATEGORY).withGlobalFallback().build();

    public static final TaskOption<Integer> OPENCL_GPU_BLOCK_2D_Y = TaskOption.integer("opencl.gpu.block2d.y") //
            .defaultValue(4) //
            .description("OpenCL work-group size in y of two-dimensional kernels on GPUs.") //
            .category(CATEGORY).withGlobalFallback().build();

    public static final TaskOption<Integer> METAL_THREADS_PER_THREADGROUP_X = TaskOption.integer("metal.threads.per.threadgroup.x") //
            .defaultValue(1) //
            .description("Metal threads per threadgroup in x.") //
            .category(CATEGORY).withGlobalFallback().build();

    public static final TaskOption<Integer> METAL_THREADS_PER_THREADGROUP_Y = TaskOption.integer("metal.threads.per.threadgroup.y") //
            .defaultValue(1) //
            .description("Metal threads per threadgroup in y.") //
            .category(CATEGORY).withGlobalFallback().build();

    public static final TaskOption<Integer> METAL_THREADS_PER_THREADGROUP_Z = TaskOption.integer("metal.threads.per.threadgroup.z") //
            .defaultValue(1) //
            .description("Metal threads per threadgroup in z.") //
            .category(CATEGORY).withGlobalFallback().build();

    public static final Option<Boolean> BLOCK_SCHEDULER = Option.bool("tornado.scheduler.block") //
            .defaultValue(false) //
            .description("Split the iteration space into blocks, one per visible CPU core, when running on CPUs.") //
            .category(CATEGORY).build();

    private TaskFlags() {
    }
}
