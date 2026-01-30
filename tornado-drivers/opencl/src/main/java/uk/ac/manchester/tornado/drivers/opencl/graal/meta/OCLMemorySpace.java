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
package uk.ac.manchester.tornado.drivers.opencl.graal.meta;

import jdk.graal.compiler.core.common.LIRKind;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLArchitecture;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants;

public class OCLMemorySpace extends Value {

    public static final OCLMemorySpace GLOBAL = new OCLMemorySpace(OCLAssemblerConstants.GLOBAL_MEM_MODIFIER);
    public static final OCLMemorySpace SHARED = new OCLMemorySpace(OCLAssemblerConstants.SHARED_MEM_MODIFIER);
    public static final OCLMemorySpace LOCAL = new OCLMemorySpace(OCLAssemblerConstants.LOCAL_MEM_MODIFIER);
    public static final OCLMemorySpace PRIVATE = new OCLMemorySpace(OCLAssemblerConstants.PRIVATE_MEM_MODIFIER);
    public static final OCLMemorySpace CONSTANT = new OCLMemorySpace(OCLAssemblerConstants.CONSTANT_MEM_MODIFIER);

    private final String name;

    protected OCLMemorySpace(String name) {
        super(LIRKind.Illegal);
        this.name = name;
    }

    public OCLArchitecture.OCLMemoryBase getBase() {
        if (this == GLOBAL) {
            return OCLArchitecture.globalSpace;
        } else if (this == LOCAL) {
            return OCLArchitecture.localSpace;
        } else if (this == CONSTANT) {
            return OCLArchitecture.constantSpace;
        } else if (this == PRIVATE) {
            return OCLArchitecture.privateSpace;
        } else {
            TornadoInternalError.shouldNotReachHere();
            return null;
        }
    }

    public String name() {
        return name;
    }
}
