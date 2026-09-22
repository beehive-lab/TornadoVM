/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2023, 2024, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package uk.ac.manchester.tornado.drivers.cuda.mm;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.memory.XPUBuffer;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.api.types.collections.TornadoCollectionInterface;
import uk.ac.manchester.tornado.api.types.images.TornadoImagesInterface;
import uk.ac.manchester.tornado.api.types.matrix.TornadoMatrixInterface;
import uk.ac.manchester.tornado.api.types.volumes.TornadoVolumesInterface;
import uk.ac.manchester.tornado.drivers.cuda.CUDADeviceContext;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.common.exceptions.TornadoUnsupportedError;

public class CUDAMemorySegmentWrapper implements XPUBuffer {

    private static final int INIT_VALUE = -1;

    /**
     * Alignment, in bytes, that the first data element of a native array is placed on.
     *
     * <p>
     * A warp reads 32 x 4 = 128 contiguous bytes and the L1-to-L2 path is addressed in 32-byte sectors, so an aligned
     * warp-wide access is served by exactly 4 sectors. {@code cuMemAlloc} returns a suitably aligned base, but the
     * generated kernel reaches the data at {@code base + ARRAY_HEADER} -- 16 bytes in -- and that sub-sector offset
     * makes every warp-wide access straddle a fifth sector: 5 transactions instead of 4.
     * </p>
     *
     * <p>
     * Only sub-sector misalignment costs anything, so <b>32 bytes is sufficient</b> and is the default. Measured on an
     * RTX 4090 (sm_89) by sweeping the base offset of a single compiled kernel, varying nothing else: a payload on a
     * 32-, 64- or 128-byte boundary all give 4.00 sectors per request, while the current 16-byte offset gives 5.00.
     * Aligning to 128 instead would pad every buffer by 112 bytes rather than 16 for the same sector count.
     * </p>
     */
    private static final long PAYLOAD_ALIGNMENT = Long.getLong("tornado.cuda.payloadAlignment", 32L);

    /**
     * Bytes prepended to the device allocation so that {@code base + HEADER_PAD + ARRAY_HEADER} is
     * {@link #PAYLOAD_ALIGNMENT}-aligned. The kernel is handed the padded pointer and still indexes at
     * {@code + ARRAY_HEADER}, so the generated code and any pre-built kernel are unchanged -- only where the buffer
     * starts moves.
     */
    private static final long HEADER_PAD = PAYLOAD_ALIGNMENT <= 0 ? 0 : Math.floorMod(-TornadoNativeArray.ARRAY_HEADER, PAYLOAD_ALIGNMENT);

    private final CUDADeviceContext deviceContext;
    private final long batchSize;
    private long bufferId;
    /**
     * The address the buffer provider handed out, which is what has to be given back on release. {@link #bufferId} is
     * this plus {@link #HEADER_PAD} whenever this wrapper owns its allocation.
     */
    private long bufferIdBase;
    private long bufferOffset;
    private long bufferSize;

    private long subregionSize;
    private Access access;
    private final int sizeOfType;

    /** Base address of the host segment this wrapper holds pinned, or 0 when not pinned. */
    private long pinnedHostPointer;

    public CUDAMemorySegmentWrapper(long bufferSize, CUDADeviceContext deviceContext, long batchSize, Access access, int sizeOfType) {
        this.deviceContext = deviceContext;
        this.batchSize = batchSize;
        this.bufferSize = bufferSize;
        this.bufferId = INIT_VALUE;
        this.bufferIdBase = INIT_VALUE;
        this.bufferOffset = 0;
        this.access = access;
        this.sizeOfType = sizeOfType;
        if (sizeOfType <= 0) {
            throw new TornadoRuntimeException("Invalid size of type " + sizeOfType);
        }
    }

    public CUDAMemorySegmentWrapper(CUDADeviceContext deviceContext, long batchSize, Access access, int sizeOfType) {
        this(INIT_VALUE, deviceContext, batchSize, access, sizeOfType);
    }

