/*
 * Copyright (c) 2013-2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api.types.matrix;

abstract class Matrix2DType {

    /**
     * Number of rows.
     */
    protected final int ROWS;

    /**
     * Number of columns.
     */
    protected final int COLUMNS;

    Matrix2DType(int numRows, int numColumns) {
        this.ROWS = numRows;
        this.COLUMNS = numColumns;
    }

    public int getNumRows() {
        return ROWS;
    }

    public int getNumColumns() {
        return COLUMNS;
    }

    /**
     * It returns the final index of the range to be copied, starting from the input
     * parameter.
     *
     * @param fromIndex
     * @return int
     */
    public int getFinalIndexOfRange(int fromIndex) {
        return fromIndex + COLUMNS;
    }

    public abstract void clear();

}
