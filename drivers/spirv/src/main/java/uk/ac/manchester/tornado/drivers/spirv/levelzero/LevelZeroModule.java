package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class LevelZeroModule {

    private ZeModuleHandle module;
    private ZeModuleDesc moduleDesc;
    private ZeBuildLogHandle buildLog;

    public LevelZeroModule(ZeModuleHandle module, ZeModuleDesc moduleDesc, ZeBuildLogHandle buildLog) {
        this.module = module;
        this.moduleDesc = moduleDesc;
        this.buildLog = buildLog;
    }

    public ZeModuleHandle getModule() {
        return module;
    }

    public ZeModuleDesc getModuleDesc() {
        return moduleDesc;
    }

    public ZeBuildLogHandle getBuildLog() {
        return buildLog;
    }

    public native int zeModuleBuildLogDestroy(ZeBuildLogHandle buildLog);

    native int zeKernelCreate_native(long ptrZeModuleHandle, ZeKernelDesc kernelDesc, ZeKernelHandle kernel);

    public int zeKernelCreate(long ptrZeModuleHandle, ZeKernelDesc kernelDesc, ZeKernelHandle kernel) {
        int result = zeKernelCreate_native(ptrZeModuleHandle, kernelDesc, kernel);
        return result;
    }
}
