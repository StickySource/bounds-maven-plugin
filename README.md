bounds-maven-plugin
===================

A maven plugin to manipulate dependency ranges
* update the lower bounds of ranges to reduce metadata downloads
* upgrade ranges to the highest versions
* figure out the highest version for an artifact in a range
* figure out the next release version for this project

## Updating the lower bounds

The plugin is in maven central so it should 'Just Work'.

Run the plugin from your Apache Maven project directory:

    mvn net.stickycode.plugins:bounds-maven-plugin:4.7:update

And your version ranges will have there lower bound updated to the latest released
artifact version.

If you want to include any SNAPSHOT references when calculating the lower bound, set the
`includeSnapshots` property:

    -DincludeSnapshots

when calling `mvn`.

### Example of the change

When you run bounds:update for a project that contains this

      <dependency>
       <groupId>net.stickycode.composite</groupId>
       <artifactId>sticky-composite-logging-api</artifactId>
       <version>[2.3,3)</version>
      </dependency>

and the latest release of sticky-composite-logging-api is 2.4, then you will end up with

     <dependency>
       <groupId>net.stickycode.composite</groupId>
       <artifactId>sticky-composite-logging-api</artifactId>
       <version>[2.4,3)</version>
     </dependency>

### Update bounds during release

To update the bounds during release you can do this

    <pluginManagement>
     <plugins>

      <plugin>
       <groupId>net.stickycode.plugins</groupId>
       <artifactId>bounds-maven-plugin</artifactId>
       <version>4.7</version>
      </plugin>
      <plugin>
       <groupId>org.apache.maven.plugins</groupId>
       <artifactId>maven-release-plugin</artifactId>
       <version>2.2.2</version>
       <configuration>
         <preparationGoals>bounds:update enforcer:enforce clean verify</preparationGoals>
       </configuration>
      </plugin>
     </plugins>
    </pluginManagement>

### Line endings

You can specify the line separator used like so

      <plugin>
       <groupId>net.stickycode.plugins</groupId>
       <artifactId>bounds-maven-plugin</artifactId>
       <version>4.7</version>
       <configuration>
        <lineSeparator>Unix</lineSeparator>
       </configuration>
      </plugin>

## Extract Current Version

To get the current version of a library from a range use bounds:current-version, this will set the property *sticky-coercion.version* to the latest 2.x version

    <plugins>
      <plugin>
        <groupId>net.stickycode.plugins</groupId>
        <artifactId>bounds-maven-plugin</artifactId>
        <version>4.7</version>
        <executions>
          <execution>
            <goals>
              <goal>current-version</goal>
            </goals>
            <configuration>
              <artifacts>
                <artifact>net.stickycode:sticky-coercion:[2,3]</artifact>
              </artifacts>
            </configuration>
          </execution>
        </execution>
      </plugin>
    </plugins>


If you want to specify a special property name you can use the coordinates syntax, this will set the *some-other-name.version* property to the latest 2.x version

    <plugins>
      <plugin>
        <groupId>net.stickycode.plugins</groupId>
        <artifactId>bounds-maven-plugin</artifactId>
        <version>4.7</version>
        <executions>
          <execution>
            <goals>
              <goal>current-version</goal>
            </goals>
            <configuration>
              <coordinates>
                <some-other-name.version>net.stickycode:sticky-coercion:[2,3]</some-other-name.version>
              </coordinates>
            </configuration>
          </execution>
        </execution>
      </plugin>
    </plugins>

## Upgrade version ranges

It can be useful to upgrade all the ranges to the highest valid range, so running

`mvn bounds:upgrade`

will turn

    <project>
      <version>1.6-SNAPSHOT</version>
      <dependencies>
        <dependency>
          <groupId>net.stickycode.composite</groupId>
          <artifactId>sticky-composite-logging-api</artifactId>
          <version>[2.4,3)</version>
        </dependency>
      </dependencies>
    </project>

into

    <project>
      <version>2.1-SNAPSHOT</version>
      <dependencies>
        <dependency>
          <groupId>net.stickycode.composite</groupId>
          <artifactId>sticky-composite-logging-api</artifactId>
          <version>[4.7,5)</version>
        </dependency>
      </dependencies>
    </project>

This also supports fixed ranges so [1.5.6] can be upgraded to [1.7.2] for example

## Next version

Use the bounds:next-version mojo to derive the next likely version of this project from the already released artifacts

Caveats
* its scoped to the repositories you have configured
* it uses metadata so if that is cached it may not pull the latest version

This configuration

    <project>
      <groupId>com.example</groupId>
      <artifactId>example-artifact</artifactId>
      <version>1.6-SNAPSHOT</version>
      <plugins>
        <plugin>
          <groupId>net.stickycode.plugins</groupId>
          <artifactId>bounds-maven-plugin</artifactId>
          <version>4.7</version>
          <executions>
            <execution>
              <goals>
                <goal>next-version</goal>
              </goals>
              <configuration>
                <updateProjectVersion>true</updateProjectVersion>
                <nextVersionProperty>some-property.version</nextVersionProperty>
              </configuration>
            </execution>
          </execution>
        </plugin>
      </plugins>
    </projects>

will set **some-property.version** to the highest available version for the range com.example:example-artifact:[1,2), it will also set the *version* property of the project.

### To not set the project version

updateProjectVersion defaults to true to you need to set it to false to avoid setting the project version

### nextVersionProperty

Is optional so only set it if you wish to use the version outside of the context of the project.version

## Releases

### Release 4.8

* Improve bounds:upgrade to also upgrade fixed ranges, this means ranges like [1.5.7] will be upgraded to the latest version

### Release 4.7

* Improve bounds:current-version to set the 'artifact.versionRange' property to the range used to resolve 'artifact.version' property

### Release 4.6

* Bugfix current-version Reinstate **coordinates** for bounds:current-version its actually useful and breaking things is not nice for users

### Release 4.1

* Improve bounds:curent-version to use an artifact list and default the property to *artifactId.version*

### Release 3.6

* Improve bounds:upgrade Add a flag to ignore minor changes as causing a version bump, defaults to true

### Release 3.5

* Improve bounds:upgrade to bump the major version of the project when any of the dependencies are bumped

### Release 3.4

* Add bounds:upgrade to upgrade the version ranges to the highest valid range

### Release 3.3

* dependencies with classifiers were being ignored incorrectly

### Release 3.2

* support for setting a property to the highest version in a range

### Release 2.6

* added support for dependencyManagement - although I would suggest you never ever us it
* added support for version defined as properties - although again I would suggest you don't do that
* allow the line separator on rewrite to be configured (Mac, Unix Windows), useful when you define the line ending in your SCM and need re-generated poms to match

