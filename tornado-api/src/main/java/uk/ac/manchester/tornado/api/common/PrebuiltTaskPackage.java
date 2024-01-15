/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
 * The University of Manchester.
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
package uk.ac.manchester.tornado.api.common;

public class PrebuiltTaskPackage extends TaskPackage {

    private final String entryPoint;
    private final String filename;
    private final Object[] args;
    private final Access[] accesses;
    private final TornadoDevice device;
    private final int[] dimensions;
    private int[] atomics;

    public PrebuiltTaskPackage(String id, String entryPoint, String fileName, Object[] args, Access[] accesses, TornadoDevice device, int[] dimensions) {
        super(id, null);
        this.entryPoint = entryPoint;
        this.filename = fileName;
        this.args = args;
        this.accesses = accesses;
        this.device = device;
        this.dimensions = dimensions;
    }

    public PrebuiltTaskPackage withAtomics(int[] atomics) {
        this.atomics = atomics;
        return this;
    }

    public String getEntryPoint() {
        return entryPoint;
    }

    public String getFilename() {
        return filename;
    }

    public Object[] getArgs() {
        return args;
    }

    public Access[] getAccesses() {
        return accesses;
    }

    public TornadoDevice getDevice() {
        return device;
    }

    public int[] getDimensions() {
        return dimensions;
    }

    @Override
    public boolean isPrebuiltTask() {
        return true;
    }

    public int[] getAtomics() {
        return atomics;
    }
}
