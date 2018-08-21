package uk.ac.manchester.tornado.runtime.api;

import java.util.BitSet;

import uk.ac.manchester.tornado.api.annotations.Event;
import uk.ac.manchester.tornado.api.common.GenericDevice;

public interface TornadoEvents {

    public int cardinality();

    public void reset();

    public boolean hasNext();

    public Event next();
    
    public BitSet getProfiles();
    
    public GenericDevice getDevice();

}
