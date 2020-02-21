package uk.ac.manchester.tornado.drivers.cuda.graal;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.meta.PTXMemorySpace;

import java.nio.ByteOrder;

import static jdk.vm.ci.code.MemoryBarriers.LOAD_STORE;
import static jdk.vm.ci.code.MemoryBarriers.STORE_STORE;

public class PTXArchitecture extends Architecture {

    public PTXArchitecture(PlatformKind wordKind, ByteOrder byteOrder) {
        super("Tornado PTX",
                wordKind,
                byteOrder,
                false,
                null,
                LOAD_STORE | STORE_STORE,
                0,
                0
        );
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

    public static class PTXRegister {
        public final int number;
        public final String name;
        public final PTXKind lirKind;

        public PTXRegister(int number, PTXKind lirKind) {
            this.number = number;
            this.lirKind = lirKind;
            this.name = "r" + lirKind.getTypeChar() + number;
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
