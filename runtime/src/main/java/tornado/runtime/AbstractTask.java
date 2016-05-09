package tornado.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import tornado.runtime.api.Action;
import tornado.api.DeviceMapping;
import tornado.api.Event;
import tornado.api.enums.TornadoExecutionStatus;
import tornado.common.RuntimeUtilities;
import tornado.common.enums.Access;
import tornado.meta.Meta;

public abstract class AbstractTask<T extends Action> implements SchedulableTask {
    protected final Object[] arguments;
    protected final Access[] argumentsAccess;

    protected final Meta meta;
    protected Event event;
    protected final T action;

    public AbstractTask(T action, int numArguments) {
        this.meta = new Meta();
        this.action = action;
        this.arguments = new Object[numArguments];
        this.argumentsAccess = new Access[numArguments];
    }

    protected void copyToArguments(Object... objects) {
        for (int i = 0; i < objects.length; i++) {
            final Object object = objects[i];
            if (object != null && !RuntimeUtilities.isBoxedPrimitive(object)
                    && !object.getClass().isPrimitive()) {
                final ObjectReference<?,?> ref = TornadoRuntime
                        .resolveObject(object);
                arguments[i] = ref;
                // System.out.printf("task - arg[%d]: 0x%x (device: 0x%x)\n",i,object.hashCode(),ref.getLastWrite().getBuffer().toAbsoluteAddress());
            } else {
                arguments[i] = object;
                // System.out.printf("task - arg[%d]: 0x%x\n",i,object.hashCode());
            }
            // System.out.printf("task - arg[%d]: 0x%x (device: 0x%x)\n",i,object.hashCode());
        }
    }

    public Object[] getArguments() {
        return arguments;
    }

    public Access[] getArgumentsAccess() {
        return argumentsAccess;
    }

    public Meta meta() {
        return meta;
    }

    public SchedulableTask mapTo(DeviceMapping mapping) {
        meta.addProvider(DeviceMapping.class, mapping);
        return this;
    }

    public DeviceMapping getDeviceMapping() {
        return meta.getProvider(DeviceMapping.class);
    }

    public double getExecutionTime() {
        return event.getExecutionTime();
    }

    public double getQueuedTime() {
        return event.getQueuedTime();
    }

    public TornadoExecutionStatus getStatus() {
        return event.getStatus();
    }

    public double getTotalTime() {
        return event.getTotalTime();
    }

    public void waitOn() {
        event.waitOn();
        event.toString();
    }

    public Event getEvent() {
        return event;
    }

    public void schedule(Event... events) {
        final List<Event> waitEvents = new ArrayList<Event>();
        for (int i = 0; i < events.length; i++)
            waitEvents.add(events[i]);
        schedule(waitEvents);
    }

    public void schedule() {
        schedule(Collections.emptyList());
    }

    public void schedule(List<Event> events) {
        event = action.apply(arguments, argumentsAccess, meta, events);
    }

    public String getName() {
        return event.getName();
    }

    public T action() {
        return action;
    }
}
