/*
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.collections.types;

import java.nio.FloatBuffer;

import static java.lang.Float.MAX_VALUE;
import static java.lang.Float.MIN_VALUE;
import static java.lang.String.format;
import static java.nio.FloatBuffer.wrap;
import static tornado.collections.types.Float4.findULPDistance;
import static tornado.collections.types.Float4.sqrt;
import static tornado.collections.types.FloatOps.fmt3;
import static tornado.collections.types.StorageFormats.toRowMajor;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;

public class ImageFloat4 implements PrimitiveStorage<FloatBuffer> {

    /**
     * backing array
     */
    final protected float[] storage;

    /**
     * number of elements in the storage
     */
    final private int numElements;
    final private static int elementSize = 4;

    /**
     * Number of rows
     */
    final protected int Y;

    /**
     * Number of columns
     */
    final protected int X;

    /**
     * Storage format for matrix
     *
     * @param height number of columns
     * @param width  number of rows
     * @param data   array reference which contains data
     */
    public ImageFloat4(int width, int height, float[] array) {
        storage = array;
        X = width;
        Y = height;
        numElements = X * Y * elementSize;
    }

    /**
     * Storage format for matrix
     *
     * @param height number of columns
     * @param width  number of rows
     */
    public ImageFloat4(int width, int height) {
        this(width, height, new float[width * height * elementSize]);
    }

    public ImageFloat4(float[][] matrix) {
        this(matrix.length / elementSize, matrix[0].length / elementSize, toRowMajor(matrix));
    }

    private int toIndex(int x, int y) {
        return elementSize * (x + (y * X));
    }

    public Float4 get(int x) {
        return get(x, 0);
    }

    public void set(int x, Float4 value) {
        set(x, 0, value);
    }

    public Float4 get(int x, int y) {
        final int offset = toIndex(x, y);
        return Float4.loadFromArray(storage, offset);
    }

    public void set(int x, int y, Float4 value) {
        final int offset = toIndex(x, y);
        value.storeToArray(storage, offset);
    }

    public int X() {
        return X;
    }

    public int Y() {
        return Y;
    }

    @Deprecated
    public VectorFloat4 row(int row) {
        shouldNotReachHere();
        return null;
//    	int index = toIndex(0,row);
//    	VectorFloat4 v = new VectorFloat4(X,index,1,getElementSize(),storage);
//    	return v;
    }

    @Deprecated
    public VectorFloat4 column(int col) {
        shouldNotReachHere();
        return null;
//    	int index = toIndex(col, 0);
//    	VectorFloat4 v = new VectorFloat4(Y,index,getStep(),getElementSize(),storage );
//    	return v;
    }

    @Deprecated
    public VectorFloat4 diag() {
        shouldNotReachHere();
        return null;
//    	VectorFloat4 v = new VectorFloat4(Math.min(X,Y), getOffset(), getStep() + 1,getElementSize(),storage);
//    	return v;
    }

    @Deprecated
    public ImageFloat4 subImage(int x0, int y0, int x1, int y1) {
        shouldNotReachHere();
        return null;
//    	int index = get(x0,y0).getOffset();
//    	ImageFloat4 subM = new ImageFloat4(x1,y1,index,getStep(),getElementSize(),storage);
//    	return subM;
    }

    public void fill(float value) {
        for (int i = 0; i < storage.length; i++) {
            storage[i] = value;
        }
    }

    /**
     * Transposes the matrix in-place
     *
     * @param m matrix to transpose
     */
    @Deprecated
    public void transpose() {
        shouldNotReachHere();

//        if(X == Y){
//            // transpose square matrix
//            for(int i=0;i<Y;i++){
//                for(int j=0;j<i;j++){
//                    Float4 tmp = new Float4();
//                    tmp.set(get(i, j));
//                    set(i, j, get(j, i));
//                    set(j, i, tmp);
//                }
//            }
//        } else {
//            // transpose rectangular matrix
//
//        	// not implemented
//
//        }
    }

    public ImageFloat4 duplicate() {
        ImageFloat4 matrix = new ImageFloat4(X, Y);
        matrix.set(this);
        return matrix;
    }

    public void set(ImageFloat4 m) {
        for (int i = 0; i < storage.length; i++) {
            storage[i] = m.storage[i];
        }
    }

    public String toString(String fmt) {
        String str = "";

        for (int i = 0; i < Y; i++) {
            for (int j = 0; j < X; j++) {
                str += get(j, i).toString(fmt) + "\n";
            }
        }

        return str;
    }

    @Override
    public String toString() {
        String result = format("ImageFloat4 <%d x %d>", X, Y);
        if (X <= 8 && Y <= 8) {
            result += "\n" + toString(fmt3);
        }
        return result;
    }

    @Deprecated
    public void scale(float alpha) {
        shouldNotReachHere();
    }

    public Float4 mean() {
        Float4 result = new Float4();
        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                result = Float4.add(result, get(col, row));
            }
        }

        return Float4.div(result, (X * Y));
    }

    public Float4 min() {
        Float4 result = new Float4(MAX_VALUE, MAX_VALUE, MAX_VALUE, MAX_VALUE);

        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                result = Float4.min(result, get(col, row));
            }
        }

        return result;
    }

    public Float4 max() {
        Float4 result = new Float4(MIN_VALUE, MIN_VALUE, MIN_VALUE, MIN_VALUE);

        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                result = Float4.max(result, get(col, row));
            }
        }

        return result;
    }

    public Float4 stdDev() {
        final Float4 mean = mean();
        Float4 varience = new Float4();
        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                Float4 v = Float4.sub(mean, get(col, row));
                v = Float4.mult(v, v);
                v = Float4.div(v, X);
                varience = Float4.add(v, varience);
            }
        }

        return sqrt(varience);
    }

    public String summerise() {
        return format("ImageFloat4<%dx%d>: min=%s, max=%s, mean=%s, sd=%s", X, Y, min(), max(), mean(), stdDev());
    }

    @Override
    public void loadFromBuffer(FloatBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public FloatBuffer asBuffer() {
        return wrap(storage);
    }

    @Override
    public int size() {
        return numElements;
    }

    public FloatingPointError calculateULP(ImageFloat4 ref) {
        float maxULP = MIN_VALUE;
        float minULP = MAX_VALUE;
        float averageULP = 0f;

        /*
         * check to make sure dimensions match
         */
        if (ref.X != X && ref.Y != Y) {
            return new FloatingPointError(-1f, 0f, 0f, 0f);
        }

        int errors = 0;
        for (int j = 0; j < Y; j++) {
            for (int i = 0; i < X; i++) {
                final Float4 v = get(i, j);
                final Float4 r = ref.get(i, j);

                final float ulpFactor = findULPDistance(v, r);
                averageULP += ulpFactor;
                minULP = Math.min(ulpFactor, minULP);
                maxULP = Math.max(ulpFactor, maxULP);

                if (ulpFactor > 5f) {
                    errors++;
//					if(i==317 && j==239)
//                    System.out.printf("[%d, %d]: %f -> error %s != %s\n", i, j, ulpFactor, v.toString(FloatOps.fmt3e), r.toString(FloatOps.fmt3e));
                }

            }
        }

        averageULP /= (float) X * Y;

        return new FloatingPointError(averageULP, minULP, maxULP, -1f, errors);
    }

}
