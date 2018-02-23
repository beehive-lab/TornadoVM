/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package tornado.runtime;

import java.util.BitSet;
import tornado.api.Event;
import tornado.common.TornadoDevice;

/**
 *
 * @author James Clarkson
 */
public class EventSet {

    private final TornadoDevice device;
    private final BitSet profiles;
    private int index;
    private Event event;

    public EventSet(TornadoDevice device, BitSet profiles) {
        this.device = device;
        this.profiles = profiles;
        index = profiles.nextSetBit(0);
        event = device.resolveEvent(index);
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
        event = device.resolveEvent(index);
        index = profiles.nextSetBit(index);
        return event;
    }

    public TornadoDevice getDevice() {
        return device;
    }

    public BitSet getProfiles() {
        return profiles;
    }
}