    /**
     * Whether transfers of this buffer should go through the pinned staging ring -
     * enabled, non-batch (batched chunks are pipelined by the device-buffer ring instead), and
     * large enough that the per-chunk staging overhead is amortised.
     */
    private boolean useStagedTransfer() {
        return deviceContext.isStagedTransfersEnabled() && batchSize <= 0 && bufferSize >= TornadoOptions.STAGED_TRANSFER_MIN_SIZE;
    }

    @Override
    public long toBuffer() {
        return this.bufferId;
    }

    @Override
    public void setBuffer(XPUBufferWrapper bufferWrapper) {
        // A buffer handed over by someone else: it is not ours to pad or to release, so bufferIdBase tracks it
        // unchanged and no alignment padding is applied.
        this.bufferId = bufferWrapper.buffer;
        this.bufferIdBase = bufferWrapper.buffer;
        this.bufferOffset = bufferWrapper.bufferOffset;

        bufferWrapper.bufferOffset += bufferSize;
    }

    @Override
    public long getBufferOffset() {
        return bufferOffset;
    }

    @Override
    public void read(long executionPlanId, final Object reference) {
        read(executionPlanId, reference, 0, 0, null, false);
    }

    private MemorySegment getSegmentWithHeader(final Object reference) {
        return switch (reference) {
            case TornadoNativeArray tornadoNativeArray -> tornadoNativeArray.getSegmentWithHeader();
            case TornadoCollectionInterface<?> tornadoCollectionInterface -> tornadoCollectionInterface.getSegmentWithHeader();
            case TornadoImagesInterface<?> imagesInterface -> imagesInterface.getSegmentWithHeader();
            case TornadoMatrixInterface<?> matrixInterface -> matrixInterface.getSegmentWithHeader();
            case TornadoVolumesInterface<?> volumesInterface -> volumesInterface.getSegmentWithHeader();
            default -> throw new TornadoMemoryException("Memory Segment not supported: " + reference.getClass());
        };
    }

    @Override
    public int read(long executionPlanId, final Object reference, long hostOffset, long partialReadSize, int[] events, boolean useDeps) {
        MemorySegment segment;
        segment = getSegmentWithHeader(reference);
        final int returnEvent;
        final long numBytes = getSizeSubRegionSize() > 0 ? getSizeSubRegionSize() : bufferSize;
        if (partialReadSize != 0) {
            // Partial Copy Out due to an under demand copy by the user
            // in this case the host offset is equal to the device offset
            returnEvent = deviceContext.readBuffer(executionPlanId, toBuffer(), hostOffset, partialReadSize, segment.address(), hostOffset, (useDeps) ? events : null);
        } else if (batchSize <= 0) {
            // Partial Copy Out due to batch processing
            returnEvent = deviceContext.readBuffer(executionPlanId, toBuffer(), bufferOffset, numBytes, segment.address(), hostOffset, (useDeps) ? events : null);
        } else {
            // Full copy out (default)
            returnEvent = deviceContext.readBuffer(executionPlanId, toBuffer(), TornadoNativeArray.ARRAY_HEADER, numBytes, segment.address(), hostOffset + TornadoNativeArray.ARRAY_HEADER, (useDeps)
                    ? events
                    : null);
        }

        return useDeps ? returnEvent : -1;
    }

    @Override

    public void write(long executionPlanId, Object reference) {
        MemorySegment segment;
        segment = getSegmentWithHeader(reference);
        if (batchSize <= 0) {
            deviceContext.writeBuffer(executionPlanId, toBuffer(), bufferOffset, bufferSize, segment.address(), 0, null);
        } else {
            throw new TornadoUnsupportedError("[UNSUPPORTED] batch processing for writeBuffer operation");
        }
    }

