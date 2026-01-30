/*
 * Copyright (c) 2020-2022, APT Group, Department of Computer Science,
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

import static jdk.vm.ci.code.MemoryBarriers.LOAD_STORE;
import static jdk.vm.ci.code.MemoryBarriers.STORE_STORE;

import java.nio.ByteOrder;
import java.util.Set;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.lir.Variable;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.Register.RegisterCategory;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.common.architecture.ArchitectureRegister;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.meta.PTXMemorySpace;

public class PTXArchitecture extends Architecture {

    public static final RegisterCategory PTX_ABI = new RegisterCategory("abi");
    public static final PTXMemoryBase globalSpace = new PTXMemoryBase(PTXMemorySpace.GLOBAL);
    public static final PTXMemoryBase paramSpace = new PTXMemoryBase(PTXMemorySpace.PARAM);
    public static final PTXMemoryBase sharedSpace = new PTXMemoryBase(PTXMemorySpace.SHARED);
    public static final PTXMemoryBase localSpace = new PTXMemoryBase(PTXMemorySpace.LOCAL);
    private static final int NATIVE_CALL_DISPLACEMENT_OFFSET = 0;
    private static final int RETURN_ADDRESS_SIZE = 0;
    public static PTXParam KERNEL_CONTEXT;
    public static PTXParam[] abiRegisters;

    public static PTXBuiltInRegister ThreadIDX = new PTXBuiltInRegister("%tid.x");
    public static PTXBuiltInRegister ThreadIDY = new PTXBuiltInRegister("%tid.y");
    public static PTXBuiltInRegister ThreadIDZ = new PTXBuiltInRegister("%tid.z");

    public static PTXBuiltInRegister BlockDimX = new PTXBuiltInRegister("%ntid.x");
    public static PTXBuiltInRegister BlockDimY = new PTXBuiltInRegister("%ntid.y");
    public static PTXBuiltInRegister BlockDimZ = new PTXBuiltInRegister("%ntid.z");

    public static PTXBuiltInRegister BlockIDX = new PTXBuiltInRegister("%ctaid.x");
    public static PTXBuiltInRegister BlockIDY = new PTXBuiltInRegister("%ctaid.y");
    public static PTXBuiltInRegister BlockIDZ = new PTXBuiltInRegister("%ctaid.z");

    public static PTXBuiltInRegister GridDimX = new PTXBuiltInRegister("%nctaid.x");
    public static PTXBuiltInRegister GridDimY = new PTXBuiltInRegister("%nctaid.y");
    public static PTXBuiltInRegister GridDimZ = new PTXBuiltInRegister("%nctaid.z");

    public PTXArchitecture(PTXKind wordKind, ByteOrder byteOrder) {
        super("Tornado PTX", wordKind, byteOrder, false, null, LOAD_STORE | STORE_STORE, NATIVE_CALL_DISPLACEMENT_OFFSET, RETURN_ADDRESS_SIZE);

        KERNEL_CONTEXT = new PTXParam(PTXAssemblerConstants.KERNEL_CONTEXT_NAME, 8, wordKind);

        abiRegisters = new PTXParam[] { KERNEL_CONTEXT };
    }

    @Override
    public boolean canStoreValue(RegisterCategory category, PlatformKind kind) {
        return false;
    }

    @Override
    public PlatformKind getLargestStorableKind(RegisterCategory category) {
        return null;
    }

    @Override
    public PlatformKind getPlatformKind(JavaKind javaKind) {
        PTXKind ptxKind = PTXKind.ILLEGAL;
        switch (javaKind) {
            case Boolean:
                ptxKind = PTXKind.U8;
                break;
            case Byte:
                ptxKind = PTXKind.S8;
                break;
            case Char:
                ptxKind = PTXKind.U16;
                break;
            case Short:
                ptxKind = (javaKind.isUnsigned()) ? PTXKind.U16 : PTXKind.S16;
                break;
            case Int:
                ptxKind = (javaKind.isUnsigned()) ? PTXKind.U32 : PTXKind.S32;
                break;
            case Long:
                ptxKind = (javaKind.isUnsigned()) ? PTXKind.U64 : PTXKind.S64;
                break;
            case Float:
                ptxKind = PTXKind.F32;
                break;
            case Double:
                ptxKind = PTXKind.F64;
                break;
            case Object:
                ptxKind = (PTXKind) getWordKind();
                break;
            case Void:
            case Illegal:
                ptxKind = PTXKind.ILLEGAL;
                break;
            default:
                throw new TornadoBailoutRuntimeException("illegal java type for " + javaKind.name());
        }
        return ptxKind;
    }

    /*
     * We use jdk.vm.ci.amd64.AMD64.CPUFeature as a type parameter because the
     * return type of Architecture::getFeatures in JVMCI of JDK 17 is Set<? extends
     * CPUFeatureName>. The method Architecture::getFeatures does not exist in the
     * JVMCI of JDK 11, but the method getFeatures is implemented for each backend
     * returning EnumSet<AMD64.CPUFeature>. In order to implement our own CPUFeature
     * enum for each architecture, we would have to keep two different versions of
     * the source code. One in which CPUFeature extends CPUFeatureName for JDK 17
     * and another in which it does not for JDK 11.
     */
    public Set<jdk.vm.ci.amd64.AMD64.CPUFeature> getFeatures() {
        TornadoInternalError.unimplemented();
        return null;
    }

    public String getABI() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < abiRegisters.length; i++) {
            sb.append(abiRegisters[i].getDeclaration());
            if (i < abiRegisters.length - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    private abstract static class PTXRegister extends ArchitectureRegister {

        // Constant to represent an unused value for this backend.
        private static final int UNUSED_NUMBER = 0;

        public PTXRegister(String name, PTXKind ptxKind) {
            super(UNUSED_NUMBER, name, ptxKind);
        }
    }

    public static class PTXParam extends PTXRegister {
        private int alignment = 0;

        public PTXParam(String name, PTXKind lirKind) {
            super(name, lirKind);
        }

        public PTXParam(String name, int alignment, PTXKind lirKind) {
            super(name, lirKind);
            this.alignment = alignment;
        }

        @Override
        public String getDeclaration() {
            if (alignment != 0) {
                return String.format(".param .%s .ptr .global .align %s %s", getLirKind().toString(), alignment, name);
            } else {
                return String.format(".param .%s %s", getLirKind().toString(), name);
            }
        }
    }

    /**
     * It represents a base class for PTX memory objects.
     */
    public static class PTXMemoryBase extends PTXRegister {

        public final PTXMemorySpace memorySpace;

        /**
         * It constructs a new {@link PTXMemoryBase} with the specified memory space.
         *
         * @param memorySpace
         *            The {@link PTXMemorySpace} to associate with this memory base.
         */
        public PTXMemoryBase(PTXMemorySpace memorySpace) {
            super(memorySpace.getName(), PTXKind.B64);
            this.memorySpace = memorySpace;
        }
    }

    /**
     * It represents a built-in register in PTX.
     */
    public static class PTXBuiltInRegister extends Variable {
        private final String name;

        /**
         * It constructs a new PTXBuiltInRegister with the specified name.
         *
         * @param name
         *            The name of the built-in register.
         */
        protected PTXBuiltInRegister(String name) {
            super(LIRKind.value(PTXKind.U32), 0);
            this.name = name;
        }

        /**
         * It gets the name of the built-in register.
         *
         * @return The name of the built-in register.
         */
        public String getName() {
            return name;
        }

        /**
         * It gets the PTXKind associated with this built-in register.
         *
         * @return The PTXKind associated with this built-in register.
         */
        public PTXKind getPtxKind() {
            return PTXKind.U32;
        }
    }

    public static class PTXBuiltInRegisterArray {
        public final PTXBuiltInRegister threadID;
        public final PTXBuiltInRegister blockID;
        public final PTXBuiltInRegister blockDim;
        public final PTXBuiltInRegister gridDim;

        public PTXBuiltInRegisterArray(int dim) {
            switch (dim) {
                case 0:
                    threadID = PTXArchitecture.ThreadIDX;
                    blockDim = PTXArchitecture.BlockDimX;
                    blockID = PTXArchitecture.BlockIDX;
                    gridDim = PTXArchitecture.GridDimX;
                    break;
                case 1:
                    threadID = PTXArchitecture.ThreadIDY;
                    blockDim = PTXArchitecture.BlockDimY;
                    blockID = PTXArchitecture.BlockIDY;
                    gridDim = PTXArchitecture.GridDimY;
                    break;
                case 2:
                    threadID = PTXArchitecture.ThreadIDZ;
                    blockDim = PTXArchitecture.BlockDimZ;
                    blockID = PTXArchitecture.BlockIDZ;
                    gridDim = PTXArchitecture.GridDimZ;
                    break;
                default:
                    throw new TornadoBailoutRuntimeException(String.format("[ERROR] Too many dimensions: %d", dim));
            }
        }
    }
}
