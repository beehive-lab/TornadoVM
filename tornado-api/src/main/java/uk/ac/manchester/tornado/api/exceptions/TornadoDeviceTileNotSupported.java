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
package uk.ac.manchester.tornado.api.exceptions;

/**
 * Thrown when a task uses the CUDA Tile programming model on a device, driver or toolkit that
 * cannot run it. The message always names the requirement that was not met, because the fix is
 * usually an install rather than a code change.
 */
public class TornadoDeviceTileNotSupported extends RuntimeException {

    public TornadoDeviceTileNotSupported(final String msg) {
        super(msg);
    }

}
