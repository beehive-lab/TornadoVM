package uk.ac.manchester.spirvproto.lib.instructions.operands;

import javax.annotation.Generated;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVFPRoundingMode extends SPIRVEnum {

    protected SPIRVFPRoundingMode(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }


    public static SPIRVFPRoundingMode RTE() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVFPRoundingMode(0, "RTE", params, SPIRVCapability.Kernel(), SPIRVCapability.StorageUniform16(), SPIRVCapability.StoragePushConstant16(), SPIRVCapability.StorageInputOutput16());
    }
    public static SPIRVFPRoundingMode RTZ() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVFPRoundingMode(1, "RTZ", params, SPIRVCapability.Kernel(), SPIRVCapability.StorageUniform16(), SPIRVCapability.StoragePushConstant16(), SPIRVCapability.StorageInputOutput16());
    }
    public static SPIRVFPRoundingMode RTP() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVFPRoundingMode(2, "RTP", params, SPIRVCapability.Kernel(), SPIRVCapability.StorageUniform16(), SPIRVCapability.StoragePushConstant16(), SPIRVCapability.StorageInputOutput16());
    }
    public static SPIRVFPRoundingMode RTN() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVFPRoundingMode(3, "RTN", params, SPIRVCapability.Kernel(), SPIRVCapability.StorageUniform16(), SPIRVCapability.StoragePushConstant16(), SPIRVCapability.StorageInputOutput16());
    }
}
