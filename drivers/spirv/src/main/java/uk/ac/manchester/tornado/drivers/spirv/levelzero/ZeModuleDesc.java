package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeModuleDesc {

    private final int stype;
    private long pNext;
    private int format;

    private int inputSize;
    private byte[] pInputModule;
    private String pBuildFlags;

    // Unfold ze_module_constants_t structure from level-zero
    private int numConstants;
    private long pConstantsIds;
    private long pConstantValues;

    private long ptrZeModuleDesc;

    public ZeModuleDesc() {
        this.stype = Ze_Structure_Type.ZE_STRUCTURE_TYPE_MODULE_DESC;
        this.ptrZeModuleDesc = -1;
    }

    public int getStype() {
        return stype;
    }

    public long getpNext() {
        return pNext;
    }

    public int getFormat() {
        return format;
    }

    public int getInputSize() {
        return inputSize;
    }

    public byte[] getInputModule() {
        return pInputModule;
    }

    public String getpBuildFlags() {
        return pBuildFlags;
    }

    public int getNumConstants() {
        return numConstants;
    }

    public long getpConstantsIds() {
        return pConstantsIds;
    }

    public long getpConstantValues() {
        return pConstantValues;
    }

    public void setFormat(int format) {
        this.format = format;
    }

    public void setInputSize(int inputSize) {
        this.inputSize = inputSize;
    }

    public void setBuildFlags(String buildFlags) {
        this.pBuildFlags = buildFlags;
    }

    public void setInputModule(byte[] inputModule) {
        this.pInputModule = inputModule;
    }
}
