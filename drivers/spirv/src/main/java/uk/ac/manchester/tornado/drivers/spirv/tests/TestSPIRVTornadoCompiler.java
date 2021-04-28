package uk.ac.manchester.tornado.drivers.spirv.tests;

import uk.ac.manchester.tornado.drivers.spirv.SPIRVCodeCache;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVContext;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVLevelZeroCodeCache;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVPlatform;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVProxy;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVInstalledCode;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResult;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleMetaData;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class TestSPIRVTornadoCompiler {

    public static void main(String[] args) {

        SPIRVPlatform platform = SPIRVProxy.getPlatform(0);
        SPIRVContext context = platform.createContext();
        SPIRVDeviceContext deviceContext = context.getDeviceContext(0);
        SPIRVCodeCache codeCache = new SPIRVLevelZeroCodeCache(deviceContext);

        ScheduleMetaData scheduleMetaData = new ScheduleMetaData("SPIRV-Backend");
        TaskMetaData task = new TaskMetaData(scheduleMetaData, "saxpy");
        new SPIRVCompilationResult("saxpy", "saxpy", task);

        // byte[] binary = ...
        byte[] binary = new byte[100];
        SPIRVInstalledCode code = codeCache.installSPIRVBinary(task, "saxpy", "saxpy", binary);
        String generatedCode = code.getGeneratedSourceCode();

        if (TornadoOptions.PRINT_SOURCE) {
            System.out.println(generatedCode);
        }
    }
}
