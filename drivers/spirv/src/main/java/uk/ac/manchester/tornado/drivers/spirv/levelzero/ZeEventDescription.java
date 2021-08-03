package uk.ac.manchester.tornado.drivers.spirv.levelzero;

/**
 * Event Descriptor
 */
public class ZeEventDescription {

    /**
     * [in] type of this structure
     */
    private int stype;

    /**
     * [in][optional] pointer to extension-specific structure
     */
    private long pNext;

    /**
     * [in] index of the event within the pool; must be less-than the count
     * specified during pool creation
     */
    private long index;

    /**
     * [in] defines the scope of relevant cache hierarchies to flush on a
     * signal action before the event is triggered.
     * Must be 0 (default) or a valid combination of {@link ZeEventScopeFlags}
     *
     * The default behavior is synchronization within the command list only, no
     * additional cache hierarchies are flushed.
     */
    private int signal;

    /**
     * [in] defines the scope of relevant cache hierarchies to invalidate on
     * a wait action after the event is complete.
     * Must be 0 (default) or a valid combination of {@link ZeEventScopeFlags}
     * default behavior is synchronization within the command list only, no
     * additional cache hierarchies are invalidated.
     */
    private int wait;

    private long ptrZeEventDescription;

    public ZeEventDescription() {
        this.stype = Ze_Structure_Type.ZE_STRUCTURE_TYPE_EVENT_DESC;
        this.ptrZeEventDescription = -1;
    }

    public void setStype(int stype) {
        this.stype = stype;
    }

    public void setIndex(long index) {
        this.index = index;
    }

    public void setSignal(int signal) {
        this.signal = signal;
    }

    public void setWait(int wait) {
        this.wait = wait;
    }

    public int getStype() {
        return stype;
    }

    public long getpNext() {
        return pNext;
    }

    public long getIndex() {
        return index;
    }

    public int getSignal() {
        return signal;
    }

    public int getWait() {
        return wait;
    }

    public long getPtrZeEventDescription() {
        return ptrZeEventDescription;
    }
}
