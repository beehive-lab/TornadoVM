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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.TornadoBackend;
import uk.ac.manchester.tornado.api.TornadoRuntime;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.nccl.NcclCommunicator;
import uk.ac.manchester.tornado.nccl.NcclUniqueId;
import uk.ac.manchester.tornado.nccl.provider.NcclLibraryProvider;
import uk.ac.manchester.tornado.nccl.tests.NcclMultiProcessWorker;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoVMHIPNotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoVMMultiDeviceNotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoVMOpenCLNotSupported;

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

    @BeforeEach
    public void ncclMustBeAvailable() {
        TornadoVMBackendType backendType = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backendType == TornadoVMBackendType.HIP) {
            if (!NcclLibraryProvider.isAvailable(TornadoVMBackendType.HIP)) {
                throw new TornadoVMHIPNotSupported("RCCL is not available on this host");
            }
            return;
        }
        if (backendType != TornadoVMBackendType.CUDA) {
            String message = "NCCL library tasks require the CUDA or HIP backend (default device is " + backendType + ")";
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
        NcclUniqueId id = NcclUniqueId.create(backend().getDevice(0));
        assertEquals(id, NcclUniqueId.fromBytes(id.toBytes()));
        assertEquals(id, NcclUniqueId.fromBase64(id.toBase64()));
        assertArrayEquals(id.toBytes(), NcclUniqueId.fromBase64(id.toBase64()).toBytes());
        assertThrows(TornadoRuntimeException.class, () -> NcclUniqueId.fromBytes(new byte[16]));
    }

    @Test
    public void testRanksMustFitTheWorldSize() {
        TornadoDevice device = backend().getDevice(0);
        NcclUniqueId id = NcclUniqueId.create(device);
        assertThrows(TornadoRuntimeException.class, () -> NcclCommunicator.create(id, 2, 2, device));
        assertThrows(TornadoRuntimeException.class, () -> NcclCommunicator.create(id, 0, 0, device));
        assertThrows(TornadoRuntimeException.class, () -> NcclCommunicator.create(id, 2, -1, device));
    }

    /** A communicator of one process and one rank, created from a unique id: runs on a single GPU. */
    @Test
    public void testSingleRankFromUniqueId() throws Exception {
        TornadoDevice device = backend().getDevice(0);
        NcclMultiProcessWorker.runRank(NcclUniqueId.create(device), 1, 0, device);
    }

    /**
     * {@link NcclUniqueId#create()} names no device and must not start a backend to choose between
     * NCCL and RCCL. A child JVM enables OpenCL next to this test's backend, so OpenCL is backend 0
     * under the default priorities (and may find no platform at all), creates the id with
     * {@code create()} and runs one rank on device 0 of this test's backend, which it finds by type.
     */
    @Test
    public void testUniqueIdWithoutDeviceStartsNoBackend() throws Exception {
        if (ModuleLayer.boot().findModule("tornado.drivers.opencl").isEmpty()) {
            throw new TornadoVMOpenCLNotSupported("This test needs the OpenCL backend installed, to be the default backend of the child process");
        }
        TornadoVMBackendType backendType = backend().getDefaultDevice().getTornadoVMBackend();
        List<String> command = childCommand(List.of("-Dtornado.enabled.backends=opencl," + backendType.name().toLowerCase(Locale.ROOT)), mainClass(RankWithIdWithoutDevice.class),
                backendType.name(), "0");
        Process child = new ProcessBuilder(command).redirectErrorStream(true).start();
        try {
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
            assertTrue(child.waitFor(CHILD_TIMEOUT_SECONDS, TimeUnit.SECONDS), "the child process did not finish in time");
            reader.join(TimeUnit.SECONDS.toMillis(10));
            String output = childOutput.toString();
            assertEquals(0, child.exitValue(), "child process failed:\n" + output);
            assertTrue(output.contains("NCCL-WORKER rank 0 OK"), output);
        } finally {
            child.destroyForcibly();
        }
    }

    /**
     * The child of {@link #testUniqueIdWithoutDeviceStartsNoBackend}: a one-rank communicator from an
     * id created with {@link NcclUniqueId#create()}, on device {@code args[1]} of the backend of type
     * {@code args[0]}.
     */
    public static final class RankWithIdWithoutDevice {
        public static void main(String[] args) {
            try {
                NcclUniqueId id = NcclUniqueId.create();
                NcclMultiProcessWorker.runRank(id, 1, 0, backendOfType(TornadoVMBackendType.valueOf(args[0])).getDevice(Integer.parseInt(args[1])));
                System.out.println("NCCL-WORKER rank 0 OK");
                System.exit(0);
            } catch (Throwable t) {
                System.out.println("NCCL-WORKER rank 0 FAILED: " + t);
                t.printStackTrace(System.out);
                System.exit(1);
            }
        }

        /** The backend of {@code type}; a backend that fails to start, such as OpenCL with no platform, is skipped. */
        private static TornadoBackend backendOfType(TornadoVMBackendType type) {
            TornadoRuntime runtime = TornadoRuntimeProvider.getTornadoRuntime();
            for (int i = 0; i < runtime.getNumBackends(); i++) {
                try {
                    if (runtime.getBackendType(i) == type) {
                        return runtime.getBackend(i);
                    }
                } catch (RuntimeException e) {
                    // not the backend under test
                }
            }
            throw new TornadoRuntimeException("No " + type + " backend is enabled");
        }
    }

    /** The command line of this JVM, with its main module and class replaced by the worker's. */
    private static List<String> childCommand(String... workerArgs) {
        return childCommand(List.of(), mainClass(NcclMultiProcessWorker.class), workerArgs);
    }

    /** The command line of this JVM, with {@code jvmOptions} added and its main module and class replaced. */
    private static List<String> childCommand(List<String> jvmOptions, String mainClass, String... mainArgs) {
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
        command.addAll(jvmOptions);
        command.add("-m");
        command.add(mainClass);
        command.addAll(Arrays.asList(mainArgs));
        return command;
    }

    /** The {@code -m} argument that runs {@code type}. */
    private static String mainClass(Class<?> type) {
        return type.getModule().getName() + "/" + type.getName();
    }

    @Test
    public void testTwoProcesses() throws Exception {
        TornadoBackend backend = backend();
        if (backend.getNumDevices() < 2) {
            throw new TornadoVMMultiDeviceNotSupported("This test needs at least 2 CUDA devices");
        }
        NcclUniqueId id = NcclUniqueId.create(backend.getDevice(0));
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
            assertTrue(!rank0.isAlive(), "rank 0 did not finish in time; child output:\n" + childOutput);
            if (rank0Failure[0] != null) {
                throw new AssertionError("rank 0 failed: " + rank0Failure[0] + "\nchild output:\n" + childOutput, rank0Failure[0]);
            }
            assertTrue(child.waitFor(CHILD_TIMEOUT_SECONDS, TimeUnit.SECONDS), "the child process did not finish in time");
            reader.join(TimeUnit.SECONDS.toMillis(10));
            String output = childOutput.toString();
            assertEquals(0, child.exitValue(), "child process failed:\n" + output);
            assertTrue(output.contains("NCCL-WORKER rank 1 OK"), output);
        } finally {
            child.destroyForcibly();
        }
    }
}
