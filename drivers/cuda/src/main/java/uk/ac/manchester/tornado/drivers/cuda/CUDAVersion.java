package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;

public class CUDAVersion {
    private static CUDAVersion[] cudaVersions = new CUDAVersion[] {
        // 8.0 is the first version to have PTX ISA documentation available therefore this is the oldest supported
        new CUDAVersion(8000, new CUDAComputeCapability(5, 0)),
        new CUDAVersion(9000, new CUDAComputeCapability(6, 0)),
        new CUDAVersion(9010, new CUDAComputeCapability(6, 1)),
        new CUDAVersion(9020, new CUDAComputeCapability(6, 2)),
        new CUDAVersion(10000, new CUDAComputeCapability(6, 3)),
        new CUDAVersion(10010, new CUDAComputeCapability(6, 4)),
        new CUDAVersion(10020, new CUDAComputeCapability(6, 5)),
    };

    private final int sdkVersion;
    private final PTXVersion maxPTXVersion;

    private CUDAVersion(int cudaVersion, CUDAComputeCapability ptxVersion) {
        this.sdkVersion = cudaVersion;
        this.maxPTXVersion = new PTXVersion(ptxVersion);
    }

    private static int getMajor(int version) {
        return version / 1000;
    }

    private static int getMinor(int version) {
        return (version % 1000) / 10;
    }

    public static PTXVersion getMaxPTXVersion(int cudaVersion) {
        for (int i = cudaVersions.length - 1; i >= 0; i--) {
            if (cudaVersion >= cudaVersions[i].sdkVersion) return cudaVersions[i].maxPTXVersion;
        }
        TornadoInternalError.shouldNotReachHere(String.format(
                "Unsupported CUDA toolkit version: %d.%d. Please consider upgrading to version %d.%d or higher.",
                getMajor(cudaVersion),
                getMinor(cudaVersion),
                getMajor(cudaVersions[0].sdkVersion),
                getMinor(cudaVersions[0].sdkVersion)
        ));
        return null;
    }
}
