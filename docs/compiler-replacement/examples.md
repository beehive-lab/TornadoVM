# End-to-end examples: Java → bytecode → TC IR → CUDA

The three C3 design-test kernels compiled **from their Java class files** by TC (frontend,
ABI lowering, structuring, CUDA emission) and run on the RTX 4090: all three match their Java
references (`tc-gpu` `DesignTest --java`). For comparison, the CUDA the existing Graal path
generates for the same Java on the same device (`tornado --printKernel`).

No stage below is hand-written. Stages: **Java** → **bytecode** (`javac -g`, TC disassembler)
→ **TC-SSA pre-ABI** (TC frontend: Braun et al. SSA, API calls bound to TC intrinsics) →
**TC-SSA post-ABI** (`AbiLowering`) → **TC-S** (`Structurizer`) → **CUDA from TC** vs
**CUDA from Graal**. Per-compilation HTML reports with stage timings:
[c3/from-java/*.report.html](c3/from-java/).

Regenerate: `tornado -cp <tc classes> uk.ac.manchester.tornado.tc.gpu.DesignTest --params "1:0 --java"`
and `tornado --printKernel -cp . C3Kernels` ([c3/java/C3Kernels.java](c3/java/C3Kernels.java)).

| Kernel | CUDA lines (TC) | CUDA lines (Graal) |
|---|---:|---:|
| Grid-stride vector addition (`add`) | 23 | 33 |
| If/else with `sqrt` (`compute`) | 21 | 45 |
| KernelContext shared-memory tree reduction (`blockSum`) | 39 | 73 |

What the comparison shows, and what TC still owes (all in [optimizations.md](optimizations.md)):

- **Readability:** TC names values after Java locals, keeps Java line numbers, prints `while`
  loops, guard clauses and direct comparisons; values with no Java name (`v9`, `v24`) are javac
  stack temporaries. Graal prints `arg1`, `f_10` and `// BLOCK n` labels.
- **No optimiser yet (`tc-opt`, O2–O10):** constants are not folded (`(long long) 0 * 4LL`), the
  address `i * 4 + 16` is recomputed per access (O6 GVN), and the loop re-reads `a.getSize()` and
  `ctx.globalGroupSizeX` every iteration (O10 loop-invariant code motion). NVRTC cleans most of this
  up in machine code, but the source should not need it.
- **Value specialisation (T1):** Graal folds `a.getSize()` to `1048576`; TC reads the array header.
- **Same ABI:** four leading regions, byte pointers, 16-byte array header, KernelContext dropped.


## Grid-stride vector addition

### Java
```java
    public static void add(KernelContext ctx, FloatArray a, FloatArray b, FloatArray c) {
        for (int i = ctx.globalIdx; i < a.getSize(); i += ctx.globalGroupSizeX) {
            c.set(i, a.get(i) + b.get(i));
        }
    }
```

### Bytecode (`javac -g`)
```
// uk.ac.manchester.tornado.tc.gpu.kernels.C3Kernels.add(Luk/ac/manchester/tornado/api/KernelContext;Luk/ac/manchester/tornado/api/types/arrays/FloatArray;Luk/ac/manchester/tornado/api/types/arrays/FloatArray;Luk/ac/manchester/tornado/api/types/arrays/FloatArray;)V
// max_stack=5 max_locals=5
  // line 34
     0: aload_0         
     1: getfield        KernelContext.globalIdx Ljava/lang/Integer;
     4: invokevirtual   Integer.intValue ()I
     7: istore          4
     9: iload           4
    11: aload_1         
    12: invokevirtual   FloatArray.getSize ()I
    15: if_icmpge       52
  // line 35
    18: aload_3         
    19: iload           4
    21: aload_1         
    22: iload           4
    24: invokevirtual   FloatArray.get (I)F
    27: aload_2         
    28: iload           4
    30: invokevirtual   FloatArray.get (I)F
    33: fadd            
    34: invokevirtual   FloatArray.set (IF)V
  // line 34
    37: iload           4
    39: aload_0         
    40: getfield        KernelContext.globalGroupSizeX Ljava/lang/Integer;
    43: invokevirtual   Integer.intValue ()I
    46: iadd            
    47: istore          4
    49: goto            9
  // line 37
    52: return
```

### TC-SSA from the frontend (pre-ABI)
```llvm
kernel @C3Kernels.add(%ctx: obj<KernelContext>, %a: array<f32>, %b: array<f32>, %c: array<f32>) -> void {
^entry:
  br ^bci0
^bci0:
  %i = tornado.thread_id.global.x() : i32  loc("C3Kernels.java":34, bci=1)
  br ^bci9(%i)  loc("C3Kernels.java":34, bci=7)
^bci9(%i.1: i32):
  %0 = array.length %a : i32  loc("C3Kernels.java":34, bci=12)
  %1 = icmp.sge %i.1, %0 : i1  loc("C3Kernels.java":34, bci=15)
  cond_br %1, ^bci52, ^bci18  loc("C3Kernels.java":34, bci=15)
^bci18:
  %2 = array.load %a[%i.1] : f32  loc("C3Kernels.java":35, bci=24)
  %3 = array.load %b[%i.1] : f32  loc("C3Kernels.java":35, bci=30)
  %4 = fadd %2, %3 : f32  loc("C3Kernels.java":35, bci=33)
  array.store %c[%i.1], %4  loc("C3Kernels.java":35, bci=34)
  %5 = tornado.global_size.x() : i32  loc("C3Kernels.java":34, bci=40)
  %i.2 = add %i.1, %5 : i32  loc("C3Kernels.java":34, bci=46)
  br ^bci9(%i.2)  loc("C3Kernels.java":34, bci=49)
^bci52:
  ret  loc("C3Kernels.java":37, bci=52)
}
```

### TC-SSA after ABI lowering
```llvm
kernel @C3Kernels.add(%a: ptr<global>, %b: ptr<global>, %c: ptr<global>) -> void {
^entry:
  br ^bci0
^bci0:
  %i = tornado.thread_id.global.x() : i32  loc("C3Kernels.java":34, bci=1)
  br ^bci9(%i)  loc("C3Kernels.java":34, bci=7)
^bci9(%i.1: i32):
  %0 = load %a : i64  loc("C3Kernels.java":34, bci=12)
  %1 = trunc %0 : i32  loc("C3Kernels.java":34, bci=12)
  %2 = icmp.sge %i.1, %1 : i1  loc("C3Kernels.java":34, bci=15)
  cond_br %2, ^bci52, ^bci18  loc("C3Kernels.java":34, bci=15)
^bci18:
  %3 = sext %i.1 : i64  loc("C3Kernels.java":35, bci=24)
  %4 = const 4 : i64  loc("C3Kernels.java":35, bci=24)
  %5 = mul %3, %4 : i64  loc("C3Kernels.java":35, bci=24)
  %6 = const 16 : i64  loc("C3Kernels.java":35, bci=24)
  %7 = add %5, %6 : i64  loc("C3Kernels.java":35, bci=24)
  %8 = ptr.offset %a, %7 : ptr<global>  loc("C3Kernels.java":35, bci=24)
  %9 = load %8 : f32  loc("C3Kernels.java":35, bci=24)
  %10 = sext %i.1 : i64  loc("C3Kernels.java":35, bci=30)
  %11 = const 4 : i64  loc("C3Kernels.java":35, bci=30)
  %12 = mul %10, %11 : i64  loc("C3Kernels.java":35, bci=30)
  %13 = const 16 : i64  loc("C3Kernels.java":35, bci=30)
  %14 = add %12, %13 : i64  loc("C3Kernels.java":35, bci=30)
  %15 = ptr.offset %b, %14 : ptr<global>  loc("C3Kernels.java":35, bci=30)
  %16 = load %15 : f32  loc("C3Kernels.java":35, bci=30)
  %17 = fadd %9, %16 : f32  loc("C3Kernels.java":35, bci=33)
  %18 = sext %i.1 : i64  loc("C3Kernels.java":35, bci=34)
  %19 = const 4 : i64  loc("C3Kernels.java":35, bci=34)
  %20 = mul %18, %19 : i64  loc("C3Kernels.java":35, bci=34)
  %21 = const 16 : i64  loc("C3Kernels.java":35, bci=34)
  %22 = add %20, %21 : i64  loc("C3Kernels.java":35, bci=34)
  %23 = ptr.offset %c, %22 : ptr<global>  loc("C3Kernels.java":35, bci=34)
  store %23, %17  loc("C3Kernels.java":35, bci=34)
  %24 = tornado.global_size.x() : i32  loc("C3Kernels.java":34, bci=40)
  %i.2 = add %i.1, %24 : i32  loc("C3Kernels.java":34, bci=46)
  br ^bci9(%i.2)  loc("C3Kernels.java":34, bci=49)
^bci52:
  ret  loc("C3Kernels.java":37, bci=52)
}
```

### TC-S (structured)
```c
kernel C3Kernels.add(ptr<global> a, ptr<global> b, ptr<global> c) -> void {
  i32 i;
  i32 i_1;
  i64 v0;
  f32 v9;
  f32 v16;
  i32 v24;
  i = tornado.thread_id.global.x();
  i_1 = i;
  block L_bci52 {
    loop L_bci9 {
      v0 = load<i64>(a);
      if ((i_1 sge trunc<i32>(v0))) {
        break L_bci52;
      } else {
        v9 = load<f32>(ptr.offset(a, ((sext<i64>(i_1) * 4) + 16)));
        v16 = load<f32>(ptr.offset(b, ((sext<i64>(i_1) * 4) + 16)));
        store(ptr.offset(c, ((sext<i64>(i_1) * 4) + 16)), (v9 + v16));
        v24 = tornado.global_size.x();
        i_1 = (i_1 + v24);
      }
    }
  }
  return;
}
```

### CUDA generated by TC (ran on the RTX 4090: PASS)
```cuda
extern "C" __global__ void C3Kernels_add(long long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics, unsigned char *a, unsigned char *b, unsigned char *c)
{
  int i;
  int i_1;
  long long v0;
  float v9;
  float v16;
  int v24;
  i = (int) (blockIdx.x * blockDim.x + threadIdx.x);  // C3Kernels.java:34
  i_1 = i;
  for (;;) {
    v0 = *((long long *) a);  // C3Kernels.java:34
    if (i_1 >= (int) v0) {
      break;
    }
    v9 = *((float *) (a + ((long long) i_1 * 4LL + 16LL)));  // C3Kernels.java:35
    v16 = *((float *) (b + ((long long) i_1 * 4LL + 16LL)));  // C3Kernels.java:35
    *((float *) (c + ((long long) i_1 * 4LL + 16LL))) = v9 + v16;  // C3Kernels.java:35
    v24 = (int) (gridDim.x * blockDim.x);  // C3Kernels.java:34
    i_1 = i_1 + v24;
  }
  return;
}
```

### CUDA generated by the Graal path (same Java)
```cuda
extern "C" __global__ void add(long long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics, unsigned char *arg1, unsigned char *arg2, unsigned char *arg3)
{
  unsigned long long ul_0, ul_1, ul_2, ul_9, ul_11, ul_13; 
  int i_3, i_4, i_5, i_15; 
  long long l_6, l_7, l_8; 
  float f_10, f_12, f_14; 

  // BLOCK 0
  ul_0  =  (unsigned long long) arg1;
  ul_1  =  (unsigned long long) arg2;
  ul_2  =  (unsigned long long) arg3;
  i_3  =  (gridDim.x*blockDim.x);
  i_4  =  (blockIdx.x*blockDim.x+threadIdx.x);
  // BLOCK 1 MERGES [0 2 ]
  i_5  =  i_4;
  for(;i_5 < 1048576;)
  {
    // BLOCK 2
    l_6  =  (long long) i_5;
    l_7  =  l_6 + 4L;
    l_8  =  l_7 << 2;
    ul_9  =  ul_0 + l_8;
    f_10  =  *(( float *) ul_9);
    ul_11  =  ul_1 + l_8;
    f_12  =  *(( float *) ul_11);
    ul_13  =  ul_2 + l_8;
    f_14  =  f_10 + f_12;
    *(( float *) ul_13)  =  f_14;
    i_15  =  i_3 + i_5;
    i_5  =  i_15;
  }  // B1
  // BLOCK 3
  return;
}  //  kernel
```

## If/else with `sqrt`

### Java
```java
    public static void compute(KernelContext ctx, FloatArray in, FloatArray out) {
        int i = ctx.globalIdx;
        if (i < in.getSize()) {
            float x = in.get(i);
            float v;
            if (x > 0) {
                v = TornadoMath.sqrt(x) * 2;
            } else {
                v = -x;
            }
            out.set(i, v);
        }
    }
```

### Bytecode (`javac -g`)
```
// uk.ac.manchester.tornado.tc.gpu.kernels.C3Kernels.compute(Luk/ac/manchester/tornado/api/KernelContext;Luk/ac/manchester/tornado/api/types/arrays/FloatArray;Luk/ac/manchester/tornado/api/types/arrays/FloatArray;)V
// max_stack=3 max_locals=6
  // line 40
     0: aload_0         
     1: getfield        KernelContext.globalIdx Ljava/lang/Integer;
     4: invokevirtual   Integer.intValue ()I
     7: istore_3        
  // line 41
     8: iload_3         
     9: aload_1         
    10: invokevirtual   FloatArray.getSize ()I
    13: if_icmpge       54
  // line 42
    16: aload_1         
    17: iload_3         
    18: invokevirtual   FloatArray.get (I)F
    21: fstore          4
  // line 44
    23: fload           4
    25: fconst_0        
    26: fcmpl           
    27: ifle            42
  // line 45
    30: fload           4
    32: invokestatic    TornadoMath.sqrt (F)F
    35: fconst_2        
    36: fmul            
    37: fstore          5
    39: goto            47
  // line 47
    42: fload           4
    44: fneg            
    45: fstore          5
  // line 49
    47: aload_2         
    48: iload_3         
    49: fload           5
    51: invokevirtual   FloatArray.set (IF)V
  // line 51
    54: return
```

### TC-SSA from the frontend (pre-ABI)
```llvm
kernel @C3Kernels.compute(%ctx: obj<KernelContext>, %in: array<f32>, %out: array<f32>) -> void {
^entry:
  br ^bci0
^bci0:
  %i = tornado.thread_id.global.x() : i32  loc("C3Kernels.java":40, bci=1)
  %0 = array.length %in : i32  loc("C3Kernels.java":41, bci=10)
  %1 = icmp.sge %i, %0 : i1  loc("C3Kernels.java":41, bci=13)
  cond_br %1, ^bci54, ^bci16  loc("C3Kernels.java":41, bci=13)
^bci16:
  %x = array.load %in[%i] : f32  loc("C3Kernels.java":42, bci=18)
  %2 = const 0.0 : f32  loc("C3Kernels.java":44, bci=25)
  %3 = fcmp.ule %x, %2 : i1  loc("C3Kernels.java":44, bci=26)
  cond_br %3, ^bci42, ^bci30  loc("C3Kernels.java":44, bci=26)
^bci30:
  %4 = math.sqrt(%x) : f32  loc("C3Kernels.java":45, bci=32)
  %5 = const 2.0 : f32  loc("C3Kernels.java":45, bci=35)
  %v = fmul %4, %5 : f32  loc("C3Kernels.java":45, bci=36)
  br ^bci47(%v)  loc("C3Kernels.java":45, bci=39)
^bci42:
  %v.1 = fneg %x : f32  loc("C3Kernels.java":47, bci=44)
  br ^bci47(%v.1)  loc("C3Kernels.java":47, bci=45)
^bci47(%v.2: f32):
  array.store %out[%i], %v.2  loc("C3Kernels.java":49, bci=51)
  br ^bci54  loc("C3Kernels.java":49, bci=51)
^bci54:
  ret  loc("C3Kernels.java":51, bci=54)
}
```

### TC-SSA after ABI lowering
```llvm
kernel @C3Kernels.compute(%in: ptr<global>, %out: ptr<global>) -> void {
^entry:
  br ^bci0
^bci0:
  %i = tornado.thread_id.global.x() : i32  loc("C3Kernels.java":40, bci=1)
  %0 = load %in : i64  loc("C3Kernels.java":41, bci=10)
  %1 = trunc %0 : i32  loc("C3Kernels.java":41, bci=10)
  %2 = icmp.sge %i, %1 : i1  loc("C3Kernels.java":41, bci=13)
  cond_br %2, ^bci54, ^bci16  loc("C3Kernels.java":41, bci=13)
^bci16:
  %3 = sext %i : i64  loc("C3Kernels.java":42, bci=18)
  %4 = const 4 : i64  loc("C3Kernels.java":42, bci=18)
  %5 = mul %3, %4 : i64  loc("C3Kernels.java":42, bci=18)
  %6 = const 16 : i64  loc("C3Kernels.java":42, bci=18)
  %7 = add %5, %6 : i64  loc("C3Kernels.java":42, bci=18)
  %8 = ptr.offset %in, %7 : ptr<global>  loc("C3Kernels.java":42, bci=18)
  %x = load %8 : f32  loc("C3Kernels.java":42, bci=18)
  %9 = const 0.0 : f32  loc("C3Kernels.java":44, bci=25)
  %10 = fcmp.ule %x, %9 : i1  loc("C3Kernels.java":44, bci=26)
  cond_br %10, ^bci42, ^bci30  loc("C3Kernels.java":44, bci=26)
^bci30:
  %11 = math.sqrt(%x) : f32  loc("C3Kernels.java":45, bci=32)
  %12 = const 2.0 : f32  loc("C3Kernels.java":45, bci=35)
  %v = fmul %11, %12 : f32  loc("C3Kernels.java":45, bci=36)
  br ^bci47(%v)  loc("C3Kernels.java":45, bci=39)
^bci42:
  %v.1 = fneg %x : f32  loc("C3Kernels.java":47, bci=44)
  br ^bci47(%v.1)  loc("C3Kernels.java":47, bci=45)
^bci47(%v.2: f32):
  %13 = sext %i : i64  loc("C3Kernels.java":49, bci=51)
  %14 = const 4 : i64  loc("C3Kernels.java":49, bci=51)
  %15 = mul %13, %14 : i64  loc("C3Kernels.java":49, bci=51)
  %16 = const 16 : i64  loc("C3Kernels.java":49, bci=51)
  %17 = add %15, %16 : i64  loc("C3Kernels.java":49, bci=51)
  %18 = ptr.offset %out, %17 : ptr<global>  loc("C3Kernels.java":49, bci=51)
  store %18, %v.2  loc("C3Kernels.java":49, bci=51)
  br ^bci54  loc("C3Kernels.java":49, bci=51)
^bci54:
  ret  loc("C3Kernels.java":51, bci=54)
}
```

### TC-S (structured)
```c
kernel C3Kernels.compute(ptr<global> in, ptr<global> out) -> void {
  i32 i;
  i64 v0;
  f32 x;
  f32 v11;
  f32 v_2;
  i = tornado.thread_id.global.x();
  v0 = load<i64>(in);
  if ((i sge trunc<i32>(v0))) {
  } else {
    x = load<f32>(ptr.offset(in, ((sext<i64>(i) * 4) + 16)));
    if ((x ule 0.0)) {
      v_2 = fneg(x);
    } else {
      v11 = math.sqrt(x);
      v_2 = (v11 * 2.0);
    }
    store(ptr.offset(out, ((sext<i64>(i) * 4) + 16)), v_2);
  }
  return;
}
```

### CUDA generated by TC (ran on the RTX 4090: PASS)
```cuda
extern "C" __global__ void C3Kernels_compute(long long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics, unsigned char *in, unsigned char *out)
{
  int i;
  long long v0;
  float x;
  float v11;
  float v_2;
  i = (int) (blockIdx.x * blockDim.x + threadIdx.x);  // C3Kernels.java:40
  v0 = *((long long *) in);  // C3Kernels.java:41
  if (i < (int) v0) {
    x = *((float *) (in + ((long long) i * 4LL + 16LL)));  // C3Kernels.java:42
    if (x > 0.0f) {
      v11 = sqrtf(x);  // C3Kernels.java:45
      v_2 = v11 * 2.0f;
    } else {
      v_2 = -x;
    }
    *((float *) (out + ((long long) i * 4LL + 16LL))) = v_2;  // C3Kernels.java:49
  }
  return;
}
```

### CUDA generated by the Graal path (same Java)
```cuda
extern "C" __global__ void compute(long long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics, unsigned char *arg1, unsigned char *arg2)
{
  unsigned long long ul_0, ul_1, ul_7, ul_14; 
  int i_2, i_9; 
  bool b_3; 
  long long l_4, l_5, l_6; 
  float f_8, f_13, f_12, f_10, f_11; 

  // BLOCK 0
  ul_0  =  (unsigned long long) arg1;
  ul_1  =  (unsigned long long) arg2;
  i_2  =  (blockIdx.x*blockDim.x+threadIdx.x);
  b_3  =  i_2 < 1048576;
  if(b_3)
  {
    // BLOCK 1
    l_4  =  (long long) i_2;
    l_5  =  l_4 + 4L;
    l_6  =  l_5 << 2;
    ul_7  =  ul_0 + l_6;
    f_8  =  *(( float *) ul_7);
    i_9  =  ((0.0F) < (f_8));
    if(i_9 == 1)
    {
      // BLOCK 2
      f_10  =  sqrt(f_8);
      f_11  =  f_10 * 2.0F;
      f_12  =  f_11;
    }  // B1
    else
    {
      // BLOCK 3
      f_13  =  -f_8;
      f_12  =  f_13;
    }  // B1
    // BLOCK 4 MERGES [2 3 ]
    ul_14  =  ul_1 + l_6;
    *(( float *) ul_14)  =  f_12;
  }  // B0
  else
  {
    // BLOCK 5
  }  // B0
  // BLOCK 6 MERGES [5 4 ]
  return;
}  //  kernel
```

## KernelContext shared-memory tree reduction

### Java
```java
    public static void blockSum(KernelContext ctx, FloatArray in, FloatArray out) {
        int gid = ctx.globalIdx;
        int lid = ctx.localIdx;
        int grp = ctx.groupIdx;
        int lsz = ctx.localGroupSizeX;
        float[] scratch = ctx.allocateFloatLocalArray(256);
        scratch[lid] = in.get(gid);
        ctx.localBarrier();
        for (int s = lsz / 2; s > 0; s /= 2) {
            if (lid < s) {
                scratch[lid] += scratch[lid + s];
            }
            ctx.localBarrier();
        }
        if (lid == 0) {
            out.set(grp, scratch[0]);
        }
    }
```

### Bytecode (`javac -g`)
```
// uk.ac.manchester.tornado.tc.gpu.kernels.C3Kernels.blockSum(Luk/ac/manchester/tornado/api/KernelContext;Luk/ac/manchester/tornado/api/types/arrays/FloatArray;Luk/ac/manchester/tornado/api/types/arrays/FloatArray;)V
// max_stack=6 max_locals=9
  // line 54
     0: aload_0         
     1: getfield        KernelContext.globalIdx Ljava/lang/Integer;
     4: invokevirtual   Integer.intValue ()I
     7: istore_3        
  // line 55
     8: aload_0         
     9: getfield        KernelContext.localIdx Ljava/lang/Integer;
    12: invokevirtual   Integer.intValue ()I
    15: istore          4
  // line 56
    17: aload_0         
    18: getfield        KernelContext.groupIdx Ljava/lang/Integer;
    21: invokevirtual   Integer.intValue ()I
    24: istore          5
  // line 57
    26: aload_0         
    27: getfield        KernelContext.localGroupSizeX Ljava/lang/Integer;
    30: invokevirtual   Integer.intValue ()I
    33: istore          6
  // line 58
    35: aload_0         
    36: sipush          256
    39: invokevirtual   KernelContext.allocateFloatLocalArray (I)[F
    42: astore          7
  // line 59
    44: aload           7
    46: iload           4
    48: aload_1         
    49: iload_3         
    50: invokevirtual   FloatArray.get (I)F
    53: fastore         
  // line 60
    54: aload_0         
    55: invokevirtual   KernelContext.localBarrier ()V
  // line 61
    58: iload           6
    60: iconst_2        
    61: idiv            
    62: istore          8
    64: iload           8
    66: ifle            105
  // line 62
    69: iload           4
    71: iload           8
    73: if_icmpge       92
  // line 63
    76: aload           7
    78: iload           4
    80: dup2            
    81: faload          
    82: aload           7
    84: iload           4
    86: iload           8
    88: iadd            
    89: faload          
    90: fadd            
    91: fastore         
  // line 65
    92: aload_0         
    93: invokevirtual   KernelContext.localBarrier ()V
  // line 61
    96: iload           8
    98: iconst_2        
    99: idiv            
   100: istore          8
   102: goto            64
  // line 67
   105: iload           4
   107: ifne            120
  // line 68
   110: aload_2         
   111: iload           5
   113: aload           7
   115: iconst_0        
   116: faload          
   117: invokevirtual   FloatArray.set (IF)V
  // line 70
   120: return
```

### TC-SSA from the frontend (pre-ABI)
```llvm
kernel @C3Kernels.blockSum(%ctx: obj<KernelContext>, %in: array<f32>, %out: array<f32>) -> void {
^entry:
  br ^bci0
^bci0:
  %gid = tornado.thread_id.global.x() : i32  loc("C3Kernels.java":54, bci=1)
  %lid = tornado.thread_id.local.x() : i32  loc("C3Kernels.java":55, bci=9)
  %grp = tornado.group_id.x() : i32  loc("C3Kernels.java":56, bci=18)
  %lsz = tornado.group_size.local.x() : i32  loc("C3Kernels.java":57, bci=27)
  %0 = const 256 : i32  loc("C3Kernels.java":58, bci=36)
  %scratch = alloca.shared f32[256] : ptr<shared>  loc("C3Kernels.java":58, bci=39)
  %1 = array.load %in[%gid] : f32  loc("C3Kernels.java":59, bci=50)
  %2 = sext %lid : i64  loc("C3Kernels.java":59, bci=53)
  %3 = const 4 : i64  loc("C3Kernels.java":59, bci=53)
  %4 = mul %2, %3 : i64  loc("C3Kernels.java":59, bci=53)
  %5 = ptr.offset %scratch, %4 : ptr<shared>  loc("C3Kernels.java":59, bci=53)
  store %5, %1  loc("C3Kernels.java":59, bci=53)
  tornado.barrier.local()  loc("C3Kernels.java":60, bci=55)
  %6 = const 2 : i32  loc("C3Kernels.java":61, bci=60)
  %s = div %lsz, %6 : i32  loc("C3Kernels.java":61, bci=61)
  br ^bci64(%s)  loc("C3Kernels.java":61, bci=62)
^bci64(%s.1: i32):
  %7 = const 0 : i32  loc("C3Kernels.java":61, bci=66)
  %8 = icmp.sle %s.1, %7 : i1  loc("C3Kernels.java":61, bci=66)
  cond_br %8, ^bci105, ^bci69  loc("C3Kernels.java":61, bci=66)
^bci69:
  %9 = icmp.sge %lid, %s.1 : i1  loc("C3Kernels.java":62, bci=73)
  cond_br %9, ^bci92, ^bci76  loc("C3Kernels.java":62, bci=73)
^bci76:
  %10 = sext %lid : i64  loc("C3Kernels.java":63, bci=81)
  %11 = const 4 : i64  loc("C3Kernels.java":63, bci=81)
  %12 = mul %10, %11 : i64  loc("C3Kernels.java":63, bci=81)
  %13 = ptr.offset %scratch, %12 : ptr<shared>  loc("C3Kernels.java":63, bci=81)
  %14 = load %13 : f32  loc("C3Kernels.java":63, bci=81)
  %15 = add %lid, %s.1 : i32  loc("C3Kernels.java":63, bci=88)
  %16 = sext %15 : i64  loc("C3Kernels.java":63, bci=89)
  %17 = const 4 : i64  loc("C3Kernels.java":63, bci=89)
  %18 = mul %16, %17 : i64  loc("C3Kernels.java":63, bci=89)
  %19 = ptr.offset %scratch, %18 : ptr<shared>  loc("C3Kernels.java":63, bci=89)
  %20 = load %19 : f32  loc("C3Kernels.java":63, bci=89)
  %21 = fadd %14, %20 : f32  loc("C3Kernels.java":63, bci=90)
  %22 = sext %lid : i64  loc("C3Kernels.java":63, bci=91)
  %23 = const 4 : i64  loc("C3Kernels.java":63, bci=91)
  %24 = mul %22, %23 : i64  loc("C3Kernels.java":63, bci=91)
  %25 = ptr.offset %scratch, %24 : ptr<shared>  loc("C3Kernels.java":63, bci=91)
  store %25, %21  loc("C3Kernels.java":63, bci=91)
  br ^bci92  loc("C3Kernels.java":63, bci=91)
^bci92:
  tornado.barrier.local()  loc("C3Kernels.java":65, bci=93)
  %26 = const 2 : i32  loc("C3Kernels.java":61, bci=98)
  %s.2 = div %s.1, %26 : i32  loc("C3Kernels.java":61, bci=99)
  br ^bci64(%s.2)  loc("C3Kernels.java":61, bci=102)
^bci105:
  %27 = const 0 : i32  loc("C3Kernels.java":67, bci=107)
  %28 = icmp.ne %lid, %27 : i1  loc("C3Kernels.java":67, bci=107)
  cond_br %28, ^bci120, ^bci110  loc("C3Kernels.java":67, bci=107)
^bci110:
  %29 = const 0 : i32  loc("C3Kernels.java":68, bci=115)
  %30 = sext %29 : i64  loc("C3Kernels.java":68, bci=116)
  %31 = const 4 : i64  loc("C3Kernels.java":68, bci=116)
  %32 = mul %30, %31 : i64  loc("C3Kernels.java":68, bci=116)
  %33 = ptr.offset %scratch, %32 : ptr<shared>  loc("C3Kernels.java":68, bci=116)
  %34 = load %33 : f32  loc("C3Kernels.java":68, bci=116)
  array.store %out[%grp], %34  loc("C3Kernels.java":68, bci=117)
  br ^bci120  loc("C3Kernels.java":68, bci=117)
^bci120:
  ret  loc("C3Kernels.java":70, bci=120)
}
```

### TC-SSA after ABI lowering
```llvm
kernel @C3Kernels.blockSum(%in: ptr<global>, %out: ptr<global>) -> void {
^entry:
  br ^bci0
^bci0:
  %gid = tornado.thread_id.global.x() : i32  loc("C3Kernels.java":54, bci=1)
  %lid = tornado.thread_id.local.x() : i32  loc("C3Kernels.java":55, bci=9)
  %grp = tornado.group_id.x() : i32  loc("C3Kernels.java":56, bci=18)
  %lsz = tornado.group_size.local.x() : i32  loc("C3Kernels.java":57, bci=27)
  %0 = const 256 : i32  loc("C3Kernels.java":58, bci=36)
  %scratch = alloca.shared f32[256] : ptr<shared>  loc("C3Kernels.java":58, bci=39)
  %1 = sext %gid : i64  loc("C3Kernels.java":59, bci=50)
  %2 = const 4 : i64  loc("C3Kernels.java":59, bci=50)
  %3 = mul %1, %2 : i64  loc("C3Kernels.java":59, bci=50)
  %4 = const 16 : i64  loc("C3Kernels.java":59, bci=50)
  %5 = add %3, %4 : i64  loc("C3Kernels.java":59, bci=50)
  %6 = ptr.offset %in, %5 : ptr<global>  loc("C3Kernels.java":59, bci=50)
  %7 = load %6 : f32  loc("C3Kernels.java":59, bci=50)
  %8 = sext %lid : i64  loc("C3Kernels.java":59, bci=53)
  %9 = const 4 : i64  loc("C3Kernels.java":59, bci=53)
  %10 = mul %8, %9 : i64  loc("C3Kernels.java":59, bci=53)
  %11 = ptr.offset %scratch, %10 : ptr<shared>  loc("C3Kernels.java":59, bci=53)
  store %11, %7  loc("C3Kernels.java":59, bci=53)
  tornado.barrier.local()  loc("C3Kernels.java":60, bci=55)
  %12 = const 2 : i32  loc("C3Kernels.java":61, bci=60)
  %s = div %lsz, %12 : i32  loc("C3Kernels.java":61, bci=61)
  br ^bci64(%s)  loc("C3Kernels.java":61, bci=62)
^bci64(%s.1: i32):
  %13 = const 0 : i32  loc("C3Kernels.java":61, bci=66)
  %14 = icmp.sle %s.1, %13 : i1  loc("C3Kernels.java":61, bci=66)
  cond_br %14, ^bci105, ^bci69  loc("C3Kernels.java":61, bci=66)
^bci69:
  %15 = icmp.sge %lid, %s.1 : i1  loc("C3Kernels.java":62, bci=73)
  cond_br %15, ^bci92, ^bci76  loc("C3Kernels.java":62, bci=73)
^bci76:
  %16 = sext %lid : i64  loc("C3Kernels.java":63, bci=81)
  %17 = const 4 : i64  loc("C3Kernels.java":63, bci=81)
  %18 = mul %16, %17 : i64  loc("C3Kernels.java":63, bci=81)
  %19 = ptr.offset %scratch, %18 : ptr<shared>  loc("C3Kernels.java":63, bci=81)
  %20 = load %19 : f32  loc("C3Kernels.java":63, bci=81)
  %21 = add %lid, %s.1 : i32  loc("C3Kernels.java":63, bci=88)
  %22 = sext %21 : i64  loc("C3Kernels.java":63, bci=89)
  %23 = const 4 : i64  loc("C3Kernels.java":63, bci=89)
  %24 = mul %22, %23 : i64  loc("C3Kernels.java":63, bci=89)
  %25 = ptr.offset %scratch, %24 : ptr<shared>  loc("C3Kernels.java":63, bci=89)
  %26 = load %25 : f32  loc("C3Kernels.java":63, bci=89)
  %27 = fadd %20, %26 : f32  loc("C3Kernels.java":63, bci=90)
  %28 = sext %lid : i64  loc("C3Kernels.java":63, bci=91)
  %29 = const 4 : i64  loc("C3Kernels.java":63, bci=91)
  %30 = mul %28, %29 : i64  loc("C3Kernels.java":63, bci=91)
  %31 = ptr.offset %scratch, %30 : ptr<shared>  loc("C3Kernels.java":63, bci=91)
  store %31, %27  loc("C3Kernels.java":63, bci=91)
  br ^bci92  loc("C3Kernels.java":63, bci=91)
^bci92:
  tornado.barrier.local()  loc("C3Kernels.java":65, bci=93)
  %32 = const 2 : i32  loc("C3Kernels.java":61, bci=98)
  %s.2 = div %s.1, %32 : i32  loc("C3Kernels.java":61, bci=99)
  br ^bci64(%s.2)  loc("C3Kernels.java":61, bci=102)
^bci105:
  %33 = const 0 : i32  loc("C3Kernels.java":67, bci=107)
  %34 = icmp.ne %lid, %33 : i1  loc("C3Kernels.java":67, bci=107)
  cond_br %34, ^bci120, ^bci110  loc("C3Kernels.java":67, bci=107)
^bci110:
  %35 = const 0 : i32  loc("C3Kernels.java":68, bci=115)
  %36 = sext %35 : i64  loc("C3Kernels.java":68, bci=116)
  %37 = const 4 : i64  loc("C3Kernels.java":68, bci=116)
  %38 = mul %36, %37 : i64  loc("C3Kernels.java":68, bci=116)
  %39 = ptr.offset %scratch, %38 : ptr<shared>  loc("C3Kernels.java":68, bci=116)
  %40 = load %39 : f32  loc("C3Kernels.java":68, bci=116)
  %41 = sext %grp : i64  loc("C3Kernels.java":68, bci=117)
  %42 = const 4 : i64  loc("C3Kernels.java":68, bci=117)
  %43 = mul %41, %42 : i64  loc("C3Kernels.java":68, bci=117)
  %44 = const 16 : i64  loc("C3Kernels.java":68, bci=117)
  %45 = add %43, %44 : i64  loc("C3Kernels.java":68, bci=117)
  %46 = ptr.offset %out, %45 : ptr<global>  loc("C3Kernels.java":68, bci=117)
  store %46, %40  loc("C3Kernels.java":68, bci=117)
  br ^bci120  loc("C3Kernels.java":68, bci=117)
^bci120:
  ret  loc("C3Kernels.java":70, bci=120)
}
```

### TC-S (structured)
```c
kernel C3Kernels.blockSum(ptr<global> in, ptr<global> out) -> void {
  shared f32 scratch[256];
  i32 gid;
  i32 lid;
  i32 grp;
  i32 lsz;
  f32 v7;
  i32 s;
  i32 s_1;
  f32 v20;
  f32 v26;
  i32 s_2;
  f32 v40;
  gid = tornado.thread_id.global.x();
  lid = tornado.thread_id.local.x();
  grp = tornado.group_id.x();
  lsz = tornado.group_size.local.x();
  v7 = load<f32>(ptr.offset(in, ((sext<i64>(gid) * 4) + 16)));
  store(ptr.offset(&scratch, (sext<i64>(lid) * 4)), v7);
  tornado.barrier.local();
  s = (lsz / 2);
  s_1 = s;
  block L_bci105 {
    loop L_bci64 {
      if ((s_1 sle 0)) {
        break L_bci105;
      } else {
        if ((lid sge s_1)) {
        } else {
          v20 = load<f32>(ptr.offset(&scratch, (sext<i64>(lid) * 4)));
          v26 = load<f32>(ptr.offset(&scratch, (sext<i64>((lid + s_1)) * 4)));
          store(ptr.offset(&scratch, (sext<i64>(lid) * 4)), (v20 + v26));
        }
        tornado.barrier.local();
        s_2 = (s_1 / 2);
        s_1 = s_2;
      }
    }
  }
  if ((lid ne 0)) {
  } else {
    v40 = load<f32>(ptr.offset(&scratch, (sext<i64>(0) * 4)));
    store(ptr.offset(out, ((sext<i64>(grp) * 4) + 16)), v40);
  }
  return;
}
```

### CUDA generated by TC (ran on the RTX 4090: PASS)
```cuda
extern "C" __global__ void C3Kernels_blockSum(long long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics, unsigned char *in, unsigned char *out)
{
  __shared__ float scratch[256];
  int gid;
  int lid;
  int grp;
  int lsz;
  float v7;
  int s;
  int s_1;
  float v20;
  float v26;
  int s_2;
  float v40;
  gid = (int) (blockIdx.x * blockDim.x + threadIdx.x);  // C3Kernels.java:54
  lid = (int) threadIdx.x;  // C3Kernels.java:55
  grp = (int) blockIdx.x;  // C3Kernels.java:56
  lsz = (int) blockDim.x;  // C3Kernels.java:57
  v7 = *((float *) (in + ((long long) gid * 4LL + 16LL)));  // C3Kernels.java:59
  *((float *) ((unsigned char *) scratch + (long long) lid * 4LL)) = v7;  // C3Kernels.java:59
  __syncthreads();  // C3Kernels.java:60
  s = lsz / 2;  // C3Kernels.java:61
  s_1 = s;
  while (s_1 > 0) {
    if (lid < s_1) {
      v20 = *((float *) ((unsigned char *) scratch + (long long) lid * 4LL));  // C3Kernels.java:63
      v26 = *((float *) ((unsigned char *) scratch + (long long) (lid + s_1) * 4LL));  // C3Kernels.java:63
      *((float *) ((unsigned char *) scratch + (long long) lid * 4LL)) = v20 + v26;  // C3Kernels.java:63
    }
    __syncthreads();  // C3Kernels.java:65
    s_2 = s_1 / 2;  // C3Kernels.java:61
    s_1 = s_2;
  }
  if (lid == 0) {
    v40 = *((float *) ((unsigned char *) scratch + (long long) 0 * 4LL));  // C3Kernels.java:68
    *((float *) (out + ((long long) grp * 4LL + 16LL))) = v40;  // C3Kernels.java:68
  }
  return;
}
```

### CUDA generated by the Graal path (same Java)
```cuda
extern "C" __global__ void blockSum(long long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics, unsigned char *arg1, unsigned char *arg2)
{
  unsigned long long ul_0, ul_1, ul_7, ul_31; 
  int i_3, i_9, i_10, i_13, i_14, i_15, i_16, i_19, i_24, i_27; 
  long long l_4, l_5, l_6, l_28, l_29, l_30; 
  float f_8, f_18, f_20, f_21, f_26; 
  unsigned int ui_11, ui_12, ui_22, ui_23; 
  bool b_17, b_25; 

  // BLOCK 0
  ul_0  =  (unsigned long long) arg1;
  ul_1  =  (unsigned long long) arg2;
  __shared__ float adf_2[256];
  i_3  =  (blockIdx.x*blockDim.x+threadIdx.x);
  l_4  =  (long long) i_3;
  l_5  =  l_4 + 4L;
  l_6  =  l_5 << 2;
  ul_7  =  ul_0 + l_6;
  f_8  =  *(( float *) ul_7);
  i_9  =  (threadIdx.x);
  adf_2[i_9]  =  f_8;
  __syncthreads();
  i_10  =  (blockDim.x);
  ui_11  =  (unsigned int) i_10;
  ui_12  =  ui_11 >> 31;
  i_13  =  (int) ui_12;
  i_14  =  i_13 + i_10;
  i_15  =  i_14 >> 1;
  // BLOCK 1 MERGES [0 5 ]
  i_16  =  i_15;
  for(;i_16 >= 1;)
  {
    // BLOCK 2
    b_17  =  i_9 < i_16;
    if(b_17)
    {
      // BLOCK 3
      f_18  =  adf_2[i_9];
      i_19  =  i_16 + i_9;
      f_20  =  adf_2[i_19];
      f_21  =  f_18 + f_20;
      adf_2[i_9]  =  f_21;
    }  // B2
    else
    {
      // BLOCK 4
    }  // B2
    // BLOCK 5 MERGES [4 3 ]
    __syncthreads();
    ui_22  =  (unsigned int) i_16;
    ui_23  =  ui_22 >> 1;
    i_24  =  (int) ui_23;
    i_16  =  i_24;
  }  // B1
  // BLOCK 6
  b_25  =  i_9 == 0;
  if(b_25)
  {
    // BLOCK 7
    f_26  =  adf_2[0];
    i_27  =  (blockIdx.x);
    l_28  =  (long long) i_27;
    l_29  =  l_28 + 4L;
    l_30  =  l_29 << 2;
    ul_31  =  ul_1 + l_30;
    *(( float *) ul_31)  =  f_26;
  }  // B6
  else
  {
    // BLOCK 8
  }  // B6
  // BLOCK 9 MERGES [8 7 ]
  return;
}  //  kernel
```
