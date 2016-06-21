package tornado.drivers.opencl.runtime;



import java.lang.ref.ReferenceQueue;
import java.util.List;

import tornado.api.Event;
import tornado.common.DeviceMapping;
import tornado.common.ObjectBuffer;
import tornado.common.exceptions.TornadoOutOfMemoryException;
import tornado.drivers.opencl.OCLDeviceContext;
import tornado.drivers.opencl.mm.OCLDoubleArrayWrapper;
import tornado.drivers.opencl.mm.OCLFloatArrayWrapper;
import tornado.drivers.opencl.mm.OCLIntArrayWrapper;
import tornado.drivers.opencl.mm.OCLLongArrayWrapper;
import tornado.drivers.opencl.mm.OCLObjectWrapper;
import tornado.runtime.ObjectReference;

public class OCLObjectReference<T> extends ObjectReference<OCLDeviceContext, T> {

    
//    public static class ReferenceContext<T> {
//        private final OCLDeviceContext device;
//        private final ObjectBuffer<T> buffer;
//        private Event event;
//
//        public ReferenceContext(OCLDeviceContext device, ObjectBuffer<T> buffer) {
//            this(device, buffer, null);
//        }
//
//        public ReferenceContext(OCLDeviceContext device,
//                ObjectBuffer<T> buffer, Event event) {
//            this.device = device;
//            this.buffer = buffer;
//            this.event = event;
//        }
//
//        public OCLDeviceContext getDevice() {
//            return device;
//        }
//
//        public ObjectBuffer<T> getBuffer() {
//            return buffer;
//        }
//
//        public Event getEvent() {
//            return event;
//        }
//
//        public void setEvent(Event value) {
//            event = value;
//        }
//
//        public boolean hasEvent() {
//            return event != null;
//        }
//    }
//
//    private final ReferenceContext<T>[] history;
//    private int historyIndex;
//    private int ownerIndex;

    public OCLObjectReference(T value, int historyLength) {
        this(value, null, historyLength);
    }

    public OCLObjectReference(T value, ReferenceQueue<? super T> queue,
            int maxContexts) {
        super(value, queue, maxContexts);
    }


    
   

    @Override
    public void mapTo(DeviceMapping device) {
        try {
        	OCLDeviceMapping mapping = (OCLDeviceMapping) device;
            ensureBufferForDevice(mapping.getDeviceContext());
            for (int i = 0; i < getOwnerCount(); i++)
                if (history[i].getDevice() == mapping.getDeviceContext()) {
                    ownerIndex = i;
                    break;
                }
        } catch (TornadoOutOfMemoryException e) {
            e.printStackTrace();
        }
    }

    public Event insertWriteBarrier(OCLDeviceContext device, List<Event> events){
    	return device.enqueueMarker(events);
    }

   

//    public ObjectBuffer<?> createDeviceBuffer(OCLDeviceContext device)
//            throws TornadoOutOfMemoryException {
//
//        T arg = get();
//
//        if (arg.getClass().isArray()) {
//            ObjectBuffer<T> result = null;
//            if (arg instanceof int[]) {
//                result = (ObjectBuffer<T>) new OCLIntArrayWrapper(device);
//            } else if (arg instanceof float[]) {
//                result = (ObjectBuffer<T>) new OCLFloatArrayWrapper(device);
//            } else if (arg instanceof double[]) {
//                result = (ObjectBuffer<T>) new OCLDoubleArrayWrapper(device);
//            } else if (arg instanceof long[]) {
//                result = (ObjectBuffer<T>) new OCLLongArrayWrapper(device);
//            }
//
//            if (result != null) {
//                result.allocate(arg);
//            } else {
//                fatal("Unable to create buffer for object: " + arg);
//            }
//
//            return (ObjectBuffer<?>) result;
//
//        } else if (!arg.getClass().isPrimitive() && !arg.getClass().isArray()) {
//            ObjectBuffer<Object> result = null;
//            result = new OCLObjectWrapper(device, arg);
//
//            result.allocate(arg);
//
//            return (ObjectBuffer<?>) result;
//        }
//        return null;
//    }

   
   

   

    public boolean isOnDevice(DeviceMapping deviceMapping) {
        if (deviceMapping instanceof OCLDeviceMapping) {
            final OCLDeviceMapping mapping = (OCLDeviceMapping) deviceMapping;
            for (ReferenceContext<OCLDeviceContext,T> context : history) {
                if (context != null
                        && context.getDevice() == mapping.getDeviceContext())
                    return true;
            }
        }
        return false;
    }

    public ReferenceContext<OCLDeviceContext,T> getDeviceContext(DeviceMapping deviceMapping) {
        if (deviceMapping instanceof OCLDeviceMapping) {
            final OCLDeviceMapping mapping = (OCLDeviceMapping) deviceMapping;
            for (final ReferenceContext<OCLDeviceContext,T> context : history) {
                if (context != null
                        && context.getDevice() == mapping.getDeviceContext())
                    return context;
            }
        }
        return null;
    }

	@Override
	public ObjectBuffer createDeviceBuffer(OCLDeviceContext device)
			throws TornadoOutOfMemoryException {
		// TODO Auto-generated method stub
		return null;
	}
}
