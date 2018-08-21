package uk.ac.manchester.tornado.api.common;

public interface GenericDevice {

    public boolean isDistibutedMemory();

    public void ensureLoaded();

    public void markEvent();

    public void flushEvents();

    public int enqueueBarrier();

    public int enqueueBarrier(int[] events);

    public int enqueueMarker();

    public int enqueueMarker(int[] events);

    public void sync();

    public void flush();

    public String getDeviceName();

    public String getDescription();

    public void reset();

    public void dumpEvents();

    public void dumpMemory(String file);

    public String getPlatformName();

}