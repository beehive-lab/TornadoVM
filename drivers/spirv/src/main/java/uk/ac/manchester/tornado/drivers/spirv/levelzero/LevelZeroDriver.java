/*
 * MIT License
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package uk.ac.manchester.tornado.drivers.spirv.levelzero;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstraction of a LevelZero Driver. A LevelZero driver is composed of one or
 * more physical devices.
 */
public class LevelZeroDriver {

    /**
     * Table to keep a mapping between a driver handler and all devices that belong
     * that this specific driver.
     */
    private Map<ZeDriverHandle, ArrayList<LevelZeroDevice>> architectureMap;

    /**
     * Table to keep a mapping between the driver handler and pointers to a device
     * handler.
     */
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
     * {@link ZeResult.ZE_RESULT_ERROR_UNINITIALIZED}
     * 
     * Only one instance of each driver will be initialized per process.
     * 
     * This function is thread-safe for scenarios where multiple libraries may
     * initialize the driver(s) simultaneously.
     * 
     * @return int An error code value:
     * 
     *         <code>
     *              ZeResult.ZE_RESULT_SUCCESS  
     *              ZeResult.ZE_RESULT_ERROR_UNINITIALIZED 
     *              ZeResult.ZE_RESULT_ERROR_DEVICE_LOST 
     *              ZeResult.ZE_RESULT_ERROR_INVALID_ENUMERATION 
     *                 + `0x3 < flags` 
     *              ZeResult.ZE_RESULT_ERROR_OUT_OF_HOST_MEMORY
     *           </code>
     * 
     * @param init
     *            Flag: {@link ZeInitFlag}
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
     *              ZE_RESULT_SUCCESS
     *              ZE_RESULT_ERROR_UNINITIALIZED
     *              ZE_RESULT_ERROR_DEVICE_LOST
     *              ZE_RESULT_ERROR_INVALID_NULL_POINTER
     *                  + `nullptr == pCount`
     *         </code>
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

    /**
     * Retrieves driver instances
     * 
     * <ul>
     * <li>A driver represents a collection of physical devices.</li>
     * <li>Multiple calls to this function will return identical driver handles, in
     * the same order.</li>
     * <li>The application may pass nullptr for pDrivers when only querying the
     * number of drivers.</li>
     * <li>The application may call this function from simultaneous threads.</li>
     * <li>The implementation of this function should be lock-free.</li>
     * </ul>
     * 
     * Similar to the OpenCL call `clGetPlatformIDs`
     *
     * @return int with an error code:
     * 
     *         <code>
     *         {@link ZeResult.ZE_RESULT_SUCCESS} 
     *         {@link ZeResult.ZE_RESULT_ERROR_UNINITIALIZED}
     *         {@link ZeResult.ZE_RESULT_ERROR_DEVICE_LOST}
     *         {@link ZeResult.ZE_RESULT_ERROR_INVALID_NULL_POINTER}: if driverCount is null.*         
     *       </code>
     * @param driverCount
     *            array with driver count
     * @param driverHandler
     *            Driver Handler object {@link ZeDriverHandle}
     */
    public int zeDriverGet(int[] driverCount, ZeDriverHandle driverHandler) {
        long[] driverNativePointers = driverHandler == null ? null : driverHandler.getZe_driver_handle_t_ptr();
        int result = zeDriverGet_native(driverCount, driverNativePointers);
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

    /**
     * Retrieves properties of the driver.
     * 
     * <ul>
     * <li>The application may call this function from simultaneous threads.</li>
     * <li>The implementation of this function should be lock-free.</li>
     * </ul>
     * 
     * This call is similar to clGetPlatformInfo from OpenCL.
     * 
     * @param driverHandler
     *            {@link ZeDriverHandle}
     * @param indexDriver
     *            Index from the Level Zero Platform to query
     * @param driverProperties
     *            {@link ZeDriverProperties}
     * 
     * @return an integer representing one of the following values:
     * 
     *         <code>
     *              ZE_RESULT_SUCCESS
     *              ZE_RESULT_ERROR_UNINITIALIZED
     *              ZE_RESULT_ERROR_DEVICE_LOST
     *              ZE_RESULT_ERROR_INVALID_NULL_HANDLE
     *                 + `nullptr == driverHandler`
     *             ZE_RESULT_ERROR_INVALID_NULL_POINTER
     *                + `nullptr == driverProperties`
     *        </code>
     * 
     */
    public int zeDriverGetProperties(ZeDriverHandle driverHandler, int indexDriver, ZeDriverProperties driverProperties) {
        int result = zeDriverGetProperties(driverHandler.getZe_driver_handle_t_ptr()[indexDriver], driverProperties);
        return result;
    }

    private native int zeDriverGetApiVersion(long driverHandler, ZeAPIVersion apiVersion);

    /**
     * Returns the API version supported by the specified driver.
     * 
     * <ul>
     * <li>The application may call this function from simultaneous threads.</li>
     * <li>The implementation of this function should be lock-free.</li>
     * </ul>
     * 
     * This call is similar to `clGetPlatformInfo` from OpenCL.
     * 
     * @param driverHandler
     *            {@link ZeDriverHandle}
     * @param indexDriver
     *            Driver Index.
     * @param apiVersion
     *            {@link ZeAPIVersion}
     * 
     * @return An error code:
     * 
     *         <code>
     *              ZE_RESULT_SUCCESS
     *              ZE_RESULT_ERROR_UNINITIALIZED
     *              ZE_RESULT_ERROR_DEVICE_LOST
     *              ZE_RESULT_ERROR_INVALID_NULL_HANDLE
     *                 + `nullptr == driverHandler`
     *             ZE_RESULT_ERROR_INVALID_NULL_POINTER
     *                + `nullptr == driverProperties`
     *        </code>
     */
    public int zeDriverGetApiVersion(ZeDriverHandle driverHandler, int indexDriver, ZeAPIVersion apiVersion) {
        int result = zeDriverGetApiVersion(driverHandler.getZe_driver_handle_t_ptr()[indexDriver], apiVersion);
        return result;
    }

    private native int zeContextDestroy(long contextHandlerPtr);

    /**
     * Destroys a context.
     * 
     * <ul>
     * <li>The application must ensure the device is not currently referencing the
     * context before it is deleted.</li>
     * <li>The implementation of this function may immediately free all Host and
     * Device allocations associated with this context.</li>
     * <li>The application must **not** call this function from simultaneous threads
     * with the same context handle.</li>
     * <li>The implementation of this function must be thread-safe.</li>
     * </ul>
     * 
     * @param context
     *            {@link LevelZeroContext}
     * 
     * @returns An error code:
     * 
     *          <code>
     *              ZE_RESULT_SUCCESS
     *              ZE_RESULT_ERROR_UNINITIALIZED
     *              ZE_RESULT_ERROR_DEVICE_LOST
     *              ZE_RESULT_ERROR_INVALID_NULL_HANDLE
     *                 + `nullptr == context`
     *             ZE_RESULT_ERROR_HANDLE_OBJECT_IN_USE
     *          </code>
     */
    public int zeContextDestroy(LevelZeroContext context) {
        int result = zeContextDestroy(context.getDefaultContextPtr());
        context.initPtr();
        return result;
    }
}
