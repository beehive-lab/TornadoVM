package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroKernel;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroModule;

public class SPIRVLevelZeroModule implements SPIRVModule {

    private LevelZeroModule levelZeroModule;
    private LevelZeroKernel kernel;
    private String entryPoint;

    public SPIRVLevelZeroModule(LevelZeroModule levelZeroModule, LevelZeroKernel kernel, String entryPoint) {
        this.levelZeroModule = levelZeroModule;
        this.kernel = kernel;
        this.entryPoint = entryPoint;
    }

    public LevelZeroModule getLevelZeroModule() {
        return levelZeroModule;
    }

    public LevelZeroKernel getKernel() {
        return kernel;
    }

    public String getEntryPoint() {
        return entryPoint;
    }

}
