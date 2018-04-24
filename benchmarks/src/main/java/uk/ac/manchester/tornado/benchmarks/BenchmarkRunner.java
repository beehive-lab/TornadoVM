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

import static uk.ac.manchester.tornado.common.Tornado.*;
import static uk.ac.manchester.tornado.runtime.TornadoRuntime.*;

import java.util.*;

import uk.ac.manchester.tornado.runtime.*;

public abstract class BenchmarkRunner {

    private static final boolean SKIP_SERIAL = Boolean.parseBoolean(System.getProperty("tornado.benchmarks.skipserial", "False"));

    private static final boolean SKIP_STREAMS = Boolean.parseBoolean(System.getProperty("tornado.benchmarks.skipstreams", "True"));

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

        System.out.printf("benchmark=%s, iterations=%d, %s\n", id, iterations, getConfigString());

        final double refElapsed;
        if (!SKIP_SERIAL) {
            final BenchmarkDriver referenceTest = getJavaDriver();
            referenceTest.benchmark();

            System.out.printf("bm=%-15s, id=%-20s, %s\n", id, "java-reference", referenceTest.getSummary());

            refElapsed = referenceTest.getElapsed();

            final BenchmarkDriver streamsTest = getStreamsDriver();
            if (streamsTest != null && !SKIP_STREAMS) {
                streamsTest.benchmark();
                System.out.printf("bm=%-15s, id=%-20s, %s\n", id, "java-streams", streamsTest.getSummary());
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

                System.out.printf("bm=%-15s, device=%-5s, %s, speedup=%.4f\n", id, driverIndex + ":" + deviceIndex, deviceTest.getSummary(), refElapsed / deviceTest.getElapsed());

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

            System.out.printf("bm=%-15s, device=%-5s, %s, speedup=%.4f\n", id, driverIndex + ":" + deviceIndex, deviceTest.getSummary(), refElapsed / deviceTest.getElapsed());
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
