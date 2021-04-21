package uk.ac.manchester.spirvproto.lib.instructions.operands;

public abstract class SPIRVLiteralContextDependentNumber implements SPIRVOperand {
    public final SPIRVCapability[] capabilities = new SPIRVCapability[0];

    @Override
    public SPIRVCapability[] getCapabilities() {
        return capabilities;
    }
}
