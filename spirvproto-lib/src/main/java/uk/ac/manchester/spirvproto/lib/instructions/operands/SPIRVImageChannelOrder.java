package uk.ac.manchester.spirvproto.lib.instructions.operands;

import javax.annotation.Generated;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVImageChannelOrder extends SPIRVEnum {

    protected SPIRVImageChannelOrder(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }


    public static SPIRVImageChannelOrder R() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelOrder(0, "R", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelOrder A() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelOrder(1, "A", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelOrder RG() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelOrder(2, "RG", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelOrder RA() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelOrder(3, "RA", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelOrder RGB() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelOrder(4, "RGB", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelOrder RGBA() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelOrder(5, "RGBA", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelOrder BGRA() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelOrder(6, "BGRA", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelOrder ARGB() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelOrder(7, "ARGB", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelOrder Intensity() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelOrder(8, "Intensity", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelOrder Luminance() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelOrder(9, "Luminance", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelOrder Rx() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelOrder(10, "Rx", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelOrder RGx() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelOrder(11, "RGx", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelOrder RGBx() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelOrder(12, "RGBx", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelOrder Depth() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelOrder(13, "Depth", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelOrder DepthStencil() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelOrder(14, "DepthStencil", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelOrder sRGB() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelOrder(15, "sRGB", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelOrder sRGBx() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelOrder(16, "sRGBx", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelOrder sRGBA() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelOrder(17, "sRGBA", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelOrder sBGRA() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelOrder(18, "sBGRA", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelOrder ABGR() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelOrder(19, "ABGR", params, SPIRVCapability.Kernel());
    }
}
