package uk.ac.manchester.tornado.drivers.spirv;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVInstalledCode;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVLevelZeroInstalledCode;
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
import uk.ac.manchester.tornado.drivers.spirv.levelzero.utils.LevelZeroUtils;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class SPIRVLevelZeroCodeCache extends SPIRVCodeCache {

    public SPIRVLevelZeroCodeCache(SPIRVDeviceContext deviceContext) {
        super(deviceContext);
    }

    private static void writeBufferToFile(ByteBuffer buffer, String filepath) {
        buffer.flip();
        File out = new File(filepath);
        try {
            FileChannel channel = new FileOutputStream(out, false).getChannel();
            channel.write(buffer);
            channel.close();
        } catch (IOException e) {
            System.err.println("IO exception: " + e.getMessage());
        }
    }

    @Override
    public SPIRVInstalledCode installSPIRVBinary(TaskMetaData meta, String id, String entryPoint, byte[] code) {
        ByteBuffer buffer = ByteBuffer.allocate(code.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(code);
        String tempDirectory = System.getProperty("java.io.tmpdir");
        String spirvTempDirectory = tempDirectory + "/tornadoVM-spirv";
        Path path = Paths.get(spirvTempDirectory);
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new TornadoBailoutRuntimeException("Error - Exception when creating the temp directory for SPIR-V");
        }
        long timeStamp = System.nanoTime();
        String file = spirvTempDirectory + "/" + timeStamp + "-" + id + entryPoint + ".spv";
        if (Tornado.DEBUG) {
            System.out.println("SPIRV-File : " + file);
        }
        writeBufferToFile(buffer, file);
        return installSPIRVBinary(meta, id, entryPoint, file);
    }

    private void checkBinaryFileExists(String pathToFile) {
        final Path pathToSPIRVBin = Paths.get(pathToFile);
        if (!pathToSPIRVBin.toFile().exists()) {
            throw new RuntimeException("Binary File does not exist");
        }
    }

    @Override
    public SPIRVInstalledCode installSPIRVBinary(TaskMetaData meta, String id, String entryPoint, String pathToFile) {
        ZeModuleHandle module = new ZeModuleHandle();
        ZeModuleDesc moduleDesc = new ZeModuleDesc();
        ZeBuildLogHandle buildLog = new ZeBuildLogHandle();
        moduleDesc.setFormat(ZeModuleFormat.ZE_MODULE_FORMAT_IL_SPIRV);
        moduleDesc.setBuildFlags("");

        checkBinaryFileExists(pathToFile);

        SPIRVContext spirvContext = deviceContext.getSpirvContext();
        SPIRVLevelZeroContext levelZeroContext = (SPIRVLevelZeroContext) spirvContext;
        LevelZeroContext context = levelZeroContext.getLevelZeroContext();

        SPIRVDevice spirvDevice = deviceContext.getDevice();
        SPIRVLevelZeroDevice levelZeroDevice = (SPIRVLevelZeroDevice) spirvDevice;
        LevelZeroDevice device = levelZeroDevice.getDevice();

        int result = context.zeModuleCreate(context.getDefaultContextPtr(), device.getDeviceHandlerPtr(), moduleDesc, module, buildLog, pathToFile);
        LevelZeroUtils.errorLog("zeModuleCreate", result);

        if (result != ZeResult.ZE_RESULT_SUCCESS) {
            // Print Logs
            int[] sizeLog = new int[1];
            String[] errorMessage = new String[1];
            result = context.zeModuleBuildLogGetString(buildLog, sizeLog, errorMessage);
            LevelZeroUtils.errorLog("zeModuleBuildLogGetString", result);
            System.out.println("----------------");
            System.out.println("SPIR-V Kernel Errors from LevelZero:");
            System.out.println(errorMessage[0]);
            System.out.println("----------------");
            throw new TornadoBailoutRuntimeException("[Build SPIR-V ERROR]" + errorMessage[0]);
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
        LevelZeroKernel levelZeroKernel = new LevelZeroKernel(kernelDesc, kernel, levelZeroModule);

        SPIRVModule spirvModule = new SPIRVLevelZeroModule(levelZeroModule, levelZeroKernel, entryPoint);
        SPIRVInstalledCode installedCode = new SPIRVLevelZeroInstalledCode(id, spirvModule, deviceContext);

        // Install module in the code cache
        cache.put(id + "-" + entryPoint, installedCode);
        return installedCode;
    }
}
