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
 * Authors: Michalis Papadimitriou
 *
 */
package uk.ac.manchester.tornado.benchmarks.dft;

import static uk.ac.manchester.tornado.collections.math.TornadoMath.*;

import uk.ac.manchester.tornado.api.*;
import uk.ac.manchester.tornado.benchmarks.*;

public class DftTornado extends BenchmarkDriver {

    private int size;
    private TaskSchedule graph;
    private double[] inReal,inImag,outReal,outImag;

    public DftTornado(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        inReal = new double[size];
        inImag = new double[size];
        outReal = new double[size];
        outImag = new double[size];

        for (int i = 0; i < size; i++) {
            inReal[i] = 1 / (double) (i + 2);
            inImag[i] = 1 / (double) (i + 2);
        }

        graph = new TaskSchedule("benchmark");
        graph.task("t0", ComputeKernels::computeDft, inReal, inImag, outReal, outImag);
        graph.streamOut(outReal, outImag);
        graph.warmup();
    }

    @Override
    public boolean validate() {
        boolean val = true;
        double[] outRealTor = new double[size];
        double[] outImagTor = new double[size];

        graph.warmup();
        graph.execute();
        graph.streamOut(outReal, outImag);

        ComputeKernels.computeDft(inReal, inImag, outRealTor, outImagTor);

        for (int i = 0; i < size; i++) {
            if (abs(outImagTor[i] - outImag[i]) > 0.01) {
                val = false;
                break;
            }
            if (abs(outReal[i] - outRealTor[i]) > 0.01) {
                val = false;
                break;
            }
        }
        System.out.printf("Number validation: " + val + "\n");
        return val;
    }

    @Override
    public void tearDown() {
        graph.dumpProfiles();

        outImag = null;
        outReal = null;

        graph.getDevice().reset();
        super.tearDown();
    }

    @Override
    public void code() {
        graph.execute();

    }
}
