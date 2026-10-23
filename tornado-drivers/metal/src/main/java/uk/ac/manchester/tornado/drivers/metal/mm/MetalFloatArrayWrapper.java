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
package uk.ac.manchester.tornado.drivers.metal.mm;

import uk.ac.manchester.tornado.runtime.common.JavaKind;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.drivers.metal.MetalDeviceContext;

public class MetalFloatArrayWrapper extends MetalArrayWrapper<float[]> {

    public MetalFloatArrayWrapper(MetalDeviceContext deviceContext, long batchSize, Access access) {
        super(deviceContext, JavaKind.Float, batchSize, access);
    }

    protected MetalFloatArrayWrapper(final float[] array, final MetalDeviceContext device, long batchSize, Access access) {
        super(array, device, JavaKind.Float, batchSize, access);
    }

    @Override
    protected int readArrayData(long executionPlanId, long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents) {
        return deviceContext.readBuffer(executionPlanId, bufferId, offset, bytes, value, hostOffset, waitEvents);
    }

    @Override
    protected void writeArrayData(long executionPlanId, long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents) {
        deviceContext.writeBuffer(executionPlanId, bufferId, offset, bytes, value, hostOffset, waitEvents);
    }

    @Override
    protected int enqueueReadArrayData(long executionPlanId, long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents) {
        return deviceContext.enqueueReadBuffer(executionPlanId, bufferId, offset, bytes, value, hostOffset, waitEvents);
    }

    @Override
    protected int enqueueWriteArrayData(long executionPlanId, long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents) {
        return deviceContext.enqueueWriteBuffer(executionPlanId, bufferId, offset, bytes, value, hostOffset, waitEvents);
    }

    @Override
    public int getSizeOfType() {
        return Float.BYTES;
    }

}
