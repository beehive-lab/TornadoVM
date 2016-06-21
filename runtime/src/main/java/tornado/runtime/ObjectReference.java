package tornado.runtime;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import tornado.api.Event;
import tornado.api.enums.TornadoExecutionStatus;
import tornado.common.DeviceMapping;
import tornado.common.Initialisable;
import tornado.common.ObjectBuffer;
import tornado.common.Tornado;
import tornado.common.enums.Access;
import tornado.common.exceptions.TornadoInternalError;
import tornado.common.exceptions.TornadoOutOfMemoryException;

public abstract class ObjectReference<D,T> extends WeakReference<T> {

    protected boolean exclusive;
    protected boolean shared;
    protected boolean modified;
    protected boolean valid;
    protected boolean alwaysWrite;	// aka volatile in
    protected boolean hostDirty;

    /*
     * holds a reference to the last write made to this object
     * this may either be a data movement request OR a
     * task invocation
     */
    protected int lastWriteIndex;

    private final Logger logger = LogManager.getLogger(this.getClass());

    protected void info(String msg) {
        logger.info(msg);
    }

    protected void info(String pattern, Object... args) {
        info(String.format(pattern, args));
    }

    protected void debug(String msg) {
        logger.debug(msg);
    }

    protected void debug(String pattern, Object... args) {
        debug(String.format(pattern, args));
    }

    protected void warn(String pattern, Object... args) {
        warn(String.format(pattern, args));
    }

    protected void warn(String msg) {
        logger.warn(msg);
    }

    protected void fatal(String pattern, Object... args) {
        fatal(String.format(pattern, args));
    }

    protected void fatal(String msg) {
        logger.fatal(msg);
    }

    protected void error(String pattern, Object... args) {
        error(String.format(pattern, args));
    }

    protected void error(String msg) {
        logger.error(msg);
    }

    protected void trace(String pattern, Object... args) {
        trace(String.format(pattern, args));
    }

    protected void trace(String msg) {
        logger.trace(msg);
    }

    public static class ReferenceContext<D,T> {
        private final D device;
        private final ObjectBuffer buffer;
        private Event event;

        public ReferenceContext(D device, ObjectBuffer buffer) {
            this(device, buffer, null);
        }

        public ReferenceContext(D device,
                ObjectBuffer buffer, Event event) {
            this.device = device;
            this.buffer = buffer;
            this.event = event;
        }

        public D getDevice() {
            return device;
        }

        public ObjectBuffer getBuffer() {
            return buffer;
        }

        public Event getEvent() {
            return event;
        }

        public void setEvent(Event value) {
            event = value;
        }

        public boolean hasEvent() {
            return event != null;
        }
    }

    protected final ReferenceContext<D,T>[] history;
    protected int historyIndex;
    protected int ownerIndex;

    public ObjectReference(T value, int historyLength) {
        this(value, null, historyLength);
    }

    @SuppressWarnings("unchecked")
	public ObjectReference(T value, ReferenceQueue<? super T> queue,
            int maxContexts) {
        super(value, queue);
        history = new ReferenceContext[maxContexts];
        historyIndex = -1;
        ownerIndex = -1;
        lastWriteIndex = -1;
        alwaysWrite = false;
        exclusive = false;
        shared = false;
        modified = false;
        valid = false;
        hostDirty = false;
    }

    public void setVolatile() {
        alwaysWrite = true;
    }

    public void setHostDirty() {
        hostDirty = true;
    }

    public void setVolatile(boolean value) {
        alwaysWrite = value;
    }

    public boolean isAlive() {
        return (get() == null) ? false : true;
    }

    public boolean isFinal() {
        T value = get();
        return (isAlive() && value instanceof Initialisable) ? ((Initialisable) value)
                .isInitialised() : false;
    }

