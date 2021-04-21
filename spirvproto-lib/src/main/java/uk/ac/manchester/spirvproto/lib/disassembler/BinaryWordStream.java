package uk.ac.manchester.spirvproto.lib.disassembler;

import java.io.IOException;
import java.nio.ByteOrder;

public interface BinaryWordStream {
    /**
     * Reads the next 32 bit word.
     * @return The next word.
     * @throws IOException
     */
    int getNextWord() throws IOException;

    void setEndianness(ByteOrder endianness);
    ByteOrder getEndianness();
}
