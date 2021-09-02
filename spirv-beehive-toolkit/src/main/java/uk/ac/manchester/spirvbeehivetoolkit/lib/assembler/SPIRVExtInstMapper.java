/*
* MIT License
*
* Copyright (c) 2021, APT Group, Department of Computer Science,
* The University of Manchester.
*
* Permission is hereby granted, free of charge, to any person obtaining a copy
* of this software and associated documentation files (the "Software"), to deal
* in the Software without restriction, including without limitation the rights
* to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the Software is
* furnished to do so, subject to the following conditions:
*
* The above copyright notice and this permission notice shall be included in all
* copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
* OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
* SOFTWARE.
*/

package uk.ac.manchester.spirvbeehivetoolkit.lib.assembler;

import java.util.HashMap;
import javax.annotation.Generated;

/**
 * SPIR-V handles built in functions by OpenCL or GLSL by importing their standard.
 * This standard contains all of the functions provided by them and
 * a mapping between the function names and corresponding numbers.
 */
@Generated("beehive-lab.spirvbeehivetoolkit.generator")
class SPIRVExtInstMapper {

    private static HashMap<String , Integer> extInstNameMap;

    /**
     * Get the number of the function mapped to the given name.
     * @param name - A string containing the name of the function.
     * @return The number of the external function mapped to this string.
     */
    public static int get(String name) {
        return extInstNameMap.get(name);
    }

