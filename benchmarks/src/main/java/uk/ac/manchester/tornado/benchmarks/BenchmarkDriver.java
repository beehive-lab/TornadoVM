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

import static java.lang.Math.toIntExact;
import static java.util.Arrays.sort;
import static uk.ac.manchester.tornado.api.common.TornadoUtilities.humanReadableByteCount;

import uk.ac.manchester.tornado.api.runtinface.TornadoRuntime;

public abstract class BenchmarkDriver {

    private static final boolean PRINT_MEM_USAGE = Boolean.parseBoolean(System.getProperty("tornado.benchmarks.memusage", "false"));

    private static final boolean VALIDATE = Boolean.parseBoolean(System.getProperty("tornado.benchmarks.validate", "False"));

    public static final float MAX_ULP = Float.parseFloat(System.getProperty("tornado.benchmarks.maxulp", "1000.0"));

    protected final long iterations;
    private double elapsed;
    private boolean validResult;
    private double[] time;
    private int startingIndex = 30;

    public BenchmarkDriver(long iterations) {
        this.iterations = iterations;
    }

    public abstract void setUp();

    public void tearDown() {
        final Runtime runtime = Runtime.getRuntime();
        // BUG - this potentially triggers a crash
        // runtime.gc();
        if (PRINT_MEM_USAGE) {
            System.out.printf("memory: free=%s, total=%s, max=%s\n", humanReadableByteCount(runtime.freeMemory(), false), humanReadableByteCount(runtime.totalMemory(), false),
                    humanReadableByteCount(runtime.maxMemory(), false));
        }
    }

    public abstract boolean validate();

    public abstract void code();

    protected void barrier() {

    }

    public String getProperty(String key, String value) {
        return TornadoRuntime.getProperty(key, value);
    }

    public String getProperty(String key) {
        return TornadoRuntime.getProperty(key);
    }

    public void benchmark() {

        setUp();

        validResult = (VALIDATE) ? validate() : true;

        int size = toIntExact(iterations);

        time = new double[size];

        if (validResult) {

            System.gc();

            for (long i = 0; i < iterations; i++) {
                System.gc();
                final long start = System.nanoTime();
                code();
                final long end = System.nanoTime();
                time[toIntExact(i)] = end - start;
            }
            barrier();
        }
        tearDown();
    }

    public double getBestExecution() {
        double minValue = time[0];
        for (int i = 1; i < time.length; i++) {
            if (time[i] < minValue) {
                minValue = time[i];
            }
        }
        return minValue;
    }

    public double getFirstIteration() {
        return time[0];
    }

    public double getMedian() {
        double[] temp = time.clone();
        sort(temp);
        if (temp.length % 2 == 0) {
            return ((temp[temp.length / 2] + temp[temp.length / 2 - 1]) / 2);
        } else {
            return temp[temp.length / 2];
        }
    }

    public double getMean() {

        double sum = 0.0;

        for (int i = startingIndex; i < time.length; i++) {
            sum += time[i];
        }
        return sum / (iterations - startingIndex);

    }

    public double getVariance() {
        double mean = getMean();
        double temp = 0;
        for (int i = startingIndex; i < time.length; i++) {
            temp += (time[i] - mean) * (time[i] - mean);
        }
        return (temp / (iterations - startingIndex));
    }

    public double getStdDev() {
        return Math.sqrt(getVariance());

    }

    public double getCV() {
        return (getStdDev() / getMean()) * 100;
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

    public String getPreciseSummary() {
        return String.format("average=%6e, median=%6e, firstIteration=%6e, best=%6e", getMean(), getMedian(), getFirstIteration(), getBestExecution());
    }

    public String getSummary() {
        return String.format("elapsed=%6e, per iteration=%6e", getElapsed(), getElapsedPerIteration());
    }

}
