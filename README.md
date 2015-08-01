bounds-maven-plugin
===================

A maven plugin to update the lower bounds of ranges to reduce metadata downloads

## Example delta

When you run bounds:update for a project that contains this

      <plugin>
       <groupId>net.stickycode.composite</groupId>
       <artifactId>sticky-composite-logging-api</artifactId>
       <version>[2.3,3)</version>
      </plugin>
      
and the latest release of sticky-composite-logging-api is 2.4, then you will end up with

     <plugin>
       <groupId>net.stickycode.composite</groupId>
       <artifactId>sticky-composite-logging-api</artifactId>
       <version>[2.4,3)</version>
      </plugin>
      
## Usage

The plugin is in maven central so it should 'Just Work'.

Run the plugin from your Apache Maven project directory:

    mvn net.stickycode.plugins:bounds-maven-plugin:2.2:update

And your version ranges will have there lower bound updated to the latest released
artifact version.

If you want to include any SNAPSHOT references when calculating the lower bound, set the
`includeSnapshots` property:

    -DincludeSnapshots

when calling `mvn`.

### Update bounds during release

To update the bounds during release you can do this

    <pluginManagement>
     <plugins>

      <plugin>
       <groupId>net.stickycode.plugins</groupId>
       <artifactId>bounds-maven-plugin</artifactId>
       <version>2.6</version>
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
       <version>2.6</version>
       <configuration>
        <lineSeparator>Unix</lineSeparator>
       </configuration>
      </plugin>


## Releases

### Release 2.6

* added support for dependencyManagement - although I would suggest you never ever us it
* added support for version defined as properties - although again I would suggest you don't do that
* allow the line separator on rewrite to be configured (Mac, Unix Windows), useful when you define the line ending in your SCM and need re-generated poms to match

