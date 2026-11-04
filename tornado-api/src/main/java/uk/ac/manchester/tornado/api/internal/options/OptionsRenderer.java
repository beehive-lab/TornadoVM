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

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import uk.ac.manchester.tornado.api.options.DeprecatedKey;
import uk.ac.manchester.tornado.api.options.Option;
import uk.ac.manchester.tornado.api.options.OptionCategory;
import uk.ac.manchester.tornado.api.options.OptionVisibility;
import uk.ac.manchester.tornado.api.options.TaskOption;

/**
 * Writes the options of a registry as the text of {@code tornado --list-flags} or as the reStructuredText tables
 * of the flags documentation.
 */
public final class OptionsRenderer {

    private static final String INDENT = "      ";

    private final OptionRegistry registry;

    public OptionsRenderer(OptionRegistry registry) {
        this.registry = registry;
    }

    /**
     * @param resolver
     *     shows the value of every option that is set
     * @param includeInternal
     *     also list {@linkplain OptionVisibility#INTERNAL internal} options
     */
    public String text(OptionResolver resolver, boolean includeInternal) {
        StringBuilder out = new StringBuilder();
        for (OptionCategory category : OptionCategory.values()) {
            List<Option<?>> options = options(category, option -> includeInternal || option.visibility() == OptionVisibility.PUBLIC);
            List<TaskOption<?>> taskOptions = taskOptions(category, option -> includeInternal || option.visibility() == OptionVisibility.PUBLIC);
            if (options.isEmpty() && taskOptions.isEmpty()) {
                continue;
            }
            out.append(category.title()).append('\n');
            for (Option<?> option : options) {
                out.append(String.format("  -D%s=<%s>  (default: %s)", option.key(), option.parser().typeName(), shownDefault(option)));
                resolver.text(option).ifPresent(value -> out.append("  [set: ").append(value).append(']'));
                if (option.visibility() == OptionVisibility.INTERNAL) {
                    out.append("  [internal]");
                }
                out.append('\n').append(INDENT).append(plain(option.description())).append('\n');
                if (!option.deprecations().isEmpty()) {
                    out.append(INDENT).append("Formerly ").append(join(option.deprecations(), "-D", "", ", ")).append('\n');
                }
            }
            for (TaskOption<?> taskOption : taskOptions) {
                out.append(String.format("  -D<id>.%s=<%s>%s%s%n", taskOption.suffix(), taskOption.parser().typeName(), //
                        taskOption.defaultValue().map(value -> "  (default: " + value + ")").orElse(""), //
                        taskOption.globalFallbackKey().map(key -> "  or -D" + key + " for every task").orElse("")));
                out.append(INDENT).append(plain(taskOption.description())).append('\n');
                if (!taskOption.deprecations().isEmpty()) {
                    out.append(INDENT).append("Formerly ").append(join(taskOption.deprecations(), "-D<id>.", "", ", ")).append('\n');
                }
            }
            out.append('\n');
        }
        return out.toString();
    }

