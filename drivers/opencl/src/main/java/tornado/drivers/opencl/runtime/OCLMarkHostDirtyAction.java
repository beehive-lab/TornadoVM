package tornado.drivers.opencl.runtime;

import java.util.Collections;
import java.util.List;

import tornado.runtime.api.DataMovementAction;
import tornado.api.Event;
import tornado.common.enums.Access;
import tornado.meta.Meta;
import tornado.runtime.EmptyEvent;
import tornado.runtime.ObjectReference;

public class OCLMarkHostDirtyAction implements DataMovementAction {

    @Override
    public Event apply(Object[] parameters, Access[] access, Meta meta,
            List<Event> events) {

        for (int i = 0; i < parameters.length; i++) {
            final Object object = parameters[i];

            if (object instanceof ObjectReference<?,?>) {
                final ObjectReference<?,?> ref = (ObjectReference<?,?>) object;
                ref.setHostDirty();
                System.out.printf("ref: %s\n", ref.toString());
            }

        }

        return new EmptyEvent();
    }

    @Override
    public Access getAccess() {
        return Access.READ;
    }

    @Override
    public String getName() {
        return "mark host dirty";
    }

    @Override
    public List<Event> getEvents() {
        return Collections.emptyList();
    }

}
