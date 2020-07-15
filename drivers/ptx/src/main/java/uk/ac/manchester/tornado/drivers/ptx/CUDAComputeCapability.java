package uk.ac.manchester.tornado.drivers.ptx;

public class CUDAComputeCapability implements Comparable<CUDAComputeCapability> {

    private final int major;
    private final int minor;

    public CUDAComputeCapability(int major, int minor) {
        this.major = major;
        this.minor = minor;
    }

    @Override
    public int compareTo(CUDAComputeCapability other) {
        if (this.major != other.major) return this.major - other.major;

        return this.minor - other.minor;
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }
}
