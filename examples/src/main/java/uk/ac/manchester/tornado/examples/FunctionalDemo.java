/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
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
