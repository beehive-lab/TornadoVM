# How to contribute

We welcome contributions!
Please follow the instructions below for your Pull Requests (PRs).

## How to submit your changes

1. **Fork** the repository in GitHub.
2. **Clone** the project.
3. **Create a new branch from the `develop` branch**: 
    ```bash
    $ git checkout -b fix/my/branch 
    ```
4. **Commit your changes**:
    ```bash
    $ git add <yourFiles>
    $ git commit -a -m "My new feature/fix"
    ```
5. **Push** your work to your repository:
    ```bash
    $ git push -u myRepo feat/my/branch
    ```
6. Create a **Pull Request** (PR) to the `develop` branch.
7. When you open PR, there are a few GitHub actions. One of them is the checker for the **Contributor License Agreement**, [CLA](https://cla-assistant.io/beehive-lab/TornadoVM), if you haven't signed before, you will be prompted with the link to sign the CLA. Use the same email as you commit email. 

Please, ensure that your changes are merged with the latest changes in the `develop` branch, and the code follows the code conventions (see below).

### What's next? 

We check the PR and test it internally. Be aware we are a very small team. Thus, depending on the PR, it might take some time for us to review it since we check the PR with our regression tests and benchmarks for all backends (OCL/SPIR-V/PTX platforms) as well as with different drivers and Operating Systems. 

## Code of Conduct 

For the PR process as well as any issues and discussions we follow this [CODE_OF_CONDUCT](https://github.com/beehive-lab/TornadoVM/blob/master/CODE_OF_CONDUCT.md).


## How is the review process?

1. We have a few GitHub actions, such as code formatter, documentation rendering and checks for the CLA (Contributor License Agreement).
2. As mentioned earlier, if you haven't signed the CLA yet, you will be redirected to the TornadoVM CLA webpage, where you can read and review it.
If you agree with the terms, then you will sign it.
3. After that, the TornadoVM team can process your PR to be able to merge it into the TornadoVM's codebase.
4. At least two researchers/engineers from the TornadoVM team will review your PR.
**Expect a few comments, questions and possible changes.**
This is a totally normal process, and it tries not to introduce untested code for specific devices, better documentation, etc.
We are proud to say that TornadoVM is 10+ years of active development, with many different researchers and developers. Thus, we prioritize code maintainability and reproducibility, and we will work together to guarantee this as much as possible.


## Coding Conventions

We use the auto-formatter for **Eclipse** and **IntelliJ**. You can use any IDE you like, but keep in mind the whole TornadoVM team uses IntelliJ.  Please, ensure that your code follows the formatter rules before the pull request. The auto-formatter is set automatically by running the following script:


### Using Eclipse:
```bash
## For Eclipse, use the following script
$ python3 scripts/eclipseSetup.py
```

### Using IntelliJ:

For IntelliJ, import the XML auto-formatter. Steps [here](https://tornadovm.readthedocs.io/en/latest/installation.html#ide-code-formatter). Additionally, be sure that
IntelliJ uses a single class import:

* Settings -> General -> Code Editing -> Formatting -> Imports optimization
* Settings -> Code Style -> Java -> "Use single class import"
* Settings -> Other Settings -> Save Actions -> Formatting Actions -> Optimize Imports
* Settings -> Other Settings -> Save Actions -> Formatting Actions -> Reformat File

Note that the formatter config file is stored in the following
path: `<tornadovm>/scripts/templates/eclipse-settings/Tornado.xml`.

### Setting up IntelliJ Run Configurations

After building TornadoVM, you can generate IntelliJ run configurations for building and testing:

```bash
# Build TornadoVM first
make

# Load the environment variables
source setvars.sh

# Generate IntelliJ project files
make intellijinit
```

This creates run configurations in the `.build/` directory that IntelliJ will automatically detect. You can then use **Run > Edit Configurations** to see the available configurations for building and running tests.

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
* Michail Papadimitriou <michail (dot) papadimitriou (at) manchester (dot) ac (dot) uk >
