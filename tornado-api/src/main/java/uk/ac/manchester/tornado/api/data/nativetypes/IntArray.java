package uk.ac.manchester.tornado.api.data.nativetypes;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;

public class TornadoVMNativeType {


    private int type_code;

    private MemorySegment segment;

    public TornadoVMNativeType(int numberOfElements, String type) {
        // number of elements * number of bytes
        int byteSize = 0;
        if (type.equals("int")) {
            byteSize = 4;
            type_code = 0;
        } else if (type.equals("float")) {
           byteSize = 4;
           type_code  = 1;
        } else if (type.equals("double")) {
            byteSize = 8;
            type_code = 2;
        } else if (type.equals("long")) {
            byteSize = 8;
            type_code = 3;
        } else {
            //error
        }
        segment = MemorySegment.allocateNative(numberOfElements * byteSize);
    }


    public void set(int index, int value) {
        if (type_code == 0) {
            MemoryAccess.setIntAtIndex(segment, index, value);
        } else if (type_code == 1) {
            MemoryAccess.setFloatAtIndex(segment, index, value);
        } else if (type_code == 2) {
            MemoryAccess.setDoubleAtIndex(segment, index, value);
        } else if (type_code == 3) {
            MemoryAccess.setLongAtIndex(segment, index, value);
        } else {
            // error
        }
    }

    public void setInt(int index, int value) {
        MemoryAccess.setIntAtIndex(segment, index, value);
    }

    public void setFloat(int index, float value) {
        MemoryAccess.setFloatAtIndex(segment, index, value);
    }

    public void setDouble(int index, double value) {
        MemoryAccess.setDoubleAtIndex(segment, index, value);
    }

    public void setLong(int index, long value) {
        MemoryAccess.setLongAtIndex(segment, index, value);
    }

    public int getInt(int index) {
        return MemoryAccess.getIntAtIndex(segment, index);
    }

    public float getFloat(int index) {
        return MemoryAccess.getFloatAtIndex(segment, index);
    }

    public double getDouble(int index) {
        return MemoryAccess.getDoubleAtIndex(segment, index);
    }

    public long getLong(int index) {
        return MemoryAccess.getLongAtIndex(segment, index);
    }

    public void init(int value) {
        if (type_code == 0) {
            for (int i = 0; i < segment.byteSize() / 4; i++) {
                MemoryAccess.setIntAtIndex(segment, i, value);
            }
        } else if (type_code == 1) {
            for (int i = 0; i < segment.byteSize() / 4; i++) {
                MemoryAccess.setFloatAtIndex(segment, i, value);
            }
        } else if (type_code == 2) {
            for (int i = 0; i < segment.byteSize() / 8; i++) {
                MemoryAccess.setDoubleAtIndex(segment, i, value);
            }
        } else if (type_code == 3) {
            for (int i = 0; i < segment.byteSize() / 8; i++) {
                MemoryAccess.setLongAtIndex(segment, i, value);
            }
        } else {
            // error
        }
    }

    public MemorySegment getSegment() {
        return segment;
    }
}
