/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
