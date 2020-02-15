package uk.ac.manchester.tornado.drivers.cuda.graal;

import jdk.vm.ci.code.InstalledCode;
import uk.ac.manchester.tornado.drivers.cuda.CUDADeviceContext;
import uk.ac.manchester.tornado.drivers.cuda.CUDAModule;
import uk.ac.manchester.tornado.runtime.common.CallStack;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class PTXInstalledCode extends InstalledCode implements TornadoInstalledCode {
    private CUDAModule module;
    private CUDADeviceContext deviceContext;

    public PTXInstalledCode(String name, CUDAModule module, CUDADeviceContext deviceContext) {
        super(name);
        this.module = module;
        this.deviceContext = deviceContext;
    }

    @Override public int launchWithDeps(CallStack stack, TaskMetaData meta, long batchThreads, int[] waitEvents) {
        return 0;
    }

    @Override public int launchWithoutDeps(CallStack stack, TaskMetaData meta, long batchThreads) {
        return deviceContext.enqueueKernelLaunch(module, name, stack, meta, batchThreads);
    }
}
