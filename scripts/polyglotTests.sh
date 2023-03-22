echo " ============================================= "
echo " GraalVM Polyglot Tests "
echo " ============================================= "

tornado --printKernel --truffle python tornado-examples/src/main/java/uk/ac/manchester/tornado/examples/polyglot/mxmWithTornadoVM.py
tornado --printKernel --truffle js tornado-examples/src/main/java/uk/ac/manchester/tornado/examples/polyglot/mxmWithTornadoVM.js
tornado --printKernel --truffle r tornado-examples/src/main/java/uk/ac/manchester/tornado/examples/polyglot/mxmWithTornadoVM.r
