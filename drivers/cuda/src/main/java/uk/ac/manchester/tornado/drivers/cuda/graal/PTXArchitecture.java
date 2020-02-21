package uk.ac.manchester.tornado.drivers.cuda.graal;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.Register.RegisterCategory;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.meta.PTXMemorySpace;

import java.nio.ByteOrder;

import static jdk.vm.ci.code.MemoryBarriers.LOAD_STORE;
import static jdk.vm.ci.code.MemoryBarriers.STORE_STORE;

public class PTXArchitecture extends Architecture {

    public static final RegisterCategory PTX_ABI = new RegisterCategory("abi");

    public static PTXParam HEAP_POINTER;
    public static PTXParam STACK_POINTER;
    public static PTXParam ARG_START = new PTXParam(PTXAssemblerConstants.ARG_START, PTXKind.U8);
    public static PTXParam[] abiRegisters;

    public PTXArchitecture(PTXKind wordKind, ByteOrder byteOrder) {
        super("Tornado PTX",
                wordKind,
                byteOrder,
                false,
                null,
                LOAD_STORE | STORE_STORE,
                0,
                0
        );

        HEAP_POINTER = new PTXParam(PTXAssemblerConstants.HEAP_PTR_NAME, wordKind);
        STACK_POINTER = new PTXParam(PTXAssemblerConstants.STACK_PTR_NAME, wordKind);

        abiRegisters = new PTXParam[]{HEAP_POINTER, STACK_POINTER, ARG_START};
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

    public static class PTXRegister {
        public final int number;
        protected String name;
        public final PTXKind lirKind;

        public PTXRegister(int number, PTXKind lirKind) {
            this.number = number;
            this.lirKind = lirKind;
            this.name = "r" + lirKind.getTypeChar() + number;
        }

        public String getDeclaration() {
            return String.format(".reg .%s %s", lirKind.toString(), name);
        }

        public String getName() {
            return name;
        }
    }

    public static class PTXParam extends PTXRegister {

        public PTXParam(String name, PTXKind lirKind) {
            super(0, lirKind);
            this.name = name;
        }

        @Override
        public String getDeclaration() {
            return String.format(".param .%s %s", lirKind.toString(), name);
        }
    }

    public static class PTXMemoryBase extends PTXRegister {

        public final PTXMemorySpace memorySpace;

        public PTXMemoryBase(int number, PTXMemorySpace memorySpace) {
            super(number, PTXKind.B64);
            this.memorySpace = memorySpace;
        }
    }

    public static final PTXMemoryBase globalSpace = new PTXMemoryBase(0, PTXMemorySpace.GLOBAL);
}
