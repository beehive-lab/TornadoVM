package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class LevelZeroBinaryModule {

    private String path;
    private int size;
    private long ptrToBinaryFile;

    public LevelZeroBinaryModule(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public int getSize() {
        return size;
    }

    public long getBinary() {
        return ptrToBinaryFile;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setSize(int size) {
        this.size = size;
    }

    native int readBinary_native();

    public int readBinary() {
        return readBinary_native();
    }
}
