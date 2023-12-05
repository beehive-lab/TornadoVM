/*
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal;

import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;

public class OCLStampFactory {

    private static final OCLStamp[] stamps = new OCLStamp[OCLKind.values().length];

    public static OCLStamp getStampFor(OCLKind kind) {
        int index = 0;
        for (OCLKind oclKind : OCLKind.values()) {
            if (oclKind == kind) {
                break;
            }
            index++;
        }

        if (stamps[index] == null) {
            stamps[index] = new OCLStamp(kind);
        }

        // System.out.printf("OCLStampFactory: kind=%s -> stamp=%s\n", kind,
        // stamps[index]);
        return stamps[index];
    }

}
