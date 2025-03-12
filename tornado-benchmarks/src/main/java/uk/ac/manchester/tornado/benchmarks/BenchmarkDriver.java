/*
 * Copyright (c) 2013-2025, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import static uk.ac.manchester.tornado.api.utils.TornadoAPIUtils.humanReadableByteCount;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.TornadoProfilerResult;
import uk.ac.manchester.tornado.api.TornadoRuntime;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;

public abstract class BenchmarkDriver {

    private static final boolean PRINT_MEM_USAGE = Boolean.parseBoolean(System.getProperty("tornado.benchmarks.memusage", "False"));
    private static final int ENERGY_MONITOR_INTERVAL = Integer.parseInt(System.getProperty("energy.monitor.interval", "0"));
    private static final String DUMP_ENERGY_METRICS_TO_DIRECTORY = System.getProperty("dump.energy.metrics.to.directory", "");
    private static final boolean VALIDATE = Boolean.parseBoolean(System.getProperty("tornado.benchmarks.validate", "False"));

    public static final float MAX_ULP = Float.parseFloat(System.getProperty("tornado.benchmarks.maxulp", "1000.0"));

    protected final long iterations;
    private double elapsed;

    private double[] timers;

    private List<Long> deviceKernelTimers;
    private List<Long> deviceCopyIn;
    private List<Long> deviceCopyOut;
    private List<Long> totalEnergyMetrics;
    private List<Long> firstPowerMetricPerIteration;
    private List<Long> averagePowerMetricPerIteration;
    private List<Long> lastPowerMetricPerIteration;
    private List<Long> powerMetricsPerIteration;
    private List<Long> snapshotTimerPerIteration;

    private int startingIndex = 30;

    protected TaskGraph taskGraph;

    protected TornadoExecutionPlan executionPlan;

    protected TornadoExecutionResult executionResult;

    protected ImmutableTaskGraph immutableTaskGraph;

    protected BenchmarkDriver(long iterations) {
        this.iterations = iterations;
    }

    public abstract void setUp();

    public void tearDown() {
        final Runtime runtime = Runtime.getRuntime();
        if (PRINT_MEM_USAGE) {
            System.out.printf("memory: free=%s, total=%s, max=%s\n", humanReadableByteCount(runtime.freeMemory(), false), humanReadableByteCount(runtime.totalMemory(), false), humanReadableByteCount(
                    runtime.maxMemory(), false));
        }
    }

    public abstract boolean validate(TornadoDevice device);

    public abstract void runBenchmark(TornadoDevice device);

    public TaskGraph getTaskGraph() {
        return taskGraph;
    }

    public TornadoExecutionResult getExecutionResult() {
        return executionResult;
    }

    protected void barrier() {

    }

    public String getProperty(String key, String value) {
        return TornadoRuntimeProvider.getProperty(key, value);
    }

    public String getProperty(String key) {
        return TornadoRuntimeProvider.getProperty(key);
    }

    private boolean skipGC() {
        return true;
    }

    private boolean isEnergyMonitorIntervalEnabled() {
        return ENERGY_MONITOR_INTERVAL > 0;
    }

    public void benchmark(TornadoDevice device, boolean isProfilerEnabled) {
        setUp();
        int size = toIntExact(iterations);
        timers = new double[size];
        if (isProfilerEnabled) {
            deviceKernelTimers = new ArrayList<>();
            deviceCopyIn = new ArrayList<>();
            deviceCopyOut = new ArrayList<>();
        }

        for (long i = 0; i < iterations; i++) {
            if (!skipGC()) {
                System.gc();
            }

            final long start = System.nanoTime();
            runBenchmark(device);
            final long end = System.nanoTime();

            if (isProfilerEnabled) {
                // Ensure the execution was correct, so we can count for general stats.
                TornadoProfilerResult profilerResult = getExecutionResult().getProfilerResult();
                if (profilerResult.getDeviceKernelTime() != 0) {
                    deviceKernelTimers.add(profilerResult.getDeviceKernelTime());
                }
                if (profilerResult.getDeviceWriteTime() != 0) {
                    deviceCopyIn.add(profilerResult.getDeviceWriteTime());
                }
                if (profilerResult.getDeviceReadTime() != 0) {
                    deviceCopyOut.add(profilerResult.getDeviceReadTime());
                }
            }

            timers[toIntExact(i)] = (end - start);
        }
        barrier();

        if (VALIDATE) {
            validate(device);
        }

        tearDown();
    }

    public void benchmarkWithEnergy(String id, TornadoDevice device, boolean isProfilerEnabled) {
        setUp();
        int size = toIntExact(iterations);
        timers = new double[size];
        if (isProfilerEnabled) {
            deviceKernelTimers = new ArrayList<>();
            deviceCopyIn = new ArrayList<>();
            deviceCopyOut = new ArrayList<>();
        }
        totalEnergyMetrics = new ArrayList<>();
        firstPowerMetricPerIteration = new ArrayList<>();
        averagePowerMetricPerIteration = new ArrayList<>();
        lastPowerMetricPerIteration = new ArrayList<>();

        for (long i = 0; i < iterations; i++) {
            Thread t0;
            Thread t1;
            powerMetricsPerIteration = Collections.synchronizedList(new ArrayList<>());
            snapshotTimerPerIteration = Collections.synchronizedList(new ArrayList<>());
            t0 = new Thread(() -> runBenchmark(device), "BenchmarkThread");
            t1 = new Thread(() -> {
                TornadoRuntime runtime = TornadoRuntimeProvider.getTornadoRuntime();
                while (t0.isAlive()) {
                    if (isEnergyMonitorIntervalEnabled()) {
                        try {
                            Thread.sleep(ENERGY_MONITOR_INTERVAL);
                        } catch (InterruptedException e) {
                            System.err.println("The thread for monitoring the power consumption is interrupted: " + e.getMessage());
                            Thread.currentThread().interrupt();
                            throw new TornadoRuntimeException(e);
                        }
                    }
                    long powerMetric = runtime.getPowerMetric();
                    snapshotTimerPerIteration.add(System.nanoTime());
                    powerMetricsPerIteration.add(powerMetric);
                }
            }, "PowerMonitoringThread");

            if (!skipGC()) {
                System.gc();
            }

            final long start = System.nanoTime();
            t0.start();
            t1.start();
            try {
                t0.join();
                t1.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TornadoRuntimeException(e);
            }
            final long end = System.nanoTime();

            if (isProfilerEnabled) {
                // Ensure the execution was correct, so we can count for general stats.
                TornadoProfilerResult profilerResult = getExecutionResult().getProfilerResult();
                if (profilerResult.getDeviceKernelTime() != 0) {
                    deviceKernelTimers.add(profilerResult.getDeviceKernelTime());
                }
                if (profilerResult.getDeviceWriteTime() != 0) {
                    deviceCopyIn.add(profilerResult.getDeviceWriteTime());
                }
                if (profilerResult.getDeviceReadTime() != 0) {
                    deviceCopyOut.add(profilerResult.getDeviceReadTime());
                }
            }

            timers[toIntExact(i)] = (end - start);
            totalEnergyMetrics.add(calculateTotalEnergy(start));
            firstPowerMetricPerIteration.add(powerMetricsPerIteration.getFirst());
            averagePowerMetricPerIteration.add((long) getAverage(toArray(powerMetricsPerIteration)));
            lastPowerMetricPerIteration.add(powerMetricsPerIteration.getLast());
        }
        barrier();
        if (!DUMP_ENERGY_METRICS_TO_DIRECTORY.isEmpty()) {
            writeToCsv(id, device);
            writePowerMetricsToCsv(id, device);
        }

        if (VALIDATE) {
            validate(device);
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

    public double getMax(double[] arr) {
        double maxValue = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > maxValue) {
                maxValue = arr[i];
            }
        }
        return maxValue;
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

    private void writeToCsv(String id, TornadoDevice device) {
        String fileName = "energy_metrics_" + formatFileNameSuffix(id, device);
        writeListToCSV(totalEnergyMetrics, fileName, id);
    }

    private void writePowerMetricsToCsv(String id, TornadoDevice device) {
        String fileName = "first_power_metrics_" + formatFileNameSuffix(id, device);
        writeListToCSV(firstPowerMetricPerIteration, fileName, id);

        fileName = "average_power_metrics_" + formatFileNameSuffix(id, device);
        writeListToCSV(averagePowerMetricPerIteration, fileName, id);

        fileName = "last_power_metrics_" + formatFileNameSuffix(id, device);
        writeListToCSV(lastPowerMetricPerIteration, fileName, id);
    }

    private String formatFileNameSuffix(String id, TornadoDevice device) {
        String currentSetup = (device != null) ? device.toString() : "java_reference";
        String[] idParts = id.split("-");

        return idParts[0] + "_" + currentSetup + ".csv";
    }

    private void writeListToCSV(List list, String fileName, String id) {
        try (FileWriter writer = new FileWriter(new File(DUMP_ENERGY_METRICS_TO_DIRECTORY, fileName), true)) {
            String[] idParts = id.split("-");
            String benchmarkName = idParts[0];
            String dataSize = idParts[idParts.length - 1];
            writer.append(benchmarkName).append(",").append(dataSize).append(",");
            writeListToFileWriter(list, writer);
            writer.append("\n");
            System.out.println("Updated CSV file (" + fileName + ") with new energy metrics for " + id + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeListToFileWriter(List list, FileWriter writer) throws IOException {
        int size = list.size();
        for (int i = 0; i < size; i++) {
            writer.append(String.valueOf(list.get(i)));
            if (i < size - 1) {
                writer.append(",");
            }
        }
    }

    public long getFirstEnergyMetric() {
        return totalEnergyMetrics.getFirst();
    }

    public long getAverageEnergyMetric() {
        return (long) getAverage(toArray(totalEnergyMetrics));
    }

    public long getLowestEnergyMetric() {
        return (long) getMin(toArray(totalEnergyMetrics));
    }

    public long getHighestEnergyMetric() {
        return (long) getMax(toArray(totalEnergyMetrics));
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

    public String getPreciseSummary() {
        return String.format("average(ns)=%6e, median(ns)=%6e, firstIteration(ns)=%6e, best(ns)=%6e%n", getAverage(), getMedian(), getFirstIteration(), getBestExecution());
    }

    private Long calculateTotalEnergy(long startTime) {
        long totalEnergy = 0;

        if (snapshotTimerPerIteration.size() == powerMetricsPerIteration.size()) {
            long timeInterval = snapshotTimerPerIteration.get(0) - startTime;
            // Convert from nanoseconds to milliseconds
            timeInterval /= 1000000;
            long energyForInterval = timeInterval * powerMetricsPerIteration.get(0);
            totalEnergy += energyForInterval;
            for (int i = 0; i < snapshotTimerPerIteration.size() - 1; i++) {
                timeInterval = snapshotTimerPerIteration.get(i + 1) - snapshotTimerPerIteration.get(i);
                energyForInterval = timeInterval * powerMetricsPerIteration.get(i + 1);
                totalEnergy += energyForInterval;
            }
        } else {
            throw new IllegalArgumentException("All lists must have the same size.");
        }

        return totalEnergy;
    }

    public String getEnergySummary() {
        return String.format("firstIteration(mJ)=%d, lowestEnergy(mJ)=%d, averageEnergy(mJ)=%d, highestEnergy(mJ)=%d%n", getFirstEnergyMetric(), getLowestEnergyMetric(), getAverageEnergyMetric(),
                getHighestEnergyMetric());
    }

    public String getSummary() {
        return String.format("elapsed=%6e, per iteration=%6e", getElapsed(), getElapsedPerIteration());
    }
}
