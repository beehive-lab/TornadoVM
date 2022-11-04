package uk.ac.manchester.tornado.runtime.tasks;

public class StreamingObject {
    int mode;
    Object object;

    public StreamingObject(int mode, Object object) {
        this.mode = mode;
        this.object = object;
    }

    public Object getObject() {
        return object;
    }

    public int getMode() {
        return mode;
    }
}