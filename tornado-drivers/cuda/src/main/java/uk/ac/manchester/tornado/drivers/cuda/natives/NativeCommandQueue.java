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
package uk.ac.manchester.tornado.drivers.cuda.natives;

import uk.ac.manchester.tornado.drivers.cuda.ffm.CUDADriverAPI;
import uk.ac.manchester.tornado.drivers.cuda.ffm.CUDAHandles;

public class NativeCommandQueue {

    /**
     * CUDA device pointers are flat addresses, so mapping a source region onto a destination simply
     * yields the source device pointer; no host mapping is needed.
     */
    public static long mapOnDeviceMemoryRegion(long destDevicePtr, long srcDevicePtr) {
        return srcDevicePtr;
    }

    /**
     * Device-to-device copy of the payload, skipping the TornadoVM segment header. Returns the
     * destination pointer on success, or the failing {@code CUresult}.
     */
    public static long mapOnDeviceMemoryNDRegion(long commandQueuePtr, long destDevicePtr, long srcDevicePtr, long offset, int sizeDataType, long headerSize, long sizeSource, long sizeDest) {
        CUDAHandles.Queue queue = CUDAHandles.resolve(commandQueuePtr, CUDAHandles.Queue.class);
        long headerBytes = headerSize * sizeDataType;
        long payloadBytes = Math.max(sizeDest - headerBytes, 0);
        if (queue != null) {
            CUDADriverAPI.cuCtxSetCurrent(queue.context());
        }
        int result = CUDADriverAPI.cuMemcpyDtoD(destDevicePtr + headerBytes, srcDevicePtr + (offset * sizeDataType) + headerBytes, payloadBytes);
        if (result != CUDADriverAPI.CUDA_SUCCESS) {
            return result;
        }
        return destDevicePtr;
    }
}