    /**
     * @return one reStructuredText section per category, then the removed options
     */
    public String rst() {
        StringBuilder out = new StringBuilder();
        for (OptionCategory category : OptionCategory.values()) {
            List<Option<?>> options = options(category, option -> option.visibility() == OptionVisibility.PUBLIC);
            List<TaskOption<?>> taskOptions = taskOptions(category, option -> option.visibility() == OptionVisibility.PUBLIC);
            if (options.isEmpty() && taskOptions.isEmpty()) {
                continue;
            }
            heading(out, category.title());
            tableHeader(out, "35 15 50", "Flag", "Default", "Description");
            for (Option<?> option : options) {
                tableRow(out, "``-D" + option.key() + "``", rstDefault(option), rstText(option.description()) + formerly(option));
            }
            for (TaskOption<?> taskOption : taskOptions) {
                String flag = "``-D<id>." + taskOption.suffix() + "``" + taskOption.globalFallbackKey().map(key -> " or ``-D" + key + "``").orElse("");
                tableRow(out, flag, taskOption.defaultValue().map(value -> "``" + value + "``").orElse("unset"), rstText(taskOption.description()) + formerly(taskOption));
            }
            out.append('\n');
        }
        List<Option<?>> internal = registry.options().stream().filter(option -> option.visibility() == OptionVisibility.INTERNAL).toList();
        if (!internal.isEmpty()) {
            heading(out, "Internal and experimental options");
            out.append("For TornadoVM developers. They may change or disappear without a deprecation period.\n\n");
            tableHeader(out, "35 15 50", "Flag", "Default", "Description");
            for (Option<?> option : internal) {
                tableRow(out, "``-D" + option.key() + "``", rstDefault(option), rstText(option.description()) + formerly(option));
            }
            out.append('\n');
        }
        heading(out, "Removed options");
        out.append("Setting one of these prints a warning and has no effect. The warning is printed until the release shown.\n\n");
        tableHeader(out, "35 50 15", "Flag", "Reason", "Reported until");
        for (RemovedOption removedOption : registry.removed()) {
            tableRow(out, "``-D" + removedOption.key() + "``", removedOption.reason(), removedOption.dropIn());
        }
        return out.toString();
    }

    private List<Option<?>> options(OptionCategory category, Predicate<Option<?>> filter) {
        return registry.options().stream().filter(option -> option.category() == category).filter(filter).toList();
    }

    private List<TaskOption<?>> taskOptions(OptionCategory category, Predicate<TaskOption<?>> filter) {
        return registry.taskOptions().stream().filter(option -> option.category() == category).filter(filter).toList();
    }

    private static String shownDefault(Option<?> option) {
        return option.defaultDescription().isEmpty() ? "empty" : option.defaultDescription();
    }

    /**
     * A default that is a value reads as a literal; a missing or computed one reads as prose. An empty default
     * would make an empty literal, which reStructuredText rejects.
     */
    private static String rstDefault(Option<?> option) {
        String shown = option.defaultDescription();
        boolean isValue = option.defaultValue().map(String::valueOf).filter(shown::equals).isPresent();
        if (!isValue) {
            return shown;
        }
        return shown.isEmpty() ? "*empty*" : "``" + shown + "``";
    }

    private static String formerly(Option<?> option) {
        return option.deprecations().isEmpty() ? "" : " Formerly " + join(option.deprecations(), "``-D", "``", ", ") + ".";
    }

    private static String formerly(TaskOption<?> option) {
        return option.deprecations().isEmpty() ? "" : " Formerly " + join(option.deprecations(), "``-D<id>.", "``", ", ") + ".";
    }

    /**
     * @return each former key, quoted, with the release that stops honouring it
     */
    private static String join(List<DeprecatedKey> deprecations, String prefix, String suffix, String separator) {
        return deprecations.stream().map(deprecated -> prefix + deprecated.key() + suffix + " (until " + deprecated.dropIn() + ")").collect(Collectors.joining(separator));
    }

    private static void heading(StringBuilder out, String title) {
        out.append(title).append('\n').append("~".repeat(title.length())).append("\n\n");
    }

    private static void tableHeader(StringBuilder out, String widths, String first, String second, String third) {
        out.append(".. list-table::\n   :header-rows: 1\n   :widths: ").append(widths).append("\n\n");
        tableRow(out, first, second, third);
    }

    private static void tableRow(StringBuilder out, String first, String second, String third) {
        out.append("   * - ").append(first).append('\n');
        out.append("     - ").append(second).append('\n');
        Optional.ofNullable(third).ifPresent(text -> out.append("     - ").append(text).append('\n'));
    }

    private static String plain(String description) {
        return description.replace("`", "");
    }

    /**
     * Descriptions mark code with single backquotes; reStructuredText needs two.
     */
    private static String rstText(String description) {
        return description.replace("`", "``");
    }
}
