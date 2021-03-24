package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVInstalledCode;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class SPIRVLevelZeroCodeCache extends SPIRVCodeCache {

    public SPIRVLevelZeroCodeCache(SPIRVDeviceContext deviceContext) {
        super(deviceContext);
    }

    @Override
    public SPIRVInstalledCode installSPIRVBinary(String id, String entryPoint, byte[] targetCode) {
        if (!cache.containsKey(id)) {
            // FIXME <TODO> Print SPIRV? via Disassembler?
            SPIRVInstalledCode installedCode = new SPIRVInstalledCode(id, deviceContext);
            cache.put(id, installedCode);
            return installedCode;
        }
        return cache.get(id);
    }

    @Override
    public SPIRVInstalledCode installSource(TaskMetaData meta, String id, String entryPoint, byte[] code) {
        System.out.println("Compile & Install SPIRV-Binary ");
        SPIRVInstalledCode installedCode = new SPIRVInstalledCode(id, deviceContext);
        cache.put(id + "-" + entryPoint, installedCode);
        return installedCode;
    }
}
