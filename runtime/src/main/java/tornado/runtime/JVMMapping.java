package tornado.runtime;

import tornado.api.Event;
import tornado.api.enums.TornadoSchedulingStrategy;
import tornado.common.CallStack;
import tornado.common.DeviceMapping;
import tornado.common.DeviceObjectState;
import tornado.common.SchedulableTask;
import tornado.common.TornadoInstalledCode;

public class JVMMapping implements DeviceMapping {

    @Override
    public TornadoSchedulingStrategy getPreferedSchedule() {
        return TornadoSchedulingStrategy.PER_BLOCK;
    }

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
    
    public void markEvent(){
       
    }
    
    @Override
    public void flushEvents(){
        
    }
    
    @Override
    public String getDeviceName(){
        return "jvm";
    }

}
