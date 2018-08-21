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
package uk.ac.manchester.tornado.benchmarks.rotateimage;

import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.GraphicsKernels;

import static uk.ac.manchester.tornado.api.collections.types.FloatOps.findMaxULP;
import static uk.ac.manchester.tornado.benchmarks.GraphicsKernels.rotateImage;
import static uk.ac.manchester.tornado.common.Tornado.getProperty;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat3;
import uk.ac.manchester.tornado.api.collections.types.Matrix4x4Float;

public class RotateTornado extends BenchmarkDriver {

    private final int numElementsX, numElementsY;

    private ImageFloat3 input, output;
    private Matrix4x4Float m;

    private TaskSchedule graph;

    public RotateTornado(int iterations, int numElementsX, int numElementsY) {
        super(iterations);
        this.numElementsX = numElementsX;
        this.numElementsY = numElementsY;
    }

    @Override
    public void setUp() {
        input = new ImageFloat3(numElementsX, numElementsY);
        output = new ImageFloat3(numElementsX, numElementsY);

        m = new Matrix4x4Float();
        m.identity();

        final Float3 value = new Float3(1f, 2f, 3f);
        for (int i = 0; i < input.Y(); i++) {
            for (int j = 0; j < input.X(); j++) {
                input.set(j, i, value);
            }
        }

        graph = new TaskSchedule("benchmark");
        if (Boolean.parseBoolean(getProperty("benchmark.streamin", "True"))) {
            graph.streamIn(input);
        }
        graph.task("rotateImage", GraphicsKernels::rotateImage, output, m,
                input);
        if (Boolean.parseBoolean(getProperty("benchmark.streamout", "True"))) {
            graph.streamOut(output);
        }

        if (Boolean.parseBoolean(getProperty("benchmark.warmup", "True"))) {
            graph.warmup();
        }
    }

    @Override
    public void tearDown() {
        graph.dumpProfiles();

        input = null;
        output = null;
        m = null;

        graph.getDevice().reset();
        super.tearDown();
    }

    @Override
    public void code() {
        graph.execute();
    }

    @Override
    public boolean validate() {

        final ImageFloat3 result = new ImageFloat3(numElementsX, numElementsY);

        code();
        graph.syncObjects(output);
        graph.clearProfiles();

        rotateImage(result, m, input);

        float maxULP = 0f;
        for (int i = 0; i < input.Y(); i++) {
            for (int j = 0; j < input.X(); j++) {
                final float ulp = findMaxULP(output.get(j, i),
                        result.get(j, i));

                if (ulp > maxULP) {
                    maxULP = ulp;
                }
            }
        }
        return Float.compare(maxULP, MAX_ULP) <= 0;
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
