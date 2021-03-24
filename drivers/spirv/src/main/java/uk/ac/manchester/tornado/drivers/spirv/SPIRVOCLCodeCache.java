package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVInstalledCode;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class SPIRVOCLCodeCache extends SPIRVCodeCache {

    public SPIRVOCLCodeCache(SPIRVDeviceContext deviceContext) {
        super(deviceContext);
    }

    @Override
    public SPIRVInstalledCode installSPIRVBinary(TaskMetaData meta, String id, String entryPoint, String pathToFile) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public SPIRVInstalledCode installSPIRVBinary(TaskMetaData meta, String id, String entryPoint, byte[] pathToFile) {
        throw new RuntimeException("Unimplemented");
    }

}
