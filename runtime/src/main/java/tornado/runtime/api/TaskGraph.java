package tornado.runtime.api;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import tornado.api.DeviceMapping;
import tornado.api.Event;
import tornado.runtime.DataMovementTask;
import tornado.runtime.SchedulableTask;
import tornado.runtime.api.TornadoFunctions.Task1;
import tornado.runtime.api.TornadoFunctions.Task10;
import tornado.runtime.api.TornadoFunctions.Task2;
import tornado.runtime.api.TornadoFunctions.Task3;
import tornado.runtime.api.TornadoFunctions.Task4;
import tornado.runtime.api.TornadoFunctions.Task5;
import tornado.runtime.api.TornadoFunctions.Task6;
import tornado.runtime.api.TornadoFunctions.Task7;
import tornado.runtime.api.TornadoFunctions.Task8;
import tornado.runtime.api.TornadoFunctions.Task9;

public class TaskGraph {

    private static class TaskNode {
        final private SchedulableTask task;
        private TaskNode next;
        private TaskNode last;

        public TaskNode(SchedulableTask task, TaskNode last) {
            this.task = task;
            this.last = last;
            this.next = null;
        }

        public void addNext(TaskNode node) {
            next = node;
        }

        public TaskNode next() {
            return next;
        }

        public boolean hasNext() {
            return next != null;
        }

        public void schedule() {
            if (last == null)
                task.schedule();
            else {
                task.schedule(last.task.getEvent());
            }
            // task.waitOn();
        }
    }

    private List<Event> events;

    private TaskNode root;
    private TaskNode tail;

    public TaskGraph() {
        root = null;
        tail = null;
        events = new ArrayList<Event>();
    }

    private void insertTask(SchedulableTask task, TaskNode last) {
        final TaskNode node = new TaskNode(task, last);
        if (root == null) {
            root = node;
        } else {
            tail.addNext(node);
        }

        tail = node;
    }

    public TaskGraph add(SchedulableTask task) {

        final TaskNode last = tail;
        insertTask(task, last);

        return this;
    }

    public <T1> TaskGraph add(Task1<T1> code, T1 arg) {
        return add(TaskUtils.createTask(code, arg));
    }

    public <T1, T2> TaskGraph add(Task2<T1, T2> code, T1 arg1, T2 arg2) {
        return add(TaskUtils.createTask(code, arg1, arg2));
    }

    public <T1, T2, T3> TaskGraph add(Task3<T1, T2, T3> code, T1 arg1, T2 arg2,
            T3 arg3) {
        return add(TaskUtils.createTask(code, arg1, arg2, arg3));
    }

    public <T1, T2, T3, T4> TaskGraph add(Task4<T1, T2, T3, T4> code, T1 arg1,
            T2 arg2, T3 arg3, T4 arg4) {
        return add(TaskUtils.createTask(code, arg1, arg2, arg3, arg4));
    }

    public <T1, T2, T3, T4, T5> TaskGraph add(Task5<T1, T2, T3, T4, T5> code,
            T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5) {
        return add(TaskUtils.createTask(code, arg1, arg2, arg3, arg4, arg5));
    }

    public <T1, T2, T3, T4, T5, T6> TaskGraph add(
            Task6<T1, T2, T3, T4, T5, T6> code, T1 arg1, T2 arg2, T3 arg3,
            T4 arg4, T5 arg5, T6 arg6) {
        return add(TaskUtils.createTask(code, arg1, arg2, arg3, arg4, arg5,
                arg6));
    }

    public <T1, T2, T3, T4, T5, T6, T7> TaskGraph add(
            Task7<T1, T2, T3, T4, T5, T6, T7> code, T1 arg1, T2 arg2, T3 arg3,
            T4 arg4, T5 arg5, T6 arg6, T7 arg7) {
        return add(TaskUtils.createTask(code, arg1, arg2, arg3, arg4, arg5,
                arg6, arg7));
    }

    public <T1, T2, T3, T4, T5, T6, T7, T8> TaskGraph add(
            Task8<T1, T2, T3, T4, T5, T6, T7, T8> code, T1 arg1, T2 arg2,
            T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8) {
        return add(TaskUtils.createTask(code, arg1, arg2, arg3, arg4, arg5,
                arg6, arg7, arg8));
    }

    public <T1, T2, T3, T4, T5, T6, T7, T8, T9> TaskGraph add(
            Task9<T1, T2, T3, T4, T5, T6, T7, T8, T9> code, T1 arg1, T2 arg2,
            T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9) {
        return add(TaskUtils.createTask(code, arg1, arg2, arg3, arg4, arg5,
                arg6, arg7, arg8, arg9));
    }

    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> TaskGraph add(
            Task10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> code, T1 arg1,
            T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8,
            T9 arg9, T10 arg10) {
        return add(TaskUtils.createTask(code, arg1, arg2, arg3, arg4, arg5,
                arg6, arg7, arg8, arg9, arg10));
    }

    public TaskGraph schedule() {
        events.clear();

        TaskNode current = root;
        while (current != null) {
//            System.out.printf("scheduling: %s...\n",current.task.getName());
            current.schedule();
//            System.out.printf("\tevent: %s\n",current.task.getEvent());
            events.add(current.task.getEvent());
            current = current.next();
        }
        return this;
    }

    public void apply(Consumer<SchedulableTask> consumer) {
        TaskNode current = root;
        while (current != null) {
            consumer.accept(current.task);
            current = current.next();
        }
    }

    public TaskGraph mapAllTo(DeviceMapping mapping) {
        apply(task -> task.mapTo(mapping));
        return this;
    }

    public void dumpTimes() {
        System.out.printf("Task Graph: %d tasks\n", events.size());
        apply(task -> System.out
                .printf("\t%s: status=%s, execute=%.8f s, total=%.8f s, queued=%.8f s\n",
                        task.getName(), task.getStatus(),
                        task.getExecutionTime(), task.getTotalTime(),
                        task.getQueuedTime()));

    }

    public void waitOn() {
        for (Event event : events)
            event.waitOn();
    }

    public TaskGraph read(Object... objects) {
        final TaskNode last = tail;
        for (Object object : objects) {
            final DataMovementTask markDirty = TaskUtils.markHostDirty(object);
            insertTask(markDirty, last);
        }

        return this;
    }

    public TaskGraph collect(Object... objects) {
        final TaskNode last = tail;
        for (Object object : objects) {
            final DataMovementTask read = TaskUtils.read(object);
            insertTask(read, last);
        }

        return this;
    }

    public void printCompileTimes() {
        apply(task -> {
            if (task instanceof ExecutableTask)
                System.out.printf("compile: task=%s, time=%.8f s\n",
                        task.getName(),
                        ((ExecutableTask<?>) task).getCompileTime());
        });
    }

}
