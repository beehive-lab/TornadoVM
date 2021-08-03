package uk.ac.manchester.tornado.drivers.spirv.levelzero;

/**
 * Event Pool Descriptor
 */
public class ZeEventPoolDescription {

    /**
     * [in] Type of this structure
     */
    private int stype;

    /**
     * [in][optional] pointer to extension-specific structure
     */
    private long pNext;

    /**
     * [in] creation flags.
     */
    private int flags;

    /**
     * Must be 0 (default) or a valid combination of {@link ZeEventPoolFlags}
     * default behavior is signals and waits are visible to the entire device
     * and peer devices.
     */
    private int count;

    /**
     * C pointer with event description
     */
    private long ptrZeEventPoolDescription;

    public ZeEventPoolDescription() {
        this.stype = Ze_Structure_Type.ZE_STRUCTURE_TYPE_EVENT_POOL_DESC;
        this.ptrZeEventPoolDescription = -1;
    }

    public int getStype() {
        return stype;
    }

    public long getpNext() {
        return pNext;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public int getFlags() {
        return flags;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getCount() {
        return count;
    }

    public long getPtrZeEventPoolDescription() {
        return ptrZeEventPoolDescription;
    }
}
