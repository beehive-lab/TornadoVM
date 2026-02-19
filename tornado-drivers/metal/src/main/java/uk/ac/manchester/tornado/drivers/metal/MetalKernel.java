/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.metal;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.drivers.metal.enums.MetalKernelInfo;
import uk.ac.manchester.tornado.drivers.metal.exceptions.MetalException;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class MetalKernel {

    private final long oclKernelID;
    private final MetalDeviceContext deviceContext;
    private final ByteBuffer buffer;
    private String kernelName;
    private final TornadoLogger logger;
    // Cached structured argument metadata, populated lazily
    private volatile KernelArgInfo[] argInfoCache;

    public MetalKernel(long id, MetalDeviceContext deviceContext) {
        this.oclKernelID = id;
        this.deviceContext = deviceContext;
        this.buffer = ByteBuffer.allocate(1024);
        this.buffer.order(Metal.BYTE_ORDER);
        this.kernelName = "unknown";
        this.logger = new TornadoLogger(this.getClass());
        queryName();

    }

    native static void clReleaseKernel(long kernelId) throws MetalException;

    native static void clSetKernelArg(long kernelId, int index, long size, byte[] buffer) throws MetalException;

    native static void clSetKernelArgRef(long kernelId, int index, long buffer) throws MetalException;

    native static void clGetKernelInfo(long kernelId, int info, byte[] buffer) throws MetalException;

    // Reflection helpers implemented in the native JNI layer (objc_metal_jni.mm)
    native static int clGetKernelArgCount(long kernelId) throws MetalException;

    native static void clGetKernelArgInfo(long kernelId, int index, byte[] buffer) throws MetalException;

    public void setArg(int index, ByteBuffer buffer) {
        try {
            clSetKernelArg(oclKernelID, index, buffer.position(), buffer.array());
        } catch (MetalException e) {
            logger.error(e.getMessage());
        }
    }

    public void setArgRef(int index, long devicePtr) {
        try {
            clSetKernelArgRef(oclKernelID, index, devicePtr);
        } catch (MetalException e) {
            logger.error(e.getMessage());
        }
    }

    public void setArgUnused(int index) {
        try {
            clSetKernelArg(oclKernelID, index, 8, null);
        } catch (MetalException e) {
            logger.error(e.getMessage());
        }
    }

    public void setConstantRegion(int index, ByteBuffer buffer) {
        long maxSize = deviceContext.getDevice().getDeviceMaxConstantBufferSize();
        guarantee(buffer.position() <= maxSize, "constant buffer is too large for device");
        setArg(index, buffer);
    }

    public void setLocalRegion(int index, long size) {
        long maxSize = deviceContext.getDevice().getDeviceLocalMemorySize();
        guarantee(size <= maxSize, "local allocation is too large for device");
        try {
            clSetKernelArg(oclKernelID, index, size, null);
        } catch (MetalException e) {
            logger.error(e.getMessage());
        }
    }

    public void cleanup() {
        try {
            clReleaseKernel(oclKernelID);
        } catch (MetalException e) {
            e.printStackTrace();
        }
    }

    public String getName() {
        return kernelName;
    }

    private void queryName() {
        Arrays.fill(buffer.array(), (byte) 0);
        buffer.clear();
        try {
            clGetKernelInfo(oclKernelID, MetalKernelInfo.CL_KERNEL_FUNCTION_NAME.getValue(), buffer.array());
            kernelName = new String(buffer.array(), StandardCharsets.US_ASCII);
        } catch (MetalException e) {
            e.printStackTrace();
        }
    }

    /**
     * Structured description of a kernel argument as reported by native reflection.
     */
    public static final class KernelArgInfo {
        public enum ArgType { BUFFER, THREADGROUP, TEXTURE, SAMPLER, UNKNOWN }
        public enum Access { READ, WRITE, READWRITE, UNKNOWN }

        public final int index;
        public final String name;
        public final ArgType type;
        public final Access access;
        public final int arrayLength;

        private KernelArgInfo(int index, String name, ArgType type, Access access, int arrayLength) {
            this.index = index;
            this.name = name;
            this.type = type;
            this.access = access;
            this.arrayLength = arrayLength;
        }

        static KernelArgInfo parse(String descriptor) {
            if (descriptor == null) return new KernelArgInfo(-1, "", ArgType.UNKNOWN, Access.UNKNOWN, 0);
            // expected format: name:index:type:access:arrayLength
            String[] parts = descriptor.split(":", 5);
            String name = "";
            int idx = -1;
            ArgType t = ArgType.UNKNOWN;
            Access a = Access.UNKNOWN;
            int arr = 0;
            try {
                if (parts.length > 0) name = parts[0];
                if (parts.length > 1) idx = Integer.parseInt(parts[1]);
                if (parts.length > 2) {
                    String ts = parts[2];
                    if (ts.equalsIgnoreCase("buffer")) t = ArgType.BUFFER;
                    else if (ts.equalsIgnoreCase("threadgroup")) t = ArgType.THREADGROUP;
                    else if (ts.equalsIgnoreCase("texture")) t = ArgType.TEXTURE;
                    else if (ts.equalsIgnoreCase("sampler")) t = ArgType.SAMPLER;
                    else t = ArgType.UNKNOWN;
                }
                if (parts.length > 3) {
                    String as = parts[3];
                    if (as.equalsIgnoreCase("read")) a = Access.READ;
                    else if (as.equalsIgnoreCase("write")) a = Access.WRITE;
                    else if (as.equalsIgnoreCase("readwrite")) a = Access.READWRITE;
                    else a = Access.UNKNOWN;
                }
                if (parts.length > 4) arr = Integer.parseInt(parts[4]);
            } catch (Exception e) {
                // ignore parse errors and return unknowns
            }
            return new KernelArgInfo(idx, name, t, a, arr);
        }

        @Override
        public String toString() {
            return String.format("%s(idx=%d,type=%s,access=%s,array=%d)", name, index, type, access, arrayLength);
        }
    }

    /**
     * Query the number of arguments for this kernel using native reflection.
     */
    public int getArgCount() {
        try {
            return clGetKernelArgCount(oclKernelID);
        } catch (MetalException e) {
            logger.error(e.getMessage());
            return 0;
        }
    }

    /**
     * Query a textual description of the argument at the given index.
     * Returns an empty string on error. The native side fills the provided
     * buffer with a NUL-terminated ASCII descriptor.
     */
    public String getArgInfo(int index) {
        byte[] buf = new byte[1024];
        try {
            clGetKernelArgInfo(oclKernelID, index, buf);
            String s = new String(buf, StandardCharsets.US_ASCII);
            int z = s.indexOf('\0');
            if (z >= 0) s = s.substring(0, z);
            return s;
        } catch (MetalException e) {
            logger.error(e.getMessage());
            return "";
        }
    }

    private synchronized void ensureArgInfoLoaded() {
        if (argInfoCache != null) return;
        try {
            int n = getArgCount();
            KernelArgInfo[] arr = new KernelArgInfo[n];
            for (int i = 0; i < n; i++) {
                String desc = getArgInfo(i);
                arr[i] = KernelArgInfo.parse(desc);
            }
            argInfoCache = arr;
        } catch (Exception e) {
            logger.error("failed to load arg info: %s", e.getMessage());
            argInfoCache = new KernelArgInfo[0];
        }
    }

    /**
     * Return structured argument info as a list. This will lazily query native
     * reflection helpers and cache the parsed results.
     */
    public List<KernelArgInfo> getArgInfoList() {
        ensureArgInfoLoaded();
        List<KernelArgInfo> list = new ArrayList<>();
        if (argInfoCache != null) {
            for (KernelArgInfo k : argInfoCache) list.add(k);
        }
        return list;
    }

    /**
     * Return structured info for the specific argument index, or null if not found.
     */
    public KernelArgInfo getArgInfoObject(int index) {
        ensureArgInfoLoaded();
        if (argInfoCache == null) return null;
        if (index < 0 || index >= argInfoCache.length) return null;
        return argInfoCache[index];
    }

    public long getOclKernelID() {
        return oclKernelID;
    }

    // (no-op) end of class
}
