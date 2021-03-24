package uk.ac.manchester.tornado.drivers.spirv.graal;

import jdk.vm.ci.code.InstalledCode;
import uk.ac.manchester.tornado.api.mm.ObjectBuffer;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVModule;
import uk.ac.manchester.tornado.runtime.common.CallStack;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class SPIRVInstalledCode extends InstalledCode implements TornadoInstalledCode {

    private SPIRVDeviceContext deviceContext;
    private SPIRVModule spirvModule;

    public SPIRVInstalledCode(String name, SPIRVDeviceContext deviceContext) {
        super(name);
        this.deviceContext = deviceContext;
    }

    public SPIRVInstalledCode(String name, SPIRVModule spirvModule, SPIRVDeviceContext deviceContext) {
        super(name);
        this.deviceContext = deviceContext;
        this.spirvModule = spirvModule;
    }

    public SPIRVModule getSpirvModule() {
        return this.spirvModule;
    }

    public SPIRVDeviceContext getDeviceContext() {
        return this.deviceContext;
    }

    @Override
    public int launchWithDependencies(CallStack stack, ObjectBuffer atomicSpace, TaskMetaData meta, long batchThreads, int[] waitEvents) {
        return 0;
    }

    @Override
    public int launchWithoutDependencies(CallStack stack, ObjectBuffer atomicSpace, TaskMetaData meta, long batchThreads) {
        return 0;
    }

    public String getGeneratedSourceCode() {
        return " NOT IMPLEMENTED YET";
    }
}
