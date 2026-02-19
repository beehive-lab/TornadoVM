/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
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

import uk.ac.manchester.tornado.drivers.ptx.PTXTargetDescription;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;

public class PTXLIRKindTool implements LIRKindTool {
    private PTXTargetDescription target;

    public PTXLIRKindTool(PTXTargetDescription target) {
        this.target = target;
    }

    public LIRKind getUnsignedIntegerKind(int numBits) {
        if (numBits <= 8) {
            return LIRKind.value(PTXKind.U8);
        } else if (numBits <= 16) {
            return LIRKind.value(PTXKind.U16);
        } else if (numBits <= 32) {
            return LIRKind.value(PTXKind.U32);
        } else if (numBits <= 64) {
            return LIRKind.value(PTXKind.U64);
        } else {
            throw shouldNotReachHere();
        }
    }

    @Override
    public LIRKind getIntegerKind(int bits) {
        if (bits <= 8) {
            return LIRKind.value(PTXKind.S8);
        }
        if (bits <= 16) {
            return LIRKind.value(PTXKind.S16);
        }
        if (bits <= 32) {
            return LIRKind.value(PTXKind.S32);
        }
        if (bits <= 64) {
            return LIRKind.value(PTXKind.S64);
        }
        throw shouldNotReachHere();
    }

    @Override
    public LIRKind getFloatingKind(int bits) {
        if (bits == 32) {
            return LIRKind.value(PTXKind.F32);
        }
        if (bits == 64) {
            return LIRKind.value(PTXKind.F64);
        }
        throw shouldNotReachHere();
    }

    @Override
    public LIRKind getObjectKind() {
        return getWordKind();
    }

    @Override
    public LIRKind getWordKind() {
        return LIRKind.value(target.getArch().getWordKind());
    }

    @Override
    public LIRKind getNarrowOopKind() {
        unimplemented("GetNarrowOop not supported yet");
        return null;
    }

    @Override
    public LIRKind getNarrowPointerKind() {
        unimplemented("getNarrowPointerKind not supported yet");
        return null;
    }
}
