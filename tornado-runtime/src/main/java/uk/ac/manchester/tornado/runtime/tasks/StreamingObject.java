package uk.ac.manchester.tornado.runtime.tasks;

import uk.ac.manchester.tornado.api.enums.DataTransferMode;

public class StreamingObject {
    DataTransferMode mode;
    Object object;

    public StreamingObject(DataTransferMode mode, Object object) {
        this.mode = mode;
        this.object = object;
    }

    public Object getObject() {
        return object;
    }

    public DataTransferMode getMode() {
        return mode;
    }
}