dnl Process this file with autoconf to produce a configure script.

AC_PREREQ(2.59)
AC_INIT(drivers, 1.0)
AC_PROG_CC

AC_CANONICAL_SYSTEM

AC_CONFIG_MACRO_DIR([m4])

dnl Initialize automake
AM_INIT_AUTOMAKE

dnl this allows us specify individual liking flags for each target
AM_PROG_CC_C_O 

dnl Initialize Libtool
LT_INIT

#
# Platform specific setup
#
#############################

jdk_inst_dir=""
AC_ARG_WITH([jdk],
	AS_HELP_STRING([--with-jdk=dir],[Java SDK directory.]),
		[ jdk_inst_dir=${withval} ]
	)

if [test -n ${jdk_inst_dir} ]; then
	have_jdk="yes"
	JDK_ROOT=${jdk_inst_dir}
fi
AM_CONDITIONAL(HAVE_JDK, [test "x$have_jdk" = xyes])


# Check for which host we are on and setup a few things
# specifically based on the host
AC_CANONICAL_HOST
case $host_os in
  darwin* )
        # Do something specific for mac
        CUSTOM_CPPFLAGS="-D_OSX"
        OPENCL_LDFLAGS="-framework OpenCL"
        JDK_CPPFLAGS="-I${JDK_ROOT}/include/ -I${JDK_ROOT}/include/darwin" 
        ;;
  linux*)
        # Do something specific for linux
        CUSTOM_CPPFLAGS="-std=c99"
        OPENCL_LDFLAGS="-lOpenCL"
        JDK_CPPFLAGS="-I${JDK_ROOT}/include/ -I${JDK_ROOT}/include/linux"
        ;;
   *BSD*)
        # Do something specific for BSD
        ;;
    *)
        #Default Case
        AC_MSG_ERROR([Your platform is not currently supported])
        ;;
esac

AC_SUBST(CUSTOM_CPPFLAGS)
AC_SUBST(OPENCL_LDFLAGS)
AC_SUBST(JDK_CPPFLAGS)

AC_CONFIG_FILES(Makefile
                libtornado-opencl/Makefile
                include/Makefile)
AC_OUTPUT

echo "---------------------------------------------------------"
echo " Configuration Summary:"
echo ""
echo " OS: ${host_os}"
if test x"$have_jdk" != x"yes"; then
        echo " WARNING: Cannot find a working Java SDK."
else
        echo " Using JDK: ${JDK_ROOT}"
fi