    @Override
    public int enqueueRead(long executionPlanId, Object reference, long hostOffset, int[] events, boolean useDeps) {
        MemorySegment segment;
        segment = getSegmentWithHeader(reference);

        final int returnEvent;
        if (batchSize <= 0) {
            returnEvent = deviceContext.enqueueReadBuffer(executionPlanId, toBuffer(), bufferOffset, bufferSize, segment.address(), hostOffset, (useDeps) ? events : null);
        } else {
            throw new TornadoUnsupportedError("[UNSUPPORTED] batch processing for enqueueReadBuffer operation");
        }
        return useDeps ? returnEvent : -1;
    }

    @Override
    public List<Integer> enqueueWrite(long executionPlanId, Object reference, long batchSize, long hostOffset, int[] events, boolean useDeps) {
        List<Integer> returnEvents = new ArrayList<>();
        MemorySegment segment;
        segment = getSegmentWithHeader(reference);

        int internalEvent;
        if (batchSize <= 0) {
            if (useStagedTransfer()) {
                // Large one-shot upload (e.g. FIRST_EXECUTION weights) through the pinned staging ring.
                internalEvent = deviceContext.enqueueStagedWriteBuffer(executionPlanId, toBuffer(), bufferOffset, bufferSize, segment.address(), hostOffset, (useDeps) ? events : null);
            } else {
                internalEvent = deviceContext.enqueueWriteBuffer(executionPlanId, toBuffer(), bufferOffset, bufferSize, segment.address(), hostOffset, (useDeps) ? events : null);
            }
        } else {
            // Honour the sub-region size like read() does: a reused (locked) buffer can be larger than
            // the current chunk (e.g. a full-chunk buffer serving the smaller remainder chunk), and
            // copying its full bufferSize would overrun the host segment.
            final long numBytes = getSizeSubRegionSize() > 0 ? getSizeSubRegionSize() : bufferSize;
            internalEvent = deviceContext.enqueueWriteBuffer(executionPlanId, toBuffer(), 0, TornadoNativeArray.ARRAY_HEADER, segment.address(), 0, (useDeps) ? events : null);
            returnEvents.add(internalEvent);
            internalEvent = deviceContext.enqueueWriteBuffer(executionPlanId, toBuffer(), bufferOffset + TornadoNativeArray.ARRAY_HEADER, numBytes, segment.address(),
                    hostOffset + TornadoNativeArray.ARRAY_HEADER, (useDeps) ? events : null);
        }
        returnEvents.add(internalEvent);
        return returnEvents;
    }

    @Override
    public void allocate(Object reference, long batchSize, Access access) throws TornadoOutOfMemoryException, TornadoMemoryException {
        MemorySegment segment;
        segment = getSegmentWithHeader(reference);

        if (batchSize <= 0) {
            // HEADER_PAD extra bytes are requested and skipped over, so that the data the kernel reaches at
            // base + ARRAY_HEADER starts on a PAYLOAD_ALIGNMENT boundary. Every transfer below is expressed relative
            // to toBuffer(), so shifting the base shifts the header and the payload together.
            bufferSize = segment.byteSize();
            bufferIdBase = deviceContext.getBufferProvider().getOrAllocateBufferWithSize(bufferSize + HEADER_PAD, access);
            bufferId = bufferIdBase + HEADER_PAD;
        } else {
            // Batched slots are not padded. The slot size is chosen by the user (e.g. withBatch("300MB")) and is
            // checked against the device heap, so growing the request by HEADER_PAD would turn an allocation that
            // exactly fits into one that does not.
            bufferSize = batchSize;
            bufferIdBase = deviceContext.getBufferProvider().getOrAllocateBufferWithSize(bufferSize + TornadoNativeArray.ARRAY_HEADER, access);
            bufferId = bufferIdBase;
        }

        // Pin the full host segment so async H2D/D2H transfers DMA directly (no driver
        // staging copy, true transfer/compute overlap). Ownership, aliasing and pin
        // caching are handled by the central CUDAPinnedMemoryRegistry (refcounted,
        // stale-pin safe). Any hold from a previous allocate is released first, so the
        // refcount stays balanced across alloc/free cycles.
        // For large read-only segments served by the staged-transfer ring, skip the whole-segment
        // pin: registering synchronously pages in and pins the entire (possibly cold, mmap'd)
        // segment - exactly the upfront cost the staging ring exists to avoid - and the ring's
        // own pinned slots already make the chunked H2D DMA async.
        if (useStagedTransfer() && access == Access.READ_ONLY) {
            if (TornadoOptions.FULL_DEBUG) {
                new TornadoLogger().info("skipping host pinning (staged transfers): %s", toString());
            }
        } else if (segment != null) {
            CUDAPinnedMemoryRegistry pinRegistry = deviceContext.getPlatformContext().getPinnedMemoryRegistry();
            if (pinnedHostPointer != 0) {
                pinRegistry.unpin(pinnedHostPointer);
                pinnedHostPointer = 0;
            }
            if (pinRegistry.pin(segment, segment.byteSize())) {
                pinnedHostPointer = segment.address();
            }
        }

        if (bufferSize <= 0) {
            throw new TornadoMemoryException("[ERROR] Bytes Allocated <= 0: " + bufferSize);
        }

        if (TornadoOptions.FULL_DEBUG) {
            new TornadoLogger().info("allocated: %s", toString());
        }
    }

