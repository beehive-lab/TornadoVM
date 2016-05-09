#######################################
# The list of libraries we are building seperated by spaces.
# The 'lib_' indicates that these build products will be installed
# in the $(libdir) directory. For example /usr/lib
lib_LTLIBRARIES = libtornado-opencl.la

#######################################
# Build information for each library

# Sources for libtornado
libtornado_opencl_la_SOURCES = OCLCommandQueue.c OCLContext.c OCLDevice.c OCLEvent.c \
							   OCLKernel.c OCLPlatform.c OCLProgram.c OpenCL.c data_movers.c utils.c

# Linker options libTestProgram
libtornado_opencl_la_LDFLAGS = @OPENCL_LDFLAGS@

# Compiler options. Here we are adding the include directory
# to be searched for headers included in the source code.
libtornado_opencl_la_CPPFLAGS = -I$(top_srcdir)/include -I. @JDK_CPPFLAGS@ @CUSTOM_CPPFLAGS@

