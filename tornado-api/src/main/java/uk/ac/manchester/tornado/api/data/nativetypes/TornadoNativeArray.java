package uk.ac.manchester.tornado.api.data.nativetypes;

import java.lang.foreign.MemorySegment;

public abstract class TornadoNativeArray {
    public static final long ARRAY_HEADER = Long.parseLong(System.getProperty("tornado.panama.objectHeader", "24"));

    public abstract int getSize();

    public abstract MemorySegment getSegment();

    public abstract long getNumBytesOfSegment();

}
