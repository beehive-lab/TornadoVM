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
 */

package uk.ac.manchester.tornado.drivers.opencl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.drivers.opencl.OCLEvent.EVENT_DESCRIPTIONS;
import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLCommandQueueProperties.CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE;
import static uk.ac.manchester.tornado.runtime.common.Tornado.EVENT_WINDOW;
import static uk.ac.manchester.tornado.runtime.common.Tornado.MAX_WAIT_EVENTS;
import static uk.ac.manchester.tornado.runtime.common.Tornado.debug;
import static uk.ac.manchester.tornado.runtime.common.Tornado.fatal;
import static uk.ac.manchester.tornado.runtime.common.Tornado.getProperty;

public class OCLEventsWrapper {

    private static final boolean CIRCULAR_EVENTS = Boolean.parseBoolean(getProperty("tornado.opencl.circularevents", "True"));

    protected final long[] events;
    protected final int[] descriptors;
    protected final long[] tags;
    protected final BitSet retain;
    protected final OCLCommandQueue[] eventQueues;
    protected int eventIndex;

    private final OCLEvent internalEvent;
    protected final long[] waitEventsBuffer;

    protected OCLEventsWrapper() {
        this.retain = new BitSet(EVENT_WINDOW);
        this.retain.clear();
        this.events = new long[EVENT_WINDOW];
        this.descriptors = new int[EVENT_WINDOW];
        this.tags = new long[EVENT_WINDOW];
        this.eventQueues = new OCLCommandQueue[EVENT_WINDOW];
        this.eventIndex = 0;
        this.waitEventsBuffer = new long[MAX_WAIT_EVENTS];
        this.internalEvent = new OCLEvent();
    }

    protected int registerEvent(long oclEventId, int descriptorId, long tag, OCLCommandQueue queue) {
        if (retain.get(eventIndex)) {
            findNextEventSlot();
        }
        final int currentEvent = eventIndex;
        guarantee(!retain.get(currentEvent), "overwriting retained event");

        /*
         * OpenCL can produce an out of resources error which results in an invalid event
         * (-1). If this happens, then we log a fatal exception and gracefully exit.
         */
        if (oclEventId == -1) {
            fatal("invalid event: event=0x%x, description=%s, tag=0x%x\n", oclEventId, EVENT_DESCRIPTIONS[descriptorId], tag);
            fatal("terminating application as system integrity has been compromised.");
            System.exit(-1);
        }

        if (events[currentEvent] > 0 && !retain.get(currentEvent)) {
            internalEvent.setEventId(currentEvent, events[currentEvent]);
            internalEvent.release();
        }
        events[currentEvent] = oclEventId;
        descriptors[currentEvent] = descriptorId;
        tags[currentEvent] = tag;
        eventQueues[currentEvent] = queue;

        findNextEventSlot();
        return currentEvent;
    }

    private void findNextEventSlot() {
        eventIndex = retain.nextClearBit(eventIndex + 1);

        if (CIRCULAR_EVENTS && (eventIndex >= events.length)) {
            eventIndex = 0;
        }

        guarantee(eventIndex != -1, "event window is full (retained=%d, capacity=%d)", retain.cardinality(), EVENT_WINDOW);
    }

    protected boolean serialiseEvents(int[] dependencies, OCLCommandQueue queue) {
        boolean outOfOrderQueue = (queue.getProperties() & CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE) == 1;
        if (dependencies == null || dependencies.length == 0 || !outOfOrderQueue) {
            return false;
        }

        Arrays.fill(waitEventsBuffer, 0);

        int index = 0;
        for (int i = 0; i < dependencies.length; i++) {
            final int value = dependencies[i];
            if (value != -1) {
                index++;
                waitEventsBuffer[index] = events[value];
                debug("[%d] 0x%x - %s 0x%x\n", index, events[value], EVENT_DESCRIPTIONS[descriptors[value]], tags[value]);

            }
        }
        waitEventsBuffer[0] = index;
        return (index > 0);
    }

    public List<OCLEvent> getEvents(OCLDeviceContext deviceContext) {
        List<OCLEvent> result = new ArrayList<>();
        for (int i = 0; i < eventIndex; i++) {
            final long eventId = events[i];
            if (eventId <= 0) {
                continue;
            }
            result.add(new OCLEvent(deviceContext, eventQueues[i], i, eventId));
        }
        return result;
    }
}
