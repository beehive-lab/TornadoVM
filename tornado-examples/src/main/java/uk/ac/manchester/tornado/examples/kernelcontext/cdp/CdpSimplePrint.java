/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
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
