/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020-2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
 */

package uk.ac.manchester.tornado.drivers.cuda;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.drivers.cuda.enums.CUDACommandQueueProperties.CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.CIRCULAR_EVENTS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import uk.ac.manchester.tornado.drivers.common.utils.EventDescriptor;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

/**
 * Class which holds mapping between CUDADriver events and TornadoVM runtime events,
 * and handles event registration and serialization. It also keeps metadata such
 * as events description and tag.
 *
 * <p>
 * Each device holds an event pool. Only one instance of the pool per device.
 * </p>
 *
 * <p>
 * Relationship: one instance of the {@link CUDAEventPool} per {@link CUDADeviceContext}.
 * </p>
 */
public class CUDAEventPool {

    public final long[] waitEventsBuffer;
    private final long[] events;
    private final EventDescriptor[] descriptors;
    private final BitSet retain;
    private final CUDACommandQueue[] eventQueues;
    private final CUDAEvent internalEvent;
    private int eventIndex;
    private int eventPoolSize;
    private final TornadoLogger logger;

    public CUDAEventPool(int poolSize) {
        this.eventPoolSize = poolSize;
        this.retain = new BitSet(eventPoolSize);
        this.retain.clear();
        this.events = new long[eventPoolSize];
        this.descriptors = new EventDescriptor[eventPoolSize];
        this.eventQueues = new CUDACommandQueue[eventPoolSize];
        this.eventIndex = 0;
        this.waitEventsBuffer = new long[TornadoOptions.MAX_WAIT_EVENTS];
        this.internalEvent = new CUDAEvent();
        this.logger = new TornadoLogger(this.getClass());
    }

    public int registerEvent(long oclEventId, EventDescriptor descriptorId, CUDACommandQueue queue) {
        if (retain.get(eventIndex)) {
            findNextEventSlot();
        }
        final int currentEvent = eventIndex;
        guarantee(!retain.get(currentEvent), "overwriting retained event");

        /*
         * CUDADriver can produce an out of resources error which results in an invalid
         * event (-1). If this happens, then we log a fatal exception and gracefully
         * exit.
         */
        if (oclEventId == -1) {
            logger.fatal("invalid event: event=0x%x, description=%s\n", oclEventId, descriptorId.getNameDescription());
            logger.fatal("terminating application as system integrity has been compromised.");
            System.exit(-1);
        }

        if (events[currentEvent] > 0 && !retain.get(currentEvent)) {
            internalEvent.setEventId(currentEvent, events[currentEvent]);
            releaseEvent(currentEvent);
            internalEvent.release();
        }
        events[currentEvent] = oclEventId;
        descriptors[currentEvent] = descriptorId;
        eventQueues[currentEvent] = queue;

        findNextEventSlot();
        return currentEvent;
    }

    private void findNextEventSlot() {
        eventIndex = retain.nextClearBit(eventIndex + 1);

        if (CIRCULAR_EVENTS && (eventIndex >= events.length)) {
            eventIndex = 0;
        }

        guarantee(eventIndex != -1, "event window is full (retained=%d, capacity=%d)", retain.cardinality(), eventPoolSize);
    }

    public boolean serialiseEvents(int[] dependencies, CUDACommandQueue queue) {
        return serialiseEvents(dependencies, queue, false);
    }

    /**
     * Serialises the dependency wait list into {@link #waitEventsBuffer}. On an in-order queue the
     * list is normally redundant (queue order implies it) and is skipped; {@code forceSerialisation}
     * overrides that for plans running with intra-plan concurrency, where dependencies may have been
     * recorded on a DIFFERENT queue and must be honoured with cross-stream event waits.
     */
    public boolean serialiseEvents(int[] dependencies, CUDACommandQueue queue, boolean forceSerialisation) {
        boolean outOfOrderQueue = (queue.getProperties() & CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE) == 1;
        if (dependencies == null || dependencies.length == 0 || (!outOfOrderQueue && !forceSerialisation)) {
            return false;
        }

        int index = 0;
        for (final int value : dependencies) {
            if (value == -1) {
                continue;
            }
            // An in-order queue already orders against its OWN prior work: a
            // cuStreamWaitEvent on an event recorded on the same queue is a no-op
            // that still costs an API call per dependency, so skip those.
            if (!outOfOrderQueue && eventQueues[value] == queue) {
                continue;
            }
            index++;
            waitEventsBuffer[index] = events[value];
            logger.debug("[%d] 0x%x - %s\n", index, events[value], descriptors[value].getNameDescription());
        }
        waitEventsBuffer[0] = index;
        return (index > 0);
    }

    public List<CUDAEvent> getEvents() {
        List<CUDAEvent> result = new ArrayList<>();
        for (int i = 0; i < eventIndex; i++) {
            final long eventId = events[i];
            if (eventId <= 0) {
                continue;
            }
            result.add(new CUDAEvent(getDescriptor(i).getNameDescription(), eventQueues[i], i, eventId));
        }
        return result;
    }

    public void reset() {
        for (int index = 0; index < events.length; index++) {
            if (events[index] > 0) {
                internalEvent.setEventId(index, events[index]);
                releaseEvent(index);
                internalEvent.release();
            }
        }
        Arrays.fill(events, 0);
        eventIndex = 0;
    }

    protected void retainEvent(int localEventID) {
        retain.set(localEventID);
    }

    protected void releaseEvent(int localEventID) {
        retain.clear(localEventID);
    }

    public long getCUDAEvent(int localEventID) {
        return events[localEventID];
    }

    public EventDescriptor getDescriptor(int localEventID) {
        return descriptors[localEventID];
    }

}
