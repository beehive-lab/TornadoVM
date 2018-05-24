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
 * Authors: Juan Fumero
 *
 */
package uk.ac.manchester.tornado.runtime.utils;

public class TornadoUtils {

    public enum TornadoDeviceType {
        CPU, GPU, FPGA
    }

    public static int getSizeReduction(int inputSize, TornadoDeviceType where) {

        switch (where) {
            case CPU:
                // If it is executed on the CPU, we return the number of threads of the current
                // CPU
                return Runtime.getRuntime().availableProcessors();
            case GPU:
                // size will be the number of work-groups on the GPU
                int size = 1;
                if (inputSize > 256) {
                    size = inputSize / 256;
                }
                return size;
            case FPGA:
                throw new RuntimeException("Unimplemented yet");
        }

        return 0;
    }

}
