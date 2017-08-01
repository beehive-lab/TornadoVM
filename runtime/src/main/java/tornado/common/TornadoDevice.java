/*
 * Copyright 2012 James Clarkson.
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
 */
package tornado.common;

import tornado.api.Event;
import tornado.api.enums.TornadoSchedulingStrategy;

public interface TornadoDevice {

    public TornadoSchedulingStrategy getPreferedSchedule();

    public boolean isDistibutedMemory();

    public void ensureLoaded();

    public CallStack createStack(int numArgs);

    public int ensureAllocated(Object object, DeviceObjectState state);

    public int ensurePresent(Object object, DeviceObjectState objectState);

    public int ensurePresent(Object object, DeviceObjectState objectState, int[] events);

    public int streamIn(Object object, DeviceObjectState objectState);

    public int streamIn(Object object, DeviceObjectState objectState, int[] events);

    public int streamOut(Object object, DeviceObjectState objectState);

    public int streamOut(Object object, DeviceObjectState objectState,
            int[] list);

    public void streamOutBlocking(Object object, DeviceObjectState objectState);

    public void streamOutBlocking(Object object, DeviceObjectState objectState,
            int[] list);

    public TornadoInstalledCode installCode(SchedulableTask task);

    public Event resolveEvent(int event);

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

    public TornadoMemoryProvider getMemoryProvider();

    public void reset();

    public void dumpEvents();

}
