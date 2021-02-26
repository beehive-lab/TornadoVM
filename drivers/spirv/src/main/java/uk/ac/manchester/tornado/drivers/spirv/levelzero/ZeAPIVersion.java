package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeAPIVersion {

    private long apiVersionPtr;
    private String version;

    public ZeAPIVersion() {
        this.apiVersionPtr = 0;
    }

    public long getAPIVersionPtr() {
        return apiVersionPtr;
    }

    public String getAPIVersion() {
        return version;
    }

    public void setVersionString(String version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return version;
    }
}
