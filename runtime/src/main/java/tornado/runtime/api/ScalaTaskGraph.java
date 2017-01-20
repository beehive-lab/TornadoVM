package tornado.runtime.api;

public class ScalaTaskGraph extends AbstractTaskGraph {

    public ScalaTaskGraph(String name) {
        super(name);
    }

    public ScalaTaskGraph task(String id, Object function, Object... args) {
        addInner(TaskUtils.scalaTask(id, function, args));
        return this;
    }

    public ScalaTaskGraph streamIn(Object... objects) {
        streamInInner(objects);
        return this;
    }

    public ScalaTaskGraph streamOut(Object... objects) {
        streamOutInner(objects);
        return this;
    }

    public ScalaTaskGraph schedule() {
        scheduleInner();
        return this;
    }
}
