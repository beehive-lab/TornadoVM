package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVInstalledCode;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

import java.util.concurrent.ConcurrentHashMap;

public class SPIRVCodeCache {

    private final SPIRVDeviceContext deviceContext;
    private final ConcurrentHashMap<String, SPIRVInstalledCode> cache;

    public SPIRVCodeCache(SPIRVDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
        cache = new ConcurrentHashMap<>();
    }

    public SPIRVInstalledCode installSPIRVBinary(String id, String entryPoint, byte[] targetCode) {
        if (!cache.containsKey(id)) {
            // FIXME <TODO> Print SPIRV? via Disassembler?
            SPIRVInstalledCode installedCode = new SPIRVInstalledCode(id, deviceContext);
            cache.put(id, installedCode);
            return installedCode;
        }
        return cache.get(id);
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

}
