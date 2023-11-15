/*
 * Copyright (c) 2013-2020, 2023, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.unittests.slam.utils;

import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static uk.ac.manchester.tornado.api.math.TornadoMath.sqrt;
import static uk.ac.manchester.tornado.api.types.vectors.Float3.add;
import static uk.ac.manchester.tornado.api.types.vectors.Float3.cross;
import static uk.ac.manchester.tornado.api.types.vectors.Float3.dot;
import static uk.ac.manchester.tornado.api.types.vectors.Float3.mult;

import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.matrix.Matrix4x4Float;
import uk.ac.manchester.tornado.api.types.vectors.Float3;

public class FloatSE3 {

    final Matrix4x4Float matrix = new Matrix4x4Float();
    Float3 translation = new Float3();

    public FloatSE3(FloatArray v) {
        assert (v.getSize() == 6);
        matrix.identity();
        exp(v);

        Float3 value = new Float3(v.get(0), v.get(1), v.get(2));
        matrix.set(0, 3, value.getX());
        matrix.set(1, 3, value.getY());
        matrix.set(2, 3, value.getZ());
    }

    public static Matrix4x4Float toMatrix4(float[] v) {
        Matrix4x4Float result = new Matrix4x4Float();
        result.identity();
        result.set(0, 1, -v[5]);
        result.set(0, 2, v[4]);
        result.set(1, 2, -v[3]);
        result.set(1, 0, v[5]);
        result.set(2, 0, -v[4]);
        result.set(2, 1, v[3]);
        result.set(0, 3, v[0]);
        result.set(1, 3, v[1]);
        result.set(2, 3, v[2]);
        return result;
    }

    public static Matrix4x4Float toMatrix4(FloatArray v) {
        Matrix4x4Float result = new Matrix4x4Float();
        result.identity();
        result.set(0, 1, -v.get(5));
        result.set(0, 2, v.get(4));
        result.set(1, 2, -v.get(3));

        result.set(1, 0, v.get(5));
        result.set(2, 0, -v.get(4));
        result.set(2, 1, v.get(3));

        result.set(0, 3, v.get(0));
        result.set(1, 3, v.get(1));
        result.set(2, 3, v.get(2));

        return result;
    }

    public void exp(FloatArray mu) {
        final float one6Th = 1f / 6f;
        final float one20Th = 1f / 20f;

        Float3 muLo = new Float3(mu.get(0), mu.get(1), mu.get(2));
        Float3 w = new Float3(mu.get(3), mu.get(4), mu.get(5));
        final float thetaSq = dot(w, w);
        final float theta = sqrt(thetaSq);

        float a;
        float b;
        float c;

        Float3 crossProduct = cross(w, muLo);

        if (thetaSq < 1e-8f) {
            a = 1f - one6Th * thetaSq;
            b = 0.5f;
            translation = add(muLo, mult(crossProduct, 0.5f));
        } else {
            if (thetaSq < 1e-6f) {
                c = one6Th * (1 - one20Th * thetaSq);
                a = 1 - thetaSq * c;
                b = (float) (0.5 - 0.25 * one6Th * thetaSq);
            } else {
                final float invTheta = 1f / theta;
                a = (float) (sin(theta) * invTheta);
                b = (float) ((1 - cos(theta)) * (sq(invTheta)));
                c = (1 - a) * (sq(invTheta));
            }

            Float3 wcp = cross(w, crossProduct);
            Float3 bTemp = add(mult(crossProduct, b), mult(wcp, c));
            translation = add(muLo, bTemp);

        }
        rodriguesSo3Exp(w, a, b);

    }

    private void rodriguesSo3Exp(Float3 w, float a, float b) {
        setMatrixValue(0, 0, 1f - b * (sq(w.getX()) + sq(w.getY())), matrix);
        setMatrixValue(1, 1, 1f - b * (sq(w.getX()) + sq(w.getZ())), matrix);
        setMatrixValue(2, 2, 1f - b * (sq(w.getY()) + sq(w.getZ())), matrix);

        setMatrixValue(0, 1, b * (w.getX() * w.getY()) - a * w.getZ(), matrix);
        setMatrixValue(1, 0, b * (w.getX() * w.getY()) + a * w.getZ(), matrix);

        setMatrixValue(0, 2, b * (w.getX() * w.getZ()) + a * w.getY(), matrix);
        setMatrixValue(2, 0, b * (w.getX() * w.getZ()) - a * w.getY(), matrix);

        setMatrixValue(1, 2, b * (w.getY() * w.getZ()) - a * w.getX(), matrix);
        setMatrixValue(2, 1, b * (w.getY() * w.getZ()) + a * w.getX(), matrix);
    }

    private void setMatrixValue(int row, int col, float value, Matrix4x4Float matrix) {
        matrix.set(row, col, value);
    }

    public Float3 getTranslation() {
        return matrix.column(3).asFloat3();
    }

    public void setTranslation(Float3 trans) {
        for (int i = 0; i < 3; i++) {
            matrix.column(3).asFloat3().set(trans);
        }
    }

    public Matrix4x4Float getMatrix() {
        return matrix;
    }

    public void multiply(FloatSE3 m) {
        TornadoInternalError.unimplemented("Multiply FloatSE3 not supported yet.");
    }

    private float sq(float value) {
        return value * value;
    }

    // converts a SE3 into a 4x4 matrix
    public Matrix4x4Float toMatrix4() {
        Matrix4x4Float newMatrix = new Matrix4x4Float();
        newMatrix.set(this.matrix);
        newMatrix.set(3, 3, 1f);
        return newMatrix;
    }

    @Override
    public String toString() {
        return matrix.toString();
    }
}
