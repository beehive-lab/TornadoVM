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
 * ---------------------------------------------------------------------------
 *
 * Compiled into the native shims whose headers pull in <stdlib.h> (cuDF, cuDNN).
 *
 * Compiled against glibc 2.38 or newer, strtol/strtoll/strtoul/strtoull are redirected to
 * __isoc23_strtol and friends, which exist only from glibc 2.38. A shim built on such a host then
 * fails to load on Ubuntu 22.04 and RHEL 9. The C23 variants differ from the originals only in
 * accepting a "0b" binary prefix, so these hidden definitions satisfy the references inside the
 * shim and forward to the original symbols, which every glibc version provides.
 */
#if defined(__linux__)

extern "C" {
// The asm label names the original symbol, so the linker binds it to the oldest strtol version.
long legacy_strtol(const char *, char **, int) __asm__("strtol");
long long legacy_strtoll(const char *, char **, int) __asm__("strtoll");
unsigned long legacy_strtoul(const char *, char **, int) __asm__("strtoul");
unsigned long long legacy_strtoull(const char *, char **, int) __asm__("strtoull");

__attribute__((visibility("hidden"))) long __isoc23_strtol(const char *s, char **e, int b) {
    return legacy_strtol(s, e, b);
}
__attribute__((visibility("hidden"))) long long __isoc23_strtoll(const char *s, char **e, int b) {
    return legacy_strtoll(s, e, b);
}
__attribute__((visibility("hidden"))) unsigned long __isoc23_strtoul(const char *s, char **e, int b) {
    return legacy_strtoul(s, e, b);
}
__attribute__((visibility("hidden"))) unsigned long long __isoc23_strtoull(const char *s, char **e, int b) {
    return legacy_strtoull(s, e, b);
}
}

#endif
