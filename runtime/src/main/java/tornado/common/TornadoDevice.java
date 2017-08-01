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

    public enum CacheMode {
        CACHABLE, NON_CACHEABLE;
    }

    public enum BlockingMode {
        BLOCKING, NON_BLOCKING;
    }

    public enum SharingMode {
        EXCLUSIVE, SHARED;
    }

    public TornadoSchedulingStrategy getPreferedSchedule();

    public void ensureLoaded();

    public DeviceFrame createStack(int numArgs);

    public int read(BlockingMode blocking, SharingMode sharing, CacheMode caching, Object object, int[] waitList);

    public int write(BlockingMode blocking, CacheMode caching, Object object, int[] waitList);

    public long toAbsoluteDeviceAddress(Object object);

    public long toRelativeDeviceAddress(Object object);

    public int flushCache();

    public TornadoInstalledCode installCode(SchedulableTask task);

    public Event resolveEvent(int event);

    public void markEvent();

    public void flushEvents();

    public int enqueueBarrier();

    public int enqueueBarrier(int[] events);

    public void sync();

    public void flush();

    public String getDeviceName();

    public String getDescription();

    public TornadoMemoryProvider getMemoryProvider();

    public void reset();

    public void dumpEvents();

}
