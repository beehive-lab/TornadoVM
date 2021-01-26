/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
 */
package uk.ac.manchester.tornado.drivers.opencl;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.TornadoExecutionHandler;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLInstalledCode;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResult;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLMemoryManager;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public interface OCLDeviceContextInterface extends TornadoDeviceContext {
    OCLTargetDevice getDevice();

    OCLCodeCache getCodeCache();

    boolean isCached(String id, String entryPoint);

    OCLInstalledCode getInstalledCode(String id, String entryPoint);

    OCLInstalledCode installCode(String id, String entryPoint, byte[] code, boolean shouldCompile);

    OCLInstalledCode installCode(OCLCompilationResult result);

    OCLInstalledCode installCode(TaskMetaData meta, String id, String entryPoint, byte[] code);

    boolean isKernelAvailable();

    void reset();

    TornadoAcceleratorDevice asMapping();

    void dumpEvents();

    void flush();

    OCLMemoryManager getMemoryManager();

    void sync();

    void sync(TornadoExecutionHandler handler);

    int enqueueBarrier();

    int enqueueBarrier(int[] events);

    int enqueueMarker();

    int enqueueMarker(int[] events);

    Event resolveEvent(int event);

    void flushEvents();

    OCLExecutionEnvironment getPlatformContext();

    long getDeviceId();

    OCLProgram createProgramWithSource(byte[] source, long[] lengths);

    OCLProgram createProgramWithBinary(byte[] binary, long[] lengths);
}
