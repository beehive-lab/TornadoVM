package uk.ac.manchester.spirvproto.lib;

import java.nio.ByteBuffer;

public class SPIRVHeader {
    public final int magicNumber = 0x7230203;
    public final int majorVersion;
    public final int minorVersion;
    public final int genMagicNumber;
    public int bound;
    public final int schema;


    public SPIRVHeader(int version, int genMagicNumber, int bound, int schema) {
        this((version >> 16) & 0xFF, (version >> 8)  & 0xFF, genMagicNumber, bound, schema);
    }

    public SPIRVHeader(int majorVersion, int minorVersion, int genMagicNumber, int bound, int schema) {
        this.genMagicNumber = genMagicNumber;
        this.bound = bound;
        this.schema = schema;
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
    }

    @Override
    public String toString() {

        return String.format("; MagicNumber: 0x%x\n", magicNumber) +
                String.format("; Version: %d.%d\n", majorVersion, minorVersion) +
                String.format("; Generator ID: %d\n", genMagicNumber >> 16) +
                String.format("; Bound: %d\n", bound) +
                String.format("; Schema: %d\n", schema);
    }

    public void write(ByteBuffer output) {
        int version = (minorVersion << 8) | (majorVersion << 16);
        output.putInt(magicNumber)
              .putInt(version)
              .putInt(genMagicNumber)
              .putInt(bound)
              .putInt(schema);
    }

    public void setBound(int currentBound) {
        bound = currentBound;
    }
}
