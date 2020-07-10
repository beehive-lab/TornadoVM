package uk.ac.manchester.tornado.drivers.ptx;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class PTXVersion {
    private static LinkedHashMap<CUDAComputeCapability, CUDAComputeCapability> ptxToArch = new LinkedHashMap<CUDAComputeCapability, CUDAComputeCapability>() {{
        put(new CUDAComputeCapability(6, 3), new CUDAComputeCapability(7, 5));
        put(new CUDAComputeCapability(6, 1), new CUDAComputeCapability(7, 2));
        put(new CUDAComputeCapability(6, 0), new CUDAComputeCapability(7, 0));
        put(new CUDAComputeCapability(5, 0), new CUDAComputeCapability(6, 2));
    }};

    private CUDAComputeCapability version;
    private CUDAComputeCapability maxArch;

    public PTXVersion(CUDAComputeCapability actual) {
        this.version = actual;
        boolean foundArch = false;
        Iterator<Map.Entry<CUDAComputeCapability, CUDAComputeCapability>> iterator = ptxToArch.entrySet().iterator();
        while (iterator.hasNext() && !foundArch) {
            Map.Entry<CUDAComputeCapability, CUDAComputeCapability> versionPair = iterator.next();
            if (versionPair.getKey().compareTo(version) <= 0) {
                this.maxArch = versionPair.getValue();
                foundArch = true;
            }
        }
    }

    @Override
    public String toString() {
        return String.format("%d.%d", version.major, version.minor);
    }

    public CUDAComputeCapability getArch(CUDAComputeCapability deviceCapability) {
        return maxArch.compareTo(deviceCapability) > 0 ? deviceCapability : maxArch;
    }
}
