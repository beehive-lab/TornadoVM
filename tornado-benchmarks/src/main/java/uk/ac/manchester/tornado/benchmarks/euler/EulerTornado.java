/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.benchmarks.euler;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner euler
 * </code>
 */
public class EulerTornado extends BenchmarkDriver {

    private int size;
    LongArray input;
    LongArray outputA;
    LongArray outputB;
    LongArray outputC;
    LongArray outputD;
    LongArray outputE;

    public EulerTornado(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    private LongArray init(int size) {
        LongArray input = new LongArray(size);
        for (int i = 0; i < size; i++) {
            input.set(i, ((long) i * i * i * i * i));
        }
        return input;
    }

    @Override
    public void setUp() {
        input = init(size);
        outputA = new LongArray(size);
        outputB = new LongArray(size);
        outputC = new LongArray(size);
        outputD = new LongArray(size);
        outputE = new LongArray(size);
        taskGraph = new TaskGraph("benchmark") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("euler", ComputeKernels::euler, size, input, outputA, outputB, outputC, outputD, outputE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputA, outputB, outputC, outputD, outputE);

        immutableTaskGraph = taskGraph.snapshot();
        executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withPreCompilation();
    }

    @Override
    public void tearDown() {
        input = null;
        outputA = null;
        outputB = null;
        outputC = null;
        outputD = null;
        outputE = null;
        super.tearDown();
    }

    private void runSequential(int size, LongArray input, LongArray outputA, LongArray outputB, LongArray outputC, LongArray outputD, LongArray outputE) {
        ComputeKernels.euler(size, input, outputA, outputB, outputC, outputD, outputE);
        for (int i = 0; i < outputA.getSize(); i++) {
            if (outputA.get(i) != 0) {
                long a = outputA.get(i);
                long b = outputB.get(i);
                long c = outputC.get(i);
                long d = outputD.get(i);
                long e = outputE.get(i);
                System.out.println(a + "^5 + " + b + "^5 + " + c + "^5 + " + d + "^5 = " + e + "^5");
            }
        }
    }

    private void runParallel(int size, LongArray input, LongArray outputA, LongArray outputB, LongArray outputC, LongArray outputD, LongArray outputE, TornadoDevice device) {
        TaskGraph graph = new TaskGraph("s0") //
                .task("s0", ComputeKernels::euler, size, input, outputA, outputB, outputC, outputD, outputE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputA, outputB, outputC, outputD, outputE);

        ImmutableTaskGraph immutableTaskGraph = graph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withDevice(device).execute();
    }

    @Override
    public boolean validate(TornadoDevice device) {
        LongArray input = init(size);
        LongArray outputA = new LongArray(size);
        LongArray outputB = new LongArray(size);
        LongArray outputC = new LongArray(size);
        LongArray outputD = new LongArray(size);
        LongArray outputE = new LongArray(size);

        runSequential(size, input, outputA, outputB, outputC, outputD, outputE);

        LongArray outputAT = new LongArray(size);
        LongArray outputBT = new LongArray(size);
        LongArray outputCT = new LongArray(size);
        LongArray outputDT = new LongArray(size);
        LongArray outputET = new LongArray(size);

        runParallel(size, input, outputAT, outputBT, outputCT, outputDT, outputET, device);

        for (int i = 0; i < outputA.getSize(); i++) {
            if (outputAT.get(i) != outputA.get(i)) {
                return false;
            }
            if (outputBT.get(i) != outputB.get(i)) {
                return false;
            }
            if (outputCT.get(i) != outputC.get(i)) {
                return false;
            }
            if (outputDT.get(i) != outputD.get(i)) {
                return false;
            }
            if (outputET.get(i) != outputE.get(i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        executionResult = executionPlan.withDevice(device).execute();
    }
}
