/*
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.drivers.opencl;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import org.graalvm.compiler.options.OptionValues;
import tornado.runtime.TornadoDriver;
import tornado.runtime.TornadoDriverProvider;
import tornado.runtime.TornadoVMConfig;

public class OCLTornadoDriverProvider implements TornadoDriverProvider {

    @Override
    public String getName() {
        return "OpenCL Driver";
    }

    @Override
    public TornadoDriver createDriver(OptionValues options, HotSpotJVMCIRuntime vmRuntime, TornadoVMConfig vmConfig) {
        return new OCLDriver(options, vmRuntime, vmConfig);
    }

}
