package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public enum ZeDeviceType {

    /**
     * Graphics Processing Unit
     */
    ZE_DEVICE_TYPE_GPU(1),

    /**
     * Central Processing Unit
     */
    ZE_DEVICE_TYPE_CPU(2),

    /**
     * Field Programmable Gate Array
     */
    ZE_DEVICE_TYPE_FPGA(3),

    /**
     * Memory Copy Accelerator
     */
    ZE_DEVICE_TYPE_MCA(4),

    ZE_DEVICE_TYPE_FORCE_UINT32(0x7fffffff);

    int value;

    ZeDeviceType(int value) {
        this.value = value;
    }

    int getValue() {
        return value;
    }
}
