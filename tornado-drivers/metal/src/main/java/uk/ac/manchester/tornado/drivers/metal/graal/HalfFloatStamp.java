/*
 * Copyright (c) 2025, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2022, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.metal.graal;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.core.common.type.Stamp;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind;

public class HalfFloatStamp extends Stamp {
    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        return metaAccess.lookupJavaType(java.lang.Short.TYPE);
    }

    @Override
    public JavaKind getStackKind() {
        return JavaKind.Short;
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool tool) {
        return LIRKind.value(MetalKind.HALF);
    }

    @Override
    public Stamp meet(Stamp other) {
        return this;
    }

    @Override
    public Stamp join(Stamp other) {
        return this;
    }

    @Override
    public Stamp unrestricted() {
        return this;
    }

    @Override
    public Stamp empty() {
        return this;
    }

    @Override
    public Stamp constant(Constant c, MetaAccessProvider meta) {
        return null;
    }

    @Override
    public boolean isCompatible(Stamp other) {
        return true;
    }

    @Override
    public boolean isCompatible(Constant constant) {
        return true;
    }

    @Override
    public boolean hasValues() {
        return true;
    }

    @Override
    public Constant readConstant(MemoryAccessProvider provider, Constant base, long displacement) {
        return null;
    }

    @Override
    public Stamp improveWith(Stamp other) {
        return null;
    }

    @Override
    public String toString() {
        return "half";
    }

    @Override
    public void accept(Visitor v) {

    }
}
