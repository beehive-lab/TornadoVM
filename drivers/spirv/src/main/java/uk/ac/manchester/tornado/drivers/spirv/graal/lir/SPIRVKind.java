package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import jdk.vm.ci.meta.PlatformKind;

public enum SPIRVKind implements PlatformKind {

    U32(4, null);

    SPIRVKind(int size, Class<?> javaClass) {
        this(size, javaClass, null);
    }

    final SPIRVKind kind;
    int size;

    SPIRVKind(int size, Class<?> javaClass, SPIRVKind kind) {
        this.kind = this;
        this.size = size;
    }

    @Override
    public Key getKey() {
        return null;
    }

    @Override
    public int getSizeInBytes() {
        return size;
    }

    @Override
    public int getVectorLength() {
        return 0;
    }

    @Override
    public char getTypeChar() {
        return 0;
    }
}
