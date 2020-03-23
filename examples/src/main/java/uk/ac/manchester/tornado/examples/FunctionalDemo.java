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

import java.util.function.IntUnaryOperator;

import uk.ac.manchester.tornado.api.annotations.Parallel;

public class FunctionalDemo {

    public static class Matrix {

        private final int[] array;
        private final int m;
        private final int n;

        public Matrix(final int m, final int n) {
            this.m = m;
            this.n = n;
            array = new int[m * n];
        }

        public void apply(final IntUnaryOperator action) {
            for (@Parallel int i = 0; i < m; i++) {
                for (@Parallel int j = 0; j < n; j++) {
                    set(i, j, action.applyAsInt(get(i, j)));
                }
            }
        }

        public int get(final int r, final int c) {
            return array[(r * m) + c];
        }

        public void set(final int r, final int c, final int value) {
            array[(r * m) + c] = value;
        }

        public void times2() {
            final IntUnaryOperator times2 = (value) -> value << 1;
            apply(times2);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();

            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    sb.append(String.format("%2d ", get(i, j)));
                }
                sb.append("\n");
            }

            return sb.toString();
        }
    }

    public static void main(final String[] args) {

        final Matrix m = new Matrix(4, 4);

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                m.set(i, j, (i * 4) + j);
            }
        }

        System.out.println(m);
    }
}
