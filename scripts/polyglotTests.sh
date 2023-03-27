echo " ============================================= "
echo " GraalVM Polyglot Tests "
echo " ============================================= "

tornado --printKernel --truffle python $TORNADO_SDK/examples/polyglotTruffle/mxmWithTornadoVM.py 
tornado --printKernel --truffle js $TORNADO_SDK/examples/polyglotTruffle/mxmWithTornadoVM.js
tornado --printKernel --truffle r $TORNADO_SDK/examples/polyglotTruffle/mxmWithTornadoVM.r
