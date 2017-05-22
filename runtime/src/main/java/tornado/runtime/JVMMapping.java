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
package tornado.runtime;

import tornado.api.Event;
import tornado.api.enums.TornadoSchedulingStrategy;
import tornado.common.*;

import static tornado.common.exceptions.TornadoInternalError.unimplemented;

public class JVMMapping implements TornadoDevice {

    @Override
    public void dumpEvents() {
        unimplemented();
    }

    @Override
    public int enqueueBarrier(int[] events) {
        unimplemented();
        return -1;
    }

    @Override
    public int enqueueMarker() {
        unimplemented();
        return -1;
    }

    @Override
    public int enqueueMarker(int[] events) {
        unimplemented();
        return -1;
    }

    @Override
    public void flush() {
        unimplemented();
    }

    @Override
    public String getDescription() {
        return "default JVM";
    }

    @Override
    public TornadoMemoryProvider getMemoryProvider() {
        unimplemented();
        return null;
    }

    @Override
    public TornadoSchedulingStrategy getPreferedSchedule() {
        return TornadoSchedulingStrategy.PER_BLOCK;
    }

    @Override
    public void reset() {
        unimplemented();
    }

    @Override
    public String toString() {
        return "Host JVM";
    }

    @Override
    public boolean isDistibutedMemory() {
        return false;
    }

    @Override
    public void ensureLoaded() {

    }

    @Override
    public CallStack createStack(int numArgs) {

        return null;
    }

    @Override
    public TornadoInstalledCode installCode(SchedulableTask task) {

        return null;
    }

    @Override
    public int ensureAllocated(Object object, DeviceObjectState state) {
        // TODO Auto-generated method stub
        return -1;
    }

    @Override
    public int ensurePresent(Object object, DeviceObjectState objectState) {
        // TODO Auto-generated method stub
        return -1;
    }

    @Override
    public int streamIn(Object object, DeviceObjectState objectState) {
        // TODO Auto-generated method stub
        return -1;
    }

    @Override
    public int streamOut(Object object, DeviceObjectState objectState,
            int[] list) {
        // TODO Auto-generated method stub
        return -1;
    }

    @Override
    public int enqueueBarrier() {
        return -1;
    }

    @Override
    public void sync() {

    }

    @Override
    public Event resolveEvent(int event) {
        return new EmptyEvent();
    }

    @Override
    public void markEvent() {

    }

    @Override
    public void flushEvents() {

    }

    @Override
    public String getDeviceName() {
        return "jvm";
    }

}
