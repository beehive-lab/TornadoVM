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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.runtime;

import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

public class TornadoVMConfig extends HotSpotVMConfigAccess {

    private MetaAccessProvider metaAccessProvider;

    private final boolean useCompressedClassPointers = getFlag("UseCompressedClassPointers", Boolean.class);
    private final int arrayOopDescSize = getFieldValue("CompilerToVM::Data::sizeof_arrayOopDesc", Integer.class, "int");
    public final int hubOffset = getFieldOffset("oopDesc::_metadata._klass", Integer.class, "Klass*");
    private final int narrowKlassSize = getFieldValue("CompilerToVM::Data::sizeof_narrowKlass", Integer.class, "int");
    public final int instanceKlassFieldsOffset = getFieldOffset("InstanceKlass::_fields", Integer.class, "Array<u2>*");

    public TornadoVMConfig(HotSpotVMConfigStore store, MetaAccessProvider metaAccessProvider) {
        super(store);
        this.metaAccessProvider = metaAccessProvider;
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

}
