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
package uk.ac.manchester.tornado.benchmarks.addimage;

import static uk.ac.manchester.tornado.benchmarks.GraphicsKernels.addImage;

import uk.ac.manchester.tornado.api.collections.types.Float4;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat4;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;

public class AddJava extends BenchmarkDriver {

    private final int numElementsX;
    private final int numElementsY;

    private ImageFloat4 a, b, c;

    public AddJava(int iterations, int numElementsX, int numElementsY) {
        super(iterations);
        this.numElementsX = numElementsX;
        this.numElementsY = numElementsY;
    }

    @Override
    public void setUp() {
        a = new ImageFloat4(numElementsX, numElementsY);
        b = new ImageFloat4(numElementsX, numElementsY);
        c = new ImageFloat4(numElementsX, numElementsY);

        final Float4 valueA = new Float4(new float[]{1f, 1f, 1f, 1f});
        final Float4 valueB = new Float4(new float[]{2f, 2f, 2f, 2f});
        for (int j = 0; j < numElementsY; j++) {
            for (int i = 0; i < numElementsX; i++) {
                a.set(i, j, valueA);
                b.set(i, j, valueB);
            }
        }
    }

    @Override
    public void tearDown() {
        a = null;
        b = null;
        c = null;
        super.tearDown();
    }

    @Override
    public void code() {
        addImage(a, b, c);
    }

    @Override
    public void barrier() {

    }

    @Override
    public boolean validate() {
        return true;
    }

    public void printSummary() {
        System.out.printf("id=java-serial, elapsed=%f, per iteration=%f\n", getElapsed(), getElapsedPerIteration());
    }

}
