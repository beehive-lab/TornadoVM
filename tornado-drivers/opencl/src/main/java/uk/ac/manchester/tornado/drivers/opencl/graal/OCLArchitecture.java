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
package uk.ac.manchester.tornado.drivers.opencl.graal;

import static jdk.vm.ci.code.MemoryBarriers.LOAD_STORE;
import static jdk.vm.ci.code.MemoryBarriers.STORE_STORE;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.ATOMICS_REGION_NAME;
import static uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.CONSTANT_REGION_NAME;
import static uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.GLOBAL_REGION_NAME;
import static uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.KERNEL_CONTEXT;
import static uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.LOCAL_REGION_NAME;
import static uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.PRIVATE_REGION_NAME;

import java.nio.ByteOrder;
import java.util.List;
import java.util.Set;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.Register.RegisterCategory;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.common.architecture.ArchitectureRegister;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.meta.OCLMemorySpace;

public class OCLArchitecture extends Architecture {

    public static final RegisterCategory OCL_ABI = new RegisterCategory("abi");
    public static final OCLMemoryBase globalSpace = new OCLMemoryBase(0, GLOBAL_REGION_NAME, OCLMemorySpace.GLOBAL, OCLKind.UCHAR);
    public static final OCLMemoryBase kernelContext = new OCLMemoryBase(1, KERNEL_CONTEXT, OCLMemorySpace.GLOBAL, OCLKind.LONG);
    public static final OCLMemoryBase constantSpace = new OCLMemoryBase(2, CONSTANT_REGION_NAME, OCLMemorySpace.CONSTANT, OCLKind.UCHAR);
    public static final OCLMemoryBase localSpace = new OCLMemoryBase(3, LOCAL_REGION_NAME, OCLMemorySpace.LOCAL, OCLKind.UCHAR);
    public static final OCLMemoryBase privateSpace = new OCLMemoryBase(4, PRIVATE_REGION_NAME, OCLMemorySpace.PRIVATE, OCLKind.UCHAR);
    public static final OCLMemoryBase atomicSpace = new OCLMemoryBase(5, ATOMICS_REGION_NAME, OCLMemorySpace.GLOBAL, OCLKind.INT);
    private final static Register[] EMPTY = new Register[0];
    private final static List<Register> EMPTY_LIST = List.of();

    public static OCLRegister[] abiRegisters;

    public OCLArchitecture(final OCLKind wordKind, final ByteOrder byteOrder) {
        super("Tornado OpenCL", wordKind, byteOrder, false, EMPTY_LIST, LOAD_STORE | STORE_STORE, 0, 0);
        abiRegisters = new OCLRegister[] { kernelContext, constantSpace, localSpace, atomicSpace };
    }

    @Override
    public PlatformKind getPlatformKind(JavaKind javaKind) {
        OCLKind oclKind = OCLKind.ILLEGAL;
        switch (javaKind) {
            case Boolean:
                oclKind = OCLKind.BOOL;
                break;
            case Byte:
                oclKind = OCLKind.CHAR;
                break;
            case Short:
                oclKind = (javaKind.isUnsigned()) ? OCLKind.USHORT : OCLKind.SHORT;
                break;
            case Char:
                oclKind = OCLKind.USHORT;
                break;
            case Int:
                oclKind = (javaKind.isUnsigned()) ? OCLKind.UINT : OCLKind.INT;
                break;
            case Long:
                oclKind = (javaKind.isUnsigned()) ? OCLKind.ULONG : OCLKind.LONG;
                break;
            case Float:
                oclKind = OCLKind.FLOAT;
                break;
            case Double:
                oclKind = OCLKind.DOUBLE;
                break;
            case Object:
                oclKind = (OCLKind) getWordKind();
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
        return OCLKind.LONG;
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

    public static class OCLRegister extends ArchitectureRegister {
        public OCLRegister(int number, String name, OCLKind lirKind) {
            super(number, name, lirKind);
        }
    }

    public static class OCLMemoryBase extends OCLRegister {

        final OCLMemorySpace memorySpace;

        public OCLMemoryBase(int number, String name, OCLMemorySpace memorySpace, OCLKind kind) {
            super(number, name, kind);
            this.memorySpace = memorySpace;
        }

        public OCLMemorySpace getMemorySpace() {
            return memorySpace;
        }

        @Override
        public String getDeclaration() {
            return String.format("%s %s *%s", memorySpace.name(), lirKind.toString(), name);
        }

    }
}