    protected void createNewBufferForDevice(D device)
            throws TornadoOutOfMemoryException {

        if (historyIndex < history.length) {
            historyIndex = (historyIndex + 1) % history.length;
            final ObjectBuffer buffer = (ObjectBuffer) createDeviceBuffer(device);
            if (Tornado.DEBUG)
                info("created new buffer for object 0x%x @ 0x%x", get()
                        .hashCode(), buffer.toAbsoluteAddress());
            history[historyIndex] = new ReferenceContext<D,T>(device, buffer);
        }

    }
    
    protected void ensureBufferForDevice(D device)
            throws TornadoOutOfMemoryException {
        boolean found = false;
        for (int i = 0; i < getOwnerCount(); i++)
            if (history[i].getDevice() == device) {
                found = true;
                break;
            }

        if (!found)
            createNewBufferForDevice(device);
    }

    public abstract void mapTo(DeviceMapping device);

    protected boolean isValid() {
        return valid;
    }

    protected int getOwnerCount() {
        return historyIndex + 1;
    }

    private D getOwner() {
        return (isValid()) ? history[ownerIndex].device : null;
    }

    private void invalidateOthers() {
        for (int i = 0; i < getOwnerCount(); i++) {
            if (i != ownerIndex) {
                if (Tornado.DEBUG)
                    debug("invalidate: object=0x%x, device=%s", get()
                            .hashCode(), history[i].device);
                history[i].buffer.invalidate();
            }
        }
    }

    private void invalidateAll() {
        for (int i = 0; i < getOwnerCount(); i++) {
            history[i].buffer.invalidate();
        }
    }

    private int getDeviceIndex(D device) {
        for (int i = 0; i < getOwnerCount(); i++) {
            if (history[i].device == device) {
                return i;
            }
        }
        return -1;
    }

    public abstract Event insertWriteBarrier(D device, List<Event> events);
    
    public ObjectBuffer requestAccess(D device, Access access) {
        return requestAccess(device, access, true);
    }

