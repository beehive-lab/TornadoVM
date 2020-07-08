package uk.ac.manchester.tornado.drivers.cuda;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.runtime.common.Tornado.EVENT_WINDOW;
import static uk.ac.manchester.tornado.runtime.common.Tornado.fatal;
import static uk.ac.manchester.tornado.runtime.common.Tornado.getProperty;

public class CUDAEventsWrapper {

    private static final boolean CIRCULAR_EVENTS = Boolean.parseBoolean(getProperty("tornado.opencl.circularevents", "True"));

    private final CUDAEvent[] events;
    private final BitSet retain;
    private int eventIndex;

    protected CUDAEventsWrapper() {
        this.retain = new BitSet(EVENT_WINDOW);
        this.retain.clear();
        this.events = new CUDAEvent[EVENT_WINDOW];
        this.eventIndex = 0;
    }

    protected int registerEvent(byte[][] eventWrapper, CUDAEvent.EventDescription description) {
        if (retain.get(eventIndex)) {
            findNextEventSlot();
        }
        final int currentEvent = eventIndex;
        guarantee(!retain.get(currentEvent), "overwriting retained event");

        /*
         * TODO better error check here for PTX
         * PTX can produce an out of resources error which results in an invalid event.
         * If this happens, then we log a fatal exception and gracefully exit.
         */
        if (eventWrapper == null) {
            fatal("invalid event: event=%s\n", description.name());
            fatal("terminating application as system integrity has been compromised.");
            System.exit(-1);
        }

        if (events[currentEvent] != null && !retain.get(currentEvent)) {
            events[currentEvent].waitForEvents();
            events[currentEvent].destroy();
            events[currentEvent] = null;
            releaseEvent(currentEvent);
        }
        events[currentEvent] = new CUDAEvent(eventWrapper, description);

        findNextEventSlot();
        return currentEvent;
    }

    private void findNextEventSlot() {
        eventIndex = retain.nextClearBit(eventIndex + 1);

        if (CIRCULAR_EVENTS && (eventIndex >= events.length)) {
            eventIndex = 0;
        }

        guarantee(eventIndex != -1, "event window is full (retained=%d, capacity=%d)", retain.cardinality(), EVENT_WINDOW);
    }

    protected void reset() {
        for (CUDAEvent event : events) {
            if (event != null) {
                event.destroy();
            }
        }
        Arrays.fill(events, null);
        eventIndex = 0;
    }

    private void releaseEvent(int localEventID) {
        retain.clear(localEventID);
    }

    private void retainEvent(int localEventID) {
        retain.set(localEventID);
    }

    protected CUDAEvent getEvent(int localEventID) {
        return events[localEventID];
    }

}
