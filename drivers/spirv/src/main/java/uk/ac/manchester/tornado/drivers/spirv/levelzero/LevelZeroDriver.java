package uk.ac.manchester.tornado.drivers.spirv.levelzero;

import java.util.ArrayList;
import java.util.HashMap;

public class LevelZeroDriver {

    private HashMap<ZeDriverHandle, ArrayList<LevelZeroDevice>> architectureMap;
    private HashMap<ZeDriverHandle, ZeDevicesHandle> architecturePointers;

    static {
        // Use -Djava.library.path=./levelZeroLib/build/
        System.loadLibrary("tornado-levelzero");
    }

    public LevelZeroDriver() {
        architectureMap = new HashMap<>();
        architecturePointers = new HashMap<>();
    }

    public native int zeInit(int init);

    private native int zeDriverGet_native(int[] driverCount, long[] ptrPhDrivers);

    private native int zeDeviceGet(long driverHandle, int[] deviceCount, ZeDevicesHandle deviceHandlerPtr);

    private native int zeDriverGetProperties(long driverHandler, ZeDriverProperties driverProperties);

    public int zeDriverGet(int[] driverCount, ZeDriverHandle driverHandler) {
        long[] pointers = driverHandler == null? null: driverHandler.getZe_driver_handle_t_ptr();
        int result =  zeDriverGet_native(driverCount, pointers);
        architectureMap.put(driverHandler, null);
        return result;
    }

    private void updateArchitecture(int numDevices, ZeDriverHandle driverHandler, ZeDevicesHandle deviceHandle) {
        ArrayList<LevelZeroDevice> devices = new ArrayList<>();
        for (int i = 0; i < numDevices; i++) {
            LevelZeroDevice device = new LevelZeroDevice(driverHandler, i, deviceHandle.getDevicePtrAtIndex(i));
            devices.add(device);
        }
        architecturePointers.put(driverHandler, deviceHandle);
        architectureMap.put(driverHandler, devices);
    }

    public int zeDeviceGet(ZeDriverHandle driverHandler, int indexDriver, int[] deviceCount, ZeDevicesHandle deviceHandle) {
        if (!architectureMap.containsKey(driverHandler)) {
            throw  new RuntimeException("Driver not initialized");
        }
        int result = zeDeviceGet(driverHandler.getZe_driver_handle_t_ptr()[indexDriver], deviceCount, deviceHandle);
        if (deviceHandle != null) {
            updateArchitecture(deviceCount[0], driverHandler, deviceHandle);
        }
        return result;
    }

    public LevelZeroDevice getDevice(ZeDriverHandle driverHandler, int deviceIndex) {
        if (architectureMap.containsKey(driverHandler)) {
            return architectureMap.get(driverHandler).get(deviceIndex);
        }
        return null;
    }

    public int zeDriverGetProperties(ZeDriverHandle driverHandler, int indexDriver, ZeDriverProperties driverProperties) {
        int result = zeDriverGetProperties(driverHandler.getZe_driver_handle_t_ptr()[indexDriver], driverProperties);
        return result;
    }

    native int zeDriverGetApiVersion(long driverHandler, ZeAPIVersion apiVersion);

    public int zeDriverGetApiVersion(ZeDriverHandle driverHandler, int indexDriver, ZeAPIVersion apiVersion) {
        int result = zeDriverGetApiVersion(driverHandler.getZe_driver_handle_t_ptr()[indexDriver], apiVersion);
        return result;
    }

    native int zeContextDestroy(long contextHandlerPtr);

    public int zeContextDestroy(LevelZeroContext context) {
        int result = zeContextDestroy(context.getDefaultContextPtr());
        context.initPtr();
        return result;
    }
}
