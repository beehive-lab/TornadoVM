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
package uk.ac.manchester.tornado.benchmarks;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import tornado.runtime.TornadoDriver;

import static tornado.common.Tornado.*;
import static tornado.runtime.TornadoRuntime.getTornadoRuntime;

public abstract class BenchmarkRunner {

    private static final boolean SKIP_SERIAL = Boolean
            .parseBoolean(System.getProperty(
                    "tornado.benchmarks.skipserial",
                    "False"));

    private static final boolean SKIP_STREAMS = Boolean
            .parseBoolean(System.getProperty(
                    "tornado.benchmarks.skipstreams",
                    "True"));

    protected abstract String getName();

    protected abstract String getIdString();

    protected abstract String getConfigString();

    protected abstract BenchmarkDriver getJavaDriver();

    protected abstract BenchmarkDriver getTornadoDriver();

    protected BenchmarkDriver getStreamsDriver() {
        return null;
    }

    protected int iterations = 0;

    public void run() {
        final String id = getIdString();

        System.out.printf("benchmark=%s, iterations=%d, %s\n", id, iterations,
                getConfigString());

        final double refElapsed;
        if (!SKIP_SERIAL) {
            final BenchmarkDriver referenceTest = getJavaDriver();
            referenceTest.benchmark();

            System.out.printf("bm=%-15s, id=%-20s, %s\n", id, "java-reference",
                    referenceTest.getSummary());

            refElapsed = referenceTest.getElapsed();

            final BenchmarkDriver streamsTest = getStreamsDriver();
            if (streamsTest != null && !SKIP_STREAMS) {
                streamsTest.benchmark();
                System.out.printf("bm=%-15s, id=%-20s, %s\n", id, "java-streams",
                        streamsTest.getSummary());
            }
        } else {
            refElapsed = -1;
        }

        final boolean tornadoEnabled = Boolean.parseBoolean(getProperty("tornado.enable", "True"));
        if (tornadoEnabled) {
            final String selectedDevices = getProperty("devices");
            if (selectedDevices == null || selectedDevices.isEmpty()) {
                benchmarkAll(id, refElapsed);
            } else {
                benchmarkSelected(id, selectedDevices, refElapsed);
            }
        }
    }

    private void benchmarkAll(String id, double refElapsed) {
        final Set<Integer> blacklistedDrivers = new HashSet<>();
        final Set<Integer> blacklistedDevices = new HashSet<>();

        findBlacklisted(blacklistedDrivers, "tornado.blacklist.drivers");
        findBlacklisted(blacklistedDevices, "tornado.blacklist.devices");

        final int numDrivers = getTornadoRuntime().getNumDrivers();
        for (int driverIndex = 0; driverIndex < numDrivers; driverIndex++) {
            if (blacklistedDrivers.contains(driverIndex)) {
                continue;
            }

            final TornadoDriver driver = getTornadoRuntime().getDriver(driverIndex);
            final int numDevices = driver.getDeviceCount();

            for (int deviceIndex = 0; deviceIndex < numDevices; deviceIndex++) {
                if (blacklistedDevices.contains(deviceIndex)) {
                    continue;
                }

                setProperty("benchmark.device", driverIndex + ":" + deviceIndex);
                final BenchmarkDriver deviceTest = getTornadoDriver();

                deviceTest.benchmark();

                System.out.printf("bm=%-15s, device=%-5s, %s, speedup=%.4f\n", id,
                        driverIndex + ":" + deviceIndex,
                        deviceTest.getSummary(), refElapsed / deviceTest.getElapsed());

            }
        }
    }

    private void benchmarkSelected(String id, String selectedDevices, double refElapsed) {

        final String[] devices = selectedDevices.split(",");
        for (String device : devices) {
            final String[] indicies = device.split(":");
            final int driverIndex = Integer.parseInt(indicies[0]);
            final int deviceIndex = Integer.parseInt(indicies[1]);

            setProperty("benchmark.device", driverIndex + ":" + deviceIndex);
            final BenchmarkDriver deviceTest = getTornadoDriver();

            deviceTest.benchmark();

            System.out.printf("bm=%-15s, device=%-5s, %s, speedup=%.4f\n", id,
                    driverIndex + ":" + deviceIndex,
                    deviceTest.getSummary(), refElapsed / deviceTest.getElapsed());
        }
    }

    public abstract void parseArgs(String[] args);

    public static void main(String[] args) {
        try {
            final String canonicalName = String.format("%s.%s.Benchmark", BenchmarkRunner.class.getPackage().getName(), args[0]);
            final BenchmarkRunner bm = (BenchmarkRunner) Class.forName(canonicalName).newInstance();
            final String[] bmArgs = Arrays.copyOfRange(args, 1, args.length);

            if (System.getProperty("config") != null) {
                loadSettings(System.getProperty("config"));
            }

            bm.parseArgs(bmArgs);
            bm.run();
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            e.printStackTrace();
            System.exit(-1);
        }

    }

    private void findBlacklisted(Set<Integer> blacklist, String property) {
        final String values = System.getProperty(property, "");
        if (values.isEmpty()) {
            return;
        }

        final String[] ids = values.split(",");
        for (String id : ids) {
            final int value = Integer.parseInt(id);
            blacklist.add(value);
        }
    }

}
