/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * GNU Classpath is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 * 
 * GNU Classpath is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING.  If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 * 
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module.  An independent module is a module which is not derived from
 * or based on this library.  If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so.  If you do not wish to do so, delete this
 * exception statement from your version.
 *
 */
package uk.ac.manchester.tornado.api.collections.types;

import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static uk.ac.manchester.tornado.api.collections.math.TornadoMath.sqrt;
import static uk.ac.manchester.tornado.api.collections.types.Float3.add;
import static uk.ac.manchester.tornado.api.collections.types.Float3.cross;
import static uk.ac.manchester.tornado.api.collections.types.Float3.dot;
import static uk.ac.manchester.tornado.api.collections.types.Float3.mult;

import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;

public class FloatSE3 {

    final Matrix4x4Float matrix = new Matrix4x4Float();
    Float3 translation = new Float3();

    public FloatSE3() {

    }

    public FloatSE3(Float6 v) {
        matrix.identity();
        exp(v);

        Float3 value = v.getHigh();
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

    public static Matrix4x4Float toMatrix4(Float6 v) {
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

    public void exp(Float6 mu) {
        final float one6Th = 1f / 6f;
        final float one20Th = 1f / 20f;

        Float3 muLo = mu.getHigh();
        Float3 w = mu.getLow();
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
