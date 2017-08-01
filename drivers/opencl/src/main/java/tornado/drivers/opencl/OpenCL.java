/*
 * Copyright 2012 James Clarkson.
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
 */
package tornado.drivers.opencl;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import tornado.common.Tornado;
import tornado.common.exceptions.TornadoRuntimeException;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;

public class OpenCL {

    public static final String OPENCL_LIBRARY = "tornado-opencl";

    private static boolean initialised = false;

    private static final List<OCLPlatform> platforms = new ArrayList<>();

    public final static boolean DUMP_OPENCL_EVENTS = Boolean.parseBoolean(Tornado
            .getProperty("tornado.opencl.events.dump", "False"));

    public static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

    public static final int CL_TRUE = 1;
    public static final int CL_FALSE = 0;

    static {
        try {
            System.loadLibrary(OpenCL.OPENCL_LIBRARY);
        } catch (final UnsatisfiedLinkError e) {
            throw e;
        }

        try {
            initialise();
        } catch (final TornadoRuntimeException e) {
            e.printStackTrace();
        }

        // add a shutdown hook to free-up all OpenCL resources on VM exit
        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
                setName("OpenCL Cleanup");
//                if (DUMP_EVENTS)
//                    TornadoRuntime.getTornadoRuntime().
                OpenCL.cleanup();
            }

        });
    }

    public static void throwException(String message)
            throws TornadoRuntimeException {
        throw new TornadoRuntimeException(message);
    }

    native static boolean registerCallback();

    native static int clGetPlatformCount();

    native static int clGetPlatformIDs(long[] platformIds);

    public static void cleanup() {
        if (initialised) {
            for (final OCLPlatform platform : platforms) {
                platform.cleanup();
            }
        }

    }

    public static OCLPlatform getPlatform(int index) {
        return platforms.get(index);
    }

    public static int getNumPlatforms() {
        return platforms.size();
    }

    public static void initialise() throws TornadoRuntimeException {

        if (!initialised) {
            try {
                int numPlatforms = clGetPlatformCount();
                long[] ids = new long[numPlatforms];
                clGetPlatformIDs(ids);

                for (int i = 0; i < ids.length; i++) {
                    OCLPlatform platform = new OCLPlatform(i, ids[i]);
                    // Tornado.info("platform [%d]: %s",platform.toString());
                    platforms.add(platform);
                }

            } catch (final Exception exc) {
                exc.printStackTrace();
                throw new TornadoRuntimeException(
                        "Problem with OpenCL bindings");
            } catch (final Error err) {
                err.printStackTrace();
                throw new TornadoRuntimeException("Error with OpenCL bindings");
            }

            initialised = true;
        }
    }

    public static OCLDeviceMapping defaultDevice() {
        final int platformIndex = Integer.parseInt(Tornado.getProperty(
                "tornado.platform", "0"));
        final int deviceIndex = Integer.parseInt(Tornado.getProperty(
                "tornado.device", "0"));
        return new OCLDeviceMapping(platformIndex, deviceIndex);
    }

    public static List<OCLPlatform> platforms() {
        return platforms;
    }

    public static void main(String[] args) {

        if (args.length != 2) {
            System.out.println("usage: OpenCL <platform> <device>");
            System.out.println();

            for (int platformIndex = 0; platformIndex < platforms.size(); platformIndex++) {
                final OCLPlatform platform = platforms.get(platformIndex);
                System.out.printf("[%d]: platform: %s\n", platformIndex,
                        platform.getName());
                final OCLContext context = platform.createContext();
                for (int deviceIndex = 0; deviceIndex < context.getNumDevices(); deviceIndex++) {
                    System.out.printf("[%d:%d] device: %s\n", platformIndex,
                            deviceIndex,
                            context.createDeviceContext(deviceIndex)
                            .getDevice().getName());
                }
            }

        } else {

            final int platformIndex = Integer.parseInt(args[0]);
            final int deviceIndex = Integer.parseInt(args[1]);

            final OCLPlatform platform = platforms.get(platformIndex);
            final OCLDevice device = platform.createContext()
                    .createDeviceContext(deviceIndex).getDevice();

            System.out.println(device.toVerboseString());
        }
    }
}
