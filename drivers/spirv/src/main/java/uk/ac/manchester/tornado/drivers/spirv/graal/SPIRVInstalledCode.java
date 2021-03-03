package uk.ac.manchester.tornado.drivers.spirv.graal;

import jdk.vm.ci.code.InstalledCode;
import uk.ac.manchester.tornado.api.mm.ObjectBuffer;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.runtime.common.CallStack;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class SPIRVInstalledCode extends InstalledCode implements TornadoInstalledCode {

    private SPIRVDeviceContext deviceContext;

    public SPIRVInstalledCode(String name, SPIRVDeviceContext deviceContext) {
        super(name);
        this.deviceContext = deviceContext;
    }

    @Override
    public int launchWithDependencies(CallStack stack, ObjectBuffer atomicSpace, TaskMetaData meta, long batchThreads, int[] waitEvents) {
        return 0;
    }

    @Override
    public int launchWithoutDependencies(CallStack stack, ObjectBuffer atomicSpace, TaskMetaData meta, long batchThreads) {
        return 0;
    }
}
