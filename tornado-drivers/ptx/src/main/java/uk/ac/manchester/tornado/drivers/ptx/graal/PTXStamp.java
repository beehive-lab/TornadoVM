/*
 * Copyright (c) 2020, 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.ptx.graal;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.spi.LIRKindTool;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;

public class PTXStamp extends ObjectStamp {

    private static final ResolvedJavaType STAMP_TYPE = null;
    private static final boolean EXACT_TYPE = true;
    private static final boolean NON_NULL = true;
    private static final boolean ALWAYS_NULL = false;
    private static final boolean ALWAYS_ARRAY = false;

    private PTXKind kind;

    public PTXStamp(PTXKind kind) {
        super(STAMP_TYPE, EXACT_TYPE, NON_NULL, ALWAYS_NULL, ALWAYS_ARRAY);
        this.kind = kind;
    }

    @Override
    public Stamp constant(Constant cnstnt, MetaAccessProvider map) {
        shouldNotReachHere();
        return this;
    }

    @Override
    public Stamp empty() {
        return this;
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool lirKindTool) {
        return LIRKind.value(kind);
    }

    public PTXKind getPTXKind() {
        return kind;
    }

    @Override
    public JavaKind getStackKind() {
        if (kind.isPrimitive()) {
            switch (kind) {
                case PRED:
                    return JavaKind.Boolean;
                case S8:
                case U8:
                    return JavaKind.Byte;
                case S16:
                case U16:
                case F16:
                case B16:
                    return JavaKind.Short;
                case S32:
                case U32:
                    return JavaKind.Int;
                case S64:
                case U64:
                    return JavaKind.Long;
                case F32:
                    return JavaKind.Float;
                case F64:
                    return JavaKind.Double;
                default:
                    return JavaKind.Illegal;
            }
        } else if (kind.isVector()) {
            return JavaKind.Object;
        }
        return JavaKind.Illegal;
    }

    @Override
    public boolean hasValues() {
        // shouldNotReachHere();
        return true;
    }

    @Override
    public Stamp improveWith(Stamp stamp) {
        return this;
    }

    @Override
    public boolean isCompatible(Constant constant) {

        shouldNotReachHere();
        return false;
    }

    @Override
    public boolean isCompatible(Stamp stamp) {
        if (stamp instanceof PTXStamp && ((PTXStamp) stamp).kind == kind) {
            return true;
        }

        unimplemented("stamp is compat: %s + %s", this, stamp);
        return false;
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        if (kind.getJavaClass() != null) {
            return metaAccess.lookupJavaType(kind.getJavaClass());
        }
        shouldNotReachHere();

        return null;
    }

    @Override
    public Stamp join(Stamp stamp) {
        return this;
    }

    @Override
    public Stamp meet(Stamp stamp) {
        return this;
    }

    @Override
    public Constant readConstant(MemoryAccessProvider metaAccess, Constant constant, long displacment) {
        shouldNotReachHere();
        return null;
    }

    @Override
    public String toString() {
        return "ptx: " + kind.name();
    }

    @Override
    public Stamp unrestricted() {
        return this;
    }

}
