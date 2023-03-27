echo " ============================================= "
echo " GraalVM Polyglot Tests "
echo " ============================================= "

tornado --printKernel --truffle python bin/sdk/examples/polyglotTruffle/mxmWithTornadoVM.py 
tornado --printKernel --truffle js bin/sdk/examples/polyglotTruffle/mxmWithTornadoVM.js
tornado --printKernel --truffle r bin/sdk/examples/polyglotTruffle/mxmWithTornadoVM.r
