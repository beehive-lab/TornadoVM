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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.runtime;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.TornadoTargetDevice;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.memory.ObjectBuffer;
import uk.ac.manchester.tornado.api.memory.TornadoDeviceObjectState;
import uk.ac.manchester.tornado.api.memory.TornadoMemoryProvider;
import uk.ac.manchester.tornado.runtime.common.DeviceObjectState;
import uk.ac.manchester.tornado.runtime.common.KernelArgs;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.common.TornadoSchedulingStrategy;

public class JVMMapping implements TornadoAcceleratorDevice {

    @Override
    public void dumpEvents() {
        TornadoInternalError.unimplemented();
    }

    @Override
    public int enqueueBarrier(int[] events) {
        TornadoInternalError.unimplemented();
        return -1;
    }

    @Override
    public int enqueueMarker() {
        TornadoInternalError.unimplemented();
        return -1;
    }

    @Override
    public int enqueueMarker(int[] events) {
        TornadoInternalError.unimplemented();
        return -1;
    }

    @Override
    public List<Integer> ensurePresent(Object object, TornadoDeviceObjectState objectState, int[] events, long size, long offset) {
        TornadoInternalError.unimplemented();
        return null;
    }

    @Override
    public void flush() {
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
        return TornadoSchedulingStrategy.PER_BLOCK;
    }

    @Override
    public void reset() {
        TornadoInternalError.unimplemented();
    }

    @Override
    public List<Integer> streamIn(Object object, long batchSize, long hostOffset, TornadoDeviceObjectState objectState, int[] events) {
        TornadoInternalError.unimplemented();
        return null;
    }

    @Override
    public int streamOutBlocking(Object object, long hostOffset, TornadoDeviceObjectState objectState, int[] list) {
        TornadoInternalError.unimplemented();
        return -1;
    }

    @Override
    public String toString() {
        return "Host JVM";
    }

    @Override
    public void ensureLoaded() {
    }

    @Override
    public KernelArgs createCallWrapper(int numArgs) {
        return null;
    }

    @Override
    public ObjectBuffer createOrReuseAtomicsBuffer(int[] arr) {
        return null;
    }

    @Override
    public TornadoInstalledCode installCode(SchedulableTask task) {
        return null;
    }

    @Override
    public int allocate(Object object, long batchSize, TornadoDeviceObjectState state) {
        return -1;
    }

    @Override
    public int allocateObjects(Object[] objects, long batchSize, TornadoDeviceObjectState[] states) {
        return -1;
    }

    @Override
    public int deallocate(TornadoDeviceObjectState state) {
        return 0;
    }

    @Override
    public int streamOut(Object object, long hostOffset, TornadoDeviceObjectState objectState, int[] list) {
        return -1;
    }

    @Override
    public int enqueueBarrier() {
        return -1;
    }

    @Override
    public void sync() {

    }

    @Override
    public Event resolveEvent(int event) {
        return new EmptyEvent();
    }

    @Override
    public void flushEvents() {

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
    public boolean isFullJITMode(SchedulableTask task) {
        return false;
    }

    @Override
    public TornadoInstalledCode getCodeFromCache(SchedulableTask task) {
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
    public int[] updateAtomicRegionAndObjectState(SchedulableTask task, int[] array, int paramIndex, Object value, DeviceObjectState objectState) {
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
    public void setAtomicRegion(ObjectBuffer bufferAtomics) {

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
    public int getDriverIndex() {
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

}