    /**
     * Load all of the mapped values into memory (otherwise it is not available in order save space)
     */
    public static void loadOpenCL() {
        extInstNameMap = new HashMap<>(162);
        extInstNameMap.put("acos", 0);
        extInstNameMap.put("acosh", 1);
        extInstNameMap.put("acospi", 2);
        extInstNameMap.put("asin", 3);
        extInstNameMap.put("asinh", 4);
        extInstNameMap.put("asinpi", 5);
        extInstNameMap.put("atan", 6);
        extInstNameMap.put("atan2", 7);
        extInstNameMap.put("atanh", 8);
        extInstNameMap.put("atanpi", 9);
        extInstNameMap.put("atan2pi", 10);
        extInstNameMap.put("cbrt", 11);
        extInstNameMap.put("ceil", 12);
        extInstNameMap.put("copysign", 13);
        extInstNameMap.put("cos", 14);
        extInstNameMap.put("cosh", 15);
        extInstNameMap.put("cospi", 16);
        extInstNameMap.put("erfc", 17);
        extInstNameMap.put("erf", 18);
        extInstNameMap.put("exp", 19);
        extInstNameMap.put("exp2", 20);
        extInstNameMap.put("exp10", 21);
        extInstNameMap.put("expm1", 22);
        extInstNameMap.put("fabs", 23);
        extInstNameMap.put("fdim", 24);
        extInstNameMap.put("floor", 25);
        extInstNameMap.put("fma", 26);
        extInstNameMap.put("fmax", 27);
        extInstNameMap.put("fmin", 28);
        extInstNameMap.put("fmod", 29);
        extInstNameMap.put("fract", 30);
        extInstNameMap.put("frexp", 31);
        extInstNameMap.put("hypot", 32);
        extInstNameMap.put("ilogb", 33);
        extInstNameMap.put("ldexp", 34);
        extInstNameMap.put("lgamma", 35);
        extInstNameMap.put("lgamma_r", 36);
        extInstNameMap.put("log", 37);
        extInstNameMap.put("log2", 38);
        extInstNameMap.put("log10", 39);
        extInstNameMap.put("log1p", 40);
        extInstNameMap.put("logb", 41);
        extInstNameMap.put("mad", 42);
        extInstNameMap.put("maxmag", 43);
        extInstNameMap.put("minmag", 44);
        extInstNameMap.put("modf", 45);
        extInstNameMap.put("nan", 46);
        extInstNameMap.put("nextafter", 47);
        extInstNameMap.put("pow", 48);
        extInstNameMap.put("pown", 49);
        extInstNameMap.put("powr", 50);
        extInstNameMap.put("remainder", 51);
        extInstNameMap.put("remquo", 52);
        extInstNameMap.put("rint", 53);
        extInstNameMap.put("rootn", 54);
        extInstNameMap.put("round", 55);
        extInstNameMap.put("rsqrt", 56);
        extInstNameMap.put("sin", 57);
        extInstNameMap.put("sincos", 58);
        extInstNameMap.put("sinh", 59);
        extInstNameMap.put("sinpi", 60);
        extInstNameMap.put("sqrt", 61);
        extInstNameMap.put("tan", 62);
        extInstNameMap.put("tanh", 63);
        extInstNameMap.put("tanpi", 64);
        extInstNameMap.put("tgamma", 65);
        extInstNameMap.put("trunc", 66);
        extInstNameMap.put("half_cos", 67);
        extInstNameMap.put("half_divide", 68);
        extInstNameMap.put("half_exp", 69);
        extInstNameMap.put("half_exp2", 70);
        extInstNameMap.put("half_exp10", 71);
        extInstNameMap.put("half_log", 72);
        extInstNameMap.put("half_log2", 73);
        extInstNameMap.put("half_log10", 74);
        extInstNameMap.put("half_powr", 75);
        extInstNameMap.put("half_recip", 76);
        extInstNameMap.put("half_rsqrt", 77);
        extInstNameMap.put("half_sin", 78);
        extInstNameMap.put("half_sqrt", 79);
        extInstNameMap.put("half_tan", 80);
        extInstNameMap.put("native_cos", 81);
        extInstNameMap.put("native_divide", 82);
        extInstNameMap.put("native_exp", 83);
        extInstNameMap.put("native_exp2", 84);
        extInstNameMap.put("native_exp10", 85);
        extInstNameMap.put("native_log", 86);
        extInstNameMap.put("native_log2", 87);
        extInstNameMap.put("native_log10", 88);
        extInstNameMap.put("native_powr", 89);
        extInstNameMap.put("native_recip", 90);
        extInstNameMap.put("native_rsqrt", 91);
        extInstNameMap.put("native_sin", 92);
        extInstNameMap.put("native_sqrt", 93);
        extInstNameMap.put("native_tan", 94);
        extInstNameMap.put("fclamp", 95);
        extInstNameMap.put("degrees", 96);
        extInstNameMap.put("fmax_common", 97);
        extInstNameMap.put("fmin_common", 98);
        extInstNameMap.put("mix", 99);
        extInstNameMap.put("radians", 100);
        extInstNameMap.put("step", 101);
        extInstNameMap.put("smoothstep", 102);
        extInstNameMap.put("sign", 103);
        extInstNameMap.put("cross", 104);
        extInstNameMap.put("distance", 105);
        extInstNameMap.put("length", 106);
        extInstNameMap.put("normalize", 107);
        extInstNameMap.put("fast_distance", 108);
        extInstNameMap.put("fast_length", 109);
        extInstNameMap.put("fast_normalize", 110);
        extInstNameMap.put("s_abs", 141);
        extInstNameMap.put("s_abs_diff", 142);
        extInstNameMap.put("s_add_sat", 143);
        extInstNameMap.put("u_add_sat", 144);
        extInstNameMap.put("s_hadd", 145);
        extInstNameMap.put("u_hadd", 146);
        extInstNameMap.put("s_rhadd", 147);
        extInstNameMap.put("u_rhadd", 148);
        extInstNameMap.put("s_clamp", 149);
        extInstNameMap.put("u_clamp", 150);
        extInstNameMap.put("clz", 151);
        extInstNameMap.put("ctz", 152);
        extInstNameMap.put("s_mad_hi", 153);
        extInstNameMap.put("u_mad_sat", 154);
        extInstNameMap.put("s_mad_sat", 155);
        extInstNameMap.put("s_max", 156);
        extInstNameMap.put("u_max", 157);
        extInstNameMap.put("s_min", 158);
        extInstNameMap.put("u_min", 159);
        extInstNameMap.put("s_mul_hi", 160);
        extInstNameMap.put("rotate", 161);
        extInstNameMap.put("s_sub_sat", 162);
        extInstNameMap.put("u_sub_sat", 163);
        extInstNameMap.put("u_upsample", 164);
        extInstNameMap.put("s_upsample", 165);
        extInstNameMap.put("popcount", 166);
        extInstNameMap.put("s_mad24", 167);
        extInstNameMap.put("u_mad24", 168);
        extInstNameMap.put("s_mul24", 169);
        extInstNameMap.put("u_mul24", 170);
        extInstNameMap.put("vloadn", 171);
        extInstNameMap.put("vstoren", 172);
        extInstNameMap.put("vload_half", 173);
        extInstNameMap.put("vload_halfn", 174);
        extInstNameMap.put("vstore_half", 175);
        extInstNameMap.put("vstore_half_r", 176);
        extInstNameMap.put("vstore_halfn", 177);
        extInstNameMap.put("vstore_halfn_r", 178);
        extInstNameMap.put("vloada_halfn", 179);
        extInstNameMap.put("vstorea_halfn", 180);
        extInstNameMap.put("vstorea_halfn_r", 181);
        extInstNameMap.put("shuffle", 182);
        extInstNameMap.put("shuffle2", 183);
        extInstNameMap.put("printf", 184);
        extInstNameMap.put("prefetch", 185);
        extInstNameMap.put("bitselect", 186);
        extInstNameMap.put("select", 187);
        extInstNameMap.put("u_abs", 201);
        extInstNameMap.put("u_abs_diff", 202);
        extInstNameMap.put("u_mul_hi", 203);
        extInstNameMap.put("u_mad_hi", 204);
    }
}