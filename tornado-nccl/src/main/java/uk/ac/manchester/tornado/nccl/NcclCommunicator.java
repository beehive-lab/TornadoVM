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
package uk.ac.manchester.tornado.nccl;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.nccl.provider.NcclNativeLib;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoNativeStreamSupport;

/**
 * An NCCL communicator over a set of CUDA devices in this process, one rank per device: rank
 * {@code i} is {@code devices[i]}. Collectives on the communicator are added to task graphs with
 * the {@link Nccl} factories; each rank runs its own execution plan on its own device, and the
 * plans of all ranks have to execute concurrently (see {@link NcclPlanGroup}), because a
 * collective only completes once every rank has enqueued it.
 *
 * <p>
 * Task graphs refer to a communicator by {@link #handle()}, a plain {@code long}, because every
 * non-primitive argument of a task is treated as data to place on the device.
 * </p>
 */
public final class NcclCommunicator implements AutoCloseable {

    private static final AtomicLong NEXT_HANDLE = new AtomicLong(1);
    private static final Map<Long, NcclCommunicator> COMMUNICATORS = new ConcurrentHashMap<>();

    private final long handle;
    private final int[] ordinals;
    private final long[] comms;
    private volatile boolean closed;

    private NcclCommunicator(int[] ordinals, long[] comms) {
        this.handle = NEXT_HANDLE.getAndIncrement();
        this.ordinals = ordinals;
        this.comms = comms;
    }

    /**
     * Creates a communicator with one rank per device, in the order given. The devices must be
     * distinct CUDA devices; a single device gives a one-rank communicator.
     */
    public static NcclCommunicator create(TornadoDevice... devices) {
        if (devices == null || devices.length == 0) {
            throw new TornadoRuntimeException("[ERROR] An NCCL communicator needs at least one device");
        }
        NcclNativeLib.load();
        int[] ordinals = new int[devices.length];
        for (int i = 0; i < devices.length; i++) {
            ordinals[i] = cudaOrdinal(devices[i]);
            for (int j = 0; j < i; j++) {
                if (ordinals[j] == ordinals[i]) {
                    throw new TornadoRuntimeException("[ERROR] NCCL ranks must be on distinct GPUs: " + devices[j] + " and " + devices[i] + " are the same CUDA device");
                }
            }
        }
        NcclCommunicator communicator = new NcclCommunicator(ordinals, NcclNativeLib.commInitAll(ordinals));
        COMMUNICATORS.put(communicator.handle, communicator);
        return communicator;
    }

    /**
     * CUDA runtime ordinal of a TornadoVM device: its context is made current and the CUDA runtime
     * is asked which device that is, so the mapping holds whatever order the devices were listed in.
     */
    static int cudaOrdinal(TornadoDevice device) {
        if (!(device instanceof TornadoNativeStreamSupport nativeDevice)) {
            throw new TornadoRuntimeException("[ERROR] NCCL needs CUDA devices; " + device + " is not one");
        }
        nativeDevice.makeNativeContextCurrent();
        return NcclNativeLib.currentDeviceOrdinal();
    }

    /** The value task graphs use to refer to this communicator. */
    public long handle() {
        return handle;
    }

    /** Number of ranks. */
    public int size() {
        return comms.length;
    }

    /** Looks up a live communicator by its {@link #handle()}. */
    public static NcclCommunicator fromHandle(long handle) {
        NcclCommunicator communicator = COMMUNICATORS.get(handle);
        if (communicator == null) {
            throw new TornadoRuntimeException("[ERROR] No open NCCL communicator with handle " + handle);
        }
        return communicator;
    }

    /** Number of ranks of the communicator behind {@code handle}. */
    static int sizeOf(long handle) {
        return fromHandle(handle).size();
    }

    /** The {@code ncclComm_t} of the rank that runs on the CUDA device with the given ordinal. */
    public long commForOrdinal(int ordinal) {
        if (closed) {
            throw new TornadoRuntimeException("[ERROR] NCCL communicator " + handle + " is closed");
        }
        for (int rank = 0; rank < ordinals.length; rank++) {
            if (ordinals[rank] == ordinal) {
                return comms[rank];
            }
        }
        throw new TornadoRuntimeException("[ERROR] CUDA device " + ordinal + " is not part of NCCL communicator " + handle + " (devices " + Arrays.toString(ordinals) + ")");
    }

    /**
     * Destroys the communicator. No plan that uses it may still be executing; plans that use it
     * fail after this.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        COMMUNICATORS.remove(handle);
        for (long comm : comms) {
            NcclNativeLib.commDestroy(comm);
        }
    }

    @Override
    public String toString() {
        return "NcclCommunicator[handle=" + handle + ", devices=" + Arrays.toString(ordinals) + "]";
    }
}
