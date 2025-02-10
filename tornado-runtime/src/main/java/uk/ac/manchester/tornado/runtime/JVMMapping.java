/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
 *
 */
package uk.ac.manchester.tornado.runtime;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.TornadoTargetDevice;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.memory.DeviceBufferState;
import uk.ac.manchester.tornado.api.memory.TornadoMemoryProvider;
import uk.ac.manchester.tornado.api.memory.XPUBuffer;
import uk.ac.manchester.tornado.runtime.common.KernelStackFrame;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.common.TornadoSchedulingStrategy;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.common.XPUDeviceBufferState;

public class JVMMapping implements TornadoXPUDevice {

    @Override
    public void dumpEvents(long executionPlanId) {
        TornadoInternalError.unimplemented();
    }

    @Override
    public int enqueueBarrier(long executionPlanId, int[] events) {
        TornadoInternalError.unimplemented();
        return -1;
    }

    @Override
    public int enqueueMarker(long executionPlanId) {
        TornadoInternalError.unimplemented();
        return -1;
    }

    @Override
    public int enqueueMarker(long executionPlanId, int[] events) {
        TornadoInternalError.unimplemented();
        return -1;
    }

    @Override
    public List<Integer> ensurePresent(long executionPlanId, Object object, DeviceBufferState objectState, int[] events, long size, long offset) {
        TornadoInternalError.unimplemented();
        return null;
    }

    @Override
    public void flush(long executionPlanId) {
        TornadoInternalError.unimplemented();
    }

    @Override
    public String getDescription() {
        return "default JVM";
    }

    @Override
    public TornadoMemoryProvider getMemoryProvider() {
        TornadoInternalError.unimplemented();
        return null;
    }

    @Override
    public TornadoSchedulingStrategy getPreferredSchedule() {
        return TornadoSchedulingStrategy.PER_CPU_BLOCK;
    }

    @Override
    public void clean() {
        TornadoInternalError.unimplemented();
    }

    @Override
    public List<Integer> streamIn(long executionPlanId, Object object, long batchSize, long hostOffset, DeviceBufferState objectState, int[] events) {
        TornadoInternalError.unimplemented();
        return null;
    }

    @Override
    public int streamOutBlocking(long executionPlanId, Object object, long hostOffset, DeviceBufferState objectState, int[] list) {
        TornadoInternalError.unimplemented();
        return -1;
    }

    @Override
    public String toString() {
        return "Host JVM";
    }

    @Override
    public void ensureLoaded(long executionPlanId) {
    }

    @Override
    public KernelStackFrame createKernelStackFrame(long executionPlanId, int numArgs, Access access) {
        return null;
    }

    @Override
    public XPUBuffer createOrReuseAtomicsBuffer(int[] arr, Access access) {
        return null;
    }

    @Override
    public TornadoInstalledCode installCode(long executionPlanId, SchedulableTask task) {
        return null;
    }

    @Override
    public long allocate(Object object, long batchSize, DeviceBufferState state, Access access) {
        return -1;
    }

    @Override
    public synchronized long allocateObjects(Object[] objects, long batchSize, DeviceBufferState[] states, Access[] accesses) {
        return -1;
    }

    @Override
    public synchronized long deallocate(DeviceBufferState state) {
        return 0;
    }

    @Override
    public int streamOut(long executionPlanId, Object object, long hostOffset, DeviceBufferState objectState, int[] list) {
        return -1;
    }

    @Override
    public int enqueueBarrier(long executionPlanId) {
        return -1;
    }

    @Override
    public void sync(long executionPlanId) {

    }

    @Override
    public Event resolveEvent(long executionPlanId, int event) {
        return new EmptyEvent();
    }

    @Override
    public void flushEvents(long executionPlanId) {

    }

    @Override
    public String getDeviceName() {
        return "jvm";
    }

    @Override
    public String getPlatformName() {
        return "jvm";
    }

    @Override
    public TornadoDeviceContext getDeviceContext() {
        return null;
    }

    @Override
    public TornadoTargetDevice getPhysicalDevice() {
        return null;
    }

    @Override
    public TornadoDeviceType getDeviceType() {
        return null;
    }

    @Override
    public boolean isFullJITMode(long executionPlanId, SchedulableTask task) {
        return false;
    }

    @Override
    public TornadoInstalledCode getCodeFromCache(long executionPlanId, SchedulableTask task) {
        return null;
    }

    @Override
    public int[] checkAtomicsForTask(SchedulableTask task) {
        return null;
    }

    @Override
    public int[] checkAtomicsForTask(SchedulableTask task, int[] array, int paramIndex, Object value) {
        return null;
    }

    @Override
    public int[] updateAtomicRegionAndObjectState(SchedulableTask task, int[] array, int paramIndex, Object value, XPUDeviceBufferState objectState) {
        return null;
    }

    @Override
    public int getAtomicsGlobalIndexForTask(SchedulableTask task, int paramIndex) {
        return -1;
    }

    @Override
    public boolean checkAtomicsParametersForTask(SchedulableTask task) {
        return false;
    }

    @Override
    public void enableThreadSharing() {
        TornadoInternalError.unimplemented();
    }

    @Override
    public void setAtomicRegion(XPUBuffer bufferAtomics) {

    }

    @Override
    public long getMaxAllocMemory() {
        return Runtime.getRuntime().maxMemory();
    }

    @Override
    public long getMaxGlobalMemory() {
        return Runtime.getRuntime().maxMemory();
    }

    @Override
    public long getDeviceLocalMemorySize() {
        return 0;
    }

    @Override
    public long[] getDeviceMaxWorkgroupDimensions() {
        return new long[0];
    }

    @Override
    public String getDeviceOpenCLCVersion() {
        return "";
    }

    @Override
    public Object getDeviceInfo() {
        return null;
    }

    @Override
    public int getBackendIndex() {
        return 0;
    }

    @Override
    public Object getAtomic() {
        return null;
    }

    @Override
    public void setAtomicsMapping(ConcurrentHashMap<Object, Integer> mappingAtomics) {

    }

    @Override
    public TornadoVMBackendType getTornadoVMBackend() {
        return TornadoVMBackendType.JAVA;
    }

    @Override
    public boolean isSPIRVSupported() {
        return false;
    }

    @Override
    public void mapDeviceRegion(long executionPlanId, Object destArray, Object srcArray, DeviceBufferState deviceStateSrc, DeviceBufferState deviceStateDest, long offset) {
        throw new UnsupportedOperationException();
    }

}
