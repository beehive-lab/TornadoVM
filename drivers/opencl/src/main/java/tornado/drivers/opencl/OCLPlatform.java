/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package tornado.drivers.opencl;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import tornado.drivers.opencl.enums.OCLDeviceType;
import tornado.drivers.opencl.enums.OCLPlatformInfo;
import tornado.drivers.opencl.exceptions.OCLException;
import uk.ac.manchester.tornado.common.TornadoLogger;

public class OCLPlatform extends TornadoLogger {

    private final int index;
    private final long id;
    private final List<OCLDevice> devices;
    private final Set<OCLContext> contexts;

    public OCLPlatform(int index, long id) {
        this.index = index;
        this.id = id;
        this.devices = new ArrayList<>();
        this.contexts = new HashSet<>();

        final int deviceCount = clGetDeviceCount(id,
                OCLDeviceType.CL_DEVICE_TYPE_ALL.getValue());

        final long[] ids = new long[deviceCount];
        clGetDeviceIDs(id, OCLDeviceType.CL_DEVICE_TYPE_ALL.getValue(), ids);
        for (int i = 0; i < ids.length; i++) {
            devices.add(new OCLDevice(i, ids[i]));
        }

    }

    native static String clGetPlatformInfo(long id, int info);

    native static int clGetDeviceCount(long id, long type);

    native static int clGetDeviceIDs(long id, long type, long[] devices);

    native static long clCreateContext(long platform, long[] devices)
            throws OCLException;

    public OCLContext createContext() {
        OCLContext result = null;
        final LongBuffer deviceIds = LongBuffer.allocate(devices.size());
        for (OCLDevice device : devices) {
            deviceIds.put(device.getId());
        }

        try {
            long contextId = clCreateContext(id, deviceIds.array());
            result = new OCLContext(this, contextId, devices);
            contexts.add(result);
        } catch (OCLException e) {
            error(e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    public void cleanup() {
        for (OCLContext context : contexts) {
            context.cleanup();
        }
    }

    public String getProfile() {
        return clGetPlatformInfo(id,
                OCLPlatformInfo.CL_PLATFORM_PROFILE.getValue());
    }

    public String getVersion() {
        return clGetPlatformInfo(id,
                OCLPlatformInfo.CL_PLATFORM_VERSION.getValue());
    }

    public String getName() {
        return clGetPlatformInfo(id,
                OCLPlatformInfo.CL_PLATFORM_NAME.getValue());
    }

    public String getVendor() {
        return clGetPlatformInfo(id,
                OCLPlatformInfo.CL_PLATFORM_VENDOR.getValue());
    }

    public String getExtensions() {
        return clGetPlatformInfo(id,
                OCLPlatformInfo.CL_PLATFORM_EXTENSIONS.getValue());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("name=%s, num. devices=%d, ", getName(),
                devices.size()));
        sb.append(String.format("version=%s", getVersion()));

        return sb.toString().trim();
    }

    public int getIndex() {
        return index;
    }

}
