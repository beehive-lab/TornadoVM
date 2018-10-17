/*
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package uk.ac.manchester.tornado.benchmarks;

import java.util.ArrayList;
import java.util.function.Consumer;

import uk.ac.manchester.tornado.api.common.ProfiledAction;

public class EventList<T extends ProfiledAction> extends ArrayList<T> {

    private static final long serialVersionUID = 7127015308775832135L;

    public EventList(int size) {
        super(size);
    }

    public EventList() {
        super();
    }

    public final double getTotalExecutionTimeInSeconds() {
        double total = 0.0;

        for (ProfiledAction e : this) {
            total += e.getExecutionTimeInSeconds();
        }

        return total;
    }

    public final double getMinExecutionTimeInSeconds() {
        double result = Double.MAX_VALUE;
        for (ProfiledAction e : this) {
            result = Math.min(result, e.getExecutionTimeInSeconds());
        }
        return result;
    }

    public final double getMaxExecutionTimeInSeconds() {
        double result = Double.MIN_VALUE;
        for (ProfiledAction e : this) {
            result = Math.max(result, e.getExecutionTimeInSeconds());
        }
        return result;
    }

    public final double getMeanExecutionTimeInSeconds() {
        return getTotalExecutionTimeInSeconds() / size();
    }

    public final double getExecutionStdDevInSeconds() {
        return Math.sqrt(getExecutionVarianceInSeconds());
    }

    public double getExecutionVarianceInSeconds() {
        final double mean = getMeanExecutionTimeInSeconds();
        double temp = 0;
        for (final ProfiledAction e : this) {
            final double value = e.getExecutionTimeInSeconds();
            temp += (mean - value) * (mean - value);
        }
        return temp / size();
    }

    public void apply(final Consumer<T> function) {
        for (final T e : this) {
            function.accept(e);
        }
    }

    public final EventsSummary summeriseEvents() {
        return new EventsSummary(size(), getTotalExecutionTimeInSeconds(), getMinExecutionTimeInSeconds(), getMaxExecutionTimeInSeconds(), getMeanExecutionTimeInSeconds(), getExecutionStdDevInSeconds());
    }

    public T getLast() {
        return get(size() - 1);
    }
}
