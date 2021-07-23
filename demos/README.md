# ExoPlayer demos #

This directory contains applications that demonstrate how to use ExoPlayer.
Browse the individual demos and their READMEs to learn more.

## Running a demo ##

### From Android Studio ###

* File -> New -> Import Project -> Specify the root ExoPlayer folder.
* Choose the demo from the run configuration dropdown list.
* Click Run.

### Using gradle from the command line: ###

* Open a Terminal window at the root ExoPlayer folder.
* Run `./gradlew projects` to show all projects. Demo projects start with `demo`.
* Run `./gradlew :<demo name>:tasks` to view the list of available tasks for
the demo project. Choose an install option from the `Install tasks` section.
* Run `./gradlew :<demo name>:<install task>`.

**Example**:

`./gradlew :demo:installNoExtensionsDebug` installs the main ExoPlayer demo app
 in debug mode with no extensions.
