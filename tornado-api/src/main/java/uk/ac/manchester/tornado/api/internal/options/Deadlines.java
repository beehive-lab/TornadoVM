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
package uk.ac.manchester.tornado.api.internal.options;

/**
 * When deprecated keys and removed-option warnings go away. {@code DeprecationDeadlineTest} fails once the
 * project version reaches a deadline, so that the key is deleted instead of kept by accident.
 */
public final class Deadlines {

    /**
     * The release after the one that introduced the options catalog (8.0.1): one release of warnings.
     */
    public static final String NEXT_RELEASE = "8.1.0";

    private Deadlines() {
    }
}
