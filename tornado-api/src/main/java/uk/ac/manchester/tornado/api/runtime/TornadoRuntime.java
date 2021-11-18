/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * GNU Classpath is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 * 
 * GNU Classpath is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING.  If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 * 
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module.  An independent module is a module which is not derived from
 * or based on this library.  If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so.  If you do not wish to do so, delete this
 * exception statement from your version.
 *
 */
package uk.ac.manchester.tornado.api.runtime;

import uk.ac.manchester.tornado.api.AbstractFactoryDevice;
import uk.ac.manchester.tornado.api.TornadoCI;
import uk.ac.manchester.tornado.api.TornadoRuntimeCI;
import uk.ac.manchester.tornado.api.common.TornadoDevice;

public class TornadoRuntime {

    private static TornadoRuntimeCI runtimeImpl;
    private static TornadoCI tornadoImpl;
    private static AbstractFactoryDevice device;

    static {
        init();
    }

    private static void init() {
        if (runtimeImpl == null) {
            runtimeImpl = TornadoAPIProvider.loadRuntime();
        }
        if (tornadoImpl == null) {
            tornadoImpl = TornadoAPIProvider.loadTornado();
        }
    }

    public static TornadoRuntimeCI getTornadoRuntime() {
        return runtimeImpl;
    }

    public static boolean isProfilerEnabled() {
        return runtimeImpl.isProfilerEnabled();
    }

    public void clearObjectState() {
        runtimeImpl.clearObjectState();
    }

    public static void setProperty(String key, String value) {
        tornadoImpl.setTornadoProperty(key, value);
    }

    public static String getProperty(String key, String value) {
        return tornadoImpl.getTornadoProperty(key, value);
    }

    public static String getProperty(String key) {
        return tornadoImpl.getTornadoProperty(key);
    }

    public static void loadSettings(String property) {
        tornadoImpl.loadTornadoSettings(property);
    }

    /**
     * Method used by SLAMBENCH-TornadoVM to access the device.
     * 
     * @param backendName
     *            Backend to be used
     * @param platformIndex
     *            OpenCL|SPIRV|PTX Platform Index
     * @param deviceIndex
     *            Device index within the platform.
     * @return an instance of a TornadoDevice per architecture.
     */
    @Deprecated
    public static TornadoDevice createDevice(String backendName, int platformIndex, int deviceIndex) {
        device = TornadoAPIProvider.loadDeviceImpl(backendName);
        return device.createDevice(platformIndex, deviceIndex);
    }
}
