package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroContext;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDriver;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeContextDesc;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDevicesHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDriverHandle;

import java.util.ArrayList;
import java.util.List;

public class SPIRVLevelZeroPlatform implements SPIRVPlatform {

    private long driverPointer;
    private int indexDriver;
    private LevelZeroDriver driver;
    private ZeDriverHandle driversHandler;
    private int deviceCount = -1;
    private List<LevelZeroDevice> devices;
    private List<SPIRVDevice> spirvDevices;

    SPIRVContext spirvContext;
    LevelZeroContext levelZeroContext;

    public SPIRVLevelZeroPlatform(LevelZeroDriver driver, ZeDriverHandle driversHandler, int indexDriver, long levelZeroDriverPointer) {
        this.driver = driver;
        this.driversHandler = driversHandler;
        this.driverPointer = levelZeroDriverPointer;
        this.indexDriver = indexDriver;
        initDevices();
    }

    private void initDevices() {
        // Get number of devices in a driver
        int[] deviceCountArray = new int[1];
        driver.zeDeviceGet(driversHandler, indexDriver, deviceCountArray, null);
        deviceCount = deviceCountArray[0];

        // Instantiate a device Handler
        ZeDevicesHandle deviceHandler = new ZeDevicesHandle(deviceCount);
        driver.zeDeviceGet(driversHandler, indexDriver, deviceCountArray, deviceHandler);

        devices = new ArrayList<>();
        spirvDevices = new ArrayList<>();

        for (int i = 0; i < deviceCount; i++) {
            LevelZeroDevice device = driver.getDevice(driversHandler, i);
            devices.add(device);
            SPIRVDevice spirvDevice = new SPIRVLevelZeroDevice(indexDriver, i, devices.get(i));
            spirvDevices.add(spirvDevice);
        }
    }

    @Override
    public int getNumDevices() {
        if (deviceCount == -1) {
            initDevices();
            return deviceCount;
        }
        return deviceCount;
    }

    @Override
    public SPIRVDevice getDevice(int deviceIndex) {
        return spirvDevices.get(deviceIndex);
    }

    @Override
    public SPIRVContext createContext() {
        ZeContextDesc contextDescription = new ZeContextDesc();
        levelZeroContext = new LevelZeroContext(driversHandler, contextDescription);
        levelZeroContext.zeContextCreate(driversHandler.getZe_driver_handle_t_ptr()[indexDriver], indexDriver);
        spirvContext = new SPIRVLevelZeroContext(this, spirvDevices, levelZeroContext);
        return spirvContext;
    }

}
