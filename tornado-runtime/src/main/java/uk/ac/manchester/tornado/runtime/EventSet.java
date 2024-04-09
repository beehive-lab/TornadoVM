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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * , Juan Fumero
 *
 */
package uk.ac.manchester.tornado.runtime;

import java.util.BitSet;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.TornadoEvents;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;

public class EventSet implements TornadoEvents {

    private final TornadoXPUDevice device;
    private final BitSet profiles;
    private int index;
    private Event event;

    private long executionPlanId;

    public EventSet(TornadoXPUDevice device, BitSet profiles, long executionPlanId) {
        this.device = device;
        this.profiles = profiles;
        this.executionPlanId = executionPlanId;
        index = profiles.nextSetBit(0);
        event = device.resolveEvent(executionPlanId, index);
    }

    public int cardinality() {
        return profiles.cardinality();
    }

    public void reset() {
        index = profiles.nextSetBit(0);
    }

    public boolean hasNext() {
        return index != -1;
    }

    public Event next() {
        if (index == -1) {
            return null;
        }
        event = device.resolveEvent(executionPlanId, index);
        index = profiles.nextSetBit(index);
        return event;
    }

    public TornadoXPUDevice getDevice() {
        return device;
    }

    public BitSet getProfiles() {
        return profiles;
    }
}
