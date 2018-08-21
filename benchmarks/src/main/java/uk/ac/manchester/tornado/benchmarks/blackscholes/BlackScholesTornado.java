/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science, The
 * University of Manchester. All rights reserved. DO NOT ALTER OR REMOVE
 * COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License version 2 only, as published by
 * the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License version 2 for more
 * details (a copy is included in the LICENSE file that accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version 2
 * along with this work; if not, write to the Free Software Foundation, Inc., 51
 * Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: Michalis Papadimitriou
 *
 */
package uk.ac.manchester.tornado.benchmarks.blackscholes;

import static uk.ac.manchester.tornado.benchmarks.ComputeKernels.*;
import static uk.ac.manchester.tornado.collections.math.TornadoMath.*;

import uk.ac.manchester.tornado.api.*;
import uk.ac.manchester.tornado.benchmarks.*;

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
