package uk.ac.manchester.spirvproto.lib.instructions.operands;

import javax.annotation.Generated;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVLinkageType extends SPIRVEnum {

    protected SPIRVLinkageType(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }


    public static SPIRVLinkageType Export() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVLinkageType(0, "Export", params, SPIRVCapability.Linkage());
    }
    public static SPIRVLinkageType Import() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVLinkageType(1, "Import", params, SPIRVCapability.Linkage());
    }
}
