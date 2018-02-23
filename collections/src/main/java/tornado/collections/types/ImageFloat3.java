/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
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
 *
 * Authors: James Clarkson
 *
 */
package tornado.collections.types;

import java.nio.FloatBuffer;

import static java.lang.Float.MAX_VALUE;
import static java.lang.Float.MIN_VALUE;
import static java.lang.String.format;
import static java.nio.FloatBuffer.wrap;
import static tornado.collections.types.Float3.findULPDistance;
import static tornado.collections.types.Float3.sqrt;
import static tornado.collections.types.FloatOps.fmt3;
import static tornado.collections.types.StorageFormats.toRowMajor;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;

public class ImageFloat3 implements PrimitiveStorage<FloatBuffer> {

    /**
     * backing array
     */
    final protected float[] storage;

    /**
     * number of elements in the storage
     */
    final private int numElements;
    final private static int elementSize = 3;

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
    public ImageFloat3(int width, int height, float[] array) {
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
    public ImageFloat3(int width, int height) {
        this(width, height, new float[width * height * elementSize]);
    }

    public ImageFloat3(float[][] matrix) {
        this(matrix.length / elementSize, matrix[0].length / elementSize, toRowMajor(matrix));
    }

    private int toIndex(int x, int y) {
        return elementSize * (x + (y * X));
    }

    public Float3 get(int x) {
        return get(x, 0);
    }

    public void set(int x, Float3 value) {
        set(x, 0, value);
    }

    public Float3 get(int x, int y) {
        final int offset = toIndex(x, y);
        return Float3.loadFromArray(storage, offset);
    }

    public void set(int x, int y, Float3 value) {
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
    public VectorFloat3 row(int row) {
        shouldNotReachHere();
        return null;
//    	int index = toIndex(0,row);
//    	VectorFloat3 v = new VectorFloat3(X,index,1,getElementSize(),storage);
//    	return v;
    }

    @Deprecated
    public VectorFloat3 column(int col) {
        shouldNotReachHere();
        return null;
//    	int index = toIndex(col, 0);
//    	VectorFloat3 v = new VectorFloat3(Y,index,getStep(),getElementSize(),storage );
//    	return v;
    }

    @Deprecated
    public VectorFloat3 diag() {
        shouldNotReachHere();
        return null;
//    	VectorFloat3 v = new VectorFloat3(Math.min(X,Y), getOffset(), getStep() + 1,getElementSize(),storage);
//    	return v;
    }

    @Deprecated
    public ImageFloat3 subImage(int x0, int y0, int x1, int y1) {
        shouldNotReachHere();
        return null;
//    	int index = get(x0,y0).getOffset();
//    	ImageFloat3 subM = new ImageFloat3(x1,y1,index,getStep(),getElementSize(),storage);
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
//                    Float3 tmp = new Float3();
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

    public ImageFloat3 duplicate() {
        ImageFloat3 matrix = new ImageFloat3(X, Y);
        matrix.set(this);
        return matrix;
    }

    public void set(ImageFloat3 m) {
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
        String result = format("ImageFloat3 <%d x %d>", X, Y);
        if (X <= 8 && Y <= 8) {
            result += "\n" + toString(fmt3);
        }
        return result;
    }

    @Deprecated
    public void scale(float alpha) {
        shouldNotReachHere();
    }

    public Float3 mean() {
        Float3 result = new Float3();
        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                result = Float3.add(result, get(col, row));
            }
        }

        return Float3.div(result, (X * Y));
    }

    public Float3 min() {
        Float3 result = new Float3(MAX_VALUE, MAX_VALUE, MAX_VALUE);

        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                result = Float3.min(result, get(col, row));
            }
        }

        return result;
    }

    public Float3 max() {
        Float3 result = new Float3(MIN_VALUE, MIN_VALUE, MIN_VALUE);

        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                result = Float3.max(result, get(col, row));
            }
        }

        return result;
    }

    public Float3 stdDev() {
        final Float3 mean = mean();
        Float3 varience = new Float3();
        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                Float3 v = Float3.sub(mean, get(col, row));
                v = Float3.mult(v, v);
                v = Float3.div(v, X);
                varience = Float3.add(v, varience);
            }
        }

        return sqrt(varience);
    }

    public String summerise() {
        return format("ImageFloat3<%dx%d>: min=%s, max=%s, mean=%s, sd=%s", X, Y, min(), max(), mean(), stdDev());
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

    public FloatingPointError calculateULP(ImageFloat3 ref) {
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
                final Float3 v = get(i, j);
                final Float3 r = ref.get(i, j);

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
