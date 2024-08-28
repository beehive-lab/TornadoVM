/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2024, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.runtime.tasks.meta;

import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getTornadoRuntime;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.TornadoAcceleratorBackend;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;

public final class MetaDataUtils {

    public static TornadoXPUDevice resolveDevice(String device) {
        final String[] ids = device.split(":");
        final TornadoAcceleratorBackend driver = getTornadoRuntime().getBackend(Integer.parseInt(ids[0]));
        return (TornadoXPUDevice) driver.getDevice(Integer.parseInt(ids[1]));
    }

    public record BackendSelectionContainer(int backendIndex, int deviceIndex) {
    }

    public static BackendSelectionContainer resolveDriverDeviceIndexes(String device) {
        final String[] ids = device.split(":");
        return new BackendSelectionContainer(Integer.parseInt(ids[0]), Integer.parseInt(ids[1]));
    }

    public static String[] processPrecompiledBinariesFromFile(String fileName) {
        StringBuilder listBinaries = new StringBuilder();
        try (BufferedReader fileContent = new BufferedReader(new FileReader(fileName))) {
            String line = fileContent.readLine();
            while (line != null) {
                if (!line.isEmpty() && !line.startsWith("#")) {
                    listBinaries.append(line + ",");
                }
                line = fileContent.readLine();
            }
            listBinaries.deleteCharAt(listBinaries.length() - 1);
        } catch (FileNotFoundException e) {
            throw new TornadoRuntimeException("File: " + fileName + " not found");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return listBinaries.toString().split(",");
    }

    public static String getProperty(String key) {
        return System.getProperty(key);
    }
}
