# 1. TornadoVM

🌪️ TornadoVM is a plug-in to OpenJDK and GraalVM that allows programmers to automatically run Java programs on
heterogeneous hardware. TornadoVM currently targets OpenCL-compatible devices and it runs on multi-core CPUs, GPUs (NVIDIA and AMD), Intel integrated GPUs, and Intel FPGAs.

**Current Release:** TornadoVM 0.6  - 21/02/2020 : See [CHANGELOG](CHANGELOG.md#tornadovm-06)

Previous Releases can be found [here](pages/Releases.md)

# 2. Installation

TornadoVM can be installed either [from scratch](INSTALL.md) or by [using Docker](assembly/docs/12_INSTALL_WITH_DOCKER.md).


## What can I do with TornadoVM?

TornadoVM can currently accelerate machine learning and deep learning applications, computer vision, physics simulations, financial applications, computational photography, and signal processing.

We have a use-case, [kfusion-tornadovm](https://github.com/beehive-lab/kfusion-tornadovm), for accelerating a computer-vision application implemented in Java using the Tornado-API to run on GPUs.

We also have a set of [examples](https://github.com/beehive-lab/TornadoVM/tree/master/examples/src/main/java/uk/ac/manchester/tornado/examples) that includes NBody, DFT, KMeans computation and matrix computations.

# 6. Publications

Selected publications and citations can be found [here](assembly/src/docs/13_PUBLICATIONS.md).

## Acknowledgments

This work was initially supported by the EPSRC grants [PAMELA EP/K008730/1](http://apt.cs.manchester.ac.uk/projects/PAMELA/) and [AnyScale Apps EP/L000725/1](http://anyscale.org), and now it is funded by the [EU Horizon 2020 E2Data 780245](https://e2data.eu) and the [EU Horizon 2020 ACTiCLOUD 732366](https://acticloud.eu) grants.

## Collaborations

We welcome collaborations! Please see how to contribute in the [CONTRIBUTIONS](CONTRIBUTIONS.md).

For academic collaborations please contact [Christos Kotselidis](https://www.kotselidis.net).


## Users Mailing list

A mailing list is also available to discuss Tornado related issues:

tornado-support@googlegroups.com

## Contributors

This work was originated by James Clarkson under the joint supervision of [Mikel Luján](https://www.linkedin.com/in/mikellujan/) and [Christos Kotselidis](https://www.kotselidis.net).
Currently, this project is maintained and updated by the following contributors:

* [Juan Fumero](https://jjfumero.github.io/)
* [Michail Papadimitriou](https://mikepapadim.github.io)
* [Maria Xekalaki](https://github.com/mairooni)
* [Athanasios Stratikopoulos](https://personalpages.manchester.ac.uk/staff/athanasios.stratikopoulos)
* [Florin Blanaru](https://github.com/gigiblender)
* [Christos Kotselidis](https://www.kotselidis.net)

## License

To use TornadoVM, you can link the Tornado API to your application which is under the CLASSPATH Exception of GPLv2.0.

Each TornadoVM module is licensed as follows:

|  Module | License  |
|---|---|
| Tornado-Runtime  | [![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html) + CLASSPATH Exception  |
| Tornado-Assembly  | [![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html) + CLASSPATH Exception |
| Tornado-Drivers |  [![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html) + CLASSPATH Exception |
| Tornado-API  | [![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html) + CLASSPATH Exception |
| Tornado-Drivers-OpenCL-Headers |  [![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://github.com/KhronosGroup/OpenCL-Headers/blob/master/LICENSE) |
| Tornado-scripts |  [![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html) |
| Tornado-Annotation |  [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0) |
| Tornado-Unittests |  [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0) |
| Tornado-Benchmarks | [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)  |
| Tornado-Examples |  [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0) |
| Tornado-Matrices  |  [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0) |
