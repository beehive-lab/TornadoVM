package uk.ac.manchester.tornado.drivers.spirv.levelzero;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LevelZeroDriver {

    private Map<ZeDriverHandle, ArrayList<LevelZeroDevice>> architectureMap;
    private Map<ZeDriverHandle, ZeDevicesHandle> architecturePointers;

    static {
        // Use -Djava.library.path=./levelZeroLib/build/
        System.loadLibrary("tornado-levelzero");
    }

    public LevelZeroDriver() {
        architectureMap = new HashMap<>();
        architecturePointers = new HashMap<>();
    }

    /**
     * 
     * Initialize the 'oneAPI' driver(s)
     * 
     * This function must be called before any other API function. - If this
     * function is not called then all other functions will return
     * ::ZE_RESULT_ERROR_UNINITIALIZED.
     * 
     * Only one instance of each driver will be initialized per process. - This
     * function is thread-safe for scenarios where multiple libraries may initialize
     * the driver(s) simultaneously.
     * 
     * @returns An error code value:
     * 
     *          <code>
     *          ::ZE_RESULT_SUCCESS - 
     *          ::ZE_RESULT_ERROR_UNINITIALIZED -
     *          ::ZE_RESULT_ERROR_DEVICE_LOST -
     *          ::ZE_RESULT_ERROR_INVALID_ENUMERATION + `0x1 < flags` -
     *          ::ZE_RESULT_ERROR_OUT_OF_HOST_MEMORY
     *           </code>
     * 
     * @param init
     *            Flag
     * @return
     */
    public native int zeInit(int init);

    private native int zeDriverGet_native(int[] driverCount, long[] ptrPhDrivers);

    /**
     * Retrieves driver instances
     * 
     * A driver represents a collection of physical devices. Multiple calls to this
     * function will return identical driver handles, in the same order. The
     * application may pass nullptr for pDrivers when only querying the number of
     * drivers. The application may call this function from simultaneous threads.
     * The implementation of this function should be lock-free.
     * 
     * @remarks This function is similar to the clGetPlatformIDs from OpenCL.
     * 
     * @return A status/result value:
     * 
     *         <code>
     *       ZE_RESULT_SUCCESS
     *       ZE_RESULT_ERROR_UNINITIALIZED
     *       ZE_RESULT_ERROR_DEVICE_LOST
     *       ZE_RESULT_ERROR_INVALID_NULL_POINTER
     *          + `nullptr == pCount`
     *      </code>
     * 
     * @param driverHandle
     *            Driver Handler
     * @param deviceCount
     *            Array with the device count
     * @param deviceHandlerPtr
     *            Device Handler Native Pointers. Pass null to obtain the number of
     *            devices within the driver.
     */
    private native int zeDeviceGet(long driverHandle, int[] deviceCount, ZeDevicesHandle deviceHandlerPtr);

    private native int zeDriverGetProperties(long driverHandler, ZeDriverProperties driverProperties);

    public int zeDriverGet(int[] driverCount, ZeDriverHandle driverHandler) {
        long[] pointers = driverHandler == null ? null : driverHandler.getZe_driver_handle_t_ptr();
        int result = zeDriverGet_native(driverCount, pointers);
        architectureMap.put(driverHandler, null);
        return result;
    }

    private void updateArchitecture(int numDevices, ZeDriverHandle driverHandler, ZeDevicesHandle deviceHandle) {
        ArrayList<LevelZeroDevice> devices = new ArrayList<>();
        for (int i = 0; i < numDevices; i++) {
            LevelZeroDevice device = new LevelZeroDevice(this, driverHandler, i, deviceHandle.getDevicePtrAtIndex(i));
            devices.add(device);
        }
        architecturePointers.put(driverHandler, deviceHandle);
        architectureMap.put(driverHandler, devices);
    }

    public int zeDeviceGet(ZeDriverHandle driverHandler, int indexDriver, int[] deviceCount, ZeDevicesHandle deviceHandle) {
        if (!architectureMap.containsKey(driverHandler)) {
            throw new RuntimeException("Driver not initialized");
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
