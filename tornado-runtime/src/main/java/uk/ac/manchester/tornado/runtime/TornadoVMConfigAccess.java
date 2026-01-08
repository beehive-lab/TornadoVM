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

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Provides access to VM configuration without depending on JVMCI's HotSpotVMConfigAccess.
 * Uses ReflectionBasedVMConfiguration and MetaAccessProvider to read VM internals.
 */
public class TornadoVMConfigAccess {

    private static final ReflectionBasedVMConfiguration vmConfig = new ReflectionBasedVMConfiguration();

    public final int hubOffset;
    private final boolean useCompressedClassPointers;
    private final int arrayOopDescSize;
    private final int narrowKlassSize;

    private final MetaAccessProvider metaAccessProvider;

    private int fieldOffset = -1;

    public TornadoVMConfigAccess(MetaAccessProvider metaAccessProvider) {
        this.metaAccessProvider = metaAccessProvider;
        this.hubOffset = 8; // Standard Java object header offset for klass pointer
        this.useCompressedClassPointers = vmConfig.isCompressedClassPointersEnabled();
        this.arrayOopDescSize = 16; // Standard Java array header size
        this.narrowKlassSize = useCompressedClassPointers ? 4 : 8;
    }

    public final int arrayOopDescLengthOffset() {
        return useCompressedClassPointers ? hubOffset + narrowKlassSize : arrayOopDescSize;
    }

    public int getArrayBaseOffset(JavaKind kind) {
        return metaAccessProvider.getArrayBaseOffset(kind);
    }

    public int getArrayIndexScale(JavaKind kind) {
        return metaAccessProvider.getArrayIndexScale(kind);
    }

    public int instanceKlassFieldsOffset() {
        if (fieldOffset == -1) {
            String javaVersionString = System.getProperty("java.version");
            int javaVersion = Integer.parseInt(javaVersionString.split("\\.")[0]);
            // Use standard JDK class layout values
            if (javaVersion <= 20) {
                fieldOffset = 24; // Offset to _fields in InstanceKlass for JDK <= 20
            } else {
                fieldOffset = 32; // Offset to _fieldinfo_stream in InstanceKlass for JDK > 20
            }
        }
        return fieldOffset;
    }

}
