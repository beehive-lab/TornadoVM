package uk.ac.manchester.tornado.drivers.spirv.levelzero;

/**
 * Handle of driver's fence object
 */
public class ZeFenceHandle {

    private long ptrZeFenceHandle;

    public ZeFenceHandle() {
        this.ptrZeFenceHandle = -1;
    }

    public long getPtrZeFenceHandle() {
        return ptrZeFenceHandle;
    }
}