    public ObjectBuffer requestAccess(D device,
            Access access, boolean shouldInitialise) {
        ObjectBuffer result = null;
        if (Tornado.DEBUG)
            debug("request access: object=0x%x, device=%s, access=%s", get()
                    .hashCode(), device,
                    access.toString());
        TornadoInternalError.guarantee(access != Access.NONE
                || access != Access.UNKNOWN, "unsuported object access: %s",
                access.toString());
        try {

            List<Event> waitEvents = new ArrayList<Event>();
            boolean isWriteRequest = access == Access.READ_WRITE
                    || access == Access.WRITE;
            boolean shouldWrite = hostDirty;

            hostDirty = false;

            /*
             * Object has not been claimed by any device
             */
            if (!isValid()) {
                if (Tornado.DEBUG)
                    debug("request access: object=0x%x is now owned by %s",
                            get().hashCode(), device);
                createNewBufferForDevice(device);
                ownerIndex = historyIndex;
                exclusive = true;
                valid = true;
                modified = isWriteRequest;
                shouldWrite = shouldInitialise;
            } else {

                /*
                 * Case where access is made by the owner of the reference
                 */
                if (getOwner().equals(device)) {
                    if (Tornado.DEBUG)
                        debug("request access: object=0x%x already owner %s",
                                get().hashCode(), device);
                    assert (history[ownerIndex].buffer.isValid()) : "buffer is in an invalid state - ref="
                            + this + ", buffer=" + history[ownerIndex].buffer;
                    /*
                     * if this object has exclusive ownership all ok, otherwise
                     * we need to invalidate all other copies of this object
                     */
                    if (exclusive && isWriteRequest) {
                        modified = true;
                    } else if (isWriteRequest) {
                        invalidateOthers();
                        shared = false;
                    } else if (shouldWrite) {
                        history[ownerIndex].buffer.invalidate();
                    }
                    valid = true;

                } else {

                    /*
                     * there may be outstanding writes to this object
                     * we must enqueue these dependencies and place a write
                     * barrier to ensure they
                     * complete
                     * before overwriting/propagating this object
                     */
                    if (hasOutstandingWrite()) {
                        if (history[lastWriteIndex].event.getStatus() != TornadoExecutionStatus.COMPLETE) {
                            final List<Event> events = new ArrayList<Event>(1);
                            events.add(history[lastWriteIndex].event);
                            waitEvents.add(insertWriteBarrier(history[lastWriteIndex].device,events));
                                    
                        }
                        shouldWrite = true;
                    }

                    ensureBufferForDevice(device);
                    if (isWriteRequest) {

                        /*
                         * if the object is modified, sync first
                         */
                        if (modified) {
                            if (Tornado.DEBUG)
                                debug("request access: object=0x%x syncing with host",
                                        get().hashCode());
                            waitEvents.add(enqueueTransferFromDevice(
                                    ownerIndex, waitEvents));
                            modified = false;
                        }

                        /*
                         * take ownership of the object and invalidate
                         */
                        ownerIndex = getDeviceIndex(device);
                        invalidateOthers();

                        if (Tornado.DEBUG)
                            debug("request access: object=0x%x now owned by %s",
                                    get().hashCode(), device);

                        modified = true;
                        shared = false;
                        exclusive = true;
                    } else {
                        if (modified) {
                            /*
                             * need to sync with the modified copy...
                             */
                            if (Tornado.DEBUG)
                                debug("request access: object=0x%x syncing with host",
                                        get().hashCode());
                            waitEvents.add(enqueueTransferFromDevice(
                                    ownerIndex, waitEvents));
                            modified = false;
                            exclusive = false;
                            shared = true;
                        } else {
                            exclusive = false;
                            shared = true;
                        }
                    }
                }
            }

            /*
             * special case where the object is volatile
             */
            if (alwaysWrite) {
                if (Tornado.DEBUG)
                    debug("request access: object=0x%x is volatile invalidating",
                            get().hashCode());
                invalidateAll();
                shouldWrite = true;
            }

            /*
             * transfer data to the device
             */
            int deviceIndex = getDeviceIndex(device);
            result = history[deviceIndex].buffer;
            if ((!waitEvents.isEmpty() || !result.isValid()) && shouldWrite) {
                if (Tornado.DEBUG)
                    debug("request access: object=0x%x writing to device %s",
                            get().hashCode(), device);
                setLastWrite(deviceIndex,
                        enqueueTransferToDevice(deviceIndex, waitEvents));
            } else {
                if (Tornado.DEBUG)
                    debug("request access: object=0x%x already on device %s and up to date",
                            get().hashCode(), device);
                lastWriteIndex = -1;
            }

            if (Tornado.DEBUG)
                trace("request status: %s", toString());

        } catch (TornadoOutOfMemoryException e) {
            warn(e.getMessage());
//            TornadoRuntime.runtime.gc();
        }
        return result;
    }

    public boolean hasOutstandingWrite() {
        return (lastWriteIndex == -1) ? false : history[lastWriteIndex]
                .hasEvent();
    }

    private Event enqueueTransferToDevice(int deviceIndex,
            List<Event> waitEvents) {

        return history[deviceIndex].buffer.enqueueWriteAfterAll(get(),
                waitEvents);

    }

    private Event enqueueTransferFromDevice(int deviceIndex,
            List<Event> waitEvents) {

        return history[deviceIndex].buffer.enqueueReadAfterAll(get(),
                waitEvents);

    }

    public abstract ObjectBuffer createDeviceBuffer(D device)
            throws TornadoOutOfMemoryException;

    @Override
    public void clear() {
        super.clear();
    }

    /**
     * Updates the last write event for this object - used when a task
     * invocation modifies an object
     * 
     * @param device
     * @param buffer
     * @param event
     */
    public void setLastWrite(final D device,
            final Event event) {
        setLastWrite(getDeviceIndex(device), event);
    }

