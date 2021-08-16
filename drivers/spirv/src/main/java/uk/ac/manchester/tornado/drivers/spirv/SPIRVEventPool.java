package uk.ac.manchester.tornado.drivers.spirv;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.runtime.common.Tornado.EVENT_WINDOW;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.CIRCULAR_EVENTS;

import java.util.BitSet;

import uk.ac.manchester.tornado.drivers.EventDescriptor;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeEventHandle;

/**
 * This class controls a pools of low-level events for the device. There is a
 * pool of events per device, and it handles the actual events that will
 * communicate with the correct driver (e.g., LevelZero event, OCL events).
 */
public class SPIRVEventPool {

    private final int poolSize;
    private final BitSet retain;
    private int eventPositionIndex;

    private final ZeEventHandle[] events;
    private final EventDescriptor[] descriptors;

    protected SPIRVEventPool(int poolSize) {
        this.poolSize = poolSize;
        this.events = new ZeEventHandle[poolSize];
        this.descriptors = new EventDescriptor[poolSize];
        this.retain = new BitSet(poolSize);
        this.eventPositionIndex = 0;
    }

    private void findNextEventSlot() {
        eventPositionIndex = retain.nextClearBit(eventPositionIndex + 1);
        if (CIRCULAR_EVENTS && (eventPositionIndex >= events.length)) {
            eventPositionIndex = 0;
        }
        guarantee(eventPositionIndex != -1, "event window is full (retained=%d, capacity=%d)", retain.cardinality(), EVENT_WINDOW);
    }

    protected int registerEvent(ZeEventHandle eventHandle, EventDescriptor eventDescriptor) {
        if (retain.get(eventPositionIndex)) {
            findNextEventSlot();
        }
        final int currentEventPosition = eventPositionIndex;
        guarantee(!retain.get(currentEventPosition), "overwriting retained event");

        if (events[currentEventPosition] != null && !retain.get(currentEventPosition)) {
            events[currentEventPosition] = null;
            throw new RuntimeException("Not supported");
        }

        events[currentEventPosition] = eventHandle;
        descriptors[currentEventPosition] = eventDescriptor;
        findNextEventSlot();

        return currentEventPosition;
    }

    private int getPoolSize() {
        return poolSize;
    }

}
