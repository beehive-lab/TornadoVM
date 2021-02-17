package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeBuildLogHandle {

    private long ptrZeBuildLogHandle;

    private String errorLog;

    public ZeBuildLogHandle() {
        this.ptrZeBuildLogHandle = -1;
    }

    public long getPtrZeBuildLogHandle() {
        return ptrZeBuildLogHandle;
    }

    public String getErrorLog() {
        return errorLog;
    }
}
