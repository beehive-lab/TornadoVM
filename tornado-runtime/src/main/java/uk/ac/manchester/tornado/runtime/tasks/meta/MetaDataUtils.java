/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2024, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
}
