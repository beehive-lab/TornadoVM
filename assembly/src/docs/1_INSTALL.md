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



3. Check 

```bash
$ tornado
Usage: java [-options] class [args...]
           (to execute a class)
   or  java [-options] -jar jarfile [args...]
           (to execute a jar file)
where options include:
    -d32	  use a 32-bit data model if available
    -d64	  use a 64-bit data model if available
    -server	  to select the "server" VM
    -original	  to select the "original" VM
                  The default VM is server,
                  because you are running on a server-class machine.


    -cp <class search path of directories and zip/jar files>

...
```


