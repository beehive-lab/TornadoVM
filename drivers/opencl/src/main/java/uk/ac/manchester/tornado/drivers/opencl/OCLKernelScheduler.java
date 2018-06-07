/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
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
package uk.ac.manchester.tornado.drivers.opencl;

import uk.ac.manchester.tornado.api.Event;
import uk.ac.manchester.tornado.api.meta.TaskMetaData;

public abstract class OCLKernelScheduler {

    protected final OCLDeviceContext deviceContext;

    protected double min;
    protected double max;
    protected double sum;
    protected double mean;
    protected double std;
    protected double samples;

    public OCLKernelScheduler(final OCLDeviceContext context) {
        deviceContext = context;
    }

    public abstract void calculateGlobalWork(final TaskMetaData meta);

    public abstract void calculateLocalWork(final TaskMetaData meta);

    public int submit(final OCLKernel kernel, final TaskMetaData meta, final int[] waitEvents) {

        if (!meta.isGlobalWorkDefined()) {
            calculateGlobalWork(meta);
        }

        if (!meta.isLocalWorkDefined()) {
            calculateLocalWork(meta);
        }

        if (meta.isDebug() | true) {
            meta.printThreadDims();
        }
        System.out.print("OCLKERNEL SCHEDULER" + "\n");
        final int task;
        if (meta.shouldUseOpenclScheduling()) {
            task = deviceContext.enqueueNDRangeKernel(kernel, meta.getDims(), meta.getGlobalOffset(), meta.getGlobalWork(), null, waitEvents);
            System.out.print("OCLKERNEL SCHEDULER" + 1 + "\n");

        } else {
            task = deviceContext.enqueueNDRangeKernel(kernel, meta.getDims(), meta.getGlobalOffset(), meta.getGlobalWork(), meta.getLocalWork(), waitEvents);
            System.out.print("OCLKERNEL SCHEDULER" + 2 + "\n");

        }

        if (deviceContext.printOCLKernelTime()) {
            Event resolveEvent = deviceContext.resolveEvent(task);
            System.out.println("[OCL Kernel Execution Time] " + resolveEvent.getExecutionTimeInNanoSeconds() + " (ns)");
        }

        return task;
    }

}
