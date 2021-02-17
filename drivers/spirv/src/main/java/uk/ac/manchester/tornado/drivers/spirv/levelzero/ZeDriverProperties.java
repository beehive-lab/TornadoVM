package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeDriverProperties {

    public static final int ZE_MAX_DRIVER_UUID_SIZE = 16;

    int type;
    long pNext;
    int[] uuid;
    int driverVersion;

    long nativePointer;

    public ZeDriverProperties() {
        this.type = Ze_Structure_Type.ZE_STRUCTURE_TYPE_DRIVER_PROPERTIES;
        pNext = 0;
        uuid = new int[ZE_MAX_DRIVER_UUID_SIZE];
        this.nativePointer = -1;
    }

    public ZeDriverProperties(int type) {
        this.type = type;
        pNext = 0;
        uuid = new int[ZE_MAX_DRIVER_UUID_SIZE];
        this.nativePointer = -1;
    }

    public int getDriverVersion() {
        return this.driverVersion;
    }

}
