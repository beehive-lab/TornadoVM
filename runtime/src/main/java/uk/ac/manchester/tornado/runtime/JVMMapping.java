/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
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

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.TornadoTargetDevice;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.mm.TornadoDeviceObjectState;
import uk.ac.manchester.tornado.api.mm.TornadoMemoryProvider;
import uk.ac.manchester.tornado.runtime.common.CallStack;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.common.TornadoSchedulingStrategy;

public class JVMMapping implements TornadoAcceleratorDevice {

    @Override
    public void dumpEvents() {
        TornadoInternalError.unimplemented();
    }

    @Override
    public void dumpMemory(String file) {
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
    public int ensurePresent(Object object, TornadoDeviceObjectState objectState, int[] events) {
        TornadoInternalError.unimplemented();
        return -1;
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
    public TornadoSchedulingStrategy getPreferedSchedule() {
        return TornadoSchedulingStrategy.PER_BLOCK;
    }

    @Override
    public void reset() {
        TornadoInternalError.unimplemented();
    }

    @Override
    public int streamIn(Object object, TornadoDeviceObjectState objectState, int[] events) {
        TornadoInternalError.unimplemented();
        return -1;
    }

    @Override
    public int streamOut(Object object, TornadoDeviceObjectState objectState) {
        TornadoInternalError.unimplemented();
        return -1;
    }

    @Override
    public void streamOutBlocking(Object object, TornadoDeviceObjectState objectState) {
        TornadoInternalError.unimplemented();
    }

    @Override
    public void streamOutBlocking(Object object, TornadoDeviceObjectState objectState, int[] list) {
        TornadoInternalError.unimplemented();
    }

    @Override
    public String toString() {
        return "Host JVM";
    }

    @Override
    public boolean isDistibutedMemory() {
        return false;
    }

    @Override
    public void ensureLoaded() {

    }

    @Override
    public CallStack createStack(int numArgs) {
        return null;
    }

    @Override
    public TornadoInstalledCode installCode(SchedulableTask task) {
        return null;
    }

    @Override
    public int ensureAllocated(Object object, TornadoDeviceObjectState state) {
        // TODO Auto-generated method stub
        return -1;
    }

    @Override
    public int ensurePresent(Object object, TornadoDeviceObjectState objectState) {
        // TODO Auto-generated method stub
        return -1;
    }

    @Override
    public int streamIn(Object object, TornadoDeviceObjectState objectState) {
        // TODO Auto-generated method stub
        return -1;
    }

    @Override
    public int streamOut(Object object, TornadoDeviceObjectState objectState, int[] list) {
        // TODO Auto-generated method stub
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
    public void markEvent() {

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
    public TornadoTargetDevice getDevice() {
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
    public long getMaxAllocMemory() {
        return Runtime.getRuntime().maxMemory();
    }

    @Override
    public long getMaxGlobalMemory() {
        return Runtime.getRuntime().maxMemory();
    }

}
