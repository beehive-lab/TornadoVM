package uk.ac.manchester.tornado.drivers.spirv;

import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVInstalledCode;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public abstract class SPIRVCodeCache {

    protected final SPIRVDeviceContext deviceContext;
    protected final ConcurrentHashMap<String, SPIRVInstalledCode> cache;

    public SPIRVCodeCache(SPIRVDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
        cache = new ConcurrentHashMap<>();
    }

    public SPIRVInstalledCode getCachedCode(String name) {
        return cache.get(name);
    }

    public boolean isCached(String name) {
        return cache.containsKey(name);
    }

    public void reset() {
        cache.clear();
    }

    public SPIRVInstalledCode getInstalledCode(String id, String entryPoint) {
        return cache.get(id + "-" + entryPoint);
    }

    public abstract SPIRVInstalledCode installSource(TaskMetaData meta, String id, String entryPoint, byte[] code);

    public abstract SPIRVInstalledCode installSPIRVBinary(String id, String entryPoint, byte[] targetCode);
}
