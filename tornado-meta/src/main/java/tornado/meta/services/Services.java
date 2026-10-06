/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package tornado.meta.services;

import java.util.HashMap;
import java.util.Map;

/**
 * The members of JVMCI's {@code jdk.vm.ci.services.Services} that the vendored Graal compiler reads.
 * TornadoVM runs Graal as an ordinary library on a stock JVM, never inside or while building a native
 * image, and the system properties stand in for the VM's saved properties.
 */
public final class Services {

    /**
     * Graal never runs inside a native image under TornadoVM.
     */
    public static final boolean IS_IN_NATIVE_IMAGE = false;

    /**
     * Graal is never building a native image under TornadoVM.
     */
    public static final boolean IS_BUILDING_NATIVE_IMAGE = false;

    private Services() {
    }

    /**
     * Returns a snapshot of the system properties, in place of the properties the VM saved at startup.
     */
    public static Map<String, String> getSavedProperties() {
        Map<String, String> properties = new HashMap<>();
        for (String name : System.getProperties().stringPropertyNames()) {
            properties.put(name, System.getProperty(name));
        }
        return properties;
    }
}
