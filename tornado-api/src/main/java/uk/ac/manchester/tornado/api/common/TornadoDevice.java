/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * GNU Classpath is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * GNU Classpath is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING.  If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module.  An independent module is a module which is not derived from
 * or based on this library.  If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so.  If you do not wish to do so, delete this
 * exception statement from your version.
 *
 */
package uk.ac.manchester.tornado.api.common;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.TornadoTargetDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.memory.TornadoDeviceObjectState;
import uk.ac.manchester.tornado.api.memory.TornadoMemoryProvider;

public interface TornadoDevice {

    /**
     * It allocates an object in the pre-defined heap of the target device. It also
     * ensures that there is enough space for the input object.
     *
     * @param object
     *            to be allocated
     * @param batchSize
     *            size of the object to be allocated. If this value is <= 0, then it
     *            allocates the sizeof(object).
     * @param state
     *            state of the object in the target device
     *            {@link TornadoDeviceObjectState}
     * @return an event ID
     */
    int allocate(Object object, long batchSize, TornadoDeviceObjectState state);

    int allocateObjects(Object[] objects, long batchSize, TornadoDeviceObjectState[] states);

    int deallocate(TornadoDeviceObjectState state);

    /**
     * It allocates and copy in the content of the object to the target device.
     *
     * @param object
     *            to be allocated
     * @param objectState
     *            state of the object in the target device
     *            {@link TornadoDeviceObjectState}
     * @param events
     *            list of pending events (dependencies)
     * @param batchSize
     *            size of the object to be allocated. If this value is <= 0, then it
     *            allocates the sizeof(object).
     * @param hostOffset
     *            offset in bytes for the copy within the host input array (or
     *            object)
     * @return an event ID
     */
    List<Integer> ensurePresent(Object object, TornadoDeviceObjectState objectState, int[] events, long batchSize, long hostOffset);

    /**
     * It always copies in the input data (object) from the host to the target
     * device.
     *
     * @param object
     *            to be copied
     * @param batchSize
     *            size of the object to be allocated. If this value is <= 0, then it
     *            allocates the sizeof(object).
     * @param hostOffset
     *            offset in bytes for the copy within the host input array (or
     *            object)
     * @param objectState
     *            state of the object in the target device
     *            {@link TornadoDeviceObjectState}
     * @param events
     *            list of previous events
     * @return and event ID
     */
    List<Integer> streamIn(Object object, long batchSize, long hostOffset, TornadoDeviceObjectState objectState, int[] events);

    /**
     * It copies a device buffer from the target device to the host. Copies are
     * non-blocking
     *
     * @param object
     *            to be copied.
     * @param hostOffset
     *            offset in bytes for the copy within the host input array (or
     *            object)
     * @param objectState
     *            state of the object in the target device
     *            {@link TornadoDeviceObjectState}
     * @param events
     *            of pending events
     * @return and event ID
     */
    int streamOut(Object object, long hostOffset, TornadoDeviceObjectState objectState, int[] events);

    /**
     * It copies a device buffer from the target device to the host. Copies are
     * blocking between the device and the host.
     *
     * @param object
     *            to be copied.
     * @param hostOffset
     *            offset in bytes for the copy within the host input array (or
     *            object)
     * @param objectState
     *            state of the object in the target device
     *            {@link TornadoDeviceObjectState}
     * @param events
     *            of pending events
     * @return and event ID
     */
    int streamOutBlocking(Object object, long hostOffset, TornadoDeviceObjectState objectState, int[] events);

    /**
     * It resolves an pending event.
     *
     * @param event
     *            ID
     * @return an object of type {@link Event}
     */
    Event resolveEvent(int event);

    void ensureLoaded();

    void flushEvents();

    int enqueueBarrier();

    int enqueueBarrier(int[] events);

    int enqueueMarker();

    int enqueueMarker(int[] events);

    void sync();

    void flush();

    void reset();

    void dumpEvents();

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

    int getDriverIndex();

    /**
     * Returns the number of processors available to the JVM. We need to overwrite
     * this function only for Virtual Devices, where we read the value from the
     * descriptor file.
     */
    default int getAvailableProcessors() {
        return Runtime.getRuntime().availableProcessors();
    }

    Object getAtomic();

    void setAtomicsMapping(ConcurrentHashMap<Object, Integer> mappingAtomics);

    TornadoVMBackendType getTornadoVMBackend();

    boolean isSPIRVSupported();
}