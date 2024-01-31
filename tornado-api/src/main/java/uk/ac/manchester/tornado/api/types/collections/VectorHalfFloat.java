package uk.ac.manchester.tornado.api.types.collections;

import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.utils.FloatOps;

import java.nio.ShortBuffer;

public final class VectorHalfFloat implements TornadoCollectionInterface<ShortBuffer> {

    private static final int ELEMENT_SIZE = 1;
    private final int numElements;
    private final HalfFloatArray storage;

    public VectorHalfFloat(int numElements, HalfFloatArray array) {
        this.numElements = numElements;
        this.storage = array;
    }

    /**
     * Creates an empty vector with.
     *
     * @param numElements
     *     Number of elements
     */
    public VectorHalfFloat(int numElements) {
        this(numElements, new HalfFloatArray(numElements));
    }

    /**
     * Creates a new vector from the provided storage.
     *
     * @param storage
     *     Array to be stored
     */
    public VectorHalfFloat(HalfFloatArray storage) {
        this(storage.getSize() / ELEMENT_SIZE, storage);
    }

    //    public static float min(VectorFloat v) {
    //        float result = Float.MAX_VALUE;
    //        for (int i = 0; i < v.storage.getSize(); i++) {
    //            result = Math.min(v.storage.get(i), result);
    //        }
    //        return result;
    //    }
    //
    //    public static float max(VectorFloat v) {
    //        float result = Float.MIN_VALUE;
    //        for (int i = 0; i < v.storage.getSize(); i++) {
    //            result = Math.max(v.storage.get(i), result);
    //        }
    //        return result;
    //    }

    /**
     * Performs Dot-product.
     *
     * @return dot-product value
     */
    public static float dot(VectorHalfFloat a, VectorHalfFloat b) {
        float sum = 0;
        for (int i = 0; i < a.size(); i++) {
            sum += HalfFloat.mult(a.get(i), b.get(i)).getHalfFloatValue();
        }
        return sum;
    }

    public HalfFloatArray getArray() {
        return storage;
    }

    /**
     * Returns the float at the given index of this vector.
     *
     * @param index
     *     Position
     * @return value
     */
    public HalfFloat get(int index) {
        return storage.get(index);
    }

    /**
     * Sets the float at the given index of this vector.
     *
     * @param index
     *     Position
     * @param value
     *     Float value to be stored
     */
    public void set(int index, HalfFloat value) {
        storage.set(index, value);
    }

    /**
     * Sets the elements of this vector to that of the provided vector.
     *
     * @param values
     *     VectorFloat4
     */
    public void set(VectorHalfFloat values) {
        for (int i = 0; i < values.storage.getSize(); i++) {
            storage.set(i, values.storage.get(i));
        }
    }

    /**
     * Sets the elements of this vector to that of the provided array.
     *
     * @param values
     *     Set input array as internal stored
     */
    //TODO: implement properly/check
    public void set(HalfFloat[] values) {
        System.arraycopy(values, 0, storage, 0, values.length);
    }

    /**
     * Sets all elements to value.
     *
     * @param value
     *     Fill input array with value
     */
    public void fill(HalfFloat value) {
        storage.init(value);
    }

    /**
     * Returns slice of this vector.
     *
     * @param start
     *     starting index
     * @param length
     *     number of elements
     */
    public VectorHalfFloat subVector(int start, int length) {
        final VectorHalfFloat v = new VectorHalfFloat(length);
        for (int i = 0; i < length; i++) {
            v.storage.set(i, storage.get(i + start));
        }
        return v;
    }

    /**
     * Duplicates this vector.
     *
     */
    public VectorHalfFloat duplicate() {
        HalfFloatArray cp = new HalfFloatArray(storage.getSize());
        for (int i = 0; i < cp.getSize(); i++) {
            cp.set(i, storage.get(i));
        }
        return new VectorHalfFloat(cp);
    }

    /**
     * Vector equality test.
     *
     * @param vector
     *     input vector
     * @return true if vectors match
     */
    public boolean isEqual(VectorHalfFloat vector) {
        return TornadoMath.isEqual(storage, vector.storage);
    }

    /**
     * Prints the vector using the specified format string.
     *
     * @param fmt
     *     String Format
     * @return String
     */
    public String toString(String fmt) {
        StringBuilder sb = new StringBuilder("[");
        sb.append("[ ");
        for (int i = 0; i < numElements; i++) {
            sb.append(String.format(fmt, get(i)) + " ");
        }
        sb.append("]");
        return sb.toString();
    }

    public String toString() {
        String str = String.format("VectorFloat <%d>", numElements);
        if (numElements < 32) {
            str += toString(FloatOps.FMT);
        }
        return str;
    }

    @Override
    public void loadFromBuffer(ShortBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public ShortBuffer asBuffer() {
        //TODO
        return null;
        // return ShortBuffer.wrap(storage.toHeapArray());
    }

    @Override
    public int size() {
        return numElements;
    }

    public int getLength() {
        return numElements;
    }

    public void clear() {
        storage.clear();
    }

}
