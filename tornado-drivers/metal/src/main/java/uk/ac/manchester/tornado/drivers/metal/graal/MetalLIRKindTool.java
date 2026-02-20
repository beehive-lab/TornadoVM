/*
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.metal.graal;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.LIRKindTool;

import uk.ac.manchester.tornado.drivers.metal.MetalTargetDescription;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind;

public class MetalLIRKindTool implements LIRKindTool {

    private final MetalTargetDescription target;

    public MetalLIRKindTool(MetalTargetDescription target) {
        this.target = target;
    }


    public LIRKind getUnsignedIntegerKind(int numBits) {
        if (numBits <= 8) {
            return LIRKind.value(MetalKind.UCHAR);
        } else if (numBits <= 16) {
            return LIRKind.value(MetalKind.USHORT);
        } else if (numBits <= 32) {
            return LIRKind.value(MetalKind.UINT);
        } else if (numBits <= 64) {
            return LIRKind.value(MetalKind.ULONG);
        } else {
            throw shouldNotReachHere();
        }
    }

    @Override
    public LIRKind getIntegerKind(int numBits) {
        if (numBits <= 8) {
            return LIRKind.value(MetalKind.CHAR);
        } else if (numBits <= 16) {
            return LIRKind.value(MetalKind.SHORT);
        } else if (numBits <= 32) {
            return LIRKind.value(MetalKind.INT);
        } else if (numBits <= 64) {
            return LIRKind.value(MetalKind.LONG);
        } else {
            throw shouldNotReachHere();
        }
    }

    @Override
    public LIRKind getFloatingKind(int numBits) {
        switch (numBits) {
            case 32:
                return LIRKind.value(MetalKind.FLOAT);
            case 64:
                return LIRKind.value(MetalKind.DOUBLE);
            default:
                throw shouldNotReachHere();
        }
    }

    @Override
    public LIRKind getNarrowOopKind() {
        unimplemented("GetNarrowOop not supported yet");
        return null;
    }

    @Override
    public LIRKind getNarrowPointerKind() {
        unimplemented("GetNarrowPointerKind not supported yet");
        return null;
    }

    @Override
    public LIRKind getObjectKind() {
        return getWordKind();
    }

    @Override
    public LIRKind getWordKind() {
        return LIRKind.value(target.getArch().getWordKind());
    }

}
