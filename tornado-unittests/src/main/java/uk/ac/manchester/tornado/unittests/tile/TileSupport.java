/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.tile;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceTileNotSupported;
import uk.ac.manchester.tornado.api.tile.PartitionView;
import uk.ac.manchester.tornado.api.tile.TileContext;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;

import static uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider.getTornadoRuntime;

/**
 * Decides once per JVM whether this host can run CUDA Tile kernels, by compiling and launching
 * the smallest possible one.
 *
 * <p>
 * Probing once matters for more than speed. On a host whose driver is older than R580 every
 * launch fails inside {@code cuModuleLoadDataEx}, and repeating that failure across a whole
 * suite has been observed to bring the JVM down with a SIGSEGV in
 * {@code InterpreterRuntime::exception_handler_for_exception} - the CUDA backend's failure path
 * does not survive being taken many times in one process. A single failure is handled cleanly.
 * So the suite takes that path exactly once and reports every remaining test as UNSUPPORTED
 * from the cached answer.
 * </p>
 */
final class TileSupport {

    private static Boolean supported;

    private static String reason;

    private TileSupport() {
    }

    static void probeTileKernel(TileContext tc, FloatArray a, FloatArray b, int n) {
        PartitionView av = tc.partition(tc.view(a, n), 64);
        PartitionView bv = tc.partition(tc.view(b, n), 64);
        bv.store(av.load(tc.bidX()), tc.bidX());
    }

    /**
     * Throws the typed exception the runner counts as UNSUPPORTED when tile kernels cannot run
     * here. Returns normally when they can.
     */
    static synchronized void requireTileSupport() {
        TornadoVMBackendType backendType = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backendType != TornadoVMBackendType.CUDA) {
            throw new TornadoVMCUDANotSupported("CUDA Tile kernels require the CUDA backend (default device is " + backendType + ")");
        }
        if (supported == null) {
            runProbe();
        }
        if (!supported) {
            throw new TornadoVMCUDANotSupported(reason);
        }
    }

    private static void runProbe() {
        final int n = 64;
        FloatArray a = new FloatArray(n);
        FloatArray b = new FloatArray(n);
        a.set(0, 1.0f);

        WorkerGrid1D worker = new WorkerGrid1D(1);
        worker.setLocalWork(1, 1, 1);
        GridScheduler grid = new GridScheduler("tileProbe.probe", worker);

        TaskGraph graph = new TaskGraph("tileProbe") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("probe", TileSupport::probeTileKernel, new TileContext(), a, b, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
            supported = Boolean.TRUE;
        } catch (TornadoDeviceTileNotSupported e) {
            supported = Boolean.FALSE;
            reason = e.getMessage();
        } catch (Exception e) {
            supported = Boolean.FALSE;
            String message = String.valueOf(e.getMessage());
            reason = message.contains("device kernel image is invalid")
                    ? "Loading a CUDA Tile cubin requires driver R580 or newer: " + message
                    : "CUDA Tile kernels cannot run on this host: " + message;
        }
    }
}
