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

package uk.ac.manchester.tornado.benchmarks.montecarlo;

import static uk.ac.manchester.tornado.collections.math.TornadoMath.*;
import static uk.ac.manchester.tornado.common.Tornado.*;

import uk.ac.manchester.tornado.benchmarks.*;
import uk.ac.manchester.tornado.runtime.api.*;

public class MonteCarloTornado extends BenchmarkDriver {

    private float[] output,seq;

    private int size;

    private TaskSchedule graph;

    public MonteCarloTornado(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        output = new float[size];

        graph = new TaskSchedule("benchmark").task("montecarlo", ComputeKernels::monteCarlo, output, size).streamOut(output);

        graph.warmup();
    }

    @Override
    public void tearDown() {
        graph.dumpProfiles();

        output = null;

        graph.getDevice().reset();
        super.tearDown();
    }

    @Override
    public void code() {
        graph.execute();
    }

    @Override
    public boolean validate() {
        float[] result;
        boolean val = true;

        result = new float[size];

        ComputeKernels.monteCarlo(result, size);

        graph.warmup();
        graph.execute();
        graph.syncObjects(output);
        graph.clearProfiles();

        for (int i = 0; i < size; i++) {
            System.out.printf(output[i] + "   " + result[i] + "\n");
            if (abs(output[i] - result[i]) > 0.01) {
                val = false;
            }
        }

        System.out.printf("Number validation: " + val + "\n");

        return val;
    }

    public void printSummary() {
        if (isValid()) {
            System.out.printf("id=%s, elapsed=%f, per iteration=%f\n", getProperty("benchmark.device"), getElapsed(), getElapsedPerIteration());
        } else {
            System.out.printf("id=%s produced invalid result\n", getProperty("benchmark.device"));
        }
    }
}
