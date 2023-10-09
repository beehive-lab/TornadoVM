/*
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.benchmarks.blackscholes;

import static uk.ac.manchester.tornado.api.collections.math.TornadoMath.abs;
import static uk.ac.manchester.tornado.benchmarks.ComputeKernels.blackscholes;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner blackscholes
 * </code>
 */
public class BlackScholesTornado extends BenchmarkDriver {

    private final int size;
    private float[] randArray;
    private float[] call;
    private float[] put;

    public BlackScholesTornado(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        randArray = new float[size];
        call = new float[size];
        put = new float[size];

        for (int i = 0; i < size; i++) {
            randArray[i] = (i * 1.0f) / size;
        }

        taskGraph = new TaskGraph("benchmark") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, randArray) //
                .task("t0", ComputeKernels::blackscholes, randArray, put, call) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, put, call);

        immutableTaskGraph = taskGraph.snapshot();
        executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withWarmUp();
    }

    @Override
    public void tearDown() {
        executionResult.getProfilerResult().dumpProfiles();
        randArray = null;
        call = null;
        put = null;
        executionPlan.resetDevice();
        super.tearDown();
    }

    @Override
    public boolean validate(TornadoDevice device) {
        float[] randArrayTor;
        float[] callTor;
        float[] putTor;
        float[] calSeq;
        float[] putSeq;
        boolean val;

        val = true;

        randArrayTor = new float[size];
        callTor = new float[size];
        putTor = new float[size];
        calSeq = new float[size];
        putSeq = new float[size];

        for (int i = 0; i < size; i++) {
            randArrayTor[i] = (float) Math.random();
        }

        taskGraph = new TaskGraph("benchmark");
        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, randArrayTor);
        taskGraph.task("t0", ComputeKernels::blackscholes, randArrayTor, putTor, callTor);

        immutableTaskGraph = taskGraph.snapshot();
        executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionResult = executionPlan.withWarmUp().execute();

        executionResult.transferToHost(putTor, callTor);
        executionPlan.clearProfiles();

        blackscholes(randArrayTor, putSeq, calSeq);

        for (int i = 0; i < size; i++) {
            if (abs(putTor[i] - putSeq[i]) > 0.01) {
                val = false;
                break;
            }
            if (abs(callTor[i] - calSeq[i]) > 0.01) {
                val = false;
                break;
            }
        }
        System.out.printf("Number validation: " + val + "\n");
        return val;
    }

    @Override
    public void benchmarkMethod(TornadoDevice device) {
        executionResult = executionPlan.withDevice(device) //
                .execute();
    }
}
