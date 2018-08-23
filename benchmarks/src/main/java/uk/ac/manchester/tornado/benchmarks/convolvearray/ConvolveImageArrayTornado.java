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
package uk.ac.manchester.tornado.benchmarks.convolvearray;

import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.GraphicsKernels;

import static uk.ac.manchester.tornado.benchmarks.BenchmarkUtils.createFilter;
import static uk.ac.manchester.tornado.benchmarks.BenchmarkUtils.createImage;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.collections.types.FloatOps;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

public class ConvolveImageArrayTornado extends BenchmarkDriver {

    private final int imageSizeX,imageSizeY,filterSize;

    private float[] input,output,filter;

    private TaskSchedule graph;

    public ConvolveImageArrayTornado(int iterations, int imageSizeX, int imageSizeY, int filterSize) {
        super(iterations);
        this.imageSizeX = imageSizeX;
        this.imageSizeY = imageSizeY;
        this.filterSize = filterSize;
    }

    @Override
    public void setUp() {
        input = new float[imageSizeX * imageSizeY];
        output = new float[imageSizeX * imageSizeY];
        filter = new float[filterSize * filterSize];

        createImage(input, imageSizeX, imageSizeY);
        createFilter(filter, filterSize, filterSize);

        graph = new TaskSchedule("benchmark");
        if (Boolean.parseBoolean(TornadoRuntime.getProperty("benchmark.streamin", "True"))) {
            graph.streamIn(input);
        }
        graph.task("convolveImageArray", GraphicsKernels::convolveImageArray, input, filter, output, imageSizeX, imageSizeY, filterSize, filterSize);
        if (Boolean.parseBoolean(TornadoRuntime.getProperty("benchmark.streamout", "True"))) {
            graph.streamOut(output);
        }

        if (Boolean.parseBoolean(TornadoRuntime.getProperty("benchmark.warmup", "True"))) {
            graph.warmup();
        }

    }

    @Override
    public void tearDown() {
        graph.dumpProfiles();

        input = null;
        output = null;
        filter = null;

        graph.getDevice().reset();
        super.tearDown();
    }

    @Override
    public void code() {
        graph.execute();
    }

    @Override
    public boolean validate() {

        final float[] result = new float[imageSizeX * imageSizeY];

        code();
        graph.syncObject(output);
        graph.clearProfiles();

        GraphicsKernels.convolveImageArray(input, filter, result, imageSizeX, imageSizeY, filterSize, filterSize);

        float maxULP = 0f;
        for (int i = 0; i < output.length; i++) {
            final float ulp = FloatOps.findMaxULP(result[i], output[i]);

            if (ulp > maxULP) {
                maxULP = ulp;
            }
        }
        return Float.compare(maxULP, MAX_ULP) <= 0;
    }

    public void printSummary() {
        if (isValid()) {
            System.out.printf("id=%s, elapsed=%f, per iteration=%f\n", TornadoRuntime.getProperty("s0.device"), getElapsed(), getElapsedPerIteration());
        } else {
            System.out.printf("id=%s produced invalid result\n", TornadoRuntime.getProperty("s0.device"));
        }
    }
}
