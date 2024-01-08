/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.matrix;

import org.ejml.simple.SimpleMatrix;

import uk.ac.manchester.tornado.api.types.matrix.Matrix2DDouble;
import uk.ac.manchester.tornado.api.types.matrix.Matrix4x4Float;

public class EjmlUtil {

    public static Matrix4x4Float toMatrix4x4Float(SimpleMatrix m) {
        Matrix4x4Float result = new Matrix4x4Float();
        for (int i = 0; i < m.numRows(); i++) {
            for (int j = 0; j < m.numCols(); j++) {
                result.set(i, j, (float) m.get(i, j));
            }
        }
        return result;
    }

    public static SimpleMatrix toMatrix(Matrix4x4Float m) {
        SimpleMatrix result = new SimpleMatrix(m.getNumRows(), m.getNumColumns());
        for (int i = 0; i < m.getNumRows(); i++) {
            for (int j = 0; j < m.getNumColumns(); j++) {
                result.set(i, j, (double) m.get(i, j));
            }
        }
        return result;
    }

    public static SimpleMatrix toMatrix(Matrix2DDouble m) {
        SimpleMatrix result = new SimpleMatrix(m.getNumRows(), m.getNumColumns());
        for (int i = 0; i < m.getNumRows(); i++) {
            for (int j = 0; j < m.getNumColumns(); j++) {
                result.set(i, j, m.get(i, j));
            }
        }
        return result;
    }
}
