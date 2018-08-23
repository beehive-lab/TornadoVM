package uk.ac.manchester.tornado.api.common;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.TornadoDeviceObjectState;
import uk.ac.manchester.tornado.api.TornadoTargetDevice;

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

    public int ensureAllocated(Object object, TornadoDeviceObjectState state);

    public int ensurePresent(Object object, TornadoDeviceObjectState objectState);

    public int ensurePresent(Object object, TornadoDeviceObjectState objectState, int[] events);

    public int streamIn(Object object, TornadoDeviceObjectState objectState);

    public int streamIn(Object object, TornadoDeviceObjectState objectState, int[] events);

    public int streamOut(Object object, TornadoDeviceObjectState objectState);

    public int streamOut(Object object, TornadoDeviceObjectState objectState, int[] list);

    public void streamOutBlocking(Object object, TornadoDeviceObjectState objectState);

    public void streamOutBlocking(Object object, TornadoDeviceObjectState objectState, int[] list);

    public Event resolveEvent(int event);

    public TornadoDeviceContext getDeviceContext();

    public TornadoTargetDevice getDevice();

}