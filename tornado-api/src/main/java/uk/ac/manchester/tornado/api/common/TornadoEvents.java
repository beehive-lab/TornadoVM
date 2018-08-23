package uk.ac.manchester.tornado.api.common;

import java.util.BitSet;

public interface TornadoEvents {

    public int cardinality();

    public void reset();

    public boolean hasNext();

    public Event next();
    
    public BitSet getProfiles();
    
    public TornadoDevice getDevice();

}
