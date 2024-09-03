///*
// * Copyright (c) 2024 APT Group, Department of Computer Science,
// * The University of Manchester.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// *
// */
//package uk.ac.manchester.tornado.unittests.codegen;
//
//import org.junit.Test;
//import uk.ac.manchester.tornado.api.GridScheduler;
//import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
//import uk.ac.manchester.tornado.api.KernelContext;
//import uk.ac.manchester.tornado.api.TaskGraph;
//import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
//import uk.ac.manchester.tornado.api.WorkerGrid;
//import uk.ac.manchester.tornado.api.WorkerGrid1D;
//import uk.ac.manchester.tornado.api.enums.DataTransferMode;
//import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
//import uk.ac.manchester.tornado.api.types.arrays.IntArray;
//import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
//import uk.ac.manchester.tornado.unittests.kernelcontext.matrices.TestMatrixMultiplicationKernelContext;
//
//import java.util.Random;
//import java.util.stream.IntStream;
//
//public class TestComplexCodeGen  extends TornadoTestBase {
//
//    public static void scan(KernelContext context, IntArray input, IntArray sum) {
//        int[] temp = context.allocateIntLocalArray(4 * 128);
//        int gid2 = context.globalIdx << 1;
//        int group = context.groupIdx;
//        int item = context.localIdx;
//        int n = context.localGroupSizeX << 1;
//
//        temp[2 * item] = input.get(gid2);
//        temp[2 * item + 1] = input.get(gid2 + 1);
//
//        int decale = 1;
//
//        for (int d = n >> 1; d > 0; d = d >> 1) {
//            context.localBarrier();
//            if (item < d) {
//                int ai = decale * ((item << 1) + 1) - 1;
//                int bi = decale * ((item << 1) + 2) - 1;
//                temp[bi] += temp[ai];
//            }
//            decale = decale << 1;
//        }
//
//        if (item == 0) {
//            sum.set(group, temp[n - 1]);
//            temp[n - 1] = 0;
//        }
//
//        for (int d = 1; d < n; d = d << 1) {
//            decale = decale >> 1;
//            context.localBarrier();
//            if (item < d) {
//                int ai = decale * ((item << 1) + 1) - 1;
//                int bi = decale * ((item << 1) + 2) - 1;
//                int t = temp[ai];
//                temp[ai] = temp[bi];
//                temp[bi] += t;
//            }
//        }
//        context.localBarrier();
//
//        input.set(gid2, temp[item << 1]);
//        input.set(gid2 + 1, temp[(item << 1) + 1]);
//    }
//
//    @Test
//    public void testComplexCodeGen() {
//        final int size = 4096;
//        IntArray a = new IntArray( size);
//        IntArray b = new IntArray( size);
//
//        Random r = new Random();
//        IntStream.range(0, size).forEach(i -> {
//            a.set(i, r.nextInt());
//        });
//
//        WorkerGrid worker = new WorkerGrid1D(size);
//        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
//        KernelContext context = new KernelContext();
//
//        TaskGraph taskGraph = new TaskGraph("s0") //
//                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
//                .task("t0", TestComplexCodeGen::scan, context, a, b) //
//                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);
//
//        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
//        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
//        executionPlan.withGridScheduler(gridScheduler) //
//                .execute();
//    }
//}
