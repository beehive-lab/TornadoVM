# How to contribute

We welcome contributions!
Please follow the instructions below for your Pull Requests.

## How to submit your changes

1. **Fork** the repo in Github
2. **Clone** the project
3. **Create a new branch from the `develop` branch**
4. **Commit your changes**
5. **Push** your work to your repository
6. Create a **Pull Request** to the `develop` branch.

Please, ensure that your changes are merged with the latest changes in the `develop` branch, and the code follows the
code conventions (see below).

Be aware we are a very small team. So, depending on the PR, it might take some time for us to review it since we check
on many OCL/PTX platforms and different drivers.

For the PR process as well as any issues and discussions we follow
this [CODE_OF_CONDUCT](https://github.com/beehive-lab/TornadoVM/blob/master/CODE_OF_CONDUCT.md).

## Coding conventions

We use the auto-formatter in **Eclipse** and **IntelliJ**. Please, ensure that your code follows the formatter rules
before the pull request. The auto-formatter is set automatically by running the following script:

```bash
## For Eclipse, use the following script
$ python3 scripts/eclipseSetup.py
``` 

For IntelliJ, import the XML auto-formatter. Steps [here](assembly/src/docs/3_INTELLIJ.md). Additionally, be sure that
IntelliJ uses a single class import:

* Settings -> General -> Code Editing -> Formatting -> Imports optimization
* Settings -> Code Style -> Java -> "Use single class import"
* Settings -> Other Settings -> Save Actions -> Formatting Actions -> Optimize Imports
* Settings -> Other Settings -> Save Actions -> Formatting Actions -> Reformat File

Note that the formatter config file is stored in the following
path: `<tornadovm>/scripts/templates/eclipse-settings/Tornado.xml`.

## Looking for tasks to contribute?

Help us to develop TornadoVM or TornadoVM use cases:

* Look at Github issues tagged
  with [good first issue](https://github.com/beehive-lab/TornadoVM/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22)
  .
* **Documentation**: you can help to improve install documentation, testing platforms, and scripts to easy deploy
  TornadoVM.
* **TornadoVM use-cases**: Develop use cases that use TornadoVM for acceleration: block-chain, graphical version of
  NBody, filters for photography, etc.
* **TornadoVM Development / Improvements**: If you would like to contribute to the TornadoVM internals, here is a list
  of pending tasks/improvements:

    - Help us to solve bugs. Check out the list of public issues and feel free to work on any of those.
    - Implement a performance plot suite when running the benchmark runner. This should plot speedups against serial
      Java as well as stacked bars with breakdown analysis (e.g. time spent on compilation, execution, and data
      transfers).

For any other contribution(s), feel free to open a proposal by either sharing a *Google Docs*, or opening a proposal in
our discussions [page](https://github.com/beehive-lab/TornadoVM/discussions/categories/ideas-proposals). Alternatively,
you can send us your proposal via email to the following contacts:

Main contacts:

* Christos Kotselidis <christos (dot) kotselidis (at) manchester (dot) ac (dot) uk >
* Athanasios Stratikopoulos <athanasios (dot) stratikopoulos (at) manchester (dot) ac (dot) uk >
* Juan Fumero <juan (dot) fumero (at) manchester (dot) ac (dot) uk > 

