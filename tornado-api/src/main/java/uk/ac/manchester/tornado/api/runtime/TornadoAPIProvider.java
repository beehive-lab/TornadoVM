/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import uk.ac.manchester.tornado.api.AbstractFactoryDevice;
import uk.ac.manchester.tornado.api.AbstractTaskGraph;
import uk.ac.manchester.tornado.api.TornadoCI;
import uk.ac.manchester.tornado.api.TornadoRuntimeCI;
import uk.ac.manchester.tornado.api.exceptions.TornadoAPIException;

public class TornadoAPIProvider {

    public static AbstractTaskGraph loadScheduleRuntime(String name) {
        AbstractTaskGraph taskGraphImpl = null;
        try {
            String tornadoAPIImplementation = System.getProperty("tornado.load.api.implementation");
            Class<?> klass = Class.forName(tornadoAPIImplementation);
            Constructor<?> constructor = klass.getConstructor(String.class);
            taskGraphImpl = (AbstractTaskGraph) constructor.newInstance(name);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException | SecurityException | IllegalArgumentException | InvocationTargetException e) {
            throw new TornadoAPIException("[ERROR] Tornado API Implementation class not found", e);
        }
        return taskGraphImpl;
    }

    public static TornadoRuntimeCI loadRuntime() {
        TornadoRuntimeCI runtime = null;
        try {
            String tornadoRuntimeImplementation = System.getProperty("tornado.load.runtime.implementation");
            Class<?> klass = Class.forName(tornadoRuntimeImplementation);
            Method method = klass.getDeclaredMethod("getTornadoRuntime");
            runtime = (TornadoRuntimeCI) method.invoke(null);
        } catch (ClassNotFoundException | IllegalAccessException | NoSuchMethodException | SecurityException | IllegalArgumentException | InvocationTargetException e) {
            throw new TornadoAPIException("[ERROR] Tornado Runtime Implementation class not found", e);
        }
        return runtime;
    }

    public static TornadoCI loadTornado() {
        TornadoCI tornado = null;
        try {
            String tornadoImplementation = System.getProperty("tornado.load.tornado.implementation");
            Class<?> klass = Class.forName(tornadoImplementation);
            Constructor<?> constructor = klass.getConstructor();
            tornado = (TornadoCI) constructor.newInstance();
        } catch (ClassNotFoundException | IllegalAccessException | NoSuchMethodException | SecurityException | IllegalArgumentException | InvocationTargetException | InstantiationException e) {
            throw new TornadoAPIException("[ERROR] Tornado Implementation class not found", e);
        }
        return tornado;
    }

    public static AbstractFactoryDevice loadDeviceImpl(String backendName) {
        AbstractFactoryDevice device = null;
        String systemProperty = "tornado.load.device.implementation." + backendName.toLowerCase();
        try {
            String tornadoDeviceImplementation = System.getProperty(systemProperty);
            Class<?> klass = Class.forName(tornadoDeviceImplementation);
            Constructor<?> constructor = klass.getConstructor();
            device = (AbstractFactoryDevice) constructor.newInstance();
        } catch (ClassNotFoundException | IllegalAccessException | NoSuchMethodException | SecurityException | IllegalArgumentException | InvocationTargetException | InstantiationException e) {
            throw new TornadoAPIException("[ERROR] Tornado Device Implementation class not found", e);
        }
        return device;
    }
}
