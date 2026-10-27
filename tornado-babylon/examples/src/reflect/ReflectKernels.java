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
package reflect;

import jdk.incubator.code.Reflect;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * Plain TornadoVM kernels marked {@code @Reflect}: with {@code -Dtornado.tc.frontend=babylon} TC builds
 * them from their Babylon code models instead of their bytecode. Each result is checked against Java.
 */
public class ReflectKernels {

    @Reflect
    static float saxpy(float alpha, float x, float y) {
        return alpha * x + y;
    }

    @Reflect
    public static void axpy(KernelContext ctx, float alpha, FloatArray x, FloatArray y, FloatArray out) {
        int i = ctx.globalIdx;
        if (i < out.getSize()) {
            out.set(i, saxpy(alpha, x.get(i), y.get(i)) + TornadoMath.sqrt(Math.abs(x.get(i))));
        }
    }

    /** Per-group sums through shared memory: a tree reduction with barriers. */
    @Reflect
    public static void groupSum(KernelContext ctx, FloatArray in, FloatArray sums) {
        float[] local = ctx.allocateFloatLocalArray(256);
        int lid = ctx.localIdx;
        local[lid] = in.get(ctx.globalIdx);
        for (int stride = ctx.localGroupSizeX / 2; stride > 0; stride /= 2) {
            ctx.localBarrier();
            if (lid < stride) {
                local[lid] += local[lid + stride];
            }
        }
        if (lid == 0) {
            sums.set(ctx.groupIdx, local[0]);
        }
    }

    public static void main(String[] args) throws Exception {
        int n = 1 << 20;
        int group = 256;
        var x = new FloatArray(n);
        var y = new FloatArray(n);
        var out = new FloatArray(n);
        var sums = new FloatArray(n / group);
        for (int i = 0; i < n; i++) {
            x.set(i, (i % 97) - 48f);
            y.set(i, (i % 13) * 0.5f);
        }
        float alpha = 1.5f;
        var graph = new TaskGraph("s")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, x, y)
                .task("axpy", ReflectKernels::axpy, new KernelContext(), alpha, x, y, out)
                .task("sum", ReflectKernels::groupSum, new KernelContext(), x, sums)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out, sums);
        var grid = new WorkerGrid1D(n);
        grid.setLocalWork(group, 1, 1);
        var scheduler = new GridScheduler("s.axpy", grid);
        scheduler.addWorkerGrid("s.sum", grid);
        ImmutableTaskGraph itg = graph.snapshot();
        try (var plan = new TornadoExecutionPlan(itg)) {
            plan.withGridScheduler(scheduler).execute();
        }
        int bad = 0;
        for (int i = 0; i < n; i++) {
            float expect = alpha * x.get(i) + y.get(i) + (float) Math.sqrt(Math.abs(x.get(i)));
            if (Math.abs(out.get(i) - expect) > 1e-4f * Math.max(1f, Math.abs(expect))) {
                bad++;
            }
        }
        for (int g = 0; g < n / group; g++) {
            float expect = 0;
            for (int i = 0; i < group; i++) {
                expect += x.get(g * group + i);
            }
            if (Math.abs(sums.get(g) - expect) > 1e-2f) {
                bad++;
            }
        }
        System.out.println(bad == 0 ? "ReflectKernels: PASS" : "ReflectKernels: FAIL (" + bad + " wrong)");
        if (bad != 0) {
            System.exit(1);
        }
    }
}
