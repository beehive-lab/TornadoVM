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
package uk.ac.manchester.tornado.benchmarks.nbody;

import static uk.ac.manchester.tornado.api.math.TornadoMath.abs;
import static uk.ac.manchester.tornado.benchmarks.ComputeKernels.nBody;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner nbody
 * </code>
 */
public class NBodyTornado extends BenchmarkDriver {
    private float delT;
    private float espSqr;
    private FloatArray posSeq;
    private FloatArray velSeq;
    private int numBodies;

    public NBodyTornado(int numBodies, int iterations) {
        super(iterations);
        this.numBodies = numBodies;
    }

    @Override
    public void setUp() {
        delT = 0.005f;
        espSqr = 500.0f;

        FloatArray auxPositionRandom = new FloatArray(numBodies * 4);
        FloatArray auxVelocityZero = new FloatArray(numBodies * 3);

        for (int i = 0; i < auxPositionRandom.getSize(); i++) {
            auxPositionRandom.set(i, (float) Math.random());
        }

        auxVelocityZero.init(0.0f);

        posSeq = new FloatArray(numBodies * 4);
        velSeq = new FloatArray(numBodies * 4);

        if (auxPositionRandom.getSize() >= 0) {
            for (int i = 0; i < auxPositionRandom.getSize(); i++) {
                posSeq.set(i, auxPositionRandom.get(i));
            }
        }

        if (auxVelocityZero.getSize() >= 0) {
            for (int i = 0; i < auxVelocityZero.getSize(); i++) {
                velSeq.set(i, auxVelocityZero.get(i));
            }
        }

        taskGraph = new TaskGraph("benchmark");
        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, velSeq, posSeq) //
                .task("t0", ComputeKernels::nBody, numBodies, posSeq, velSeq, delT, espSqr);

        immutableTaskGraph = taskGraph.snapshot();
        executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withPreCompilation();
    }

    @Override
    public void tearDown() {
        executionResult.getProfilerResult().dumpProfiles();
        posSeq = null;
        velSeq = null;
        executionPlan.resetDevice();
        super.tearDown();
    }

    @Override
    public boolean validate(TornadoDevice device) {
        boolean val = true;
        FloatArray posSeqSeq;
        FloatArray velSeqSeq;
        delT = 0.005f;
        espSqr = 500.0f;

        FloatArray auxPositionRandom = new FloatArray(numBodies * 4);
        FloatArray auxVelocityZero = new FloatArray(numBodies * 3);

        for (int i = 0; i < auxPositionRandom.getSize(); i++) {
            auxPositionRandom.set(i, (float) Math.random());
        }

        auxVelocityZero.init(0.0f);

        posSeq = new FloatArray(numBodies * 4);
        velSeq = new FloatArray(numBodies * 4);
        posSeqSeq = new FloatArray(numBodies * 4);
        velSeqSeq = new FloatArray(numBodies * 4);

        for (int i = 0; i < auxPositionRandom.getSize(); i++) {
            posSeq.set(i, auxPositionRandom.get(i));
            posSeqSeq.set(i, auxPositionRandom.get(i));
        }
        for (int i = 0; i < auxVelocityZero.getSize(); i++) {
            velSeq.set(i, auxVelocityZero.get(i));
            velSeqSeq.set(i, auxVelocityZero.get(i));
        }
        TaskGraph taskGraph = new TaskGraph("benchmark");
        taskGraph.task("t0", ComputeKernels::nBody, numBodies, posSeq, velSeq, delT, espSqr);
        taskGraph.transferToHost(DataTransferMode.UNDER_DEMAND, posSeq, velSeq);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withPreCompilation();

        TornadoExecutionResult executionResult = executionPlan.withPreCompilation() //
                .withDevice(device) //
                .execute();

        executionResult.transferToHost(posSeq, velSeq);
        executionPlan.clearProfiles();

        nBody(numBodies, posSeqSeq, velSeqSeq, delT, espSqr);

        for (int i = 0; i < numBodies * 4; i++) {
            if (abs(posSeqSeq.get(i) - posSeq.get(i)) > 0.01) {
                val = false;
                break;
            }
            if (abs(velSeq.get(i) - velSeqSeq.get(i)) > 0.01) {
                val = false;
                break;
            }
        }
        System.out.printf("Number validation: " + val + "\n");
        return val;
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        executionResult = executionPlan.withDevice(device).execute();
    }
}
