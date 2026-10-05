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
package uk.ac.manchester.tornado.drivers.cuda.graal;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import tornado.meta.code.CallingConvention;
import tornado.meta.code.CallingConvention.Type;
import tornado.meta.code.Register;
import tornado.meta.code.RegisterArray;
import tornado.meta.code.RegisterAttributes;
import tornado.meta.code.RegisterConfig;
import tornado.meta.code.ValueKindFactory;
import tornado.meta.JavaKind;
import tornado.meta.JavaType;
import tornado.meta.PlatformKind;

public class CUDARegisterConfig implements RegisterConfig {

    private final static Register DUMMY = new Register(0, 0, "dummy", CUDAArchitecture.CUDA_ABI);
    private final static RegisterArray EMPTY = new RegisterArray(new Register[0]);

    @Override
    public RegisterArray getCalleeSaveRegisters() {
        return EMPTY;
    }

    @Override
    public CallingConvention getCallingConvention(Type type, JavaType jt, JavaType[] jts, ValueKindFactory<?> vkf) {
        unimplemented("Get calling convention not implemented yet.");
        return null;
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
    public RegisterArray getCallingConventionRegisters(Type type, JavaKind kind) {
        return EMPTY;
    }

    @Override
    public RegisterArray getAllocatableRegisters() {
        return EMPTY;
    }

    @Override
    public RegisterArray filterAllocatableRegisters(PlatformKind kind, RegisterArray registers) {
        unimplemented("Filter allocation registers not implemented yet.");
        return null;
    }

    @Override
    public RegisterArray getCallerSaveRegisters() {
        return EMPTY;
    }

    @Override
    public RegisterAttributes[] getAttributesMap() {
        unimplemented("Get attributes map not implemented yet");
        return null;
    }

    @Override
    public boolean areAllAllocatableRegistersCallerSaved() {
        unimplemented("get all allocatable registers caller saved, not implemented yet");
        return false;
    }

}
