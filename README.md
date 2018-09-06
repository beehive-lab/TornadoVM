# Tornado

Tornado is a practical heterogeneous programming framework for automatically accelerating Java programs on heterogeneous (OpenCL-compatible)  hardware. 

## How to start? 

The [INSTALL](https://github.com/beehive-lab/tornado/blob/master/INSTALL.md) page contains instructions on how to install Tornado while the [Examples](https://github.com/beehive-lab/tornado/blob/master/assembly/src/docs/2_EXAMPLES.md) page includes   examples regarding running Java programs on GPUs. 
We also maintain a live Tornado whitepaper document which you can download [here](https://www.dropbox.com/s/rbb2qv0q2wicgvy/main.pdf).


## Selected Publications

* James Clarkson, Juan Fumero, Michalis Papadimitriou, Foivos S. Zakkak, Maria Xekalaki, Christos Kotselidis, Mikel Luján (The University of Manchester). **Exploiting High-Performance Heterogeneous Hardware for Java Programs using Graal**. *Proceedings of the 15th International Conference on Managed Languages & Runtime.* [preprint](https://www.researchgate.net/publication/327097904_Exploiting_High-Performance_Heterogeneous_Hardware_for_Java_Programs_using_Graal)

*Sajad Saeedi, Bruno Bodin, Harry Wagstaff, Andy Nisbet, Luigi Nardi, John Mawer, Nicolas Melot, Oscar Palomar, Emanuele Vespa, Tom Spink, Cosmin Gorgovan, Andrew Webb, James Clarkson, Erik Tomusk, Thomas Debrunner, Kuba Kaszyk, Pablo Gonzalez-de-Aledo, Andrey Rodchenko, Graham Riley, Christos Kotselidis, Björn Franke, Michael FP O'Boyle, Andrew J Davison, Paul HJ Kelly, Mikel Luján, Steve Furber. **Navigating the Landscape for Real-Time Localization and Mapping for Robotics and Virtual and Augmented Reality.** In Proceedings of the IEEE, 2018.

* C. Kotselidis, J. Clarkson, A. Rodchenko, A. Nisbet, J. Mawer, and M. Luján. **Heterogeneous Managed Runtime Systems: A Computer Vision Case Study.** In Proceedings of the 13th ACM SIGPLAN/SIGOPS International Conference on Virtual Execution Environments, VEE ’17, [link](https://dl.acm.org/citation.cfm?doid=3050748.3050764)


### Citation

Please use the following citation if you use Tornado in your work.

```bibtex
@inproceedings{Clarkson:2018:EHH:3237009.3237016,
 author = {Clarkson, James and Fumero, Juan and Papadimitriou, Michail and Zakkak, Foivos S. and Xekalaki, Maria and Kotselidis, Christos and Luj\'{a}n, Mikel},
 title = {{Exploiting High-performance Heterogeneous Hardware for Java Programs Using Graal}},
 booktitle = {Proceedings of the 15th International Conference on Managed Languages \& Runtimes},
 series = {ManLang '18},
 year = {2018},
 isbn = {978-1-4503-6424-9},
 location = {Linz, Austria},
 pages = {4:1--4:13},
 articleno = {4},
 numpages = {13},
 url = {http://doi.acm.org/10.1145/3237009.3237016},
 doi = {10.1145/3237009.3237016},
 acmid = {3237016},
 publisher = {ACM},
 address = {New York, NY, USA},
 keywords = {Java, graal, heterogeneous hardware, openCL, virtual machine},
} 

```

## Acknowledgments

This work was initially supported by the EPSRC grants [PAMELA EP/K008730/1](http://apt.cs.manchester.ac.uk/projects/PAMELA/) and [AnyScale Apps EP/L000725/1](http://anyscale.org), and now it is funded by the [EU Horizon 2020 E2Data 780245](https://e2data.eu) and the [EU Horizon 2020 ACTiCLOUD 732366](https://acticloud.eu) grants.

## Collaborations

We welcome collaborations! Please see how to contribute in the [CONTRIBUTIONS](https://github.com/beehive-lab/tornado/blob/master/CONTRIBUTIONS.md).

For academic collaborations please contact [Christos Kotselidis](https://www.kotselidis.net).


## Contributors 

This work was originated by James Clarkson under the joint supervision of [Mikel Luján](https://www.linkedin.com/in/mikellujan/) and [Christos Kotselidis](https://www.kotselidis.net). 
Currently, this project is maintained and updated by the following contributors:

* [Juan Fumero](https://jjfumero.github.io/)
* [Michail Papadimitriou](https://mikepapadim.github.io)
* [Maria Xekalaki](https://github.com/mairooni)
* [Christos Kotselidis](https://www.kotselidis.net)

## License

To use Tornado, you can link the Tornado API to your application which is under the CLASSPATH Exception of GPLv2.0.

Each Tornado module is licensed as follows:

|  Module | License  |
|---|---|
| Tornado-Runtime  | [![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html) + CLASSPATH Exception  |
| Tornado-Assembly  | [![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html) + CLASSPATH Exception |
| Tornado-Drivers |  [![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html) + CLASSPATH Exception |
| Torando-API  | [![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html) + CLASSPATH Exception |
| Tornado-scripts |  [![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html) |
| Tornado-Unittests |  [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0) |
| Tornado-Benchmarks | [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)  |
| Tornado-Examples |  [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0) |
| Tornado-Matrices  |  [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0) |

