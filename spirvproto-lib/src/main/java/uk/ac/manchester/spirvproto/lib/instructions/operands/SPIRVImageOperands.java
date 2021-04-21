package uk.ac.manchester.spirvproto.lib.instructions.operands;

import javax.annotation.Generated;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVImageOperands extends SPIRVEnum {

    protected SPIRVImageOperands(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }

    public void add(SPIRVImageOperands other) {
        if (this.value == 0) this.name = other.name;
        else if (other.value != 0) this.name += "|" + other.name;

        this.value |= other.value;
        this.parameters.addAll(other.parameters);
        SPIRVCapability[] oldCapabilities = this.capabilities;
        this.capabilities = new SPIRVCapability[oldCapabilities.length + other.capabilities.length];
        int capPos = 0;
        for (SPIRVCapability capability : oldCapabilities) {
            this.capabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : other.capabilities) {
            this.capabilities[capPos++] = capability;
        }
    }

    public static SPIRVImageOperands None() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageOperands(0x0000, "None", params);
    }
    public static SPIRVImageOperands Bias(SPIRVId parameter0) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(parameter0);
        return new SPIRVImageOperands(0x0001, "Bias", params, SPIRVCapability.Shader());
    }
    public static SPIRVImageOperands Lod(SPIRVId parameter0) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(parameter0);
        return new SPIRVImageOperands(0x0002, "Lod", params);
    }
    public static SPIRVImageOperands Grad(SPIRVId parameter0, SPIRVId parameter1) {
        List<SPIRVOperand> params = new ArrayList<>(2);
        params.add(parameter0);
        params.add(parameter1);
        return new SPIRVImageOperands(0x0004, "Grad", params);
    }
    public static SPIRVImageOperands ConstOffset(SPIRVId parameter0) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(parameter0);
        return new SPIRVImageOperands(0x0008, "ConstOffset", params);
    }
    public static SPIRVImageOperands Offset(SPIRVId parameter0) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(parameter0);
        return new SPIRVImageOperands(0x0010, "Offset", params, SPIRVCapability.ImageGatherExtended());
    }
    public static SPIRVImageOperands ConstOffsets(SPIRVId parameter0) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(parameter0);
        return new SPIRVImageOperands(0x0020, "ConstOffsets", params);
    }
    public static SPIRVImageOperands Sample(SPIRVId parameter0) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(parameter0);
        return new SPIRVImageOperands(0x0040, "Sample", params);
    }
    public static SPIRVImageOperands MinLod(SPIRVId parameter0) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(parameter0);
        return new SPIRVImageOperands(0x0080, "MinLod", params, SPIRVCapability.MinLod());
    }
}
