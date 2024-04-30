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
package uk.ac.manchester.tornado.api.common;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.memory.TaskMetaDataInterface;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;

public interface SchedulableTask {

    Object[] getArguments();

    Access[] getArgumentsAccess();

    TaskMetaDataInterface meta();

    SchedulableTask mapTo(TornadoDevice mapping);

    TornadoDevice getDevice();

    String getFullName();

    String getNormalizedName();

    String getTaskName();

    String getId();

    void setBatchThreads(long batchThreads);

    long getBatchThreads();

    void setBatchNumber(int batchNumber);

    int getBatchNumber();

    void setBatchSize(long batchSize);

    long getBatchSize();

    void attachProfiler(TornadoProfiler tornadoProfiler);

    TornadoProfiler getProfiler();

    void forceCompilation();

    boolean shouldCompile();

    void enableDefaultThreadScheduler(boolean useDefaultScheduler);

    void setUseGridScheduler(boolean use);

    void setGridScheduler(GridScheduler gridScheduler);

    boolean isGridSchedulerEnabled();
}
