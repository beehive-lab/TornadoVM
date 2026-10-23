/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
