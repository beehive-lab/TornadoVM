package tornado.meta.domain;

public class IntDomain implements Domain {

    private int offset;
    private int step;
    private int length;

    public IntDomain(int offset, int step, int length) {
        this.offset = offset;
        this.step = step;
        this.length = length;
    }

    public IntDomain(int length) {
        this(0, 1, length);
    }

    @Override
    public int cardinality() {
        return length;
    }

    @Override
    public int map(int index) {
        return (index * step) + offset;
    }

    @Override
    public String toString() {
        return String.format("IntDomain: {offset=%d, step=%d, length=%d}", offset, step, length);
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getStep() {
        return step;
    }

    public void setStep(int step) {
        this.step = step;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

}
