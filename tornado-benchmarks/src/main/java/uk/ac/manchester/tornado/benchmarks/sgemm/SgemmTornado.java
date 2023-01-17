/*
 * Copyright (c) 2013-2022, 2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.benchmarks.sgemm;

import static uk.ac.manchester.tornado.api.collections.math.TornadoMath.abs;
import static uk.ac.manchester.tornado.benchmarks.LinearAlgebraArrays.sgemm;

import java.util.Random;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.LinearAlgebraArrays;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner sgemm
 * </code>
 */
public class SgemmTornado extends BenchmarkDriver {

    private final int m;
    private final int n;
    private final boolean USE_PREBUILT = Boolean.parseBoolean(TornadoRuntime.getProperty("usePrebuilt", "False"));
    private WorkerGrid worker;
    private float[] a;
    private float[] b;
    private float[] c;
    private GridScheduler grid;
    private boolean USE_GRID = Boolean.parseBoolean(TornadoRuntime.getProperty("usegrid", "False"));

    public SgemmTornado(int iterations, int m, int n) {
        super(iterations);
        this.m = m;
        this.n = n;
    }

    @Override
    public void setUp() {
        a = new float[m * n];
        b = new float[m * n];
        c = new float[m * n];

        final Random random = new Random();

        for (int i = 0; i < m; i++) {
            a[i * (m + 1)] = 1;
        }

        for (int i = 0; i < m * n; i++) {
            b[i] = random.nextFloat();
        }

        if (USE_GRID) {
            worker = new WorkerGrid2D(m, n);
            worker.setLocalWork(16, 16, 1);
            grid = new GridScheduler();
            grid.setWorkerGrid("benchmark.sgemm", worker);
        }

        taskGraph = new TaskGraph("benchmark");
        if (!USE_PREBUILT) {
            taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b);
            taskGraph.task("sgemm", LinearAlgebraArrays::sgemm, m, n, n, a, b, c);
            taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, c);

            immutableTaskGraph = taskGraph.snapshot();
            executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
            executionPlan.withWarmUp();

        } else {
            String filePath = "/tmp/mxmFloat.spv";

            TornadoDevice device = null;
            int maxDevices = TornadoRuntime.getTornadoRuntime().getDriver(0).getDeviceCount();
            for (int i = 0; i < maxDevices; i++) {
                device = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(i);
                if (device.isSPIRVSupported()) {
                    break;
                }
            }

            taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                    .prebuiltTask("t0", //
                            "sgemm", //
                            filePath, //
                            new Object[] { m, n, n, a, b, c }, //
                            new Access[] { Access.READ, Access.READ, Access.READ, Access.READ, Access.READ, Access.WRITE }, //
                            device, //
                            new int[] { n, n })//
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

            immutableTaskGraph = taskGraph.snapshot();
            executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        }
    }

    @Override
    public void tearDown() {
        executionResult.getProfilerResult().dumpProfiles();

        a = null;
        b = null;
        c = null;

        executionPlan.resetDevice();
        super.tearDown();
    }

    @Override
    public void benchmarkMethod(TornadoDevice device) {
        if (grid != null) {
            executionPlan.withGridScheduler(grid);
        }
        executionResult = executionPlan.withDevice(device).execute();
    }

    @Override
    public boolean validate(TornadoDevice device) {

        final float[] result = new float[m * n];
        boolean val = true;

        benchmarkMethod(device);
        executionResult.transferToHost(c);

        executionPlan.clearProfiles();

        sgemm(m, n, m, a, b, result);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (abs(result[(i * n) + j] - c[(i * n) + j]) > 0.01) {
                    val = false;
                    break;
                }
            }
        }
        System.out.printf("Number validation: " + val + "\n");
        return val;
    }

    public void printSummary() {
        if (isValid()) {
            System.out.printf("id=%s, elapsed=%f, per iteration=%f\n", TornadoRuntime.getProperty("benchmark.device"), getElapsed(), getElapsedPerIteration());
        } else {
            System.out.printf("id=%s produced invalid result\n", TornadoRuntime.getProperty("benchmark.device"));
        }
    }

}
