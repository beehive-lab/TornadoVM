package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDriver;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDriverHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeInitFlag;

import java.util.ArrayList;
import java.util.List;

public class SPIRVLevelZeroDriver implements SPIRVDispatcher {

    private LevelZeroDriver driver;
    private ZeDriverHandle driversHandler;
    private List<SPIRVPlatform> spirvPlatforms;

    @Override
    public void init() {
        driver = new LevelZeroDriver();
        driver.zeInit(ZeInitFlag.ZE_INIT_FLAG_GPU_ONLY);

        int[] numDrivers = new int[1];
        driver.zeDriverGet(numDrivers, null);
        spirvPlatforms = new ArrayList<>();

        driversHandler = new ZeDriverHandle(numDrivers[0]);
        driver.zeDriverGet(numDrivers, driversHandler);
        for (int i = 0; i < numDrivers[0]; i++) {
            SPIRVPlatform platform = new SPIRVLevelZeroPlatform(driver, driversHandler, i, driversHandler.getZe_driver_handle_t_ptr()[i]);
            spirvPlatforms.add(platform);
        }
    }

    @Override
    public int getNumPlatforms() {
        return driversHandler.getNumDrivers();
    }

    @Override
    public SPIRVPlatform getPlatform(int index) {
        return spirvPlatforms.get(index);
    }

}
