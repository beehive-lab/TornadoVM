Configuration for Code Formatting and Checkstyle
================================================
This guide will walk you through the configuration of both code formatters and Checkstyle within IntelliJ IDEA.

Multi-Language Global Settings
------------------------------

   To maintain consistent code style standards across multiple programming languages, we recommend using JetBrains' EditorConfig support. This allows you to import and export code style settings easily.

   - For detailed instructions, refer to the JetBrains IntelliJ IDEA documentation:

     `JetBrains IntelliJ IDEA EditorConfig Support Documentation <https://www.jetbrains.com/help/idea/editorconfig.html>`_

Intellij Configurations
-----------------------

When working with Java code, follow these guidelines for code formatting and style.
IntelliJ Formatter offers deep customization and integration within IntelliJ IDEA, while EditorConfig provides a standardized, cross-platform solution for code style settings

Eclipse Code Formatter
~~~~~~~~~~~~~~~~~~~~~~~~~

   - Install the Eclipse Code Formatter plugin from the JetBrains plugin repository:

     `Eclipse Code Formatter Plugin <https://plugins.jetbrains.com/plugin/6546-eclipse-code-formatter>`_

   - After installation, navigate to **File > Settings > Other Settings > Eclipse Code Formatter**.
   - Select the option **“Use the Eclipse code formatter”**.
   - Load the TornadoVM code formatter configuration file by specifying its location: ``./scripts/templates/eclipse-settings/Tornado.xml``.
   - Click **Apply** to save the settings.

Enable IntelliJ Formatter
~~~~~~~~~~~~~~~~~~~~~

   - Go to Menu Settings → Editor → Code Style.
   - Select the appropriate code style scheme.
   - Import the code style scheme by following these steps:
     - Click on the gear icon in the top right corner of the Code Style settings.
     - Choose "Scheme Import."
     - Import the file located at: ``scripts/templates/intellij-settings/Tornadovm_intellij_formatter.xml``

Checkstyle-IDEA Plugin
~~~~~~~~~~~~~~~~~~~~~~~~~

   - Install the Checkstyle-IDEA plugin by going to **File > Settings** (Windows/Linux) or **IntelliJ IDEA > Preferences…** (macOS).
   - Select **Plugins**, press **Browse Repository**, and find the plugin.
   - Restart the IDE to complete the installation.
   - Click **File > Settings… > Other Settings > Checkstyle**.
   - Set the **Scan Scope** to "Only Java sources (including tests)" to run Checkstyle for test source codes as well.
   - Ensure that the Checkstyle version is set to **8.1**, the same version used inside Gradle to avoid version incompatibility issues.
   - Click the plus sign under **Configuration File**.
   - Enter a description (e.g., "addressbook").
   - Select **Use a local Checkstyle file**.
   - Use the Checkstyle configuration file found at ``tornado-assembly/src/etc/checkstyle.xml``.
   - Click **Next > Finish**.
   - Mark the newly imported check configuration as **Active**.

EditorConfig
~~~~~~~~~~~~~~~

   - Copy the EditorConfig file to the root of your project:

     .. code-block:: bash

        cp scripts/templates/intellij-settings/.editorconfig $TORNADO_ROOT

   - In IntelliJ IDEA, navigate to Menu Settings → Editor → Code Style.
   - At the bottom of the settings window, check the box that says "Use EditorConfig."

Save Actions
-------------

   The Save Actions plugin allows you to define post-save actions, including code formatting on save.

   - To enable the auto-formatter with save-actions, follow these steps:
     - Go to **Settings -> Other Settings -> Save Actions**.
     - Mark the following options: Activate save actions on save, Activate save actions in shortcut and Reformat file

By following these guidelines, you can ensure consistent code formatting and style for Java code in your project and use additional tools like Eclipse Code Formatter, Checkstyle-IDEA, and Save Actions to streamline your development process.
