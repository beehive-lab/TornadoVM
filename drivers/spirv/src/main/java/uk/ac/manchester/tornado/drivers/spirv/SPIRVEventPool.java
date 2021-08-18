package uk.ac.manchester.tornado.drivers.spirv;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.runtime.common.Tornado.EVENT_WINDOW;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.CIRCULAR_EVENTS;

import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedList;

import uk.ac.manchester.tornado.drivers.EventDescriptor;
import uk.ac.manchester.tornado.drivers.spirv.timestamps.TimeStamp;

/**
 * This class controls a pools of low-level events for the device. There is a
 * pool of events per device, and it handles the actual events that will
 * communicate with the correct driver (e.g., LevelZero event, OCL events).
 */
public class SPIRVEventPool {

    private final int poolSize;
    private final BitSet retain;
    private int eventPositionIndex;

    private final HashMap<Integer, LinkedList<TimeStamp>> events;
    private final EventDescriptor[] descriptors;

    protected SPIRVEventPool(int poolSize) {
        this.poolSize = poolSize;
        this.events = new HashMap<>();
        this.descriptors = new EventDescriptor[poolSize];
        this.retain = new BitSet(poolSize);
        this.eventPositionIndex = 0;
    }

    private void findNextEventSlot() {
        eventPositionIndex = retain.nextClearBit(eventPositionIndex + 1);
        if (CIRCULAR_EVENTS && (eventPositionIndex >= poolSize)) {
            eventPositionIndex = 0;
        }
        guarantee(eventPositionIndex != -1, "event window is full (retained=%d, capacity=%d)", retain.cardinality(), EVENT_WINDOW);
    }

    protected int registerEvent(EventDescriptor eventDescriptor, TimeStamp start, TimeStamp stop) {
        if (retain.get(eventPositionIndex)) {
            findNextEventSlot();
        }
        final int currentEventPosition = eventPositionIndex;
        guarantee(!retain.get(currentEventPosition), "overwriting retained event");

        // if (events[currentEventPosition] != 0 && !retain.get(currentEventPosition)) {
        // events[currentEventPosition] = 0;
        // throw new RuntimeException("Not supported");
        // }

        LinkedList<TimeStamp> listTimeStamps = new LinkedList<>();
        listTimeStamps.add(start);
        listTimeStamps.add(stop);

        events.put(currentEventPosition, listTimeStamps);

        // events[currentEventPosition] = currentEventPosition;
        descriptors[currentEventPosition] = eventDescriptor;
        findNextEventSlot();

        return currentEventPosition;
    }

    private int getPoolSize() {
        return poolSize;
    }

    public LinkedList<TimeStamp> getTimers(int eventId) {
        return events.get(eventId);
    }

    public EventDescriptor getDescriptor(int eventId) {
        return descriptors[eventId];
    }
}
