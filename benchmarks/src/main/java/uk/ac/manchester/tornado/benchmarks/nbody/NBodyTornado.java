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

package uk.ac.manchester.tornado.benchmarks.nbody;

import static uk.ac.manchester.tornado.api.collections.math.TornadoMath.abs;
import static uk.ac.manchester.tornado.benchmarks.ComputeKernels.nBody;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner nbody
 * </code>
 */
public class NBodyTornado extends BenchmarkDriver {
    private float delT;
    private float espSqr;
    private float[] posSeq;
    private float[] velSeq;
    private int numBodies;

    public NBodyTornado(int numBodies, int iterations) {
        super(iterations);
        this.numBodies = numBodies;
    }

    @Override
    public void setUp() {
        delT = 0.005f;
        espSqr = 500.0f;

        float[] auxPositionRandom = new float[numBodies * 4];
        float[] auxVelocityZero = new float[numBodies * 3];

        for (int i = 0; i < auxPositionRandom.length; i++) {
            auxPositionRandom[i] = (float) Math.random();
        }

        Arrays.fill(auxVelocityZero, 0.0f);

        posSeq = new float[numBodies * 4];
        velSeq = new float[numBodies * 4];

        if (auxPositionRandom.length >= 0) {
            System.arraycopy(auxPositionRandom, 0, posSeq, 0, auxPositionRandom.length);
        }

        if (auxVelocityZero.length >= 0) {
            System.arraycopy(auxVelocityZero, 0, velSeq, 0, auxVelocityZero.length);
        }

        taskGraph = new TaskGraph("benchmark");
        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, velSeq, posSeq) //
                .task("t0", ComputeKernels::nBody, numBodies, posSeq, velSeq, delT, espSqr);
        taskGraph.warmup();
    }

    @Override
    public void tearDown() {
        taskGraph.dumpProfiles();

        posSeq = null;
        velSeq = null;

        taskGraph.getDevice().reset();
        super.tearDown();
    }

    @Override
    public boolean validate(TornadoDevice device) {
        boolean val = true;
        float[] posSeqSeq,velSeqSeq;
        delT = 0.005f;
        espSqr = 500.0f;

        float[] auxPositionRandom = new float[numBodies * 4];
        float[] auxVelocityZero = new float[numBodies * 3];

        for (int i = 0; i < auxPositionRandom.length; i++) {
            auxPositionRandom[i] = (float) Math.random();
        }

        Arrays.fill(auxVelocityZero, 0.0f);

        posSeq = new float[numBodies * 4];
        velSeq = new float[numBodies * 4];
        posSeqSeq = new float[numBodies * 4];
        velSeqSeq = new float[numBodies * 4];

        for (int i = 0; i < auxPositionRandom.length; i++) {
            posSeq[i] = auxPositionRandom[i];
            posSeqSeq[i] = auxPositionRandom[i];
        }
        for (int i = 0; i < auxVelocityZero.length; i++) {
            velSeq[i] = auxVelocityZero[i];
            velSeqSeq[i] = auxVelocityZero[i];
        }
        taskGraph = new TaskGraph("benchmark");
        taskGraph.task("t0", ComputeKernels::nBody, numBodies, posSeq, velSeq, delT, espSqr);
        taskGraph.mapAllTo(device);
        taskGraph.warmup();
        taskGraph.execute();
        taskGraph.syncObjects(posSeq, velSeq);
        taskGraph.clearProfiles();

        nBody(numBodies, posSeqSeq, velSeqSeq, delT, espSqr);

        for (int i = 0; i < numBodies * 4; i++) {
            if (abs(posSeqSeq[i] - posSeq[i]) > 0.01) {
                val = false;
                break;
            }
            if (abs(velSeq[i] - velSeqSeq[i]) > 0.01) {
                val = false;
                break;
            }
        }
        System.out.printf("Number validation: " + val + "\n");
        return val;
    }

    @Override
    public void benchmarkMethod(TornadoDevice device) {
        taskGraph.mapAllTo(device);
        taskGraph.execute();
    }
}
