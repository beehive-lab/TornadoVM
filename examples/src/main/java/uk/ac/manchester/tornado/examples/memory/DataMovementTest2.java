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
package uk.ac.manchester.tornado.examples.memory;

import java.util.Random;

import uk.ac.manchester.tornado.api.collections.types.ImageFloat;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.mm.TornadoDeviceObjectState;
import uk.ac.manchester.tornado.api.mm.TornadoGlobalObjectState;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

public class DataMovementTest2 {

    public static void main(String[] args) {

        int sizeX = args.length == 2 ? Integer.parseInt(args[0]) : 16;
        int sizeY = args.length == 2 ? Integer.parseInt(args[1]) : 16;

        ImageFloat image = new ImageFloat(sizeX, sizeY);
        final Random rand = new Random();

        for (int y = 0; y < sizeY; y++) {
            for (int x = 0; x < sizeX; x++) {
                image.set(x, y, rand.nextFloat());
            }
        }

        System.out.println("Before: ");
        System.out.printf(image.toString());

        TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
        TornadoGlobalObjectState state = TornadoRuntime.getTornadoRuntime().resolveObject(image);
        TornadoDeviceObjectState deviceState = state.getDeviceState(device);

        int writeEvent = device.ensurePresent(image, deviceState);
        if (writeEvent != -1) {
            device.resolveEvent(writeEvent).waitOn();
        }

        image.fill(-1);
        System.out.println("Reset: ");
        System.out.printf(image.toString());

        int readEvent = device.streamOut(image, deviceState, null);
        device.resolveEvent(readEvent).waitOn();

        System.out.println("After: ");
        System.out.printf(image.toString());

    }

}
