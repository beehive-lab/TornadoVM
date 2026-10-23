/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.runtime;

import java.lang.management.ManagementFactory;
import java.util.Optional;

import com.sun.management.HotSpotDiagnosticMXBean;

import uk.ac.manchester.tornado.runtime.common.FieldLayout;

/**
 * Where the JVM puts an array's length, which the runtime must know when it copies Java arrays to
 * a device: right after the object header. The header size follows from the VM's documented flags
 * (read through the HotSpot diagnostic bean), so neither JVMCI nor {@code Unsafe} is needed. Objects
 * use TornadoVM's own layout ({@link FieldLayout}), which does not depend on the JVM.
 */
public class TornadoVMConfigAccess {

    /** The mark word of a 64-bit HotSpot object header. */
    private static final int MARK_WORD_BYTES = Long.BYTES;

    private static final int OBJECT_HEADER_BYTES = objectHeaderBytes();

    /** Offset at which an object's hub/klass placeholder is zeroed when marshalling it to the device. */
    public final int hubOffset = FieldLayout.HUB_OFFSET;

    /**
     * The mark word alone with compact headers (JDK 24+); otherwise mark word plus a klass pointer,
     * compressed unless the VM says not. Without the HotSpot bean (another VM, a native image)
     * HotSpot's defaults are assumed.
     */
    private static int objectHeaderBytes() {
        if (flag("UseCompactObjectHeaders", false)) {
            return MARK_WORD_BYTES;
        }
        return MARK_WORD_BYTES + (flag("UseCompressedClassPointers", true) ? Integer.BYTES : Long.BYTES);
    }

    private static boolean flag(String name, boolean fallback) {
        return vmOption(name).map(Boolean::parseBoolean).orElse(fallback);
    }

    private static Optional<String> vmOption(String name) {
        try {
            return Optional.ofNullable(ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class).getVMOption(name).getValue());
        } catch (RuntimeException | LinkageError e) {
            // Not a HotSpot VM, or a flag this JDK does not have (UseCompactObjectHeaders before 24).
            return Optional.empty();
        }
    }

    /** Offset of an array's length field: immediately after the object header. */
    public final int arrayOopDescLengthOffset() {
        return OBJECT_HEADER_BYTES;
    }

    /** Where an object's fields start in its device copy. */
    public int instanceKlassFieldsOffset() {
        return FieldLayout.HEADER_BYTES;
    }
}
