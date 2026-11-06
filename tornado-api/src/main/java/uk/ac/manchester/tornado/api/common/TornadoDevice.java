/*
 * Copyright (c) 2013-2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api.common;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.TornadoTargetDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.memory.DeviceBufferState;
import uk.ac.manchester.tornado.api.memory.TornadoMemoryProvider;

public interface TornadoDevice {

    /**
     * Whether this device shares memory with the host and its backend supports
     * {@link uk.ac.manchester.tornado.api.TaskGraph#consumeFromUnifiedMemory(Object...)}. When false,
     * a task graph that consumes from unified memory on this device fails when the plan compiles;
     * use an ordinary transfer instead. A true result does not make every array eligible: the
     * storage requirements are listed on {@code consumeFromUnifiedMemory}.
     *
     * @return true if this device can consume host storage in place
     */
    default boolean isUnifiedMemorySupported() {
        return false;
    }

    /**
     * Whether the storage of {@code object} is in unified memory that this device can consume in
     * place, meeting every storage requirement of
     * {@link uk.ac.manchester.tornado.api.TaskGraph#consumeFromUnifiedMemory(Object...)}. The query
     * binds nothing. It does not check task accesses or the execution mode, and the answer changes
     * if the storage is closed.
     *
     * @param object
     *     the array to check
     * @return true if this device can consume the storage of {@code object} in place
     */
    default boolean isOnUnifiedMemory(Object object) {
        return false;
    }

    /**
     * It allocates an object in the pre-defined heap of the target device. It also
     * ensures that there is enough space for the input object.
     *
     * @param object
     *     to be allocated
     * @param batchSize
     *     size of the object to be allocated. If this value is <= 0, then it
     *     allocates the sizeof(object).
     * @param state
     *     state of the object in the target device
     *     {@link DeviceBufferState}
     * @return an event ID
     */
    long allocate(Object object, long batchSize, DeviceBufferState state, Access access);

    long allocateObjects(Object[] objects, long batchSize, DeviceBufferState[] states, Access[] accesses);

    long deallocate(DeviceBufferState state);

    /**
     * It allocates and copy in the content of the object to the target device.
     *
     * @param object
     *     to be allocated
     * @param objectState
     *     state of the object in the target device
     *     {@link DeviceBufferState}
     * @param events
     *     list of pending events (dependencies)
     * @param batchSize
     *     size of the object to be allocated. If this value is <= 0, then it
     *     allocates the sizeof(object).
     * @param hostOffset
     *     offset in bytes for the copy within the host input array (or
     *     object)
     * @return an event ID
     */
    List<Integer> ensurePresent(long executionPlanId, Object object, DeviceBufferState objectState, int[] events, long batchSize, long hostOffset);

    /**
     * It always copies in the input data (object) from the host to the target
     * device.
     *
     * @param object
     *     to be copied
     * @param batchSize
     *     size of the object to be allocated. If this value is <= 0, then it
     *     allocates the sizeof(object).
     * @param hostOffset
     *     offset in bytes for the copy within the host input array (or
     *     object)
     * @param objectState
     *     state of the object in the target device
     *     {@link DeviceBufferState}
     * @param events
     *     list of previous events
     * @return and event ID
     */
    List<Integer> streamIn(long executionPlanId, Object object, long batchSize, long hostOffset, DeviceBufferState objectState, int[] events);

    /**
     * It copies a device buffer from the target device to the host. Copies are
     * non-blocking
     *
     * @param object
     *     to be copied.
     * @param hostOffset
     *     offset in bytes for the copy within the host input array (or
     *     object)
     * @param objectState
     *     state of the object in the target device
     *     {@link DeviceBufferState}
     * @param events
     *     of pending events
     * @return and event ID
     */
    int streamOut(long executionPlanId, Object object, long hostOffset, DeviceBufferState objectState, int[] events);

    /**
     * It copies a device buffer from the target device to the host. Copies are
     * blocking between the device and the host.
     *
     * @param object
     *     to be copied.
     * @param hostOffset
     *     offset in bytes for the copy within the host input array (or
     *     object)
     * @param objectState
     *     state of the object in the target device
     *     {@link DeviceBufferState}
     * @param events
     *     of pending events
     * @return and event ID
     */
    int streamOutBlocking(long executionPlanId, Object object, long hostOffset, DeviceBufferState objectState, int[] events);

    /**
     * It resolves a pending event.
     *
     * @param event
     *     ID
     * @return an object of type {@link Event}
     */
    Event resolveEvent(long executionPlanId, int event);

    void ensureLoaded(long executionPlanId);

    void flushEvents(long executionPlanId);

    int enqueueBarrier(long executionPlanId);

    int enqueueBarrier(long executionPlanId, int[] events);

    int enqueueMarker(long executionPlanId);

    int enqueueMarker(long executionPlanId, int[] events);

    void sync(long executionPlanId);

    void flush(long executionPlanId);

    void clean();

    void dumpEvents(long executionPlanId);

    String getDeviceName();

    String getDescription();

    String getPlatformName();

    TornadoDeviceContext getDeviceContext();

    TornadoTargetDevice getPhysicalDevice();

    TornadoMemoryProvider getMemoryProvider();

    TornadoDeviceType getDeviceType();

    long getMaxAllocMemory();

    long getMaxGlobalMemory();

    long getDeviceLocalMemorySize();

    long[] getDeviceMaxWorkgroupDimensions();

    String getDeviceOpenCLCVersion();

    Object getDeviceInfo();

    int getBackendIndex();

    /**
     * Returns the number of processors available to the JVM. We need to overwrite
     * this function only for Virtual Devices, where we read the value from the
     * descriptor file.
     */
    default int getAvailableProcessors() {
        return Runtime.getRuntime().availableProcessors();
    }

    Object getAtomic();

    @Deprecated
    void setAtomicsMapping(ConcurrentHashMap<Object, Integer> mappingAtomics);

    TornadoVMBackendType getTornadoVMBackend();

    void mapDeviceRegion(long executionPlanId, Object destArray, Object srcArray, DeviceBufferState deviceStateSrc, DeviceBufferState deviceStateDest, long offset);
}
