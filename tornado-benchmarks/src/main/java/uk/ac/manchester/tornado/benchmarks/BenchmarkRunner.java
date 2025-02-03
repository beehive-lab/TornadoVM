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

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import uk.ac.manchester.tornado.api.TornadoBackend;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;

public abstract class BenchmarkRunner {
    private static final String FALSE = "FALSE";
    private static final String TRUE = "TRUE";
    private static final boolean SKIP_TORNADOVM = Boolean.parseBoolean(System.getProperty("tornado.benchmarks.skiptornadovm", FALSE));
    private static final boolean SKIP_SERIAL = Boolean.parseBoolean(System.getProperty("tornado.benchmarks.skipserial", FALSE));
    private static final boolean SKIP_STREAMS = Boolean.parseBoolean(System.getProperty("tornado.benchmarks.skipstreams", TRUE));
    private static final String STORE_OUTPUT_TO_FILE = System.getProperty("tornado.benchmarks.store.output.to.file", "");

    protected abstract String getName();

    protected abstract String getIdString();

    protected abstract String getConfigString();

    protected abstract BenchmarkDriver getJavaDriver();

    protected abstract BenchmarkDriver getTornadoDriver();

    protected BenchmarkDriver getStreamsDriver() {
        return null;
    }

    protected int iterations;

    private static boolean isProfilerEnabled() {
        return TornadoRuntimeProvider.isProfilerEnabled();
    }

    private static boolean isPowerMonitoringEnabled() {
        return TornadoRuntimeProvider.isPowerMonitoringEnabled();
    }

    public void run() {
        final String id = getIdString();

        final double refElapsed;
        final double refElapsedMedian;
        final double refFirstIteration;

        if (!isProfilerEnabled() && !SKIP_SERIAL) {
            StringBuilder stringBuilder = new StringBuilder();
            // Run the Java Reference
            final BenchmarkDriver referenceTest = getJavaDriver();
            if (isPowerMonitoringEnabled()) {
                referenceTest.benchmarkWithEnergy(id, null, false);
            } else {
                referenceTest.benchmark(null, false);
            }
            stringBuilder.append("Performance: bm=" + id + ", " + "id=" + "java-reference" + ", " + referenceTest.getPreciseSummary() + "\n");
            if (isPowerMonitoringEnabled()) {
                stringBuilder.append("Energy: bm=" + id + ", " + "id=" + "java-reference" + ", " + referenceTest.getEnergySummary() + "\n");
            }

            refElapsed = referenceTest.getAverage();
            refElapsedMedian = referenceTest.getMedian();
            refFirstIteration = referenceTest.getFirstIteration();

            final BenchmarkDriver streamsTest = getStreamsDriver();
            if (streamsTest != null && !SKIP_STREAMS) {
                if (isPowerMonitoringEnabled()) {
                    streamsTest.benchmarkWithEnergy(id, null, false);
                } else {
                    streamsTest.benchmark(null, false);
                }
                stringBuilder.append("Performance: bm=" + id + ", " + "id=" + "java-streams" + ", " + streamsTest.getPreciseSummary() + "\n");
                if (isPowerMonitoringEnabled()) {
                    stringBuilder.append("Energy: bm=" + id + ", " + "id=" + "java-streams" + ", " + streamsTest.getEnergySummary() + "\n");
                }
            }
            if (STORE_OUTPUT_TO_FILE.isEmpty()) {
                System.out.printf(stringBuilder.toString());
            } else {
                redirectOutputToFile(stringBuilder.toString());
            }
        } else {
            refElapsed = -1;
            refElapsedMedian = -1;
            refFirstIteration = -1;
        }

        if (!SKIP_TORNADOVM) {
            final String selectedDevices = TornadoRuntimeProvider.getProperty("devices");
            if (selectedDevices == null || selectedDevices.isEmpty()) {
                runBenchmarkAllDevices(id, refElapsed, refElapsedMedian, refFirstIteration);
            } else {
                bechmarkForSelectedDevice(id, selectedDevices, refElapsed, refElapsedMedian, refFirstIteration);
            }
        }
    }

