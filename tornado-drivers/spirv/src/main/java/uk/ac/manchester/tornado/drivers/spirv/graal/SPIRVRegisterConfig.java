/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import jdk.vm.ci.code.*;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.PlatformKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLArchitecture;

import java.util.List;

public class SPIRVRegisterConfig implements RegisterConfig {

    private final static Register DUMMY = new Register(0, 0, "dummy", OCLArchitecture.OCL_ABI);
    private final static Register[] EMPTY = new Register[0];


    @Override
    public Register getReturnRegister(JavaKind kind) {
        return null;
    }

    @Override
    public Register getFrameRegister() {
        return DUMMY;
    }

    @Override
    public CallingConvention getCallingConvention(CallingConvention.Type type, JavaType returnType, JavaType[] parameterTypes, ValueKindFactory<?> valueKindFactory) {
        return null;
    }

    @Override
    public List<Register> getCallingConventionRegisters(CallingConvention.Type type, JavaKind kind) {
        return List.of();
    }

    @Override
    public List<Register> getAllocatableRegisters() {
        return List.of();
    }

    @Override
    public List<Register> filterAllocatableRegisters(PlatformKind kind, List<Register> registers) {
        return List.of();
    }

    @Override
    public List<Register> getCallerSaveRegisters() {
        return List.of();
    }

    @Override
    public List<Register> getCalleeSaveRegisters() {
        return List.of();
    }

    @Override
    public List<RegisterAttributes> getAttributesMap() {
        return List.of();
    }

    @Override
    public boolean areAllAllocatableRegistersCallerSaved() {
        return false;
    }
}
