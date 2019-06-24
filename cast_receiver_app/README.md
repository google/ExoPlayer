# ExoPlayer cast receiver #

An HTML/JavaScript app which runs within a Google cast device and can be loaded
and controller by an Android app which uses the ExoPlayer cast extension
(https://github.com/google/ExoPlayer/tree/release-v2/extensions/cast).

# Build the app #

You can build and deploy the app to your web server and register the url as your
cast receiver app (see: https://developers.google.com/cast/docs/registration).

Building the app compiles JavaScript and CSS files. Dead JavaScript code of the
app itself and their dependencies (like ShakaPlayer) is removed and the
remaining code is minimized.

## Prerequisites ##

1. Install the most recent bazel release (https://bazel.build/) which is at
   least 0.22.0.

From within the root of the exo_receiver_app project do the following steps:

2. Clone shaka from GitHub into the directory external-js/shaka-player:
```
# git clone https://github.com/google/shaka-player.git \
       external-js/shaka-player
```

## 1. Customize html page and css (optional) ##

(Optional) Edit index.html. **Make sure you do not change the id of the video
element**.
(Optional) Customize main.css.

## 2. Build javascript and css files ##
```
# bazel build ...
```
## 3. Assemble the receiver app ##
```
# WEB_DEPLOY_DIR=www
# mkdir ${WEB_DEPLOY_DIR}
# cp bazel-bin/exo_receiver_app.js  ${WEB_DEPLOY_DIR}
# cp bazel-bin/exo_receiver_styles_bin.css ${WEB_DEPLOY_DIR}
# cp html/index.html ${WEB_DEPLOY_DIR}
```

Deploy the content of ${WEB_DEPLOY_DIR} to your web server.

## 4. Assemble the debug app (optional) ##

Debugging the player in a cast device is a little bit cumbersome compared to
debugging in a desktop browser. For this reason there is a debug app which
contains the player parts which are not depending on the cast library in a
traditional HTML app which can be run in a desktop browser.

```
# WEB_DEPLOY_DIR=www
# mkdir ${WEB_DEPLOY_DIR}
# cp bazel-bin/debug_app.js ${WEB_DEPLOY_DIR}
# cp bazel-bin/debug_styles_bin.css ${WEB_DEPLOY_DIR}
# cp html/player.html ${WEB_DEPLOY_DIR}
```

Deploy the content of ${WEB_DEPLOY_DIR} to your web server.

# Unit test

Unit tests can be run by the command
```
# bazel test ...
```