    @Override
    public void markAsFreeBuffer() throws TornadoMemoryException {
        TornadoInternalError.guarantee(bufferId != INIT_VALUE, "Fatal error: trying to deallocate an invalid buffer");

        // Release this wrapper's hold on the host pin before recycling; the registry
        // unregisters (after a context sync) only when the last holder releases.
        if (pinnedHostPointer != 0) {
            deviceContext.getPlatformContext().getPinnedMemoryRegistry().unpin(pinnedHostPointer);
            pinnedHostPointer = 0;
        }

        deviceContext.getBufferProvider().markBufferReleased(bufferIdBase, access);
        bufferId = INIT_VALUE;
        bufferIdBase = INIT_VALUE;
        bufferSize = INIT_VALUE;

        if (TornadoOptions.FULL_DEBUG) {
            new TornadoLogger().info("deallocated: %s", toString());
        }
    }

    @Override
    public long deallocate() {
        return deviceContext.getBufferProvider().deallocate(access);
    }

    @Override
    public long size() {
        return bufferSize;
    }

    @Override
    public void setSizeSubRegion(long batchSize) {
        this.subregionSize = batchSize;
    }

    @Override
    public long getSizeSubRegionSize() {
        return subregionSize;
    }

    public long getBatchSize() {
        return batchSize;
    }

    @Override
    public void mapOnDeviceMemoryRegion(long executionPlanId, XPUBuffer srcPointer, long offset) {
        if (!(srcPointer instanceof CUDAMemorySegmentWrapper oclMemorySegmentWrapper)) {
            throw new TornadoRuntimeException("[ERROR] copy pointer must be an instance of CUDAMemorySegmentWrapper: " + srcPointer);
        }
        final long sizeSource = oclMemorySegmentWrapper.bufferSize;
        final long sizeDest = bufferSize;
        this.bufferId = deviceContext.mapOnDeviceMemoryRegion(executionPlanId, this.bufferId, oclMemorySegmentWrapper.bufferId, offset, sizeOfType, sizeSource, sizeDest);
        // The mapped region replaces this wrapper's allocation; keep release behaviour exactly as it was before the
        // alignment padding was introduced, i.e. hand back whatever bufferId now points at.
        this.bufferIdBase = this.bufferId;
    }

    @Override
    public int getSizeOfType() {
        return sizeOfType;
    }


    @Override
    public boolean supportsAsyncRead() {
        // enqueueRead and read copy the same region for a whole-segment transfer.
        return true;
    }
}
