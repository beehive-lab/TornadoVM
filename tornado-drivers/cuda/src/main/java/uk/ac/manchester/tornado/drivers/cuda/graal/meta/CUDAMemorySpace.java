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
package uk.ac.manchester.tornado.drivers.cuda.graal.meta;

import tornado.graal.compiler.core.common.LIRKind;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAArchitecture;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssemblerConstants;

public class CUDAMemorySpace extends Value {

    public static final CUDAMemorySpace GLOBAL = new CUDAMemorySpace(CUDAAssemblerConstants.GLOBAL_MEM_MODIFIER);
    public static final CUDAMemorySpace SHARED = new CUDAMemorySpace(CUDAAssemblerConstants.SHARED_MEM_MODIFIER);
    public static final CUDAMemorySpace LOCAL = new CUDAMemorySpace(CUDAAssemblerConstants.LOCAL_MEM_MODIFIER);
    public static final CUDAMemorySpace PRIVATE = new CUDAMemorySpace(CUDAAssemblerConstants.PRIVATE_MEM_MODIFIER);
    public static final CUDAMemorySpace CONSTANT = new CUDAMemorySpace(CUDAAssemblerConstants.CONSTANT_MEM_MODIFIER);

    private final String name;

    protected CUDAMemorySpace(String name) {
        super(LIRKind.Illegal);
        this.name = name;
    }

    public CUDAArchitecture.CUDAMemoryBase getBase() {
        if (this == GLOBAL) {
            return CUDAArchitecture.globalSpace;
        } else if (this == LOCAL) {
            return CUDAArchitecture.localSpace;
        } else if (this == CONSTANT) {
            return CUDAArchitecture.constantSpace;
        } else if (this == PRIVATE) {
            return CUDAArchitecture.privateSpace;
        } else {
            TornadoInternalError.shouldNotReachHere();
            return null;
        }
    }

    public String name() {
        return name;
    }
}
