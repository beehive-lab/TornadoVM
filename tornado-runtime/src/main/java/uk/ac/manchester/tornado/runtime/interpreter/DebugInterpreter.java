/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2025, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.interpreter;

import java.util.List;

import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;

class DebugInterpreter {

    private static void appendLogBuilder(String logMessage, StringBuilder logBuilder) {
        logBuilder.append(logMessage).append("\n");
    }

    static void logAllocObject(Object object, TornadoXPUDevice interpreterDevice, long size, long sizeBatch, StringBuilder logBuilder) {
        String verbose = String.format("bc: %s%s on %s, size=%d, batchSize=%d", //
                InterpreterUtilities.debugHighLightBC("ALLOC"), //
                object, //
                InterpreterUtilities.debugDeviceBC(interpreterDevice), //
                size, //
                sizeBatch); //
        appendLogBuilder(verbose, logBuilder);
    }

    static void logDeallocObject(Object object, TornadoXPUDevice interpreterDevice, StringBuilder logBuilder, boolean materializeDealloc) {
        String verbose = String.format("bc: %s[0x%x] %s [Status:%s] on %s", //
                materializeDealloc ? InterpreterUtilities.debugHighLightBC("DEALLOC") : InterpreterUtilities.debugHighLightNonExecBC("DEALLOC"), //
                object.hashCode(), //
                object, //
                InterpreterUtilities.debugHighLightNonExecBC(materializeDealloc ? "Freed" : "Persisted"), //
                InterpreterUtilities.debugDeviceBC(interpreterDevice)); //
        appendLogBuilder(verbose, logBuilder);
    }

    static void logOnDeviceObject(Object object, TornadoXPUDevice interpreterDevice, StringBuilder logBuilder) {
        String verbose = String.format("bc: %s[0x%x] %s on %s", //
                InterpreterUtilities.debugHighLightBC("ON_DEVICE"), //
                object.hashCode(), //
                object, //
                InterpreterUtilities.debugDeviceBC(interpreterDevice));
        appendLogBuilder(verbose, logBuilder);
    }

    static void logPersistedObject(Object object, TornadoXPUDevice interpreterDevice, StringBuilder logBuilder) {
        String verbose = String.format("bc: %s[0x%x] %s on %s", //
                InterpreterUtilities.debugHighLightBC("PERSIST"), //
                object.hashCode(), //
                object, //
                InterpreterUtilities.debugDeviceBC(interpreterDevice));
        appendLogBuilder(verbose, logBuilder);
    }

    static void logTransferToDeviceOnce(List<Integer> allEvents, Object object, TornadoXPUDevice deviceForInterpreter, //
            long sizeObject, long sizeBatch, long offset, final int eventList, StringBuilder logBuilder) {

        boolean executed = allEvents != null;

        String transferStatus = executed ? "Transferred" : "Present";

        String coloredText = executed //
                ? InterpreterUtilities.debugHighLightBC("TRANSFER_HOST_TO_DEVICE_ONCE") //
                : InterpreterUtilities.debugHighLightNonExecBC("TRANSFER_HOST_TO_DEVICE_ONCE"); //

        String verbose = String.format("bc: %s [Object Hash Code=0x%x] %s on %s, size=%d, batchSize=%d, offset=%d [event list=%d], [Status:%s] ", //
                coloredText, //
                object.hashCode(), //
                object, //
                InterpreterUtilities.debugDeviceBC(deviceForInterpreter), //
                sizeObject, // 
                sizeBatch, //
                offset, //
                eventList, //
                InterpreterUtilities.debugHighLightNonExecBC(transferStatus));

        appendLogBuilder(verbose, logBuilder);
    }

    static void logTransferToDeviceAlways(Object object, TornadoXPUDevice deviceForInterpreter, long sizeObject, long sizeBatch, long offset, //
            final int eventList, StringBuilder logBuilder) {
        String verbose = String.format("bc: %s [0x%x] %s on %s, size=%d, batchSize=%d, offset=%d [event list=%d]", //
                InterpreterUtilities.debugHighLightBC("TRANSFER_HOST_TO_DEVICE_ALWAYS"), //
                object.hashCode(), //
                object, //
                InterpreterUtilities.debugDeviceBC(deviceForInterpreter), //
                sizeObject, //
                sizeBatch, //
                offset, //
                eventList); //
        appendLogBuilder(verbose, logBuilder);
    }

    static void logTransferToHostAlways(Object object, TornadoXPUDevice interpreterDevice, long sizeObject, long sizeBatch, //
            long offset, final int eventList, StringBuilder logBuilder) {
        String verbose = String.format("bc: " //
                + InterpreterUtilities.debugHighLightBC("TRANSFER_DEVICE_TO_HOST_ALWAYS") //
                + "[0x%x] %s on %s, size=%d, batchSize=%d, offset=%d [event list=%d]", //
                object.hashCode(), //
                object, //
                InterpreterUtilities.debugDeviceBC(interpreterDevice), //
                sizeObject, // 
                sizeBatch, //
                offset, //
                eventList);
        appendLogBuilder(verbose, logBuilder);
    }

    static void logTransferToHostAlwaysBlocking(Object object, TornadoXPUDevice interpreterDevice, StringBuilder logBuilder, //
            long sizeObject, long sizeBatch, long offset, int eventId) {
        String verbose = String.format("bc: " //
                + InterpreterUtilities.debugHighLightBC("TRANSFER_DEVICE_TO_HOST_ALWAYS_BLOCKING") //
                + " [0x%x] %s on %s, size=%d, sizeBatch=%d, offset=%d [event list=%d]", //
                object.hashCode(), //
                object, //
                InterpreterUtilities.debugDeviceBC(interpreterDevice), //
                sizeObject, // 
                sizeBatch, //
                offset, //
                eventId);
        appendLogBuilder(verbose, logBuilder);
    }

    public static void logLaunchTask(SchedulableTask task, TornadoXPUDevice interpreterDevice, long numBatchThreads, long offset, int eventId, StringBuilder logBuilder) {
        String verbose = String.format("bc: " + InterpreterUtilities.debugHighLightBC("LAUNCH") //
                + " %s on %s, numThreadBatch=%d, offset=%d [event list=%d]", //
                task.getFullName(), //
                interpreterDevice, //
                numBatchThreads, //
                offset, //
                eventId);
        appendLogBuilder(verbose, logBuilder);
    }

    public static void logStreamInAtomic(Object bufferAtomics, TornadoXPUDevice interpreterDevice, int eventId, StringBuilder logBuilder) {
        String verbose = String.format("bc: " //
                + InterpreterUtilities.debugHighLightBC("STREAM_IN") //
                + "  ATOMIC [0x%x] %s on %s, batchSize=%d, offset=%d [event list=%d]", //
                bufferAtomics.hashCode(), //
                bufferAtomics, //
                interpreterDevice, //
                0, //
                0, //
                eventId);
        appendLogBuilder(verbose, logBuilder);
    }

    public static void logAddDependency(int lastEvent, int eventId, StringBuilder logBuilder) {
        String verbose = String.format("bc: " //
                + InterpreterUtilities.debugHighLightBC("ADD_DEPENDENCY") //
                + " %s to event list %d",  //
                lastEvent,  //
                eventId); //
        appendLogBuilder(verbose, logBuilder);
    }

    public static void logBarrier(int enventId, StringBuilder logBuilder) {
        logBuilder.append(String.format("bc: " //
                + InterpreterUtilities.debugHighLightBC("BARRIER") //
                + " event-list %d%n",  //
                enventId));
    }
}
