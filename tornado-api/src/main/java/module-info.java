/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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
module tornado.api {
    requires java.management;
    exports uk.ac.manchester.tornado.api;
    exports uk.ac.manchester.tornado.api.annotations;
    exports uk.ac.manchester.tornado.api.common;
    exports uk.ac.manchester.tornado.api.enums;
    exports uk.ac.manchester.tornado.api.exceptions;
    exports uk.ac.manchester.tornado.api.memory;
    exports uk.ac.manchester.tornado.api.profiler;
    exports uk.ac.manchester.tornado.api.runtime;
    exports uk.ac.manchester.tornado.api.internal.annotations;
    exports uk.ac.manchester.tornado.api.utils;

    opens uk.ac.manchester.tornado.api;
    exports uk.ac.manchester.tornado.api.math;
    exports uk.ac.manchester.tornado.api.types.arrays;
    opens uk.ac.manchester.tornado.api.types.arrays;
    exports uk.ac.manchester.tornado.api.types.collections;
    opens uk.ac.manchester.tornado.api.types.collections;
    exports uk.ac.manchester.tornado.api.types.matrix;
    opens uk.ac.manchester.tornado.api.types.matrix;
    opens uk.ac.manchester.tornado.api.types.images;
    exports uk.ac.manchester.tornado.api.types.images;
    exports uk.ac.manchester.tornado.api.types.utils;
    opens uk.ac.manchester.tornado.api.types.utils;
    exports uk.ac.manchester.tornado.api.types.common;
    opens uk.ac.manchester.tornado.api.types.common;
    exports uk.ac.manchester.tornado.api.types.volumes;
    opens uk.ac.manchester.tornado.api.types.volumes;
    exports uk.ac.manchester.tornado.api.types.vectors;
    opens uk.ac.manchester.tornado.api.types.vectors;
    exports uk.ac.manchester.tornado.api.types;
    exports uk.ac.manchester.tornado.api.types.tensors;
    opens uk.ac.manchester.tornado.api.types.tensors;
    opens uk.ac.manchester.tornado.api.types;
    opens uk.ac.manchester.tornado.api.runtime;
    exports uk.ac.manchester.tornado.api.plan.types;
    opens uk.ac.manchester.tornado.api.plan.types;
}
