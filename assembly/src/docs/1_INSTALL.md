# Installation Process of Tornado SDK


### Prerequisites

* [Java 8 with JVM Support](https://www.dropbox.com/s/nvtpsviqc6u8vnv/jdk1.8.0_131_x86.tgz?dl=0)
* OpenCL >= 1.2


### Configuration


Obtain the Tornado SDK.


```bash
$ cd workdir
$ tar xvzf tornado-sdk.tar.gz
$ cd tornado-sdk-0.0.2-SNAPSHOT-*
```

We need to set 3 variables. 

1. Set `JAVA_HOME` to JDK


```bash
export JAVA_HOME=path/to/jdk1.8.0_131
```


2. Set a new `PATH` and `TORNADO_SDK`

```bash
export PATH=<workdir>/tornado-sdk-0.0.2-SNAPSHOT-<ID>/bin/:$PATH
export TORNADO_SDK=<workdir>/tornado-sdk-0.0.2-SNAPSHOT-<ID>
```

Note, the ID is the git-version in Tornado. 


and done!




