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
package uk.ac.manchester.tornado.benchmarks.striad;

import static uk.ac.manchester.tornado.api.collections.math.TornadoMath.findULPDistance;
import static uk.ac.manchester.tornado.benchmarks.LinearAlgebraArrays.striad;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.LinearAlgebraArrays;

public class StriadTornado extends BenchmarkDriver {

    private final int numElements;

    private float[] x,y,z;
    private final float alpha = 2f;

    private TaskSchedule graph;

    public StriadTornado(int iterations, int numElements) {
        super(iterations);
        this.numElements = numElements;
    }

    @Override
    public void setUp() {
        x = new float[numElements];
        y = new float[numElements];
        z = new float[numElements];

        Arrays.fill(x, 1);
        Arrays.fill(y, 2);

        graph = new TaskSchedule("benchmark");
        if (Boolean.parseBoolean(getProperty("benchmark.streamin", "True"))) {
            graph.streamIn(x, y);
        }

        graph.task("striad", LinearAlgebraArrays::striad, alpha, x, y, z);

        if (Boolean.parseBoolean(getProperty("benchmark.streamout", "True"))) {
            graph.streamOut(z);
        }

        if (Boolean.parseBoolean(getProperty("benchmark.warmup", "True"))) {
            graph.warmup();
        }
    }

    @Override
    public void tearDown() {
        graph.dumpProfiles();

        x = null;
        y = null;
        z = null;

        graph.getDevice().reset();
        super.tearDown();
    }

    @Override
    public void code() {
        graph.execute();
    }

    @Override
    public boolean validate() {

        final float[] result = new float[numElements];

        code();
        graph.syncObjects(z);
        graph.clearProfiles();

        striad(alpha, x, y, result);

        final float ulp = findULPDistance(z, result);
        return ulp < MAX_ULP;
    }

    public void printSummary() {
        if (isValid()) {
            System.out.printf("id=%s, elapsed=%f, per iteration=%f\n", getProperty("benchmark.device"), getElapsed(), getElapsedPerIteration());
        } else {
            System.out.printf("id=%s produced invalid result\n", getProperty("benchmark.device"));
        }
    }
}
