package uk.ac.manchester.tornado.annotation;

import uk.ac.manchester.tornado.runtime.common.ParallelAnnotationProvider;

public class ParallelAnnotation implements ParallelAnnotationProvider {
    private final int start;
    private final int length;
    private final int index;

    public ParallelAnnotation(int start, int length, int index) {
        this.start = start;
        this.length = length;
        this.index = index;
    }

    @Override
    public int getStart() {
        return start;
    }

    @Override
    public int getLength() {
        return length;
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public String toString() {
        return String.format("[local @ index %d]: %s", index);
    }
}
