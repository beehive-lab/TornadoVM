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
package uk.ac.manchester.tornado.benchmarks.blackscholes;

import static uk.ac.manchester.tornado.api.math.TornadoMath.abs;
import static uk.ac.manchester.tornado.benchmarks.ComputeKernels.blackscholes;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner blackscholes
 * </code>
 */
public class BlackScholesTornado extends BenchmarkDriver {

    private final int size;
    private FloatArray randArray;
    private FloatArray call;
    private FloatArray put;

    public BlackScholesTornado(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        randArray = new FloatArray(size);
        call = new FloatArray(size);
        put = new FloatArray(size);

        for (int i = 0; i < size; i++) {
            randArray.set(i, (i * 1.0f) / size);
        }

        taskGraph = new TaskGraph("benchmark") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, randArray) //
                .task("t0", ComputeKernels::blackscholes, randArray, put, call) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, put, call);
        immutableTaskGraph = taskGraph.snapshot();
        executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
    }

    @Override
    public void tearDown() {
        if (executionResult != null) {
            executionResult.getProfilerResult().dumpProfiles();
        }
        randArray = null;
        call = null;
        put = null;
        if (executionPlan != null) {
            executionPlan.resetDevice();
        }
        super.tearDown();
    }

    @Override
    public boolean validate(TornadoDevice device) {
        FloatArray randArraySeq;
        FloatArray callTor;
        FloatArray putTor;
        FloatArray callSeq;
        FloatArray putSeq;
        boolean val;

        val = true;

        randArraySeq = new FloatArray(size);
        callTor = new FloatArray(size);
        putTor = new FloatArray(size);
        callSeq = new FloatArray(size);
        putSeq = new FloatArray(size);

        for (int i = 0; i < size; i++) {
            randArraySeq.set(i, randArray.get(i));
        }

        TaskGraph taskGraph = new TaskGraph("benchmark") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, randArraySeq) //
                .task("t0", ComputeKernels::blackscholes, randArraySeq, putTor, callTor) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, putTor, callTor);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withDevice(device).execute();
        } catch (TornadoExecutionPlanException e) {
            e.printStackTrace();
            throw new TornadoRuntimeException(e);
        }

        blackscholes(randArraySeq, putSeq, callSeq);

        for (int i = 0; i < size; i++) {
            if (abs(putTor.get(i) - putSeq.get(i)) > 0.01) {
                System.out.printf("Number validation: " + putTor.get(i) + " vs " + putSeq.get(i) + "\n");
                val = false;
                break;
            }
            if (abs(callTor.get(i) - callSeq.get(i)) > 0.01) {
                System.out.printf("Number validation: " + callTor.get(i) + " vs " + callSeq.get(i) + "\n");
                val = false;
                break;
            }
        }
        System.out.printf("Number validation: " + val + "\n");
        return val;
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        executionResult = executionPlan.withDevice(device) //
                .execute();
    }
}
