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
package uk.ac.manchester.tornado.benchmarks.addvector;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.collections.types.Float4;
import uk.ac.manchester.tornado.api.collections.types.FloatOps;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat4;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.GraphicsKernels;

public class AddTornado extends BenchmarkDriver {

    private final int numElements;

    private VectorFloat4 a,b,c;

    private TaskSchedule graph;

    public AddTornado(int iterations, int numElements) {
        super(iterations);
        this.numElements = numElements;
    }

    @Override
    public void setUp() {
        a = new VectorFloat4(numElements);
        b = new VectorFloat4(numElements);
        c = new VectorFloat4(numElements);

        final Float4 valueA = new Float4(new float[] { 1f, 1f, 1f, 1f });
        final Float4 valueB = new Float4(new float[] { 2f, 2f, 2f, 2f });
        for (int i = 0; i < numElements; i++) {
            a.set(i, valueA);
            b.set(i, valueB);
        }

        graph = new TaskSchedule("benchmark");
        if (Boolean.parseBoolean(TornadoRuntime.getProperty("benchmark.streamin", "True"))) {
            graph.streamIn(a, b);
        }
        graph.task("addvector", GraphicsKernels::addVector, a, b, c);
        if (Boolean.parseBoolean(TornadoRuntime.getProperty("benchmark.streamout", "True"))) {
            graph.streamOut(c);
        }

        if (Boolean.parseBoolean(TornadoRuntime.getProperty("benchmark.warmup", "True"))) {
            graph.warmup();
        }
    }

    @Override
    public void tearDown() {
        graph.dumpProfiles();

        a = null;
        b = null;
        c = null;

        graph.getDevice().reset();
        super.tearDown();
    }

    @Override
    public void code() {
        graph.execute();
    }

    @Override
    public boolean validate() {

        final VectorFloat4 result = new VectorFloat4(numElements);

        code();
        graph.syncObject(c);
        graph.clearProfiles();

        GraphicsKernels.addVector(a, b, result);

        float maxULP = 0f;
        for (int i = 0; i < numElements; i++) {
            final float ulp = FloatOps.findMaxULP(result.get(i), c.get(i));

            if (ulp > maxULP) {
                maxULP = ulp;
            }
        }
        return Float.compare(maxULP, MAX_ULP) <= 0;
    }

    public void printSummary() {
        if (isValid()) {
            System.out.printf("id=%s, elapsed=%f, per iteration=%f\n", TornadoRuntime.getProperty("benchmark.device"), getElapsed(), getElapsedPerIteration());
        } else {
            System.out.printf("id=%s produced invalid result\n", TornadoRuntime.getProperty("benchmark.device"));
        }
    }

}
