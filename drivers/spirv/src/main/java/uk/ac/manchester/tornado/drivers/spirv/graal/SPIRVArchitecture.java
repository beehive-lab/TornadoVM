package uk.ac.manchester.tornado.drivers.spirv.graal;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.meta.SPIRVMemorySpace;

import java.nio.ByteOrder;

import static jdk.vm.ci.code.MemoryBarriers.LOAD_STORE;
import static jdk.vm.ci.code.MemoryBarriers.STORE_STORE;
import static uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.HEAP_REF_NAME;

/**
 * It represents a SPIRV Architecture.
 * <p>
 * It contains information such as byte ordering, platform king, memory
 * alignment, etc.
 */
public class SPIRVArchitecture extends Architecture {

    private static final int NATIVE_CALL_DISPLACEMENT_OFFSET = 0;
    private static final int RETURN_ADDRESS_SIZE = 0;

    public static final SPIRVMemoryBase globalSpace = new SPIRVMemoryBase(0, HEAP_REF_NAME, SPIRVMemorySpace.GLOBAL, SPIRVKind.OP_TYPE_INT_8);

    public SPIRVArchitecture(SPIRVKind wordKind, ByteOrder byteOrder) {
        // FIXME: REVISIT THIS CONSTRUCTOR
        super("TornadoVM SPIRV", wordKind, byteOrder, false, null, LOAD_STORE | STORE_STORE, NATIVE_CALL_DISPLACEMENT_OFFSET, RETURN_ADDRESS_SIZE);
    }

    // TODO: ABSTRACT ALL Backends (AAB)
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
        return null;
    }

    @Override
    public PlatformKind getPlatformKind(JavaKind javaKind) {
        return null;
    }
}
