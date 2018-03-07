/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.benchmarks.stencil;

import java.util.Arrays;
import java.util.Random;

import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.common.SchedulableTask;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;

import static uk.ac.manchester.tornado.benchmarks.stencil.Stencil.copy;
import static uk.ac.manchester.tornado.benchmarks.stencil.Stencil.stencil3d;
import static uk.ac.manchester.tornado.collections.math.TornadoMath.findULPDistance;
import static uk.ac.manchester.tornado.common.Tornado.getProperty;

public class StencilTornado extends BenchmarkDriver {

    private final int sz, n;
    private final float FAC = 1 / 26;
    private float[] a0, a1, ainit;

    private static final double TOLERANCE = 1.0e-15;

    private TaskSchedule graph;
    private SchedulableTask stencilTask;

    public StencilTornado(int iterations, int dataSize) {
        super(iterations);
        sz = (int) Math.cbrt(dataSize / 8) / 2;
        n = sz - 2;
    }

    @Override
    public void setUp() {
        a0 = new float[sz * sz * sz];
        a1 = new float[sz * sz * sz];
        ainit = new float[sz * sz * sz];

        Arrays.fill(a1, 0);

        final Random rand = new Random(7);
        for (int i = 1; i < n + 1; i++) {
            for (int j = 1; j < n + 1; j++) {
                for (int k = 1; k < n + 1; k++) {
                    ainit[(i * sz * sz) + (j * sz) + k] = rand.nextFloat();
                }
            }
        }

        copy(sz, ainit, a0);

        graph = new TaskSchedule("benchmark")
                .task("stencil", Stencil::stencil3d, n, sz, a0, a1, FAC)
                .task("copy", Stencil::copy, sz, a1, a0);
//                .streamOut(a0);

        stencilTask = graph.getTask("stencil");

        graph.warmup();
    }

    @Override
    public void tearDown() {
        graph.dumpProfiles();

        a0 = null;
        a1 = null;
        ainit = null;

//        graph.getDefaultDevice().reset();
        super.tearDown();
    }

    @Override
    public void code() {
        graph.execute();
    }

    @Override
    public boolean validate() {

        final float[] b0 = new float[ainit.length];
        final float[] b1 = new float[ainit.length];

        copy(sz, ainit, b0);
        for (int i = 0; i < iterations; i++) {
            code();
        }
        barrier();
        graph.clearProfiles();

        for (int i = 0; i < iterations; i++) {
            stencil3d(n, sz, b0, b1, FAC);
            copy(sz, b1, b0);
        }

        System.out.println(Arrays.toString(a0));
        System.out.println("----");
        System.out.println(Arrays.toString(b0));
        final float ulp = findULPDistance(a0, b0);
        return ulp < MAX_ULP;
    }

    @Override
    protected void barrier() {
        graph.syncObjects();
    }

    public void printSummary() {
        if (isValid()) {
            System.out.printf(
                    "id=%s, elapsed=%f, per iteration=%f\n",
                    getProperty("benchmark.device"), getElapsed(),
                    getElapsedPerIteration());
        } else {
            System.out.printf("id=%s produced invalid result\n",
                    getProperty("benchmark.device"));
        }
    }
}
