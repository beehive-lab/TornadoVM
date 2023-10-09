Developer Guidelines
====================

This guide shows the configuration for the code formatting and code check styles for TornadoVM and IntelliJ.


IntelliJ Configurations
-----------------------


1. Enable Eclipse Code Formatter for IntelliJ
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

   - Install the Eclipse Code Formatter plugin from the JetBrains plugin repository:

     `Eclipse Code Formatter Plugin <https://plugins.jetbrains.com/plugin/6546-eclipse-code-formatter>`_

   - After installation, navigate to **File > Settings > Adapter for Eclipse Code Formatter**.
   - Select the option **“Use the Eclipse code formatter”**.
   - Load the TornadoVM code formatter from this path ``scripts/templates/eclipse-settings/Tornadovm_eclipse_formatter.xml`` using the selector **Eclipse Formatter Config** > **Eclipse workspace/project folder**.
   - Click **Apply** to save the settings.

2. Enable IntelliJ Code Formatter
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

   - Go to Menu Settings → Editor → Code Style.
   - Import the code style scheme by following these steps:
     - Click on the cog icon in the top right corner of the Code Style settings ("Schema" field).
     - Choose "Import Schema"
     - Import the file located at: ``scripts/templates/intellij-settings/Tornadovm_intellij_formatter.xml``
     - Click **Apply**.

3. Checkstyle-IDEA Plugin
~~~~~~~~~~~~~~~~~~~~~~~~~
This plugin provides both real-time and on-demand scanning of Java files with Checkstyle from within IDEA.

   - Install the Checkstyle-IDEA plugin by going to **File > Settings** (Windows/Linux) or **IntelliJ IDEA > Preferences…** (macOS).
   - Select **Plugins**, press **Browse Repository**, and find the plugin `CheckStyle-IDEA <https://plugins.jetbrains.com/plugin/1065-checkstyle-idea>`_
   - Restart the IDE to complete the installation.
   - Click **File > Settings > Tools > Checkstyle**.
   - Set the **Scan Scope** to "Only Java sources (including tests)" to run Checkstyle for test source codes as well.
   - Click the plus sign under **Configuration File**.
   - Enter a description (e.g., "TornadoVM Checkstyle").
   - Select **Use a local Checkstyle file**.
   - Use the Checkstyle configuration file found at ``tornado-assembly/src/etc/checkstyle.xml``.
   - Click **Next > Finish**.
   - Mark the newly imported check configuration as **Active** and click **Apply**.

4. EditorConfig
~~~~~~~~~~~~~~~
   We use JetBrains' EditorConfig. This allows us to import and export code style settings easily.

- Copy the EditorConfig file to the root of your project:

.. code:: bash

  cd $TORNADO_ROOT
  cp scripts/templates/intellij-settings/.editorconfig .


- In IntelliJ IDEA, navigate to Menu Settings → Editor → Code Style.
- At the bottom of the settings window, check the box "Use EditorConfig".

1. Save Actions
~~~~~~~~~~~~~~~

Install the **Save Actions** Plugin. This allows you  to define post-save actions, including code formatting.

- To enable the auto-formatter with save-actions, follow these steps:
  - Go to **Settings > Other Settings > Save Actions**.
  - Mark the following options: Activate save actions on save, Activate save actions in shortcut and Reformat file.



Pre-commit hooks
----------------


Install pre-commit hooks
~~~~~~~~~~~~~~~~~~~~~~~~

Pre-commit docs: `<https://pre-commit.com/>_`


.. code:: bash

  pip install pre-commit
  pre-commit install


Every time there is a commit in the TornadoVM repo, the pre-commit will pass some checks (including code check style and code formatter).
If all checks are correct, then the commit will be done.

To guarantee the commit, pass the check style before:

.. code:: bash

  make checkstyle     ### If there are errors regarding the code formatting, fix it at this stage.
