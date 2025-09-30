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
package uk.ac.manchester.tornado.drivers.metal.graal;

import static jdk.vm.ci.code.MemoryBarriers.LOAD_STORE;
import static jdk.vm.ci.code.MemoryBarriers.STORE_STORE;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssemblerConstants.ATOMICS_REGION_NAME;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssemblerConstants.CONSTANT_REGION_NAME;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssemblerConstants.GLOBAL_REGION_NAME;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssemblerConstants.KERNEL_CONTEXT;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssemblerConstants.LOCAL_REGION_NAME;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssemblerConstants.PRIVATE_REGION_NAME;

import java.nio.ByteOrder;
import java.util.Set;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.Register.RegisterCategory;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.common.architecture.ArchitectureRegister;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind;
import uk.ac.manchester.tornado.drivers.metal.graal.meta.MetalMemorySpace;

public class MetalArchitecture extends Architecture {

    public static final RegisterCategory Metal_ABI = new RegisterCategory("abi");
    public static final MetalMemoryBase globalSpace = new MetalMemoryBase(0, GLOBAL_REGION_NAME, MetalMemorySpace.GLOBAL, MetalKind.UCHAR);
    public static final MetalMemoryBase kernelContext = new MetalMemoryBase(1, KERNEL_CONTEXT, MetalMemorySpace.GLOBAL, MetalKind.LONG);
    public static final MetalMemoryBase constantSpace = new MetalMemoryBase(2, CONSTANT_REGION_NAME, MetalMemorySpace.CONSTANT, MetalKind.UCHAR);
    public static final MetalMemoryBase localSpace = new MetalMemoryBase(3, LOCAL_REGION_NAME, MetalMemorySpace.LOCAL, MetalKind.UCHAR);
    public static final MetalMemoryBase privateSpace = new MetalMemoryBase(4, PRIVATE_REGION_NAME, MetalMemorySpace.PRIVATE, MetalKind.UCHAR);
    public static final MetalMemoryBase atomicSpace = new MetalMemoryBase(5, ATOMICS_REGION_NAME, MetalMemorySpace.GLOBAL, MetalKind.INT);

    public static MetalRegister[] abiRegisters;

    public MetalArchitecture(final MetalKind wordKind, final ByteOrder byteOrder) {
        super("Tornado Metal", wordKind, byteOrder, false, null, LOAD_STORE | STORE_STORE, 0, 0);
        abiRegisters = new MetalRegister[] { kernelContext, constantSpace, localSpace, atomicSpace };
    }

    @Override
    public PlatformKind getPlatformKind(JavaKind javaKind) {
        MetalKind oclKind = MetalKind.ILLEGAL;
        switch (javaKind) {
            case Boolean:
                oclKind = MetalKind.BOOL;
                break;
            case Byte:
                oclKind = MetalKind.CHAR;
                break;
            case Short:
                oclKind = (javaKind.isUnsigned()) ? MetalKind.USHORT : MetalKind.SHORT;
                break;
            case Char:
                oclKind = MetalKind.USHORT;
                break;
            case Int:
                oclKind = (javaKind.isUnsigned()) ? MetalKind.UINT : MetalKind.INT;
                break;
            case Long:
                oclKind = (javaKind.isUnsigned()) ? MetalKind.ULONG : MetalKind.LONG;
                break;
            case Float:
                oclKind = MetalKind.FLOAT;
                break;
            case Double:
                oclKind = MetalKind.DOUBLE;
                break;
            case Object:
                oclKind = (MetalKind) getWordKind();
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
        return MetalKind.LONG;
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

    public static class MetalRegister extends ArchitectureRegister {
        public MetalRegister(int number, String name, MetalKind lirKind) {
            super(number, name, lirKind);
        }
    }

    public static class MetalMemoryBase extends MetalRegister {

        final MetalMemorySpace memorySpace;

        public MetalMemoryBase(int number, String name, MetalMemorySpace memorySpace, MetalKind kind) {
            super(number, name, kind);
            this.memorySpace = memorySpace;
        }

        public MetalMemorySpace getMemorySpace() {
            return memorySpace;
        }

        @Override
        public String getDeclaration() {
            return String.format("%s %s *%s", memorySpace.name(), lirKind.toString(), name);
        }

    }
}
