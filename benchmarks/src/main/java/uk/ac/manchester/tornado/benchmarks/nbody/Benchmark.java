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

package uk.ac.manchester.tornado.benchmarks.nbody;

import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.BenchmarkRunner;

public class Benchmark extends BenchmarkRunner {

    private int numBodies;

    @Override
    public void parseArgs(String[] args) {
        if (args.length == 2) {
            iterations = Integer.parseInt(args[0]);
            numBodies = Integer.parseInt(args[1]);
        } else if (args.length == 1) {
            System.out.printf("Two arguments are needed: iterations size");
        } else {
            iterations = 50;
            numBodies = 4096;
        }
    }

    @Override
    protected String getName() {
        return "nbody";
    }

    @Override
    protected String getIdString() {
        return String.format("%s-%d-%d", getName(), iterations, numBodies);
    }

    @Override
    protected String getConfigString() {
        return String.format("size=%d", numBodies);
    }

    @Override
    protected BenchmarkDriver getJavaDriver() {
        return new NBodyJava(numBodies, iterations);
    }

    @Override
    protected BenchmarkDriver getTornadoDriver() {
        return new NBodyTornado(numBodies, iterations);
    }

}
