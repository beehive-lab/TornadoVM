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
package uk.ac.manchester.tornado.drivers.opencl.graal;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CallingConvention.Type;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterAttributes;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.ValueKindFactory;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.PlatformKind;

import java.util.List;

public class OCLRegisterConfig implements RegisterConfig {

    private final static Register DUMMY = new Register(0, 0, "dummy", OCLArchitecture.OCL_ABI);
    private final static Register[] EMPTY = new Register[0];

    @Override
    public CallingConvention getCallingConvention(Type type, JavaType jt, JavaType[] jts, ValueKindFactory<?> vkf) {
        unimplemented("Get calling convention not implemented yet.");
        return null;
    }

    @Override
    public List<Register> getCallingConventionRegisters(Type type, JavaKind kind) {
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
    public Register getReturnRegister(JavaKind kind) {
        unimplemented("return register method not implemented yet.");
        return null;
    }

    @Override
    public Register getFrameRegister() {
        return DUMMY;
    }

    @Override
    public boolean areAllAllocatableRegistersCallerSaved() {
        unimplemented("get all allocatable registers caller saved, not implemented yet");
        return false;
    }

}
