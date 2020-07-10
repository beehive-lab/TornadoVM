package uk.ac.manchester.tornado.drivers.ptx;

import java.util.Arrays;
import java.util.BitSet;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.runtime.common.Tornado.EVENT_WINDOW;
import static uk.ac.manchester.tornado.runtime.common.Tornado.fatal;
import static uk.ac.manchester.tornado.runtime.common.Tornado.getProperty;

public class PTXEventsWrapper {

    private static final boolean CIRCULAR_EVENTS = Boolean.parseBoolean(getProperty("tornado.opencl.circularevents", "True"));

    private final PTXEvent[] events;
    private final BitSet retain;
    private int eventIndex;

    protected PTXEventsWrapper() {
        this.retain = new BitSet(EVENT_WINDOW);
        this.retain.clear();
        this.events = new PTXEvent[EVENT_WINDOW];
        this.eventIndex = 0;
    }

    protected int registerEvent(byte[][] eventWrapper, PTXEvent.EventDescription description) {
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
        events[currentEvent] = new PTXEvent(eventWrapper, description);

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
        for (PTXEvent event : events) {
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

    protected PTXEvent getEvent(int localEventID) {
        return events[localEventID];
    }

}
