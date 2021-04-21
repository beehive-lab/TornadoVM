package uk.ac.manchester.spirvproto.lib.disassembler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;

public class SPIRVLine {
    private final Iterator<Integer> operands;
    private final ByteOrder byteOrder;

    public SPIRVLine(Iterator<Integer> operands, ByteOrder byteOrder) {
        this.operands = operands;
        this.byteOrder = byteOrder;
    }

    public int next() {
        return operands.next();
    }

    public byte[] nextInBytes() {
        return ByteBuffer.allocate(4).order(byteOrder).putInt(operands.next()).array();
    }

    public boolean hasNext() {
        return operands.hasNext();
    }
}
