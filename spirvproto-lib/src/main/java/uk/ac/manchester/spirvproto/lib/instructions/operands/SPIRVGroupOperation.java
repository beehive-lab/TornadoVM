package uk.ac.manchester.spirvproto.lib.instructions.operands;

import javax.annotation.Generated;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVGroupOperation extends SPIRVEnum {

    protected SPIRVGroupOperation(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }


    public static SPIRVGroupOperation Reduce() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVGroupOperation(0, "Reduce", params, SPIRVCapability.Kernel());
    }
    public static SPIRVGroupOperation InclusiveScan() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVGroupOperation(1, "InclusiveScan", params, SPIRVCapability.Kernel());
    }
    public static SPIRVGroupOperation ExclusiveScan() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVGroupOperation(2, "ExclusiveScan", params, SPIRVCapability.Kernel());
    }
}
