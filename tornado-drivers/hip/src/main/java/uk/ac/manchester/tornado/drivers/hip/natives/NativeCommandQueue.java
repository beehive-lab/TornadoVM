/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2025, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.hip.natives;

import uk.ac.manchester.tornado.drivers.hip.ffm.HIPDriverAPI;
import uk.ac.manchester.tornado.drivers.hip.ffm.HIPHandles;

public class NativeCommandQueue {

    /**
     * HIP device pointers are flat addresses, so mapping a source region onto a destination simply
     * yields the source device pointer; no host mapping is needed.
     */
    public static long mapOnDeviceMemoryRegion(long destDevicePtr, long srcDevicePtr) {
        return srcDevicePtr;
    }

    /**
     * Device-to-device copy of the payload, skipping the TornadoVM segment header. Returns the
     * destination pointer on success, or the failing {@code hipError_t}.
     *
     * <p>{@code hipMemcpyDtoD} is asynchronous with respect to the host and runs on the legacy
     * default stream, which TornadoVM's streams -- created {@code hipStreamNonBlocking} -- are not
     * ordered against. So the device is synchronised before the copy, to let the kernel that
     * produced the source finish, and after it, so that the next kernel reading the destination
     * cannot start before the copy lands. Without the second synchronisation the destination task
     * graph can read the buffer as it was before the copy (seen on Windows with a Radeon 8060S).
     */
    public static long mapOnDeviceMemoryNDRegion(long commandQueuePtr, long destDevicePtr, long srcDevicePtr, long offset, int sizeDataType, long headerSize, long sizeSource, long sizeDest) {
        HIPHandles.Queue queue = HIPHandles.resolve(commandQueuePtr, HIPHandles.Queue.class);
        long headerBytes = headerSize * sizeDataType;
        long payloadBytes = Math.max(sizeDest - headerBytes, 0);
        if (queue != null) {
            HIPDriverAPI.hipCtxSetCurrent(queue.context());
        }
        int result = HIPDriverAPI.hipCtxSynchronize();
        if (result != HIPDriverAPI.HIP_SUCCESS) {
            return result;
        }
        result = HIPDriverAPI.hipMemcpyDtoD(destDevicePtr + headerBytes, srcDevicePtr + (offset * sizeDataType) + headerBytes, payloadBytes);
        if (result != HIPDriverAPI.HIP_SUCCESS) {
            return result;
        }
        result = HIPDriverAPI.hipCtxSynchronize();
        if (result != HIPDriverAPI.HIP_SUCCESS) {
            return result;
        }
        return destDevicePtr;
    }
}
