package uk.ac.manchester.tornado.drivers.ptx;

public class PTXVersion {
    private enum PTX_VERSION_TO_ARCHITECTURE {
        PTX_70(new CUDAComputeCapability(7, 0), new TargetArchitecture(8, 0)),
        PTX_63(new CUDAComputeCapability(6, 3), new TargetArchitecture(7, 5)),
        PTX_61(new CUDAComputeCapability(6, 1), new TargetArchitecture(7, 2)),
        PTX_60(new CUDAComputeCapability(6, 0), new TargetArchitecture(7, 0)),
        PTX_50(new CUDAComputeCapability(5, 0), new TargetArchitecture(6, 2));

        private final CUDAComputeCapability ptxIsa;
        private final TargetArchitecture targetArchitecture;

        PTX_VERSION_TO_ARCHITECTURE(CUDAComputeCapability ptxIsa, TargetArchitecture targetArchitecture) {
            this.ptxIsa = ptxIsa;
            this.targetArchitecture = targetArchitecture;
        }
    }

    private final CUDAComputeCapability version;
    private TargetArchitecture maxArch;

    public PTXVersion(CUDAComputeCapability actual) {
        this.version = actual;
        for (PTX_VERSION_TO_ARCHITECTURE ptxToArchitecture : PTX_VERSION_TO_ARCHITECTURE.values()) {
            if (ptxToArchitecture.ptxIsa.compareTo(version) <= 0) {
                this.maxArch = ptxToArchitecture.targetArchitecture;
                break;
            }
        }
    }

    @Override
    public String toString() {
        return String.format("%d.%d", version.getMajor(), version.getMinor());
    }

    public TargetArchitecture getArchitecture(CUDAComputeCapability deviceCapability) {
        CUDAComputeCapability computeCapability = maxArch.compareTo(deviceCapability) > 0 ? deviceCapability : maxArch;
        return new TargetArchitecture(computeCapability);
    }
}
