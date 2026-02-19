/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021-2022, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.spirv.graal;

import static jdk.vm.ci.code.MemoryBarriers.LOAD_STORE;
import static jdk.vm.ci.code.MemoryBarriers.STORE_STORE;
import static uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.GLOBAL_REGION_NAME;
import static uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.LOCAL_REGION_NAME;
import static uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.PRIVATE_REGION_NAME;

import java.nio.ByteOrder;
import java.util.List;
import java.util.Set;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.common.architecture.ArchitectureRegister;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVRuntimeType;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.meta.SPIRVMemorySpace;

/**
 * It represents a SPIR-V Architecture.
 *
 * <p>
 * It contains information such as byte ordering, platform king, memory
 * alignment, etc.
 * </p>
 *
 */
public class SPIRVArchitecture extends Architecture {

    public static final SPIRVMemoryBase kernelContextSpace = new SPIRVMemoryBase(0, GLOBAL_REGION_NAME, SPIRVMemorySpace.GLOBAL, SPIRVKind.OP_TYPE_INT_8);
    public static final SPIRVMemoryBase localSpace = new SPIRVMemoryBase(1, LOCAL_REGION_NAME, SPIRVMemorySpace.LOCAL, SPIRVKind.OP_TYPE_INT_8);
    public static final SPIRVMemoryBase privateSpace = new SPIRVMemoryBase(2, PRIVATE_REGION_NAME, SPIRVMemorySpace.PRIVATE, SPIRVKind.OP_TYPE_INT_8);
    private static final int NATIVE_CALL_DISPLACEMENT_OFFSET = 0;
    private static final int RETURN_ADDRESS_SIZE = 0;
    public static String BACKEND_ARCHITECTURE = "TornadoVM SPIR-V";
    private final static List<Register> EMPTY_LIST = List.of();

    public SPIRVArchitecture(SPIRVKind wordKind, ByteOrder byteOrder, SPIRVRuntimeType runtime) {
        super(BACKEND_ARCHITECTURE + "@" + runtime.name(), wordKind, byteOrder, false, EMPTY_LIST, LOAD_STORE | STORE_STORE, NATIVE_CALL_DISPLACEMENT_OFFSET, RETURN_ADDRESS_SIZE);
    }

    @Override
    public boolean canStoreValue(Register.RegisterCategory category, PlatformKind kind) {
        return false;
    }

    @Override
    public PlatformKind getLargestStorableKind(Register.RegisterCategory category) {
        return SPIRVKind.OP_TYPE_INT_64;
    }

    @Override
    public PlatformKind getPlatformKind(JavaKind javaKind) {
        return switch (javaKind) {
            case Boolean -> SPIRVKind.OP_TYPE_BOOL;
            case Byte -> SPIRVKind.OP_TYPE_INT_8;
            case Short -> SPIRVKind.OP_TYPE_FLOAT_16;
            case Char -> SPIRVKind.OP_TYPE_INT_8;
            case Int -> SPIRVKind.OP_TYPE_INT_32;
            case Long -> SPIRVKind.OP_TYPE_INT_64;
            case Float -> SPIRVKind.OP_TYPE_FLOAT_32;
            case Double -> SPIRVKind.OP_TYPE_FLOAT_64;
            case Object -> getWordKind();
            case Void -> SPIRVKind.OP_TYPE_VOID;
            case Illegal -> SPIRVKind.ILLEGAL;
            default -> throw new RuntimeException("Java Type for SPIR-V not supported: " + javaKind.name());
        };
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
    public Set<AMD64.CPUFeature> getFeatures() {
        TornadoInternalError.unimplemented();
        return null;
    }

    @Override
    public int getReturnAddressSize() {
        return this.getWordSize();
    }

    public static class SPIRVRegister extends ArchitectureRegister {
        public SPIRVRegister(int number, String name, SPIRVKind lirKind) {
            super(number, name, lirKind);
        }
    }

    public static class SPIRVMemoryBase extends SPIRVRegister {
        final SPIRVMemorySpace memorySpace;

        public SPIRVMemoryBase(int number, String name, SPIRVMemorySpace memorySpace, SPIRVKind kind) {
            super(number, name, kind);
            this.memorySpace = memorySpace;
        }

        public SPIRVMemorySpace getMemorySpace() {
            return memorySpace;
        }

        @Override
        public String getDeclaration() {
            return String.format("%s %s *%s", memorySpace.getName(), lirKind.toString(), name);
        }
    }
}
