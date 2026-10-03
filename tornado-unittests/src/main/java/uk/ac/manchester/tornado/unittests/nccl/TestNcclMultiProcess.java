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
package uk.ac.manchester.tornado.unittests.nccl;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import uk.ac.manchester.tornado.api.TornadoBackend;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.nccl.NcclCommunicator;
import uk.ac.manchester.tornado.nccl.NcclUniqueId;
import uk.ac.manchester.tornado.nccl.provider.NcclLibraryProvider;
import uk.ac.manchester.tornado.nccl.tests.NcclMultiProcessWorker;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoVMMultiDeviceNotSupported;

/**
 * NCCL communicators that span several processes. The two-process test starts a second JVM with
 * the same TornadoVM settings: this process is rank 0 on the first GPU, the child is rank 1 on the
 * second, the child gets the unique id on its command line, and both run an all-reduce and a ring
 * exchange across the processes ({@link NcclMultiProcessWorker}).
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.nccl.TestNcclMultiProcess
 * </code>
 */
public class TestNcclMultiProcess extends TornadoTestBase {

    private static final long CHILD_TIMEOUT_SECONDS = 180;

    @Before
    public void ncclMustBeAvailable() {
        TornadoVMBackendType backendType = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backendType != TornadoVMBackendType.CUDA) {
            String message = "NCCL library tasks require the CUDA backend (default device is " + backendType + ")";
            switch (backendType) {
                case OPENCL, METAL -> assertNotBackend(backendType, message);
                default -> throw new TornadoVMCUDANotSupported(message);
            }
        }
        if (!NcclLibraryProvider.isAvailable()) {
            throw new TornadoVMCUDANotSupported("NCCL is not available on this host");
        }
    }

    private TornadoBackend backend() {
        return getTornadoRuntime().getBackend(getTornadoRuntime().getDefaultDevice().getBackendIndex());
    }

    @Test
    public void testUniqueIdRoundTrips() {
        NcclUniqueId id = NcclUniqueId.create();
        assertEquals(id, NcclUniqueId.fromBytes(id.toBytes()));
        assertEquals(id, NcclUniqueId.fromBase64(id.toBase64()));
        assertArrayEquals(id.toBytes(), NcclUniqueId.fromBase64(id.toBase64()).toBytes());
        assertThrows(TornadoRuntimeException.class, () -> NcclUniqueId.fromBytes(new byte[16]));
    }

    @Test
    public void testRanksMustFitTheWorldSize() {
        NcclUniqueId id = NcclUniqueId.create();
        TornadoDevice device = backend().getDevice(0);
        assertThrows(TornadoRuntimeException.class, () -> NcclCommunicator.create(id, 2, 2, device));
        assertThrows(TornadoRuntimeException.class, () -> NcclCommunicator.create(id, 0, 0, device));
        assertThrows(TornadoRuntimeException.class, () -> NcclCommunicator.create(id, 2, -1, device));
    }

    /** A communicator of one process and one rank, created from a unique id: runs on a single GPU. */
    @Test
    public void testSingleRankFromUniqueId() throws Exception {
        NcclMultiProcessWorker.runRank(NcclUniqueId.create(), 1, 0, backend().getDevice(0));
    }

    /** The command line of this JVM, with its main module and class replaced by the worker's. */
    private static List<String> childCommand(String... workerArgs) {
        ProcessHandle.Info info = ProcessHandle.current().info();
        String[] arguments = info.arguments().orElse(null);
        if (info.command().isEmpty() || arguments == null) {
            throw new TornadoVMMultiDeviceNotSupported("The command line of this JVM is not available, so a second TornadoVM process cannot be started");
        }
        List<String> command = new ArrayList<>();
        command.add(info.command().get());
        for (String argument : arguments) {
            if (argument.equals("-m") || argument.equals("--module") || argument.startsWith("--module=")) {
                break;
            }
            command.add(argument);
        }
        command.add("-m");
        command.add("tornado.nccl/uk.ac.manchester.tornado.nccl.tests.NcclMultiProcessWorker");
        command.addAll(Arrays.asList(workerArgs));
        return command;
    }

    @Test
    public void testTwoProcesses() throws Exception {
        TornadoBackend backend = backend();
        if (backend.getNumDevices() < 2) {
            throw new TornadoVMMultiDeviceNotSupported("This test needs at least 2 CUDA devices");
        }
        NcclUniqueId id = NcclUniqueId.create();
        Process child = new ProcessBuilder(childCommand(id.toBase64(), "2", "1", "1")).redirectErrorStream(true).start();
        ByteArrayOutputStream childOutput = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try (InputStream in = child.getInputStream()) {
                in.transferTo(childOutput);
            } catch (Exception e) {
                // the child's exit status is checked below
            }
        });
        reader.setDaemon(true);
        reader.start();
        try {
            // Rank 0 runs in this process, on a thread of its own: it blocks in the communicator
            // creation until the child joins, and must not hang the test if the child never does.
            Throwable[] rank0Failure = new Throwable[1];
            Thread rank0 = new Thread(() -> {
                try {
                    NcclMultiProcessWorker.runRank(id, 2, 0, backend.getDevice(0));
                } catch (Throwable t) {
                    rank0Failure[0] = t;
                }
            }, "nccl-rank-0");
            rank0.setDaemon(true);
            rank0.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CHILD_TIMEOUT_SECONDS);
            while (rank0.isAlive() && System.nanoTime() < deadline) {
                if (!child.isAlive() && child.exitValue() != 0) {
                    reader.join(TimeUnit.SECONDS.toMillis(10));
                    throw new AssertionError("child process failed before rank 0 finished:\n" + childOutput);
                }
                rank0.join(100);
            }
            assertTrue("rank 0 did not finish in time; child output:\n" + childOutput, !rank0.isAlive());
            if (rank0Failure[0] != null) {
                throw new AssertionError("rank 0 failed: " + rank0Failure[0] + "\nchild output:\n" + childOutput, rank0Failure[0]);
            }
            assertTrue("the child process did not finish in time", child.waitFor(CHILD_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            reader.join(TimeUnit.SECONDS.toMillis(10));
            String output = childOutput.toString();
            assertEquals("child process failed:\n" + output, 0, child.exitValue());
            assertTrue(output, output.contains("NCCL-WORKER rank 1 OK"));
        } finally {
            child.destroyForcibly();
        }
    }
}
