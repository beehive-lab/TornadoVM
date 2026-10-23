/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 *
 * Portions of this file are derived from NVIDIA cuda-samples
 * (https://github.com/NVIDIA/cuda-samples), Copyright (c) 2022, NVIDIA CORPORATION.
 * All rights reserved. Used under the BSD 3-Clause License; see THIRD_PARTY_NOTICES.
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
package uk.ac.manchester.tornado.examples.kernelcontext.cdp;

import uk.ac.manchester.tornado.api.DeviceKernel;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

/**
 * Port of {@code cdpSimplePrint} from NVIDIA's cuda-samples ({@code cpp/3_CUDA_Features/cdpSimplePrint}) to CUDA Dynamic
 * Parallelism in TornadoVM ({@link KernelContext#launch}). The CPU launches 2 blocks of 2 threads. On the device, every thread launches 2 blocks of 2 threads, recursively, down to {@code maxDepth}. Every block records its depth, its parent and the thread that launched it; the launch tree is printed and checked.
 *
 * <p>
 * The same kernels are tested in {@code TestCudaSamplesDynamicParallelism}. How to run (CUDA backend):
 * </p>
 *
 * <pre>
 * tornado --threadInfo -m tornado.examples/uk.ac.manchester.tornado.examples.kernelcontext.cdp.CdpSimplePrint
 * </pre>
 */
public class CdpSimplePrint {

    private static GridScheduler grid(String task, int global, int local) {
        WorkerGrid worker = new WorkerGrid1D(global);
        worker.setLocalWork(local, 1, 1);
        return new GridScheduler(task, worker);
    }

    private static final DeviceKernel SIMPLE_PRINT = DeviceKernel.of(CdpSimplePrint::cdpKernel);

    /** Index of the first block of {@code depth}: two root blocks, and four times as many per level. */
    private static int levelOffset(int depth) {
        int offset = 0;
        int blocks = 2;
        for (int d = 0; d < depth; d++) {
            offset += blocks;
            blocks *= 4;
        }
        return offset;
    }

    /**
     * cdp_kernel: every block records who launched it, then each of its threads launches a grid of the
     * same shape (2 blocks of 2 threads), until {@code maxDepth}. The sample numbers blocks with a global
     * atomic counter; here the id follows from the position in the launch tree: {@code firstIndex} is
     * the index, within its level, of the first block of this launch.
     */
    private static void cdpKernel(KernelContext context, IntArray records, int maxDepth, int depth, int thread, int parentUid, int firstIndex) {
        int index = firstIndex + context.groupIdx;
        int uid = levelOffset(depth) + index;
        if (context.localIdx == 0) {
            records.set(uid * 3, depth);
            records.set(uid * 3 + 1, parentUid);
            records.set(uid * 3 + 2, thread);
        }
        if (depth + 1 < maxDepth) {
            context.launch(SIMPLE_PRINT, 4, 2, records, maxDepth, depth + 1, context.localIdx, uid, index * 4 + context.localIdx * 2);
        }
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        final int maxDepth = args.length > 0 ? Integer.parseInt(args[0]) : 2;
        final int blocks = levelOffset(maxDepth);
        System.out.printf("The CPU launches 2 blocks of 2 threads each. On the device each thread will launch 2 blocks of 2 threads each,%n"
                + "recursively, until it reaches max_depth=%d: %d blocks in total (%d from the GPU).%n%n", maxDepth, blocks, blocks - 2);
        IntArray records = new IntArray(blocks * 3);
        records.init(-2);
        TaskGraph taskGraph = new TaskGraph("print") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, records) //
                .task("cdp", CdpSimplePrint::cdpKernel, new KernelContext(), records, maxDepth, 0, 0, -1, 0) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, records);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("print.cdp", 4, 2)).execute();
        }
        boolean ok = true;
        for (int depth = 0; depth < maxDepth; depth++) {
            int count = 2 * (int) Math.pow(4, depth);
            for (int index = 0; index < count; index++) {
                int uid = levelOffset(depth) + index;
                int expectedParent = depth == 0 ? -1 : levelOffset(depth - 1) + index / 4;
                System.out.printf("%s BLOCK %d launched by thread %d of block %d%n", "|  ".repeat(depth) + "***", uid, records.get(uid * 3 + 2), records.get(uid * 3 + 1));
                ok &= records.get(uid * 3) == depth && records.get(uid * 3 + 1) == expectedParent;
            }
        }
        System.out.println(ok ? "\nOK" : "\nFAILED");
    }
}
