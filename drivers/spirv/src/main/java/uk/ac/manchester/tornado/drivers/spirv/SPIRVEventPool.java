package uk.ac.manchester.tornado.drivers.spirv;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.runtime.common.Tornado.EVENT_WINDOW;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.CIRCULAR_EVENTS;

import java.util.BitSet;

/**
 * This class controls a pools of low-level events for the device. There is a
 * pool of events per device, and it handles the actual events that will
 * communicate with the correct driver (e.g., LevelZero event, OCL events).
 */
public class SPIRVEventPool {

    private final int poolSize;
    private long[] events;
    private final BitSet retain;
    private int eventIndex;

    protected SPIRVEventPool(int poolSize) {
        this.poolSize = poolSize;
        this.events = new long[poolSize];
        this.retain = new BitSet(poolSize);
        this.eventIndex = 0;
    }

    private void findNextEventSlot() {
        eventIndex = retain.nextClearBit(eventIndex + 1);
        if (CIRCULAR_EVENTS && (eventIndex >= events.length)) {
            eventIndex = 0;
        }
        guarantee(eventIndex != -1, "event window is full (retained=%d, capacity=%d)", retain.cardinality(), EVENT_WINDOW);
    }

    protected int registerEvent(int descriptorId) {
        if (retain.get(eventIndex)) {
            findNextEventSlot();
        }
        final int currentEvent = eventIndex;
        guarantee(!retain.get(currentEvent), "overwriting retained event");

        return currentEvent;
    }

    private int getPoolSize() {
        return poolSize;
    }

}
