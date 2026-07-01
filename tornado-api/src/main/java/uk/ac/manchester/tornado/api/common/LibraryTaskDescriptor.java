/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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

/**
 * Describes a call to an external native library function (e.g., NVIDIA cuBLAS)
 * to be executed as a task within a {@link uk.ac.manchester.tornado.api.TaskGraph}.
 *
 * <p>
 * Instances are built by library binding modules (e.g., {@code tornado-cublas})
 * and consumed by the TornadoVM runtime, which resolves the parameters to device
 * buffers and dispatches the call through the matching library provider.
 * </p>
 */
public class LibraryTaskDescriptor {

    private String libraryName;
    private String functionName;
    private Object[] parameters;
    private Access[] access;

    public LibraryTaskDescriptor withLibrary(String libraryName) {
        this.libraryName = libraryName;
        return this;
    }

    public LibraryTaskDescriptor withFunction(String functionName) {
        this.functionName = functionName;
        return this;
    }

    public LibraryTaskDescriptor withParameters(Object[] parameters) {
        this.parameters = parameters;
        return this;
    }

    public LibraryTaskDescriptor withAccess(Access[] access) {
        this.access = access;
        return this;
    }

    public String getLibraryName() {
        return libraryName;
    }

    public String getFunctionName() {
        return functionName;
    }

    public Object[] getParameters() {
        return parameters;
    }

    public Access[] getAccess() {
        return access;
    }
}
