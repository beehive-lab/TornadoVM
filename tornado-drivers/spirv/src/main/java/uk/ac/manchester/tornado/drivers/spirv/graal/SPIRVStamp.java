/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009-2021, Oracle and/or its affiliates. All rights reserved.
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
 */
package uk.ac.manchester.tornado.drivers.spirv.graal;

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
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;

// FIXME <Refactor> Stamp are very similar between different backends
public class SPIRVStamp extends ObjectStamp {

    private static final ResolvedJavaType STAMP_TYPE = null;
    private static final boolean EXACT_TYPE = true;
    private static final boolean NON_NULL = true;
    private static final boolean ALWAYS_NULL = false;
    private static final boolean ALWAYS_ARRAY = false;
    private SPIRVKind spirvKind;

    public SPIRVStamp(SPIRVKind kind) {
        super(STAMP_TYPE, EXACT_TYPE, NON_NULL, ALWAYS_NULL, ALWAYS_ARRAY);
        this.spirvKind = kind;
    }

    @Override
    public Stamp constant(Constant constant, MetaAccessProvider map) {
        shouldNotReachHere();
        return this;
    }

    @Override
    public Stamp empty() {
        return this;
    }

    /**
     * Gets a platform dependent {@link LIRKind} that can be used to store a value
     * of this stamp.
     * 
     * @param lirKindTool
     *            Platform VM specific kinds.
     * @return {@link LIRKind}
     */
    @Override
    public LIRKind getLIRKind(LIRKindTool lirKindTool) {
        return LIRKind.value(spirvKind);
    }

    public SPIRVKind getSPIRVKind() {
        return spirvKind;
    }

    public JavaKind getJavaKindFromPrimitive() {
        switch (spirvKind) {
            case OP_TYPE_BOOL:
                return JavaKind.Boolean;
            case OP_TYPE_INT_8:
                return JavaKind.Byte;
            case OP_TYPE_INT_16:
                return JavaKind.Short;
            case OP_TYPE_INT_32:
                return JavaKind.Int;
            case OP_TYPE_INT_64:
                return JavaKind.Long;
            case OP_TYPE_FLOAT_32:
                return JavaKind.Float;
            case OP_TYPE_FLOAT_64:
                return JavaKind.Double;
            default:
                throw new RuntimeException("Not implemented yet");
        }
    }

    @Override
    public JavaKind getStackKind() {
        if (spirvKind.isPrimitive()) {
            return getJavaKindFromPrimitive();
        } else if (spirvKind.isVector()) {
            return JavaKind.Object;
        }
        return JavaKind.Illegal;
    }

    @Override
    public boolean hasValues() {
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
        if (stamp instanceof SPIRVStamp && ((SPIRVStamp) stamp).spirvKind == spirvKind) {
            return true;
        }
        unimplemented("stamp is compat: %s + %s", this, stamp);
        return false;
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        if (spirvKind.getJavaClass() != null) {
            return metaAccess.lookupJavaType(spirvKind.getJavaClass());
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
        return "spirv: " + spirvKind.name();
    }

    @Override
    public Stamp unrestricted() {
        return this;
    }

}
