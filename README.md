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

== Update bounds during release

To update the bounds during release you can do this

    <pluginManagement>
     <plugins>

      <plugin>
       <groupId>net.stickycode.plugins</groupId>
       <artifactId>sticky-bounds-plugin</artifactId>
       <version>2.3</version>
      </plugin>
      <plugin>
       <groupId>org.apache.maven.plugins</groupId>
       <artifactId>maven-release-plugin</artifactId>
       <version>2.2.2</version>
       <configuration>
         <preparationGoals>sticky-bounds:update enforcer:enforce clean verify</preparationGoals>
       </configuration>
      </plugin>
     </plugins>
    </pluginManagement>

