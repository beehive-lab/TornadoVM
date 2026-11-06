/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.autotune;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

import uk.ac.manchester.tornado.runtime.autotune.spi.AutoTuneSearchStrategy;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;

/**
 * Discovers {@link AutoTuneSearchStrategy} implementations via {@link ServiceLoader} and caches the
 * discovered list. Backend modules register one with {@code provides AutoTuneSearchStrategy with ...}
 * in their module descriptor. Unlike {@code LibraryRegistry}, a lookup that finds no matching strategy
 * is not an error.
 */
public final class AutoTuneStrategyRegistry {

    private static volatile List<AutoTuneSearchStrategy> strategies;

    private AutoTuneStrategyRegistry() {
    }

    private static List<AutoTuneSearchStrategy> getStrategies() {
        if (strategies == null) {
            synchronized (AutoTuneStrategyRegistry.class) {
                if (strategies == null) {
                    List<AutoTuneSearchStrategy> discovered = new ArrayList<>();
                    for (AutoTuneSearchStrategy strategy : ServiceLoader.load(AutoTuneSearchStrategy.class, AutoTuneStrategyRegistry.class.getClassLoader())) {
                        discovered.add(strategy);
                    }
                    strategies = discovered;
                }
            }
        }
        return strategies;
    }

    /**
     * Returns the first registered strategy whose {@code canHandle(device)} is {@code true}, or
     * {@link Optional#empty()} if there is none, in which case the task keeps its default scheduler.
     */
    public static Optional<AutoTuneSearchStrategy> findStrategy(TornadoXPUDevice device) {
        for (AutoTuneSearchStrategy strategy : getStrategies()) {
            if (strategy.canHandle(device)) {
                return Optional.of(strategy);
            }
        }
        return Optional.empty();
    }
}