/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.metal.natives;

import uk.ac.manchester.tornado.drivers.metal.ffm.MetalObjects;

public class NativeCommandQueue {

    public static long mapOnDeviceMemoryRegion(long destDevicePtr, long srcDevicePtr) {
        return MetalObjects.mapOnDeviceMemoryRegion(destDevicePtr, srcDevicePtr);
    }

    public static long mapOnDeviceMemoryNDRegion(long commandQueuePtr, long destDevicePtr, long srcDevicePtr, long offset, int sizeDataType, long headerSize, long sizeSource, long sizeDest) {
        return MetalObjects.mapOnDeviceMemoryNDRegion(commandQueuePtr, destDevicePtr, srcDevicePtr, offset, sizeDataType, headerSize, sizeSource, sizeDest);
    }
}
