# Tornado

Tornado is a practical heterogeneous programming framework for automatically accelerating Java programs on heterogeneous (OpenCL-compatible)  hardware. 

## How to start? 

The [INSTALL](https://github.com/beehive-lab/tornado/blob/master/INSTALL.md) page contains instructions on how to install Tornado while the [Examples](https://github.com/beehive-lab/tornado/blob/master/assembly/src/docs/2_EXAMPLES.md) page includes   examples regarding running Java programs on GPUs. 
We also maintain a live Tornado whitepaper document which you can download [here]().


## Publications

### 2018 

* James Clarkson, Juan Fumero, Michalis Papadimitriou, Foivos S. Zakkak, Maria Xekalaki, Christos Kotselidis, Mikel Lujan (The University of Manchester). **Exploiting High-Performance Heterogeneous Hardware for Java Programs using Graal**. *To appear in ManLang 2018.* [preprint](https://www.researchgate.net/publication/327097904_Exploiting_High-Performance_Heterogeneous_Hardware_for_Java_Programs_using_Graal)
* James Clarkson, Juan Fumero, Michalis Papadimitriou, Maria Xekalaki, Christos Kotselidis. **Towards Practical Heterogeneous Virtual Machines**. *MoreVMs 2018.* [link](https://dl.acm.org/citation.cfm?id=3191730)

### 2017 

* C. Kotselidis, J. Clarkson, A. Rodchenko, A. Nisbet, J. Mawer, and M. Luján. **Heterogeneous Managed Runtime Systems: A Computer Vision Case Study.** In Proceedings of the 13th ACM SIGPLAN/SIGOPS International Conference on Virtual Execution Environments, VEE ’17, [link](https://dl.acm.org/citation.cfm?doid=3050748.3050764)
* James Clarkson, Christos Kotselidis, Gavin Brown, and Mikel Luján. **Boosting Java Performance Using GPGPUs.** In Architecture of Computing Systems ARCS 2017. [link](https://arxiv.org/abs/1508.06791)

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

This work is partially supported by the EPSRC grants [PAMELA EP/K008730/1](http://apt.cs.manchester.ac.uk/projects/PAMELA/) and [AnyScale Apps EP/L000725/1](http://anyscale.org), and the [EU Horizon 2020 E2Data 780245](https://e2data.eu).

## Collaborations

We welcome collaborations! Please see how to contribute in the [CONTRIBUTIONS](https://github.com/beehive-lab/tornado/blob/master/CONTRIBUTIONS.md).


## Authors 

This work was originated during the PhD thesis of James Clarkson under the joint supervision of [Mikel Luján](http://apt.cs.manchester.ac.uk/people/mlujan/) and [Christos Kotselidis](https://www.kotselidis.net). 
Currently, this project is maintained and updated by the following authors:

* [Juan Fumero](https://jjfumero.github.io/)
* [Michail Papadimitriou](https://mikepapadim.github.io)
* [Maria Xekalaki](https://github.com/mairooni)
* [Christos Kotselidis](https://www.kotselidis.net)

## License

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

