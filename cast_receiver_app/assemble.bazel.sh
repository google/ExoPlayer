#!/bin/bash
# Copyright (C) 2019 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

##
# Assembles the html, css and javascript files which have been created by the
# bazel build in a destination directory.

HTML_DIR=app/html
HTML_DEBUG_DIR=app-desktop/html
BIN=bazel-bin

function usage {
  echo "usage: `basename "$0"` -d=DESTINATION_DIR"
}

for i in "$@"
do
case $i in
    -d=*|--destination=*)
    DESTINATION="${i#*=}"
    shift # past argument=value
    ;;
    -h|--help)
    usage
    exit 0
    ;;
    *)
    # unknown option
    ;;
esac
done

if [ ! -d "$DESTINATION" ]; then
  echo "destination directory '$DESTINATION' is not declared or is not a\
 directory"
  usage
  exit 1
fi

if [ ! -f "$BIN/app.js" ];then
  echo "file $BIN/app.js not found. Did you build already with bazel?"
  echo "-> # bazel build .. --incompatible_package_name_is_a_function=false"
  exit 1
fi

if [ ! -f "$BIN/app_desktop.js" ];then
  echo "file $BIN/app_desktop.js not found. Did you build already with bazel?"
  echo "-> # bazel build .. --incompatible_package_name_is_a_function=false"
  exit 1
fi

echo "assembling receiver and desktop app in $DESTINATION"
echo "-------"

# cleaning up asset files in destination directory
FILES=(
  app.js
  app_desktop.js
  app_styles.css
  app_desktop_styles.css
  index.html
  player.html
)
for file in ${FILES[@]}; do
  if [ -f $DESTINATION/$file ]; then
    echo "deleting $file"
    rm -f $DESTINATION/$file
  fi
done
echo "-------"

echo "copy html files to $DESTINATION"
cp $HTML_DIR/index.html $DESTINATION
cp $HTML_DEBUG_DIR/index.html $DESTINATION/player.html
echo "copy javascript files to $DESTINATION"
cp $BIN/app.js $BIN/app_desktop.js $DESTINATION
echo "copy css style to $DESTINATION"
cp $BIN/app_styles.css $BIN/app_desktop_styles.css $DESTINATION
echo "-------"

echo "done."
