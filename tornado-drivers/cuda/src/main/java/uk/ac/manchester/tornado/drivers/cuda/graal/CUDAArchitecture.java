/*
 * Copyright (c) 2018-2022 APT Group, Department of Computer Science,
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

import static jdk.vm.ci.code.MemoryBarriers.LOAD_STORE;
import static jdk.vm.ci.code.MemoryBarriers.STORE_STORE;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssemblerConstants.ATOMICS_REGION_NAME;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssemblerConstants.CONSTANT_REGION_NAME;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssemblerConstants.GLOBAL_REGION_NAME;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssemblerConstants.KERNEL_CONTEXT;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssemblerConstants.LOCAL_REGION_NAME;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssemblerConstants.PRIVATE_REGION_NAME;

import java.nio.ByteOrder;
import java.util.Set;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.Register.RegisterCategory;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.common.architecture.ArchitectureRegister;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.meta.CUDAMemorySpace;

public class CUDAArchitecture extends Architecture {

    public static final RegisterCategory CUDA_ABI = new RegisterCategory("abi");
    public static final CUDAMemoryBase globalSpace = new CUDAMemoryBase(0, GLOBAL_REGION_NAME, CUDAMemorySpace.GLOBAL, CUDAKind.UCHAR);
    public static final CUDAMemoryBase kernelContext = new CUDAMemoryBase(1, KERNEL_CONTEXT, CUDAMemorySpace.GLOBAL, CUDAKind.LONG);
    public static final CUDAMemoryBase constantSpace = new CUDAMemoryBase(2, CONSTANT_REGION_NAME, CUDAMemorySpace.CONSTANT, CUDAKind.UCHAR);
    public static final CUDAMemoryBase localSpace = new CUDAMemoryBase(3, LOCAL_REGION_NAME, CUDAMemorySpace.LOCAL, CUDAKind.UCHAR);
    public static final CUDAMemoryBase privateSpace = new CUDAMemoryBase(4, PRIVATE_REGION_NAME, CUDAMemorySpace.PRIVATE, CUDAKind.UCHAR);
    public static final CUDAMemoryBase atomicSpace = new CUDAMemoryBase(5, ATOMICS_REGION_NAME, CUDAMemorySpace.GLOBAL, CUDAKind.INT);

    public static CUDARegister[] abiRegisters;

    public CUDAArchitecture(final CUDAKind wordKind, final ByteOrder byteOrder) {
        super("Tornado CUDADriver", wordKind, byteOrder, false, null, LOAD_STORE | STORE_STORE, 0, 0);
        abiRegisters = new CUDARegister[] { kernelContext, constantSpace, localSpace, atomicSpace };
    }

    @Override
    public PlatformKind getPlatformKind(JavaKind javaKind) {
        CUDAKind oclKind = CUDAKind.ILLEGAL;
        switch (javaKind) {
            case Boolean:
                oclKind = CUDAKind.BOOL;
                break;
            case Byte:
                oclKind = CUDAKind.CHAR;
                break;
            case Short:
                oclKind = (javaKind.isUnsigned()) ? CUDAKind.USHORT : CUDAKind.SHORT;
                break;
            case Char:
                oclKind = CUDAKind.USHORT;
                break;
            case Int:
                oclKind = (javaKind.isUnsigned()) ? CUDAKind.UINT : CUDAKind.INT;
                break;
            case Long:
                oclKind = (javaKind.isUnsigned()) ? CUDAKind.ULONG : CUDAKind.LONG;
                break;
            case Float:
                oclKind = CUDAKind.FLOAT;
                break;
            case Double:
                oclKind = CUDAKind.DOUBLE;
                break;
            case Object:
                oclKind = (CUDAKind) getWordKind();
                break;
            case Void:
            case Illegal:
                break;
            default:
                shouldNotReachHere("illegal java type for %s", javaKind.name());
        }

        return oclKind;
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

    @Override
    public int getReturnAddressSize() {
        return this.getWordSize();
    }

    @Override
    public boolean canStoreValue(RegisterCategory category, PlatformKind platformKind) {
        return false;
    }

    @Override
    public PlatformKind getLargestStorableKind(RegisterCategory category) {
        return CUDAKind.LONG;
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

    public String getCallingConvention() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < abiRegisters.length; i++) {
            sb.append(abiRegisters[i].getName());
            if (i < abiRegisters.length - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    public static class CUDARegister extends ArchitectureRegister {
        public CUDARegister(int number, String name, CUDAKind lirKind) {
            super(number, name, lirKind);
        }
    }

    public static class CUDAMemoryBase extends CUDARegister {

        final CUDAMemorySpace memorySpace;

        public CUDAMemoryBase(int number, String name, CUDAMemorySpace memorySpace, CUDAKind kind) {
            super(number, name, kind);
            this.memorySpace = memorySpace;
        }

        public CUDAMemorySpace getMemorySpace() {
            return memorySpace;
        }

        @Override
        public String getDeclaration() {
            // In CUDA C, kernel parameters are plain device pointers and cannot carry an
            // address-space qualifier (__shared__/__constant__ do not apply to parameters).
            // Every ABI region is passed as a global device pointer, so emit no qualifier.
            return String.format("%s *%s", lirKind.toString(), name);
        }

    }
}
