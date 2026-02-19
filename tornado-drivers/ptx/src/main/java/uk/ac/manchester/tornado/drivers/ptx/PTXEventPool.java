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
 *
 */
package uk.ac.manchester.tornado.drivers.ptx;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.CIRCULAR_EVENTS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.drivers.common.utils.EventDescriptor;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class PTXEventPool {

    private final PTXEvent[] events;
    private final BitSet retain;
    private int eventIndex;
    private int eventPoolSize;

    protected PTXEventPool(int poolSize) {
        this.eventPoolSize = poolSize;
        this.retain = new BitSet(poolSize);
        this.retain.clear();
        this.events = new PTXEvent[poolSize];
        this.eventIndex = 0;
    }

    protected int registerEvent(byte[][] eventWrapper, EventDescriptor descriptorId) {
        return registerEvent(eventWrapper, descriptorId, PTXStreamType.DEFAULT);
    }

    protected int registerEvent(byte[][] eventWrapper, EventDescriptor descriptorId, PTXStreamType streamType) {
        if (retain.get(eventIndex)) {
            findNextEventSlot();
        }
        final int currentEvent = eventIndex;
        guarantee(!retain.get(currentEvent), "overwriting retained event");

        if (eventWrapper == null) {
            TornadoLogger logger = new TornadoLogger();
            logger.fatal("invalid event: description=%s\n", descriptorId.getNameDescription());
            logger.fatal("terminating application as system integrity has been compromised.");
            throw new TornadoBailoutRuntimeException("[ERROR] NULL event received from the CUDA driver !");
        }

        if (events[currentEvent] != null && !retain.get(currentEvent)) {
            events[currentEvent].waitForEvents(0);
            events[currentEvent].destroy();
            events[currentEvent] = null;
        }
        events[currentEvent] = new PTXEvent(eventWrapper, descriptorId, streamType);

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

    protected void reset() {
        for (PTXEvent event : events) {
            if (event != null) {
                event.destroy();
            }
        }
        Arrays.fill(events, null);
        eventIndex = 0;
    }

    private void releaseEvent(int localEventID) {
        retain.clear(localEventID);
    }

    private void retainEvent(int localEventID) {
        retain.set(localEventID);
    }

    protected PTXEvent getEvent(int localEventID) {
        return events[localEventID];
    }

    public List<PTXEvent> getEvents() {
        List<PTXEvent> result = new ArrayList<>();
        for (int i = 0; i < eventIndex; i++) {
            if (events[i] == null) {
                continue;
            }
            result.add(events[i]);
        }
        return result;
    }

}
