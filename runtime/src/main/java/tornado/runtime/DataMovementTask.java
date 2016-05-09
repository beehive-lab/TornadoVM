package tornado.runtime;

import java.util.List;

import tornado.runtime.api.DataMovementAction;
import tornado.api.Event;

public class DataMovementTask extends AbstractTask<DataMovementAction>
        implements SchedulableTask {

    public DataMovementTask(DataMovementAction action, Object... objects) {
        super(action, objects.length);
        copyToArguments(objects);
        for (int i = 0; i < objects.length; i++)
            argumentsAccess[i] = action.getAccess();
    }

    @Override
    public String getName() {
        return action.getName();
    }

    public void dumpTimes() {
        System.out.printf("DataMovement [%s]:\n", action.getName());
        final List<Event> events = action.getEvents();
        for (int i = 0; i < arguments.length; i++) {
            final Event event = events.get(i);
            if (event != null) {
                System.out
                        .printf("\ttransfer: %s, queued=%.8f s, execute=%.8f s,total=%.8f s\n",
                                arguments[i], event.getQueuedTime(),
                                event.getExecutionTime(), event.getTotalTime());
            }
        }
    }

    @Override
    public void waitOn() {
        super.waitOn();
        // dumpTimes();
    }

}
