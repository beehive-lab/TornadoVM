/*
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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

import static uk.ac.manchester.tornado.benchmarks.ComputeKernels.nBody;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;

public class NBodyJava extends BenchmarkDriver {

    private float delT;
    private float espSqr;
    private float[] posSeq;
    private float[] velSeq;
    private int numBodies;

    public NBodyJava(int numBodies, int iterations) {
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
    }

    @Override
    public boolean validate(TornadoDevice device) {
        return true;
    }

    @Override
    public void benchmarkMethod(TornadoDevice device) {
        nBody(numBodies, posSeq, velSeq, delT, espSqr);
    }
}
