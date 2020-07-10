package uk.ac.manchester.tornado.drivers.ptx.graal;

import jdk.vm.ci.code.InstalledCode;
import uk.ac.manchester.tornado.drivers.ptx.PTXDeviceContext;
import uk.ac.manchester.tornado.drivers.ptx.PTXModule;
import uk.ac.manchester.tornado.runtime.common.CallStack;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

public class PTXInstalledCode extends InstalledCode implements TornadoInstalledCode {
    private PTXModule module;
    private PTXDeviceContext deviceContext;

    public PTXInstalledCode(String name, PTXModule module, PTXDeviceContext deviceContext) {
        super(name);
        this.module = module;
        this.deviceContext = deviceContext;
    }

    @Override
    public int launchWithDependencies(CallStack stack, TaskMetaData meta, long batchThreads, int[] waitEvents) {
        unimplemented("launch with deps");
        return 0;
    }

    @Override
    public int launchWithoutDependencies(CallStack stack, TaskMetaData meta, long batchThreads) {
        return deviceContext.enqueueKernelLaunch(module, stack, batchThreads);
    }

    public String getGeneratedSourceCode() {
        return new String(module.getSource());
    }
}
