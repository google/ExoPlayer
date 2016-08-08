# ExoPlayer Cronet Extension #

## Description ##

[Cronet][] is Chromium's Networking stack packaged as a library.

The Cronet Extension is an [HttpDataSource][] implementation using [Cronet][].

[HttpDataSource]: https://google.github.io/ExoPlayer/doc/reference/com/google/android/exoplayer/upstream/HttpDataSource.html
[Cronet]: https://chromium.googlesource.com/chromium/src/+/master/components/cronet?autodive=0%2F%2F

## Build Instructions ##

* Checkout ExoPlayer along with Extensions:

```
git clone https://github.com/google/ExoPlayer.git
```

* Get the Cronet libraries:

1. Go to https://console.cloud.google.com/storage/browser/chromium-cronet/android and choose the latest release

2. Navigate to chromium-cronet/ android/releasenumber/Release/cronet 

3. Get .jar files

4. Download cronet.jar, cronet_api.jar 

5. Directly under the "app" directory of your project, create a "libs" directory. Use a shell command if you like, or use "File|New|Directory" from the menu. But note that you only get "Directory" as an option if you are in "Project" view, not "Android" view. "Project" models the local machine's filesystem, but Android is a virtual layout of files corresponding to the deployed hierarchy.

6. Copy cronet.jar and cronet_api.jar under the libs directory

7. Select both files at once

8. Bring up the context menu and choose "Add as Library"

9. Confirm "OK" at the "Add to module" dialog

10. Get .so files

11. Download .so files, currently located under: https://console.cloud.google.com/storage/browser/chromium-cronet/android/latest-releasexxx/0/Release/cronet/libs/

12. Under "app/src/main" create a directory named "jniLibs"

13. Copy armeabi and ameabi-v7a into jniLibs, which should contain only subdirectories, not directly a '.so' file. If you typically use an emulator, copy x64_64 folder into jniLibs folder as well 

* Uncomment the line 'project(':extension-cronet').projectDir = new File(settingsDir, 'extensions/cronet')' from settings.gradle

