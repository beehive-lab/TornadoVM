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
package uk.ac.manchester.tornado.benchmarks;

import static uk.ac.manchester.tornado.common.RuntimeUtilities.elapsedTimeInSeconds;
import static uk.ac.manchester.tornado.common.RuntimeUtilities.humanReadableByteCount;

public abstract class BenchmarkDriver {

    private static final boolean PRINT_MEM_USAGE = Boolean
            .parseBoolean(System.getProperty(
                    "tornado.benchmarks.memusage",
                    "false"));

    private static final boolean VALIDATE = Boolean
            .parseBoolean(System.getProperty(
                    "tornado.benchmarks.validate",
                    "True"));

    public static final float MAX_ULP = Float.parseFloat(System.getProperty("tornado.benchmarks.maxulp", "5.0"));

    protected final long iterations;
    private double elapsed;
    private boolean validResult;

    public BenchmarkDriver(long iterations) {
        this.iterations = iterations;
    }

    public abstract void setUp();

    public void tearDown() {
        final Runtime runtime = Runtime.getRuntime();
// BUG - this potentially triggers a crash
//        runtime.gc();
        if (PRINT_MEM_USAGE) {
            System.out.printf("memory: free=%s, total=%s, max=%s\n",
                    humanReadableByteCount(runtime.freeMemory(), false),
                    humanReadableByteCount(runtime.totalMemory(), false),
                    humanReadableByteCount(runtime.maxMemory(), false));
        }
    }

    public abstract boolean validate();

    public abstract void code();

    protected void barrier() {

    }

    public void benchmark() {

        setUp();

        validResult = (VALIDATE) ? validate() : true;

        if (validResult) {

            final long start = System.nanoTime();
            for (long i = 0; i < iterations; i++) {
                code();
            }

            barrier();
            final long end = System.nanoTime();

            elapsed = elapsedTimeInSeconds(start, end);

        }

        tearDown();

    }

    public double getElapsed() {
        return elapsed;
    }

    public double getElapsedPerIteration() {
        return elapsed / iterations;
    }

    public boolean isValid() {
        return validResult;
    }

    public String getSummary() {
        return String.format("elapsed=%6e, per iteration=%6e", getElapsed(),
                getElapsedPerIteration());
    }

}
