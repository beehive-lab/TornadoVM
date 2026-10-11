/*
 * metalc: compiles Metal Shading Language files with -[MTLDevice newLibraryWithSource:options:error:]
 * on the Mac's GPU, the call the TornadoVM Metal driver makes at run time (default options, so fast
 * math as there). Used by the TC lit tests the way clbuild checks the OpenCL ones, and on a whole
 * EmitSweep directory. Warnings are not failures. Prints the compiler log of each file that fails.
 * Usage: metalc <file.metal | -> [more files...]
 * Build: clang -fobjc-arc -O2 -o metalc metalc.m -framework Metal -framework Foundation
 */
#import <Foundation/Foundation.h>
#import <Metal/Metal.h>

int main(int argc, char **argv) {
    @autoreleasepool {
        if (argc < 2) { fprintf(stderr, "usage: metalc <file.metal | -> [more files...]\n"); return 2; }
        id<MTLDevice> device = MTLCreateSystemDefaultDevice();
        if (!device) { fprintf(stderr, "metalc: no Metal device\n"); return 3; }
        int built = 0, failed = 0;
        for (int i = 1; i < argc; i++) {
            NSString *src;
            if (strcmp(argv[i], "-") == 0) {
                NSData *data = [[NSFileHandle fileHandleWithStandardInput] readDataToEndOfFile];
                src = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
            } else {
                NSError *readError = nil;
                src = [NSString stringWithContentsOfFile:@(argv[i]) encoding:NSUTF8StringEncoding error:&readError];
                if (!src) { fprintf(stderr, "metalc: %s: cannot read\n", argv[i]); failed++; continue; }
            }
            NSError *error = nil;
            id<MTLLibrary> library = [device newLibraryWithSource:src options:nil error:&error];
            if (!library) {
                fprintf(stderr, "metalc: %s: build failed\n%s\n", argv[i], error.localizedDescription.UTF8String);
                failed++;
            } else {
                built++;
            }
        }
        if (argc > 2 || failed) printf("metalc: %s: built %d, failed %d\n", device.name.UTF8String, built, failed);
        return failed ? 1 : 0;
    }
}
