/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2022, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.hip.runtime;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.drivers.common.TornadoBufferProvider;
import uk.ac.manchester.tornado.drivers.hip.HIPDeviceContext;
import uk.ac.manchester.tornado.drivers.hip.enums.HIPMemFlags;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;

public class HIPBufferProvider extends TornadoBufferProvider {

    public HIPBufferProvider(HIPDeviceContext deviceContext) {
        super(deviceContext);
    }

    @Override
    public long allocateBuffer(long size, Access access) {
        long oclMemFlags = getHIPMemFlagForAccess(access);
        return ((HIPDeviceContext) deviceContext).getMemoryManager().createBuffer(size, oclMemFlags).getBuffer();
    }

    /**
     * Device buffers obtained from {@code hipMalloc} are not zero-initialised,
     * and TornadoVM reuses pooled device buffers across executions. A
     * {@code WRITE_ONLY} output that the kernel does not fully write (for example,
     * an early-returning kernel) would otherwise read back stale data from a
     * previous allocation, instead of the expected zero.
     *
     * <p>
     * To match the behaviour expected by the runtime (unwritten output buffers
     * read back as zero), we zero the device buffer here, on (re)allocation, when
     * the buffer is handed out from the pool. This happens once per allocation
     * request rather than on every kernel launch, so the performance impact is
     * limited to the allocation path.
     *
     * <p>
     * The zeroing is restricted to {@code WRITE_ONLY} buffers on purpose:
     * <ul>
     * <li>{@code READ_ONLY} / {@code READ_WRITE} buffers receive their initial
     * contents from a host-to-device copy before the kernel runs, so zeroing them
     * is both unnecessary and potentially harmful. For instance, a reduction whose
     * accumulator is an output that is initialised on the host (e.g.
     * {@code result.init(Float.MAX_VALUE)}) and then read-modified-written by the
     * kernel is marked {@code READ_WRITE}; zeroing it would clobber the neutral
     * element.</li>
     * <li>{@code WRITE_ONLY} buffers are pure outputs that the runtime does not
     * copy in from the host. If the kernel skips writing some (or all) of the
     * buffer, the host must observe zeros, not stale pool data.</li>
     * </ul>
     */
    @Override
    public synchronized long getOrAllocateBufferWithSize(long sizeInBytes, Access access) {
        long buffer = super.getOrAllocateBufferWithSize(sizeInBytes, access);
        if (access == Access.WRITE_ONLY) {
            ((HIPDeviceContext) deviceContext).getPlatformContext().zeroBuffer(((HIPDeviceContext) deviceContext).getDeviceIndex(), buffer, sizeInBytes);
        }
        return buffer;
    }

    @Override
    protected void releaseBuffer(long buffer) {
        ((HIPDeviceContext) deviceContext).getMemoryManager().releaseBuffer(buffer);
    }

    private static long getHIPMemFlagForAccess(Access access) {
        switch (access) {
            case READ_ONLY:
                return HIPMemFlags.CL_MEM_READ_ONLY;
            case WRITE_ONLY:
                return HIPMemFlags.CL_MEM_WRITE_ONLY;
            case READ_WRITE:
                return HIPMemFlags.CL_MEM_READ_WRITE;
            default:
                // if access has not been deducted by sketcher set it as RW
                return HIPMemFlags.CL_MEM_READ_WRITE;
        }
    }

}
