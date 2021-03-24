package uk.ac.manchester.tornado.drivers.spirv;

import java.nio.file.Path;
import java.nio.file.Paths;

import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVInstalledCode;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroBinaryModule;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroContext;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroKernel;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroModule;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeBuildLogHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeKernelDesc;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeKernelHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeModuleDesc;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeModuleFormat;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeModuleHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeResult;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.samples.LevelZeroUtils;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class SPIRVLevelZeroCodeCache extends SPIRVCodeCache {

    public SPIRVLevelZeroCodeCache(SPIRVDeviceContext deviceContext) {
        super(deviceContext);
    }

    @Override
    public SPIRVInstalledCode installSPIRVBinary(TaskMetaData meta, String id, String entryPoint, byte[] code) {
        return null;
    }

    @Override
    public SPIRVInstalledCode installSPIRVBinary(TaskMetaData meta, String id, String entryPoint, String pathToFile) {
        ZeModuleHandle module = new ZeModuleHandle();
        ZeModuleDesc moduleDesc = new ZeModuleDesc();
        ZeBuildLogHandle buildLog = new ZeBuildLogHandle();
        moduleDesc.setFormat(ZeModuleFormat.ZE_MODULE_FORMAT_IL_SPIRV);
        moduleDesc.setBuildFlags("");

        final Path pathToSPIRVBin = Paths.get(pathToFile);
        if (!pathToSPIRVBin.toFile().exists()) {
            throw new RuntimeException("Binary File does not exist");
        }

        LevelZeroBinaryModule binaryModule = new LevelZeroBinaryModule(pathToFile);
        int result = binaryModule.readBinary();
        LevelZeroUtils.errorLog("readBinary", result);

        SPIRVContext spirvContext = deviceContext.getSpirvContext();
        SPIRVLevelZeroContext levelZeroContext = (SPIRVLevelZeroContext) spirvContext;
        LevelZeroContext context = levelZeroContext.getLevelZeroContext();

        SPIRVDevice spirvDevice = deviceContext.getDevice();
        SPIRVLevelZeroDevice levelZeroDevice = (SPIRVLevelZeroDevice) spirvDevice;
        LevelZeroDevice device = levelZeroDevice.getDevice();

        result = context.zeModuleCreate(context.getDefaultContextPtr(), device.getDeviceHandlerPtr(), binaryModule, moduleDesc, module, buildLog);
        LevelZeroUtils.errorLog("zeModuleCreate", result);

        if (result != ZeResult.ZE_RESULT_SUCCESS) {
            // Print Logs
            int[] sizeLog = new int[1];
            String errorMessage = "";
            result = context.zeModuleBuildLogGetString(buildLog, sizeLog, errorMessage);
            System.out.println("LOGS::: " + sizeLog[0] + "  -- " + errorMessage);
            LevelZeroUtils.errorLog("zeModuleBuildLogGetString", result);
        }

        // Create Module Object
        LevelZeroModule levelZeroModule = new LevelZeroModule(module, moduleDesc, buildLog);

        // Destroy Log
        result = levelZeroModule.zeModuleBuildLogDestroy(buildLog);
        LevelZeroUtils.errorLog("zeModuleBuildLogDestroy", result);

        ZeKernelDesc kernelDesc = new ZeKernelDesc();
        ZeKernelHandle kernel = new ZeKernelHandle();
        System.out.println("Set entry point! : " + entryPoint);
        kernelDesc.setKernelName(entryPoint);
        result = levelZeroModule.zeKernelCreate(module.getPtrZeModuleHandle(), kernelDesc, kernel);
        LevelZeroUtils.errorLog("zeKernelCreate", result);

        // Create a Level Zero kernel Object
        LevelZeroKernel levelZeroKernel = new LevelZeroKernel(kernelDesc, kernel);

        SPIRVModule spirvModule = new SPIRVLevelZeroModule(levelZeroModule, levelZeroKernel, entryPoint, binaryModule);
        SPIRVInstalledCode installedCode = new SPIRVInstalledCode(id, spirvModule, deviceContext);

        // Install module in the code cache
        cache.put(id + "-" + entryPoint, installedCode);
        return installedCode;
    }
}
