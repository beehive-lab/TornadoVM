package tornado.drivers.opencl.runtime;

import java.util.ArrayList;
import java.util.List;

import tornado.runtime.api.DataMovementAction;
import tornado.api.Event;
import tornado.common.enums.Access;
import tornado.meta.Meta;
import tornado.runtime.EmptyEvent;
import tornado.runtime.ObjectReference;
import tornado.runtime.api.TaskUtils;

public class OCLReadAction implements DataMovementAction {
    final List<Event> waitEvents = new ArrayList<Event>(1);

    @Override
    public Event apply(Object[] parameters, Access[] accesses, Meta meta,
            List<Event> events) {
        Event result = null;
        waitEvents.clear();
        // System.out.println("Read");
        TaskUtils.waitForEvents(events);

        final Object object = parameters[0];
        if (object instanceof ObjectReference<?,?>) {
            final ObjectReference<?,?> ref = (ObjectReference<?,?>) object;
           //System.out.printf("reading: ref=%s, object=%s\n", ref.toString(),
             ref.get().toString());
            if (ref.hasOutstandingWrite()) {
                final Event lastWrite = ref.getLastWrite().getEvent();
                // System.out.printf("reading: outstanding write %s - status %s\n",lastWrite,lastWrite.getStatus());
                result = ref.enqueueReadAfter(lastWrite);
            } else {
                System.out.printf("reading: now...\n");
                result = ref.enqueueRead();
            }
            waitEvents.add(result);
        }

        return (result == null) ? new EmptyEvent() : result;
    }

    @Override
    public Access getAccess() {
        return Access.READ;
    }

    @Override
    public String getName() {
        return "read from device";
    }

    @Override
    public List<Event> getEvents() {
        return waitEvents;
    }

}
