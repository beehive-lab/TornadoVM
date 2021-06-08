package uk.ac.manchester.tornado.drivers.spirv.graal;

import static jdk.vm.ci.code.MemoryBarriers.LOAD_STORE;
import static jdk.vm.ci.code.MemoryBarriers.STORE_STORE;
import static uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.FRAME_BASE_NAME;
import static uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.HEAP_REF_NAME;

import java.nio.ByteOrder;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.meta.SPIRVMemorySpace;

/**
 * It represents a SPIRV Architecture.
 * <p>
 * It contains information such as byte ordering, platform king, memory
 * alignment, etc.
 *
 */
public class SPIRVArchitecture extends Architecture {

    private static final int NATIVE_CALL_DISPLACEMENT_OFFSET = 0;
    private static final int RETURN_ADDRESS_SIZE = 0;

    public static final Register.RegisterCategory SPIRV_ABI = new Register.RegisterCategory("abi");

    public static final SPIRVMemoryBase globalSpace = new SPIRVMemoryBase(0, HEAP_REF_NAME, SPIRVMemorySpace.GLOBAL, SPIRVKind.OP_TYPE_INT_8);
    public static final SPIRVMemoryBase constantSpace = new SPIRVMemoryBase(2, HEAP_REF_NAME, SPIRVMemorySpace.CONSTANT, SPIRVKind.OP_TYPE_INT_8);
    public static final SPIRVMemoryBase localSpace = new SPIRVMemoryBase(3, HEAP_REF_NAME, SPIRVMemorySpace.LOCAL, SPIRVKind.OP_TYPE_INT_8);
    public static final SPIRVMemoryBase privateSpace = new SPIRVMemoryBase(4, HEAP_REF_NAME, SPIRVMemorySpace.PRIVATE, SPIRVKind.OP_TYPE_INT_8);

    private SPIRVRegister[] abiRegisters;
    private SPIRVRegister sp;

    public SPIRVArchitecture(SPIRVKind wordKind, ByteOrder byteOrder) {
        super("TornadoVM SPIRV", wordKind, byteOrder, false, null, LOAD_STORE | STORE_STORE, NATIVE_CALL_DISPLACEMENT_OFFSET, RETURN_ADDRESS_SIZE);
        sp = new SPIRVRegister(1, FRAME_BASE_NAME, wordKind);
        // abiRegisters = new SPIRVRegister[] { globalSpace, sp, constantSpace,
        // localSpace };
        abiRegisters = new SPIRVRegister[] { globalSpace, sp };
    }

    // FIXME <REFACTOR> ABSTRACT ALL Backends (AAB)
    public static class SPIRVRegister {

        public final int number;
        public final String name;
        public final SPIRVKind lirKind;

        public SPIRVRegister(int number, String name, SPIRVKind lirKind) {
            this.number = number;
            this.name = name;
            this.lirKind = lirKind;
        }

        public String getDeclaration() {
            return String.format("%s %s", lirKind.toString(), name);
        }

        public String getName() {
            return name;
        }
    }

    // FIXME <REFACTOR> (AAB)
    public static class SPIRVMemoryBase extends SPIRVRegister {

        public final SPIRVMemorySpace memorySpace;

        public SPIRVMemoryBase(int number, String name, SPIRVMemorySpace memorySpace, SPIRVKind kind) {
            super(number, name, kind);
            this.memorySpace = memorySpace;
        }

        @Override
        public String getDeclaration() {
            return String.format("%s %s *%s", memorySpace.getName(), lirKind.toString(), name);
        }

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
        switch (javaKind) {
            case Boolean:
                return SPIRVKind.OP_TYPE_BOOL;
            case Byte:
                return SPIRVKind.OP_TYPE_INT_8;
            case Short:
                return SPIRVKind.OP_TYPE_FLOAT_16;
            case Char:
                return SPIRVKind.OP_TYPE_INT_8;
            case Int:
                return SPIRVKind.OP_TYPE_INT_32;
            case Long:
                return SPIRVKind.OP_TYPE_INT_64;
            case Float:
                return SPIRVKind.OP_TYPE_FLOAT_32;
            case Double:
                return SPIRVKind.OP_TYPE_FLOAT_64;
            case Object:
                return getWordKind();
            case Void:
                return SPIRVKind.OP_TYPE_VOID;
            case Illegal:
                return SPIRVKind.ILLEGAL;
            default:
                throw new RuntimeException("Java Type for SPIR-V not supported: " + javaKind.name());

        }
    }

    @Override
    public int getReturnAddressSize() {
        return this.getWordSize();
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

}
