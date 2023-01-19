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
package uk.ac.manchester.tornado.runtime.common;

import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.memory.ObjectBuffer;

// FIXME <ADD JAVA DOC>
public interface TornadoAcceleratorDevice extends TornadoDevice {

    TornadoSchedulingStrategy getPreferredSchedule();

    KernelArgs createCallWrapper(int numArgs);

    ObjectBuffer createOrReuseAtomicsBuffer(int[] arr);

    TornadoInstalledCode installCode(SchedulableTask task);

    boolean isFullJITMode(SchedulableTask task);

    TornadoInstalledCode getCodeFromCache(SchedulableTask task);

    int[] checkAtomicsForTask(SchedulableTask task);

    int[] checkAtomicsForTask(SchedulableTask task, int[] array, int paramIndex, Object value);

    int[] updateAtomicRegionAndObjectState(SchedulableTask task, int[] array, int paramIndex, Object value, DeviceObjectState objectState);

    int getAtomicsGlobalIndexForTask(SchedulableTask task, int paramIndex);

    boolean checkAtomicsParametersForTask(SchedulableTask task);

    /**
     * In CUDA the context is not attached to the whole process, but to individual
     * threads Therefore, in the case of new threads executing a task schedule, we
     * must make sure that the context is set for that thread.
     */
    void enableThreadSharing();

    void setAtomicRegion(ObjectBuffer bufferAtomics);
}