    /**
     * Updates the last write event for this object - used when a task
     * invocation modifies an object
     * 
     * @param deviceContext
     * @param buffer
     * @param event
     */
    private void setLastWrite(int index, final Event event) {
        lastWriteIndex = index;
        history[lastWriteIndex].setEvent(event);
        modified = true;
    }

    public ReferenceContext<D,T> getLastWrite() {
        return (lastWriteIndex != -1) ? history[lastWriteIndex] : null;
    }

    public boolean isModified() {
        return modified;
    }

    public void enqueueSync(Map<D, List<Event>> waitEvents) {

        if (modified) {
            final D device = getOwner();
            if (!waitEvents.containsKey(device))
                waitEvents.put(device, new ArrayList<Event>());

            final List<Event> events = waitEvents.get(device);
            events.add(getOwnerBuffer().enqueueRead(get()));
            modified = false;
        }
    }

    private final ObjectBuffer getOwnerBuffer() {
        return history[ownerIndex].buffer;
    }

    public void read() {
        if (modified) {
            if (Tornado.DEBUG)
                debug("object=0x%x reading from device %s", get().hashCode(),
                        getOwner());
            getOwnerBuffer().read(get());
            modified = false;
        }
    }

    public void write() {
        getOwnerBuffer().write(get());
        modified = false;
    }

    public Event enqueueRead() {
        modified = false;
        return getOwnerBuffer().enqueueRead(get());
    }

    public Event enqueueWrite() {
        return getOwnerBuffer().enqueueWrite(get());
    }

    public Event enqueueReadAfter(Event event) {
        modified = false;
        return getOwnerBuffer().enqueueReadAfter(get(), event);
    }

    public Event enqueueWriteAfter(Event event) {
        return getOwnerBuffer().enqueueWriteAfter(get(), event);
    }

    public Event enqueueReadAfterAll(List<Event> events) {
        modified = false;
        // System.out.println("enqueue read after all on " + toString());
        return getOwnerBuffer().enqueueReadAfterAll(get(), events);
    }

    public Event enqueueWriteAfterAll(List<Event> events) {
        // System.out.println("enqueue write after all on " + toString());
        return getOwnerBuffer().enqueueWriteAfterAll(get(), events);
    }

    private String trimClassName(String className) {
        final int index = className.lastIndexOf('.');
        return className.substring(index + 1);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        T value = get();
        if (value == null) {
            sb.append("dead object");
        } else {
            sb.append(String.format("object=0x%x, type=%s, flags=",
                    value.hashCode(), trimClassName(value.getClass().getName())));
            sb.append(hostDirty ? "H" : "-");
            sb.append(valid ? "V" : "-");
            sb.append(modified ? "M" : "-");
            sb.append(shared ? "S" : "-");
            sb.append(exclusive ? "X" : "-");

            if (isValid()) {
                sb.append(", owner="
                        + history[ownerIndex].device);
                sb.append(String.format(", address=0x%x",
                        history[ownerIndex].buffer.toRelativeAddress()));
            }

        }
        return sb.toString();
    }

    
    public abstract boolean isOnDevice(DeviceMapping mapping); 

    public abstract ReferenceContext<D,T> getDeviceContext(DeviceMapping deviceMapping);

    public long toAbsoluteAddress(DeviceMapping deviceMapping) {
        final ReferenceContext<D,T> context = getDeviceContext(deviceMapping);
        return context.getBuffer().toAbsoluteAddress();
    }

    public void printHeapTrace(DeviceMapping deviceMapping) {
        final ReferenceContext<D,T> context = getDeviceContext(deviceMapping);
        context.getBuffer().printHeapTrace();
    }

    public long toRelativeAddress(DeviceMapping deviceMapping) {
        final ReferenceContext<D,T> context = getDeviceContext(deviceMapping);
        return context.getBuffer().toRelativeAddress();
    }

    public long sizeOf(DeviceMapping deviceMapping) {
        final ReferenceContext<D,T> context = getDeviceContext(deviceMapping);
        return context.getBuffer().size();
    }

    public void setDirty() {
        modified = true;

    }
}
