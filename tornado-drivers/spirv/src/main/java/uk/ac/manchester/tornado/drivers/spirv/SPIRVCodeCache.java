/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021-2022, 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.spirv;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVInstalledCode;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public abstract class SPIRVCodeCache {

    protected final SPIRVDeviceContext deviceContext;
    protected final ConcurrentHashMap<String, SPIRVInstalledCode> cache;

    protected SPIRVCodeCache(SPIRVDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
        cache = new ConcurrentHashMap<>();
    }

    public SPIRVInstalledCode getCachedCode(String name) {
        return cache.get(name);
    }

    public boolean isCached(String name) {
        return cache.containsKey(name);
    }

    public void reset() {
        for (SPIRVInstalledCode code : cache.values()) {
            code.invalidate();
        }
        cache.clear();
    }

    public SPIRVInstalledCode getInstalledCode(String id, String entryPoint) {
        return cache.get(id + "-" + entryPoint);
    }

    protected void writeBufferToFile(ByteBuffer buffer, String filepath) {
        buffer.flip();
        try (FileOutputStream fos = new FileOutputStream(filepath)) {
            fos.write(buffer.array());
        } catch (Exception e) {
            throw new RuntimeException("[ERROR] Store of the SPIR-V File failed.");
        } finally {
            buffer.clear();
        }
    }

    protected void checkBinaryFileExists(String pathToFile) {
        final Path pathToSPIRVBin = Paths.get(pathToFile);
        if (!pathToSPIRVBin.toFile().exists()) {
            throw new RuntimeException("Binary File does not exist");
        }
    }

    protected String createSPIRVTempDirectoryName() {
        String tempDirectory = System.getProperty("java.io.tmpdir");
        String user = System.getProperty("user.name");
        String pathSeparator = FileSystems.getDefault().getSeparator();
        return tempDirectory + pathSeparator + user + pathSeparator + "tornadoVM-spirv";
    }

    public SPIRVInstalledCode installSPIRVBinary(TaskMetaData meta, String id, String entryPoint, byte[] binary) {
        if (binary == null || binary.length == 0) {
            throw new RuntimeException("[ERROR] SPIR-V Binary Module is Empty");
        }
        ByteBuffer buffer = ByteBuffer.allocate(binary.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(binary);
        String spirvTempDirectory = createSPIRVTempDirectoryName();
        Path path = Paths.get(spirvTempDirectory);
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new TornadoBailoutRuntimeException("Error - Exception when creating the temp directory for SPIR-V");
        }
        long timeStamp = System.nanoTime();
        String pathSeparator = FileSystems.getDefault().getSeparator();
        String spirvFile = spirvTempDirectory + pathSeparator + timeStamp + "-" + id + entryPoint + ".spv";
        if (TornadoOptions.DEBUG) {
            System.out.println("SPIR-V Binary File: " + spirvFile);
        }

        writeBufferToFile(buffer, spirvFile);
        return installSPIRVBinary(meta, id, entryPoint, spirvFile);
    }

    public abstract SPIRVInstalledCode installSPIRVBinary(TaskMetaData meta, String id, String entryPoint, String pathToFile);
}
