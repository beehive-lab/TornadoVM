package uk.ac.manchester.tornado.drivers.spirv.graal;

import uk.ac.manchester.tornado.api.mm.ObjectBuffer;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVModule;
import uk.ac.manchester.tornado.runtime.common.CallStack;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class SPIRVOCLInstalledCode extends SPIRVInstalledCode {

    public SPIRVOCLInstalledCode(String name, SPIRVModule spirvModule, SPIRVDeviceContext deviceContext) {
        super(name, spirvModule, deviceContext);
    }

    @Override
    public int launchWithDependencies(CallStack stack, ObjectBuffer atomicSpace, TaskMetaData meta, long batchThreads, int[] waitEvents) {
        throw new RuntimeException("Not implemented yet");
    }

    @Override
    public int launchWithoutDependencies(CallStack stack, ObjectBuffer atomicSpace, TaskMetaData meta, long batchThreads) {
        throw new RuntimeException("Not implemented yet");
    }
}
