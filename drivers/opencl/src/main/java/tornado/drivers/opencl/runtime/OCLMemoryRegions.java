package tornado.drivers.opencl.runtime;

public class OCLMemoryRegions {

    private int globalSize;
    private int constantSize;
    private int localSize;
    private int privateSize;
    private byte[] constantData;

    public OCLMemoryRegions() {
        this.globalSize = 0;
        this.constantSize = 0;
        this.localSize = 0;
        this.privateSize = 0;
        this.constantData = null;
    }

    public void allocGlobal(int size) {
        this.globalSize = size;
    }

    public void allocLocal(int size) {
        this.localSize = size;
    }

    public void allocPrivate(int size) {
        this.privateSize = size;
    }

    public void allocConstant(int size) {
        this.constantSize = size;
        constantData = new byte[size];
    }

    public int getGlobalSize() {
        return globalSize;
    }

    public int getConstantSize() {
        return constantSize;
    }

    public int getLocalSize() {
        return localSize;
    }

    public int getPrivateSize() {
        return privateSize;
    }

    public byte[] getConstantData() {
        return constantData;
    }

}