    private void redirectOutputToFile(String output) {
        try (FileWriter writer = new FileWriter(STORE_OUTPUT_TO_FILE, true)) {
            writer.append(output);
            writer.append("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void runBenchmarkAllDevices(String id, double refElapsed, double refElapsedMedian, double refFirstIteration) {
        StringBuilder stringBuilder = new StringBuilder();
        final Map<Integer, Set<Integer>> blacklistedDevices = new HashMap<>();

        // Specify in <backendIndex:deviceIndex>
        findBlacklisted(blacklistedDevices, "tornado.blacklist.devices");

        final int numDrivers = TornadoRuntimeProvider.getTornadoRuntime().getNumBackends();
        for (int driverIndex = 0; driverIndex < numDrivers; driverIndex++) {

            final TornadoBackend driver = TornadoRuntimeProvider.getTornadoRuntime().getBackend(driverIndex);
            final int numDevices = driver.getNumDevices();

            for (int deviceIndex = 0; deviceIndex < numDevices; deviceIndex++) {
                if (blacklistedDevices.containsKey(driverIndex)) {
                    Set<Integer> setIgnoredDevices = blacklistedDevices.get(driverIndex);
                    if (setIgnoredDevices.contains(deviceIndex)) {
                        continue;
                    }
                }

                TornadoDevice tornadoDevice = driver.getDevice(deviceIndex);

                TornadoRuntimeProvider.setProperty("benchmark.device", driverIndex + ":" + deviceIndex);
                final BenchmarkDriver benchmarkDriver = getTornadoDriver();
                try {
                    if (isPowerMonitoringEnabled()) {
                        benchmarkDriver.benchmarkWithEnergy(id, tornadoDevice, isProfilerEnabled());
                    } else {
                        benchmarkDriver.benchmark(tornadoDevice, isProfilerEnabled());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (!isProfilerEnabled()) {
                    stringBuilder.append("Performance: bm=" + id //
                            + ", device=" + driverIndex + ":" + deviceIndex //
                            + ", " + benchmarkDriver.getPreciseSummary() //
                            + ", speedupAvg=" + refElapsed / benchmarkDriver.getAverage() //
                            + ", speedupMedian=" + refElapsedMedian / benchmarkDriver.getMedian() //
                            + ", speedupFirstIteration=" + refFirstIteration / benchmarkDriver.getFirstIteration() //
                            + ", CV=" + benchmarkDriver.getCV() //
                            + ", deviceName=" + driver.getDevice(deviceIndex) + "\n");
                } else {
                    // Profiler enabled
                    stringBuilder.append("Performance: bm=" + id //
                            + ", " + "id=" + driverIndex + ":" + deviceIndex //
                            + ", kernelMin=" + benchmarkDriver.getBestKernelTime() //
                            + ", kernelAvg=" + benchmarkDriver.getAverageKernelTime() //
                            + ", copyInAvg=" + benchmarkDriver.getAverageCopyInTime() //
                            + ", copyOutAvg=" + benchmarkDriver.getAverageCopyOutTime() //
                            + ", deviceName=" + driver.getDevice(deviceIndex) + "\n");
                }
                if (isPowerMonitoringEnabled()) {
                    stringBuilder.append("Energy: bm=" + id + ", " + "id=" + driverIndex + ":" + deviceIndex + ", " + benchmarkDriver.getEnergySummary() + "\n");
                }
                if (STORE_OUTPUT_TO_FILE.isEmpty()) {
                    System.out.printf(stringBuilder.toString());
                } else {
                    redirectOutputToFile(stringBuilder.toString());
                }
            }
        }
    }

    private void bechmarkForSelectedDevice(String id, String selectedDevices, double refElapsed, double refElapsedMedian, double refFirstIteration) {
        StringBuilder stringBuilder = new StringBuilder();
        final String[] devices = selectedDevices.split(",");
        for (String device : devices) {
            final String[] stringIndex = device.split(":");
            final int driverIndex = Integer.parseInt(stringIndex[0]);
            final int deviceIndex = Integer.parseInt(stringIndex[1]);

            final BenchmarkDriver deviceTest = getTornadoDriver();
            final TornadoBackend driver = TornadoRuntimeProvider.getTornadoRuntime().getBackend(driverIndex);
            final TornadoDevice tornadoDevice = driver.getDevice(deviceIndex);
            if (isPowerMonitoringEnabled()) {
                deviceTest.benchmarkWithEnergy(id, tornadoDevice, isProfilerEnabled());
            } else {
                deviceTest.benchmark(tornadoDevice, isProfilerEnabled());
            }
            stringBuilder.append("Performance: bm=" + id //
                    + ", " + "device=" + driverIndex + ":" + deviceIndex //
                    + ", " + deviceTest.getPreciseSummary() //
                    + ", speedupAvg=" + refElapsed / deviceTest.getAverage() //
                    + ", speedupMedian=" + refElapsedMedian / deviceTest.getMedian() //
                    + ", speedupFirstIteration=" + refFirstIteration / deviceTest.getFirstIteration() //
                    + ", CV=" + deviceTest.getCV() //
                    + ", deviceName=" + driver.getDevice(deviceIndex) + "\n");
            if (STORE_OUTPUT_TO_FILE.isEmpty()) {
                System.out.printf(stringBuilder.toString());
            } else {
                redirectOutputToFile(stringBuilder.toString());
            }
        }
    }

    private void findBlacklisted(Map<Integer, Set<Integer>> blacklist, String property) {
        final String values = System.getProperty(property, "");
        if (values.isEmpty()) {
            return;
        }

        final String[] tuple2BackendDevice = values.split(",");
        for (String t2 : tuple2BackendDevice) {
            String[] deviceIdentifier = t2.split(":");
            int backendIndex = Integer.parseInt(deviceIdentifier[0]);
            int deviceIndex = Integer.parseInt(deviceIdentifier[1]);
            Set<Integer> set;
            if (blacklist.containsKey(backendIndex)) {
                set = blacklist.get(backendIndex);
            } else {
                set = new HashSet<>();
            }
            set.add(deviceIndex);
            blacklist.put(backendIndex, set);
        }
    }

    public abstract void parseArgs(String[] args);

    private static BenchmarkRunner getBenchMarkInstance(String benchmark) {
        benchmark = benchmark.toLowerCase();
        return switch (benchmark) {
            case "addimage" -> new uk.ac.manchester.tornado.benchmarks.addImage.Benchmark();
            case "blackscholes" -> new uk.ac.manchester.tornado.benchmarks.blackscholes.Benchmark();
            case "blurfilter" -> new uk.ac.manchester.tornado.benchmarks.blurFilter.Benchmark();
            case "convolvearray" -> new uk.ac.manchester.tornado.benchmarks.convolvearray.Benchmark();
            case "convolveimage" -> new uk.ac.manchester.tornado.benchmarks.convolveimage.Benchmark();
            case "dft" -> new uk.ac.manchester.tornado.benchmarks.dft.Benchmark();
            case "dgemm" -> new uk.ac.manchester.tornado.benchmarks.dgemm.Benchmark();
            case "dotimage" -> new uk.ac.manchester.tornado.benchmarks.dotimage.Benchmark();
            case "dorvector" -> new uk.ac.manchester.tornado.benchmarks.dotvector.Benchmark();
            case "euler" -> new uk.ac.manchester.tornado.benchmarks.euler.Benchmark();
            case "hilbert" -> new uk.ac.manchester.tornado.benchmarks.hilbert.Benchmark();
            case "juliaset" -> new uk.ac.manchester.tornado.benchmarks.juliaset.Benchmark();
            case "mandelbrot" -> new uk.ac.manchester.tornado.benchmarks.mandelbrot.Benchmark();
            case "montecarlo" -> new uk.ac.manchester.tornado.benchmarks.montecarlo.Benchmark();
            case "nbody" -> new uk.ac.manchester.tornado.benchmarks.nbody.Benchmark();
            case "rendertrack" -> new uk.ac.manchester.tornado.benchmarks.renderTrack.Benchmark();
            case "rotateimage" -> new uk.ac.manchester.tornado.benchmarks.rotateimage.Benchmark();
            case "rotatevector" -> new uk.ac.manchester.tornado.benchmarks.rotatevector.Benchmark();
            case "saxpy" -> new uk.ac.manchester.tornado.benchmarks.saxpy.Benchmark();
            case "sgemm" -> new uk.ac.manchester.tornado.benchmarks.sgemm.Benchmark();
            case "spmv" -> new uk.ac.manchester.tornado.benchmarks.spmv.Benchmark();
            case "stencil" -> new uk.ac.manchester.tornado.benchmarks.stencil.Benchmark();
            default -> throw new TornadoRuntimeException("Benchmark not recognized: " + benchmark);
        };
    }

    public static void main(String[] args) {

        if (args.length < 1) {
            String helpMessage = "[ERROR] Provide a benchmark to run " + "\n Example: $ tornado uk.ac.manchester.tornado.benchmarks.BenchmarkRunner juliaset 10 4096";
            System.out.println(helpMessage);
            System.exit(0);
        }
        BenchmarkRunner benchmarkRunner = getBenchMarkInstance(args[0]);
        final String[] benchmarkArgs = Arrays.copyOfRange(args, 1, args.length);
        if (System.getProperty("config") != null) {
            TornadoRuntimeProvider.loadSettings(System.getProperty("config"));
        }
        benchmarkRunner.parseArgs(benchmarkArgs);
        benchmarkRunner.run();
    }
}
