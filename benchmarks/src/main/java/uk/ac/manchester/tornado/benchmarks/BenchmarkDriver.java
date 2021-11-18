/*
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package uk.ac.manchester.tornado.benchmarks;

import static java.lang.Math.toIntExact;
import static java.util.Arrays.sort;
import static uk.ac.manchester.tornado.api.utils.TornadoUtilities.humanReadableByteCount;

import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

public abstract class BenchmarkDriver {

    private static final boolean PRINT_MEM_USAGE = Boolean.parseBoolean(System.getProperty("tornado.benchmarks.memusage", "false"));

    private static final boolean VALIDATE = Boolean.parseBoolean(System.getProperty("tornado.benchmarks.validate", "False"));

    public static final float MAX_ULP = Float.parseFloat(System.getProperty("tornado.benchmarks.maxulp", "1000.0"));

    protected final long iterations;
    private double elapsed;
    private boolean validResult;
    private double[] timers;

    private List<Long> deviceKernelTimers;
    private List<Long> deviceCopyIn;
    private List<Long> deviceCopyOut;

    private int startingIndex = 30;

    protected TaskSchedule ts;

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

    public abstract boolean validate(TornadoDevice device);

    public abstract void benchmarkMethod(TornadoDevice device);

    public TaskSchedule getTaskSchedule() {
        return ts;
    }

    protected void barrier() {

    }

    public String getProperty(String key, String value) {
        return TornadoRuntime.getProperty(key, value);
    }

    public String getProperty(String key) {
        return TornadoRuntime.getProperty(key);
    }

    private boolean skipGC() {
        return true;
    }

    public void benchmark(TornadoDevice device, boolean isProfilerEnabled) {

        setUp();

        validResult = (!VALIDATE) || validate(device);

        int size = toIntExact(iterations);

        timers = new double[size];

        if (isProfilerEnabled) {
            deviceKernelTimers = new ArrayList<>();
            deviceCopyIn = new ArrayList<>();
            deviceCopyOut = new ArrayList<>();
        }

        if (validResult) {
            for (long i = 0; i < iterations; i++) {
                if (!skipGC()) {
                    System.gc();
                }
                final long start = System.nanoTime();
                benchmarkMethod(device);
                final long end = System.nanoTime();

                if (isProfilerEnabled) {

                    // Ensure the execution was correct, so we can count for general stats.
                    if (getTaskSchedule().getDeviceKernelTime() != 0) {
                        deviceKernelTimers.add(getTaskSchedule().getDeviceKernelTime());
                    }
                    if (getTaskSchedule().getDeviceWriteTime() != 0) {
                        deviceCopyIn.add(getTaskSchedule().getDeviceWriteTime());
                    }
                    if (getTaskSchedule().getDeviceReadTime() != 0) {
                        deviceCopyOut.add(getTaskSchedule().getDeviceReadTime());
                    }
                }

                timers[toIntExact(i)] = (end - start);
            }
            barrier();
        }
        tearDown();
    }

    public double getMin(double[] arr) {
        double minValue = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] < minValue) {
                minValue = arr[i];
            }
        }
        return minValue;
    }

    public double getMedian(double[] arr) {
        double[] temp = arr.clone();
        sort(temp);
        if (temp.length % 2 == 0) {
            return ((temp[temp.length / 2] + temp[temp.length / 2 - 1]) / 2);
        } else {
            return temp[temp.length / 2];
        }
    }

    public double[] toArray(List<Long> list) {
        return list.stream().mapToDouble(i -> i).toArray();
    }

    public double getBestKernelTime() {
        return getMin(toArray(deviceKernelTimers));
    }

    public double getMedianKernelTime() {
        return getMedian(toArray(deviceKernelTimers));
    }

    public double getAverageKernelTime() {
        return getAverage(toArray(deviceKernelTimers));
    }

    public double getAverageCopyInTime() {
        return getAverage(toArray(deviceCopyIn));
    }

    public double getAverageCopyOutTime() {
        return getAverage(toArray(deviceCopyOut));
    }

    public double getBestCopyIn() {
        return getMin(toArray(deviceCopyIn));
    }

    public double getBestCopyOut() {
        return getMin(toArray(deviceCopyOut));
    }

    public double getBestExecution() {
        return getMin(timers);
    }

    public double getFirstIteration() {
        return timers[0];
    }

    public double getMedian() {
        return getMedian(timers);
    }

    public double getAverage(double[] arr) {
        double sum = 0.0;
        int start = startingIndex;
        if (arr.length <= startingIndex) {
            start = 0;
        }
        for (int i = start; i < arr.length; i++) {
            sum += arr[i];
        }
        return sum / (arr.length - start);
    }

    public double getAverage() {
        return getAverage(timers);
    }

    public double getVariance() {
        double mean = getAverage();
        double temp = 0;
        for (int i = startingIndex; i < timers.length; i++) {
            temp += (timers[i] - mean) * (timers[i] - mean);
        }
        return (temp / (iterations - startingIndex));
    }

    public double getStdDev() {
        return Math.sqrt(getVariance());
    }

    public double getCV() {
        return (getStdDev() / getAverage()) * 100;
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
        return String.format("average=%6e, median=%6e, firstIteration=%6e, best=%6e", getAverage(), getMedian(), getFirstIteration(), getBestExecution());
    }

    public String getSummary() {
        return String.format("elapsed=%6e, per iteration=%6e", getElapsed(), getElapsedPerIteration());
    }
}
