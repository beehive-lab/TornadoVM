# TornadoVM Maven Repositories

To include the latest version in your project: 

```xml
   <repositories>
     <repository>
       <id>universityOfManchester-graal</id>
       <url>https://raw.githubusercontent.com/beehive-lab/tornado/maven-tornadovm</url>
     </repository>
   </repositories>
  
   <dependencies>   
      <dependency>
         <groupId>tornado</groupId>
         <artifactId>tornado-api</artifactId>
         <version>0.13</version>
      </dependency>

      <dependency>
         <groupId>tornado</groupId>
         <artifactId>tornado-matrices</artifactId>
         <version>0.13</version>
      </dependency>

   </dependencies>
```

## Versions available

* 0.13
* 0.12
* 0.11
* 0.10
* 0.9
* 0.8
* 0.7
* 0.6
* 0.5
* 0.4
* 0.3 
* 0.2   
* 0.1.0 


## License

To use TornadoVM, you can link the TornadoVM API to your application which is under the CLASSPATH Exception of GPLv2.0.

Each TornadoVM module is licensed as follows:

|  Module | License  |
|---|---|
| Tornado-API       |  [![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://github.com/beehive-lab/TornadoVM/blob/master/LICENSE_GPLv2CEl) + CLASSPATH Exception |
| Tornado-Runtime   |  [![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)   |
| Tornado-Assembly  |  [![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)  |
| Tornado-Drivers   |  [![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)   |
| Tornado-Drivers-OpenCL-Headers |  [![License: MIT](https://img.shields.io/badge/License-MIT%20-orange.svg)](https://github.com/KhronosGroup/OpenCL-Headers/blob/master/LICENSE) |
| Tornado-scripts   |  [![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)   |
| Tornado-Annotation|  [![License](https://img.shields.io/badge/License-Apache%202.0-red.svg)](hhttps://github.com/beehive-lab/TornadoVM/blob/master/LICENSE_APACHE2) |
| Tornado-Unittests |  [![License](https://img.shields.io/badge/License-Apache%202.0-red.svg)](https://github.com/beehive-lab/TornadoVM/blob/master/LICENSE_APACHE2)  |
| Tornado-Benchmarks|  [![License](https://img.shields.io/badge/License-Apache%202.0-red.svg)](https://github.com/beehive-lab/TornadoVM/blob/master/LICENSE_APACHE2)  |
| Tornado-Examples  |  [![License](https://img.shields.io/badge/License-Apache%202.0-red.svg)](https://github.com/beehive-lab/TornadoVM/blob/master/LICENSE_APACHE2)   |
| Tornado-Matrices  |  [![License](https://img.shields.io/badge/License-Apache%202.0-red.svg)](https://github.com/beehive-lab/TornadoVM/blob/master/LICENSE_APACHE2)  |
| JNI Libraries (OpenCL, PTX and LevelZero)  |  [![License](https://img.shields.io/badge/License-MIT%20-orange.svg)](https://mit-license.org/)  |

