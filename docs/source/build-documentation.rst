.. _build-documentation:

Build the Documentation
====================

To build the documentation, you need to install/configure `sphinx <https://www.sphinx-doc.org/en/master/usage/installation.html>`_:

.. code:: bash

  pip3 install -U sphinx


And all the required module dependencies, for example: 

.. code:: bash

  pip3 install sphinx_rtd_theme
  pip3 install sphinxcontrib-jquery


Then run:

.. code:: bash

  make docs
  ## visualize the documentation (E.g., with Firefox)
  firefox docs/build/html/index.html 

