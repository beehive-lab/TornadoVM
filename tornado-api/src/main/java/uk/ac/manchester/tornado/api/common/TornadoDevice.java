/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
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

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.TornadoTargetDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.mm.TornadoDeviceObjectState;
import uk.ac.manchester.tornado.api.mm.TornadoMemoryProvider;

public interface TornadoDevice {

    void ensureLoaded();

    void markEvent();

    void flushEvents();

    int enqueueBarrier();

    int enqueueBarrier(int[] events);

    int enqueueMarker();

    int enqueueMarker(int[] events);

    void sync();

    void flush();

    void reset();

    void dumpEvents();

    void dumpMemory(String file);

    int ensureAllocated(Object object, long batchSize, TornadoDeviceObjectState state);

    int ensurePresent(Object object, TornadoDeviceObjectState objectState, int[] events, long size, long hostOffset);

    int streamIn(Object object, long batchSize, long hostOffset, TornadoDeviceObjectState objectState, int[] events);

    int streamOut(Object object, TornadoDeviceObjectState objectState, int[] list);

    void streamOutBlocking(Object object, TornadoDeviceObjectState objectState, int[] list);

    boolean isDistibutedMemory();

    Event resolveEvent(int event);

    // Getters

    String getDeviceName();

    String getDescription();

    String getPlatformName();

    TornadoDeviceContext getDeviceContext();

    TornadoTargetDevice getDevice();

    TornadoMemoryProvider getMemoryProvider();

    TornadoDeviceType getDeviceType();

    long getMaxAllocMemory();

    long getMaxGlobalMemory();

}