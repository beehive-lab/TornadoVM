package tornado.common;

import java.lang.reflect.Method;
import tornado.api.Event;
import tornado.api.enums.TornadoSchedulingStrategy;
import tornado.meta.Meta;

public interface TornadoDevice {

    public Meta createMeta(Method method);

    public Meta createMeta(int numParameters);

    public TornadoSchedulingStrategy getPreferedSchedule();

    public boolean isDistibutedMemory();

    public void ensureLoaded();

    public CallStack createStack(int numArgs);

    public int ensureAllocated(Object object, DeviceObjectState state);

    public int ensurePresent(Object object, DeviceObjectState objectState);

    public int streamIn(Object object, DeviceObjectState objectState);

    public int streamOut(Object object, DeviceObjectState objectState,
            int[] list);

    public TornadoInstalledCode installCode(SchedulableTask task);

    public Event resolveEvent(int event);

    public void markEvent();

    public void flushEvents();

    public int enqueueBarrier();

    public void sync();

    public String getDeviceName();

    public String getDescription();

    public TornadoMemoryProvider getMemoryProvider();

    public void reset();

    public void dumpEvents();

}
