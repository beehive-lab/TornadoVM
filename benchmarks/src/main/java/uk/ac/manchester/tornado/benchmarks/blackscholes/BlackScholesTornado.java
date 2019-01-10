/*
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
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

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

public class BlackScholesTornado extends BenchmarkDriver {
    private int size;
    private float[] randArray,call,put;
    private TaskSchedule graph;

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

        graph = new TaskSchedule("benchmark");
        graph.task("t0", ComputeKernels::blackscholes, randArray, put, call);

        graph.warmup();
    }

    @Override
    public void tearDown() {
        graph.dumpProfiles();

        randArray = null;
        call = null;
        put = null;

        graph.getDevice().reset();
        super.tearDown();
    }

    @Override
    public boolean validate() {
        float[] randArrayTor,callTor,putTor,calSeq,putSeq;
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

        graph = new TaskSchedule("benchmark");
        graph.task("t0", ComputeKernels::blackscholes, randArrayTor, putTor, callTor);

        graph.warmup();
        graph.execute();
        graph.syncObjects(putTor, callTor);
        graph.clearProfiles();

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
    public void code() {
        graph.execute();
    }
}
