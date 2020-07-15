package uk.ac.manchester.tornado.drivers.ptx;

public class TargetArchitecture extends CUDAComputeCapability {

    public TargetArchitecture(CUDAComputeCapability computeCapability) {
        super(computeCapability.getMajor(), computeCapability.getMinor());
    }

    @Override
    public String toString() {
        return String.format("sm_%d%d", getMajor(), getMinor());
    }
}
