package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeModuleFormat {

    // < Format is SPIRV IL format
    public static final int ZE_MODULE_FORMAT_IL_SPIRV = 0;

    //< Format is device native format
    public static final int ZE_MODULE_FORMAT_NATIVE = 1;

    public static final int ZE_MODULE_FORMAT_FORCE_UINT32 = 0x7fffffff;

    private ZeModuleFormat() {}
}
