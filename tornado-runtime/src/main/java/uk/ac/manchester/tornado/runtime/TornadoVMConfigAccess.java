/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
