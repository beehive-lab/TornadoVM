/*
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package uk.ac.manchester.tornado.examples;

import java.util.Random;
import org.ejml.data.DMatrix1Row;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.RandomMatrices_DDRM;
import org.ejml.dense.row.mult.MatrixMatrixMult_DDRM;

import uk.ac.manchester.tornado.api.TaskSchedule;

public class EJMLMatrixMult {

    public static final void print(DMatrixRMaj mat) {
        for (int r = 0; r < mat.numRows; r++) {
            System.out.printf("%3d| ", r);
            for (int c = 0; c < mat.numCols; c++) {
                System.out.printf("%.2f ", mat.get(r, c));
            }
            System.out.println();
        }
    }

    public static void mult_small(DMatrix1Row a, DMatrix1Row b, DMatrix1Row c) {

        for (int i = 0; i < a.numRows; i++) {
            for (int j = 0; j < b.numCols; j++) {
                double total = 0;

                int indexA = (i * a.numRows);
                int cIndex = (i * c.numRows);
                int indexB = j;
                int end = indexA + b.numRows;
                while (indexA < end) {
                    total += a.get(indexA++) * b.get(indexB);
                    indexB += b.numCols;
                }

                c.set(cIndex, total);
            }
        }
    }

    public static final void main(String[] args) {

        DMatrixRMaj a = CommonOps_DDRM.identity(8, 8);
        DMatrixRMaj b = new DMatrixRMaj(8, 8);
        DMatrixRMaj c = new DMatrixRMaj(8, 8);

        Random rand = new Random(7);
        RandomMatrices_DDRM.fillUniform(b, rand);

        CommonOps_DDRM.fill(c, 0);

        // CommonOps_DDRM.mult(a, b, c);
        TaskSchedule s0 = new TaskSchedule("s0").task("mult", MatrixMatrixMult_DDRM::mult_small, a, b, c).streamOut(c);

        s0.execute();

        print(c);
        // MatrixMatrixMult_DDRM.mult_small(a, b, c);
    }

}
