package uk.ac.manchester.spirvproto.lib.disassembler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SPVByteStreamReader implements BinaryWordStream {

    private final InputStream input;
    private ByteOrder endianness;

    public SPVByteStreamReader(InputStream input) {
        this.input = input;
        endianness = ByteOrder.LITTLE_ENDIAN;
    }

    @Override
    public int getNextWord() throws IOException {
        byte[] bytes = new byte[4];
        int status = input.read(bytes);
        if (status == -1) return -1;
        return ByteBuffer
                .wrap(bytes)
                .order(endianness)
                .getInt();
    }

    @Override
    public void setEndianness(ByteOrder endianness) {
        this.endianness = endianness;
    }

    @Override
    public ByteOrder getEndianness() {
        return endianness;
    }
}
