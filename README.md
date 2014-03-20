bounds-maven-plugin
===================

A maven plugin to update the lower bounds of ranges to reduce metadata downloads

== Usage

Run the plugin from your Apache Maven project directory:

    mvn net.stickycode.plugins:bounds-maven-plugin:2.2:update

And your verion ranges will have there lower bound updated to the latest released
artifact version.

If you want to include any SNAPSHOT references when calculating the lower bound, set the
`includeSnapshots` property:

    -DincludeSnapshots

when calling `mvn`.
