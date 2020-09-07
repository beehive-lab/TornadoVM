package uk.ac.manchester.tornado.drivers.ptx.graal.meta;

import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants;

//public class PTXMemorySpace extends Value {

    public enum PTXMemorySpace {

        GLOBAL(0, PTXAssemblerConstants.GLOBAL_MEM_MODIFIER),
        PARAM(1, PTXAssemblerConstants.PARAM_MEM_MODIFIER),
        SHARED(2, PTXAssemblerConstants.SHARED_MEM_MODIFIER),
        LOCAL(3, PTXAssemblerConstants.LOCAL_MEM_MODIFIER);

        private final int index;
        private final String name;

        PTXMemorySpace(int index, String name) {
            this.index = index;
            this.name = name;
        }

        public int index() {
            return index;
        }

        public String getName() {
            return name;
        }
//    }

//    public static final PTXMemorySpace GLOBAL = new PTXMemorySpace(PTXAssemblerConstants.GLOBAL_MEM_MODIFIER);
//    public static final PTXMemorySpace PARAM = new PTXMemorySpace(PTXAssemblerConstants.PARAM_MEM_MODIFIER);
//    public static final PTXMemorySpace SHARED = new PTXMemorySpace(PTXAssemblerConstants.SHARED_MEM_MODIFIER);
//    public static final PTXMemorySpace LOCAL = new PTXMemorySpace(PTXAssemblerConstants.LOCAL_MEM_MODIFIER);
//
//    private final String name;
//
//    private PTXMemorySpace(String name) {
//        super(LIRKind.Illegal);
//        this.name = name;
//    }
//
//    public String name() {
//        return name;
//    }
}
