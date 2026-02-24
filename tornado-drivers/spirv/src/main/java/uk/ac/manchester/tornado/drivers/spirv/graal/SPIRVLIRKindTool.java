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

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.spi.LIRKindTool;

import uk.ac.manchester.tornado.drivers.spirv.SPIRVTargetDescription;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;

public class SPIRVLIRKindTool implements LIRKindTool {

    final SPIRVTargetDescription targetDescription;

    public SPIRVLIRKindTool(SPIRVTargetDescription target) {
        this.targetDescription = target;
    }

    @Override
    public LIRKind getIntegerKind(int bits) {
        if (bits <= 8) {
            return LIRKind.value(SPIRVKind.OP_TYPE_INT_8);
        } else if (bits <= 16) {
            return LIRKind.value(SPIRVKind.OP_TYPE_INT_16);
        } else if (bits <= 32) {
            return LIRKind.value(SPIRVKind.OP_TYPE_INT_32);
        } else if (bits <= 64) {
            return LIRKind.value(SPIRVKind.OP_TYPE_INT_64);
        } else {
            throw new RuntimeException("Data Type Not Supported");
        }
    }

    @Override
    public LIRKind getFloatingKind(int bits) {
        switch (bits) {
            case 32:
                return LIRKind.value(SPIRVKind.OP_TYPE_FLOAT_32);
            case 64:
                return LIRKind.value(SPIRVKind.OP_TYPE_FLOAT_64);
            default:
                throw new RuntimeException("Data Type Not Supported.");
        }
    }

    @Override
    public LIRKind getObjectKind() {
        return getWordKind();
    }

    @Override
    public LIRKind getWordKind() {
        return LIRKind.value(targetDescription.getArch().getWordKind());
    }

    @Override
    public LIRKind getNarrowOopKind() {
        return null;
    }

    @Override
    public LIRKind getNarrowPointerKind() {
        return null;
    }
}
