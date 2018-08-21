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
 * Authors: Juan Fumero, Michalis Papadimitriou
 *
 */
package uk.ac.manchester.tornado.unittests.tools;

public class TornadoTestRunner {

    public static void main(String[] args) throws ClassNotFoundException {

        boolean verbose = TornadoHelper.getProperty("tornado.unittests.verbose");

        System.out.println("!!!!!!!!!! HERE");

        String[] classAndMethod = args[0].split("#");
        if (!verbose) {
            if (classAndMethod.length > 1) {
                TornadoHelper.runTestClassAndMethod(classAndMethod[0], classAndMethod[1]);
            } else {
                TornadoHelper.runTestClass(classAndMethod[0]);
            }
        } else {
            if (classAndMethod.length > 1) {
                TornadoHelper.runTestVerbose(classAndMethod[0], classAndMethod[1]);
            } else {
                TornadoHelper.runTestVerbose(classAndMethod[0], null);
            }
        }
    }
}
