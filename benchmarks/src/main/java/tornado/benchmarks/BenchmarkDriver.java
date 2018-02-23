/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package tornado.benchmarks;

import static tornado.common.RuntimeUtilities.elapsedTimeInSeconds;
import static tornado.common.RuntimeUtilities.humanReadableByteCount;

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
