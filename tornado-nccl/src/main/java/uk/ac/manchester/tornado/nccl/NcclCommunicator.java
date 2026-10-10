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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.nccl.provider.NcclNativeLib;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoNativeStreamSupport;

/**
 * An NCCL communicator: one rank per CUDA device. {@link #create(TornadoDevice...)} puts all ranks
 * in this process (rank {@code i} is {@code devices[i]});
 * {@link #create(NcclUniqueId, int, int, TornadoDevice...)} creates this process's ranks of a
 * communicator that spans several processes, possibly on several machines. Collectives on the communicator are added to task graphs with
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
    /** CUDA runtime ordinal, {@code ncclComm_t} and global rank of each of this process's ranks. */
    private final int[] ordinals;
    private final long[] comms;
    private final int[] ranks;
    /** Number of ranks across all processes. */
    private final int size;
    private volatile boolean closed;
    private volatile boolean aborted;

    /**
     * Communicators used by the NCCL tasks run on the current thread, when the thread belongs to an
     * {@link NcclPlanGroup}: the group aborts them if one of its ranks fails.
     */
    private static final ThreadLocal<Set<NcclCommunicator>> USED_ON_THIS_THREAD = new ThreadLocal<>();

    private NcclCommunicator(int[] ordinals, long[] comms, int[] ranks, int size) {
        this.handle = NEXT_HANDLE.getAndIncrement();
        this.ordinals = ordinals;
        this.comms = comms;
        this.ranks = ranks;
        this.size = size;
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
        int[] ordinals = distinctOrdinals(devices);
        int[] ranks = new int[devices.length];
        Arrays.setAll(ranks, i -> i);
        return register(new NcclCommunicator(ordinals, NcclNativeLib.commInitAll(ordinals), ranks, devices.length));
    }

    /**
     * Creates this process's ranks of a communicator that spans several processes: the devices
     * get the global ranks {@code firstRank}, {@code firstRank + 1}, ... of a communicator of
     * {@code worldSize} ranks. Every process passes the same {@code id} (created once, see
     * {@link NcclUniqueId}) and its own ranks, and the call blocks until all {@code worldSize} ranks
     * have joined.
     */
    public static NcclCommunicator create(NcclUniqueId id, int worldSize, int firstRank, TornadoDevice... devices) {
        if (id == null) {
            throw new TornadoRuntimeException("[ERROR] A multi-process NCCL communicator needs the unique id shared by its processes");
        }
        if (devices == null || devices.length == 0) {
            throw new TornadoRuntimeException("[ERROR] An NCCL communicator needs at least one device");
        }
        if (worldSize < 1 || firstRank < 0 || firstRank + devices.length > worldSize) {
            throw new TornadoRuntimeException("[ERROR] Ranks " + firstRank + " to " + (firstRank + devices.length - 1) + " do not fit a communicator of " + worldSize + " ranks");
        }
        NcclNativeLib.load();
        int[] ordinals = distinctOrdinals(devices);
        int[] ranks = new int[devices.length];
        long[] comms = new long[devices.length];
        if (devices.length == 1) {
            ranks[0] = firstRank;
            // ncclCommInitRank works on the CUDA device current on the calling thread.
            cudaOrdinal(devices[0]);
            comms[0] = NcclNativeLib.commInitRank(worldSize, id.bytes(), firstRank);
        } else {
            NcclNativeLib.groupStart();
            try {
                for (int i = 0; i < devices.length; i++) {
                    ranks[i] = firstRank + i;
                    cudaOrdinal(devices[i]);
                    comms[i] = NcclNativeLib.commInitRank(worldSize, id.bytes(), firstRank + i);
                }
            } finally {
                NcclNativeLib.groupEnd();
            }
        }
        return register(new NcclCommunicator(ordinals, comms, ranks, worldSize));
    }

    private static NcclCommunicator register(NcclCommunicator communicator) {
        COMMUNICATORS.put(communicator.handle, communicator);
        return communicator;
    }

    /** The CUDA ordinals of the devices, which must all be different. */
    private static int[] distinctOrdinals(TornadoDevice[] devices) {
        int[] ordinals = new int[devices.length];
        for (int i = 0; i < devices.length; i++) {
            ordinals[i] = cudaOrdinal(devices[i]);
            for (int j = 0; j < i; j++) {
                if (ordinals[j] == ordinals[i]) {
                    throw new TornadoRuntimeException("[ERROR] NCCL ranks must be on distinct GPUs: " + devices[j] + " and " + devices[i] + " are the same CUDA device");
                }
            }
        }
        return ordinals;
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

    /** Number of ranks, across all the processes of the communicator. */
    public int size() {
        return size;
    }

    /** The global ranks of this process, in the order of the devices it was created with. */
    public int[] localRanks() {
        return ranks.clone();
    }

    /** Looks up a live communicator by its {@link #handle()}. */
    public static NcclCommunicator fromHandle(long handle) {
        NcclCommunicator communicator = COMMUNICATORS.get(handle);
        if (communicator == null) {
            throw new TornadoRuntimeException("[ERROR] No open NCCL communicator with handle " + handle);
        }
        return communicator;
    }


    /** Records, for the {@link NcclPlanGroup} rank thread calling it, the communicators it uses. */
    static void trackUsesOnThisThread(Set<NcclCommunicator> uses) {
        USED_ON_THIS_THREAD.set(uses);
    }

    /**
     * The {@code ncclComm_t} of the rank that runs on the CUDA device with the given ordinal. Called
     * by the provider on the thread that runs the task, before the NCCL call.
     */
    public long commForOrdinal(int ordinal) {
        if (aborted) {
            throw new TornadoRuntimeException("[ERROR] NCCL communicator " + handle + " was aborted after a rank failed; create a new communicator and new plans");
        }
        if (closed) {
            throw new TornadoRuntimeException("[ERROR] NCCL communicator " + handle + " is closed");
        }
        Set<NcclCommunicator> uses = USED_ON_THIS_THREAD.get();
        if (uses != null) {
            uses.add(this);
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
     * fail after this. Close plans captured into CUDA graphs <b>before</b> the communicator: NCCL
     * waits in {@code ncclCommDestroy} for every instantiated graph that still uses the
     * communicator, so the wrong order blocks here.
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        COMMUNICATORS.remove(handle);
        if (aborted) {
            return; // ncclCommAbort has already freed the communicators
        }
        for (long comm : comms) {
            NcclNativeLib.commDestroy(comm);
        }
    }

    /**
     * Aborts the communicator: NCCL stops its operations still running on the device, so ranks
     * waiting for a peer that failed return instead of waiting forever, and frees it. The
     * communicator cannot be used after this; tasks that use it fail with a clear error.
     * {@link NcclPlanGroup} calls this when a rank fails.
     *
     * <p>
     * Returns straight away: the abort runs on a thread of its own, because NCCL only finishes
     * freeing the communicator once no instantiated CUDA graph refers to it any more, which happens
     * when the plans that captured it are closed. Closing an aborted communicator only forgets it.
     * </p>
     */
    public void abort() {
        synchronized (this) {
            if (closed || aborted) {
                return;
            }
            aborted = true;
        }
        Thread aborter = new Thread(() -> {
            for (long comm : comms) {
                try {
                    NcclNativeLib.commAbort(comm);
                } catch (RuntimeException e) {
                    // Keep aborting the other ranks: a rank left running would keep its peers waiting.
                }
            }
        }, "tornado-nccl-abort-" + handle);
        aborter.setDaemon(true);
        aborter.start();
    }

    /** Whether the communicator was aborted, by {@link #abort()} or after a rank failure. */
    public boolean isAborted() {
        return aborted;
    }

    @Override
    public String toString() {
        return "NcclCommunicator[handle=" + handle + ", size=" + size + ", ranks=" + Arrays.toString(ranks) + " on devices " + Arrays.toString(ordinals) + "]";
    }
}
