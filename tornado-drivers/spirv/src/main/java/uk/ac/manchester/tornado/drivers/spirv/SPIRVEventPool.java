/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.spirv;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.CIRCULAR_EVENTS;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.EVENT_WINDOW;

import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedList;

import uk.ac.manchester.tornado.drivers.common.utils.EventDescriptor;
import uk.ac.manchester.tornado.drivers.spirv.timestamps.TimeStamp;

/**
 * This class controls a pools of low-level events for the device. There is a
 * pool of events per device, and it handles the actual events that will
 * communicate with the correct driver (e.g., LevelZero event, OCL events).
 */
public class SPIRVEventPool {

    private final int poolSize;
    private final BitSet retain;
    private final HashMap<Integer, LinkedList<TimeStamp>> events;
    private final EventDescriptor[] descriptors;
    private int eventPositionIndex;

    protected SPIRVEventPool(int poolSize) {
        this.poolSize = poolSize;
        this.events = new HashMap<>();
        this.descriptors = new EventDescriptor[poolSize];
        this.retain = new BitSet(poolSize);
        this.eventPositionIndex = 0;
    }

    private void findNextEventSlot() {
        eventPositionIndex = retain.nextClearBit(eventPositionIndex + 1);
        if (CIRCULAR_EVENTS && (eventPositionIndex >= poolSize)) {
            eventPositionIndex = 0;
        }
        guarantee(eventPositionIndex != -1, "event window is full (retained=%d, capacity=%d)", retain.cardinality(), EVENT_WINDOW);
    }

    protected int registerEvent(EventDescriptor eventDescriptor, ProfilerTransfer profilerTransfer) {
        if (retain.get(eventPositionIndex)) {
            findNextEventSlot();
        }
        final int currentEventPosition = eventPositionIndex;
        guarantee(!retain.get(currentEventPosition), "overwriting retained event");

        LinkedList<TimeStamp> listTimeStamps = new LinkedList<>();

        if (profilerTransfer != null) {
            listTimeStamps.add(profilerTransfer.getStart());
            listTimeStamps.add(profilerTransfer.getStop());
        }

        events.put(currentEventPosition, listTimeStamps);
        descriptors[currentEventPosition] = eventDescriptor;
        findNextEventSlot();
        return currentEventPosition;
    }

    public LinkedList<TimeStamp> getTimers(int eventId) {
        return events.get(eventId);
    }

    public EventDescriptor getDescriptor(int eventId) {
        return descriptors[eventId];
    }
}
