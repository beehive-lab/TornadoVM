/*
 * Copyright (c) 2018, 2021, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.management.HotSpotDiagnosticMXBean;

import uk.ac.manchester.tornado.runtime.common.FieldLayout;

/**
 * The parts of the JVM's object layout the runtime still mirrors when it copies Java arrays to a
 * device: the object header size, which places the array length. It is derived from the VM's
 * documented flags, so neither JVMCI nor {@code Unsafe} is needed. Objects use TornadoVM's own
 * layout ({@link FieldLayout}), which does not depend on the JVM.
 */
public class TornadoVMConfigAccess {

    /** HotSpot 64-bit mark-word size; the klass pointer (object hub) lives immediately after it. */
    private static final int MARK_WORD_SIZE = 8;

    /** Object header size in bytes: mark word, plus the klass pointer unless headers are compact. */
    private static final int HEADER_SIZE = headerSize();

    /** Offset at which an object's hub/klass placeholder is zeroed when marshalling it to the device. */
    public final int hubOffset = FieldLayout.HUB_OFFSET;

    public TornadoVMConfigAccess() {
    }

    private static int headerSize() {
        if (flag("UseCompactObjectHeaders")) {
            return MARK_WORD_SIZE;
        }
        // Without the HotSpot bean (another VM, a native image) assume HotSpot's default.
        String compressed = option("UseCompressedClassPointers");
        return compressed == null || Boolean.parseBoolean(compressed) ? MARK_WORD_SIZE + 4 : MARK_WORD_SIZE + 8;
    }

    private static boolean flag(String name) {
        return Boolean.parseBoolean(option(name));
    }

    private static String option(String name) {
        try {
            return ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class).getVMOption(name).getValue();
        } catch (RuntimeException | LinkageError e) {
            // Not a HotSpot VM, or a flag this JDK does not have (UseCompactObjectHeaders before 24).
            return null;
        }
    }

    /** Offset of the array length field: it is stored immediately after the object header. */
    public final int arrayOopDescLengthOffset() {
        return HEADER_SIZE;
    }

    /** Where an object's fields start in its device copy. */
    public int instanceKlassFieldsOffset() {
        return FieldLayout.HEADER_BYTES;
    }

}
