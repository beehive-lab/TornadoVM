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

import static uk.ac.manchester.tornado.benchmarks.ComputeKernels.nBody;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;

public class NBodyJava extends BenchmarkDriver {

    private float delT;
    private float espSqr;
    private FloatArray posSeq;
    private FloatArray velSeq;
    private int numBodies;

    public NBodyJava(int numBodies, int iterations) {
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
    }

    @Override
    public boolean validate(TornadoDevice device) {
        return true;
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        nBody(numBodies, posSeq, velSeq, delT, espSqr);
    }
}
