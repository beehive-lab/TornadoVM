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
package uk.ac.manchester.tornado.examples.vectors;

import static uk.ac.manchester.tornado.api.collections.types.Float3.add;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat3;

public class VectorTest {

    private static void test(Float3 a, Float3 b,
            VectorFloat3 results) {
        results.set(0, add(a, b));
    }

    public static void main(String[] args) {

        final Float3 value = new Float3(1f, 1f, 1f);
        System.out.printf("float3: %s\n", value.toString());

        System.out.printf("float3: %s\n", value.toString());

        final VectorFloat3 results = new VectorFloat3(1);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", VectorTest::test, value, value, results)
                .streamOut(results)
                .execute();
        //@formatter:on

        System.out.printf("result: %s\n", results.get(0).toString());

    }

}
