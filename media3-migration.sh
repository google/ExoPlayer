#!/bin/bash
# Copyright (C) 2022 The Android Open Source Project
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
shopt -s extglob

PACKAGE_MAPPINGS='com.google.android.exoplayer2 com.google.android.exoplayer2
com.google.android.exoplayer2.analytics com.google.android.exoplayer2.analytics
com.google.android.exoplayer2.audio com.google.android.exoplayer2.audio
com.google.android.exoplayer2.castdemo com.google.android.exoplayer2.castdemo
com.google.android.exoplayer2.database com.google.android.exoplayer2.database
com.google.android.exoplayer2.decoder com.google.android.exoplayer2.decoder
com.google.android.exoplayer2.demo com.google.android.exoplayer2.demo
com.google.android.exoplayer2.drm com.google.android.exoplayer2.drm
com.google.android.exoplayer2.effect com.google.android.exoplayer2.effect
com.google.android.exoplayer2.ext.av1 com.google.android.exoplayer2.ext.av1
com.google.android.exoplayer2.ext.cast com.google.android.exoplayer2.ext.cast
com.google.android.exoplayer2.ext.cronet com.google.android.exoplayer2.ext.cronet
com.google.android.exoplayer2.ext.ffmpeg com.google.android.exoplayer2.ext.ffmpeg
com.google.android.exoplayer2.ext.flac com.google.android.exoplayer2.ext.flac
com.google.android.exoplayer2.ext.ima com.google.android.exoplayer2.ext.ima
com.google.android.exoplayer2.ext.leanback com.google.android.exoplayer2.ext.leanback
com.google.android.exoplayer2.ext.okhttp com.google.android.exoplayer2.ext.okhttp
com.google.android.exoplayer2.ext.opus com.google.android.exoplayer2.ext.opus
com.google.android.exoplayer2.ext.rtmp com.google.android.exoplayer2.ext.rtmp
com.google.android.exoplayer2.ext.vp9 com.google.android.exoplayer2.ext.vp9
com.google.android.exoplayer2.ext.workmanager com.google.android.exoplayer2.ext.workmanager
com.google.android.exoplayer2.extractor com.google.android.exoplayer2.extractor
com.google.android.exoplayer2.gldemo com.google.android.exoplayer2.gldemo
com.google.android.exoplayer2.mediacodec com.google.android.exoplayer2.mediacodec
com.google.android.exoplayer2.metadata com.google.android.exoplayer2.metadata
com.google.android.exoplayer2.offline com.google.android.exoplayer2.offline
com.google.android.exoplayer2.playbacktests com.google.android.exoplayer2.playbacktests
com.google.android.exoplayer2.robolectric com.google.android.exoplayer2.robolectric
com.google.android.exoplayer2.scheduler com.google.android.exoplayer2.scheduler
com.google.android.exoplayer2.source com.google.android.exoplayer2.source
com.google.android.exoplayer2.source.dash com.google.android.exoplayer2.source.dash
com.google.android.exoplayer2.source.hls com.google.android.exoplayer2.source.hls
com.google.android.exoplayer2.source.rtsp com.google.android.exoplayer2.source.rtsp
com.google.android.exoplayer2.source.smoothstreaming com.google.android.exoplayer2.source.smoothstreaming
com.google.android.exoplayer2.surfacedemo com.google.android.exoplayer2.surfacedemo
com.google.android.exoplayer2.testdata com.google.android.exoplayer2.testdata
com.google.android.exoplayer2.testutil com.google.android.exoplayer2.testutil
com.google.android.exoplayer2.text com.google.android.exoplayer2.text
com.google.android.exoplayer2.trackselection com.google.android.exoplayer2.trackselection
com.google.android.exoplayer2.transformer com.google.android.exoplayer2.transformer
com.google.android.exoplayer2.transformerdemo com.google.android.exoplayer2.transformerdemo
com.google.android.exoplayer2.ui com.google.android.exoplayer2.ui
com.google.android.exoplayer2.upstream com.google.android.exoplayer2.upstream
com.google.android.exoplayer2.upstream.cache com.google.android.exoplayer2.upstream.cache
com.google.android.exoplayer2.upstream.crypto com.google.android.exoplayer2.upstream.crypto
com.google.android.exoplayer2.util com.google.android.exoplayer2.util
com.google.android.exoplayer2.util com.google.android.exoplayer2.util
com.google.android.exoplayer2.video com.google.android.exoplayer2.video'

CLASS_RENAMINGS='com.google.android.exoplayer2.ui.StyledPlayerView com.google.android.exoplayer2.ui.PlayerView
StyledPlayerView PlayerView
com.google.android.exoplayer2.ui.StyledPlayerControlView com.google.android.exoplayer2.ui.PlayerControlView
StyledPlayerControlView PlayerControlView
com.google.android.exoplayer2.ExoPlayerLibraryInfo androidx.media3.common.MediaLibraryInfo
ExoPlayerLibraryInfo MediaLibraryInfo
com.google.android.exoplayer2.SimpleExoPlayer com.google.android.exoplayer2.ExoPlayer
SimpleExoPlayer ExoPlayer'

CLASS_MAPPINGS='com.google.android.exoplayer2.text.span androidx.media3.common.text HorizontalTextInVerticalContextSpan LanguageFeatureSpan RubySpan SpanUtil TextAnnotation TextEmphasisSpan
com.google.android.exoplayer2.text androidx.media3.common.text CueGroup Cue
com.google.android.exoplayer2.text com.google.android.exoplayer2.text ExoplayerCuesDecoder SubtitleDecoderFactory TextOutput TextRenderer
com.google.android.exoplayer2.upstream.crypto com.google.android.exoplayer2.upstream AesCipherDataSource AesCipherDataSink AesFlushingCipher
com.google.android.exoplayer2.util com.google.android.exoplayer2.util AtomicFile Assertions BitmapLoader BundleableUtil BundleUtil Clock ClosedSource CodecSpecificDataUtil ColorParser ConditionVariable Consumer CopyOnWriteMultiset EGLSurfaceTexture GlProgram GlUtil HandlerWrapper LibraryLoader ListenerSet Log LongArray MediaFormatUtil NetworkTypeObserver NonNullApi NotificationUtil ParsableBitArray ParsableByteArray RepeatModeUtil RunnableFutureTask Size SystemClock SystemHandlerWrapper TimedValueQueue TimestampAdjuster TraceUtil UnknownNull UnstableApi UriUtil Util XmlPullParserUtil
com.google.android.exoplayer2.util androidx.media3.common DebugViewProvider Effect ErrorMessageProvider FlagSet FileTypes FrameInfo GlObjectsProvider GlTextureInfo VideoFrameProcessingException VideoFrameProcessor MimeTypes PriorityTaskManager SurfaceInfo
com.google.android.exoplayer2.metadata androidx.media3.common Metadata
com.google.android.exoplayer2.metadata com.google.android.exoplayer2.metadata MetadataDecoderFactory MetadataOutput MetadataRenderer
com.google.android.exoplayer2.audio androidx.media3.common AudioAttributes AuxEffectInfo
com.google.android.exoplayer2.audio com.google.android.exoplayer2.audio AudioProcessingPipeline AudioProcessor AudioProcessorChain
com.google.android.exoplayer2.ui androidx.media3.common AdOverlayInfo AdViewProvider
com.google.android.exoplayer2.source.ads androidx.media3.common AdPlaybackState
com.google.android.exoplayer2.source androidx.media3.common MediaPeriodId TrackGroup
com.google.android.exoplayer2.offline androidx.media3.common StreamKey
com.google.android.exoplayer2.ui com.google.android.exoplayer2.offline DownloadNotificationHelper
com.google.android.exoplayer2.trackselection androidx.media3.common TrackSelectionParameters TrackSelectionOverride
com.google.android.exoplayer2.video androidx.media3.common ColorInfo VideoSize
com.google.android.exoplayer2.upstream androidx.media3.common DataReader
com.google.android.exoplayer2.upstream com.google.android.exoplayer2.upstream Allocation Allocator BandwidthMeter CachedRegionTracker DefaultAllocator DefaultBandwidthMeter DefaultLoadErrorHandlingPolicy Loader LoaderErrorThrower ParsingLoadable SlidingPercentile TimeToFirstByteEstimator
com.google.android.exoplayer2.audio com.google.android.exoplayer2.extractor AacUtil Ac3Util Ac4Util DtsUtil MpegAudioUtil OpusUtil WavUtil
com.google.android.exoplayer2.util com.google.android.exoplayer2.container NalUnitUtil ParsableNalUnitBitArray
com.google.android.exoplayer2.video com.google.android.exoplayer2.extractor AvcConfig DolbyVisionConfig HevcConfig
com.google.android.exoplayer2.decoder com.google.android.exoplayer2 DecoderCounters DecoderReuseEvaluation
com.google.android.exoplayer2.util com.google.android.exoplayer2 MediaClock StandaloneMediaClock
com.google.android.exoplayer2 com.google.android.exoplayer2 FormatHolder PlayerMessage
com.google.android.exoplayer2 androidx.media3.common BasePlayer BundleListRetriever Bundleable ControlDispatcher C DefaultControlDispatcher DeviceInfo ErrorMessageProvider ExoPlayerLibraryInfo Format ForwardingPlayer HeartRating IllegalSeekPositionException LegacyMediaPlayerWrapper MediaItem MediaMetadata ParserException PercentageRating PlaybackException PlaybackParameters Player PositionInfo Rating SimpleBasePlayer StarRating ThumbRating Timeline Tracks
com.google.android.exoplayer2.drm androidx.media3.common DrmInitData'

DEPENDENCY_MAPPINGS='exoplayer media3-exoplayer
exoplayer-common media3-common
exoplayer-core media3-exoplayer
exoplayer-dash media3-exoplayer-dash
exoplayer-database media3-database
exoplayer-datasource media-datasource
exoplayer-decoder media3-decoder
exoplayer-extractor media3-extractor
exoplayer-hls media3-exoplayer-hls
exoplayer-robolectricutils media3-test-utils-robolectric
exoplayer-rtsp media3-exoplayer-rtsp
exoplayer-smoothstreaming media3-exoplayer-smoothstreaming
exoplayer-testutils media3-test-utils
exoplayer-transformer media3-transformer
exoplayer-effect media3-effect
exoplayer-ui media3-ui
extension-cast media3-cast
extension-cronet media3-datasource-cronet
extension-ima media3-exoplayer-ima
extension-leanback media3-ui-leanback
extension-okhttp media3-datasource-okhttp
extension-rtmp media3-datasource-rtmp
extension-workmanager media3-exoplayer-workmanager'

# Rewrites classes, packages and dependencies from the legacy ExoPlayer package structure
# to androidx.media3 structure.

MEDIA3_VERSION="1.1.1"
LEGACY_PEER_VERSION="2.19.1"

function usage() {
  echo "usage: $0 [-p|-c|-d|-v]|[-m|-l [-x <path>] [-f] PROJECT_ROOT]"
  echo " PROJECT_ROOT: path to your project root (location of 'gradlew')"
  echo " -p: list package mappings and then exit"
  echo " -c: list class mappings (precedence over package mappings) and then exit"
  echo " -d: list dependency mappings and then exit"
  echo " -m: migrate packages, classes and dependencies to AndroidX Media3"
  echo " -l: list files that will be considered for rewrite and then exit"
  echo " -x: exclude the path from the list of file to be changed: 'app/src/test'"
  echo " -f: force the action even when validation fails"
  echo " -v: print the exoplayer2/media3 version strings of this script and exit"
  echo " --noclean : Do not call './gradlew clean' in project directory."
  echo " -h, --help: show this help text"
}

function print_pairs {
  while read -r line;
  do
    IFS=' ' read -ra PAIR <<< "$line"
    printf "%-55s %-30s\n" "${PAIR[0]}" "${PAIR[1]}"
  done <<< "$(echo "$@")"
}

function print_class_mappings {
  while read -r mapping;
  do
    old=$(echo "$mapping" | cut -d ' ' -f1)
    new=$(echo "$mapping" | cut -d ' ' -f2)
    classes=$(echo "$mapping" | cut -d ' ' -f3-)
    for clazz in $classes;
    do
      printf "%-80s %-30s\n" "$old.$clazz" "$new.$clazz"
    done
  done <<< "$(echo "$CLASS_MAPPINGS" | sort)"
}

ERROR_COUNTER=0
VALIDATION_ERRORS=''

function add_validation_error {
  let ERROR_COUNTER++
  VALIDATION_ERRORS+="\033[31m[$ERROR_COUNTER] ->\033[0m ${1}"
}

function validate_exoplayer_version() {
  has_exoplayer_dependency=''
  while read -r file;
  do
    local version
    version=$(grep -m 1 "com\.google\.android\.exoplayer:" "$file" | cut -d ":" -f3 | tr -d \" | tr -d \')
    if [[ ! -z $version ]] && [[ ! "$version" =~ $LEGACY_PEER_VERSION ]];
    then
      add_validation_error "The version does not match '$LEGACY_PEER_VERSION'. \
Update to '$LEGACY_PEER_VERSION' or use the migration script matching your \
current version. Current version '$version' found in\n    $file\n"
    fi
  done <<< "$(find . -type f -name "build.gradle")"
}

function validate_string_not_contained {
  local pattern=$1 # regex
  local failure_message=$2
  while read -r file;
  do
    if grep -q -e "$pattern" "$file";
    then
      add_validation_error "$failure_message:\n    $file\n"
    fi
  done <<< "$files"
}

function validate_string_patterns {
  validate_string_not_contained \
    'com\.google\.android\.exoplayer2\..*\*' \
    'Replace wildcard import statements with fully qualified import statements';
  validate_string_not_contained \
    'com\.google\.android\.exoplayer2\.ui\.PlayerView' \
    'Migrate PlayerView to StyledPlayerView before migrating';
  validate_string_not_contained \
    'LegacyPlayerView' \
    'Migrate LegacyPlayerView to StyledPlayerView before migrating';
  validate_string_not_contained \
    'com\.google\.android\.exoplayer2\.ext\.mediasession' \
    'The MediaSessionConnector is integrated in androidx.media3.session.MediaSession'
}

SED_CMD_INPLACE='sed --in-place=.bak '
if [[ "$OSTYPE" == "darwin"* ]]; then
  SED_CMD_INPLACE="sed -i .bak "
fi

MIGRATE_FILES='1'
LIST_FILES_ONLY='1'
PRINT_CLASS_MAPPINGS='1'
PRINT_PACKAGE_MAPPINGS='1'
PRINT_DEPENDENCY_MAPPINGS='1'
PRINT_VERSION='1'
NO_CLEAN='1'
FORCE='1'
IGNORE_VERSION='1'
EXCLUDED_PATHS=''

while [[ $1 =~ ^-.* ]];
do
  case "$1" in
   -m     ) MIGRATE_FILES='';;
   -l     ) LIST_FILES_ONLY='';;
   -c     ) PRINT_CLASS_MAPPINGS='';;
   -p     ) PRINT_PACKAGE_MAPPINGS='';;
   -d     ) PRINT_DEPENDENCY_MAPPINGS='';;
   -v     ) PRINT_VERSION='';;
   -f     ) FORCE='';;
   -x     ) shift; EXCLUDED_PATHS="$(printf "%s\n%s" $EXCLUDED_PATHS $1)";;
   --noclean     ) NO_CLEAN='';;
   *      ) usage && exit 1;;
  esac
  shift
done

if [[ -z $PRINT_DEPENDENCY_MAPPINGS ]];
then
  print_pairs "$DEPENDENCY_MAPPINGS"
  exit 0
elif [[ -z $PRINT_PACKAGE_MAPPINGS ]];
then
  print_pairs "$PACKAGE_MAPPINGS"
  exit 0
elif [[ -z $PRINT_CLASS_MAPPINGS ]];
then
  print_class_mappings
  exit 0
elif [[ -z $PRINT_VERSION ]];
then
  echo "$LEGACY_PEER_VERSION -> $MEDIA3_VERSION. This script is written to migrate from ExoPlayer $LEGACY_PEER_VERSION to AndroidX Media3 $MEDIA3_VERSION"
  exit 0
elif [[ -z $1 ]];
then
  usage
  exit 1
fi

if [[ ! -f $1/gradlew ]];
then
  echo "directory seems not to exist or is not a gradle project (missing 'gradlew')"
  usage
  exit 1
fi

PROJECT_ROOT=$1
cd "$PROJECT_ROOT"

# Create the set of files to transform
exclusion="/build/|/.idea/|/res/drawable|/res/color|/res/mipmap|/res/values|"
if [[ ! -z $EXCLUDED_PATHS ]];
then
  while read -r path;
  do
    exclusion="$exclusion./$path|"
  done <<< "$EXCLUDED_PATHS"
fi
files=$(find . -name '*\.java' -o -name '*\.kt' -o -name '*\.xml' | grep -Ev "'$exclusion'")

# Validate project and exit in case of validation errors
validate_string_patterns
validate_exoplayer_version "$PROJECT_ROOT"
if [[ ! -z $FORCE && ! -z "$VALIDATION_ERRORS" ]];
then
  echo "============================================="
  echo "Validation errors (use -f to force execution)"
  echo "---------------------------------------------"
  echo -e "$VALIDATION_ERRORS"
  exit 1
fi

if [[ -z $LIST_FILES_ONLY ]];
then
  echo "$files" | cut -c 3-
  find . -type f -name 'build\.gradle' | cut -c 3-
  exit 0
fi

# start migration after successful validation or when forced to disregard validation
# errors

if [[ ! -z "$MIGRATE_FILES" ]];
then
  echo "nothing to do"
  usage
  exit 0
fi

if [[ ! -z $NO_CLEAN ]];
then
  ./gradlew clean
fi

# create expressions for class renamings
renaming_expressions=''
while read -r renaming;
do
  src=$(echo "$renaming" | cut -d ' ' -f1 | sed -e 's/\./\\\./g')
  dest=$(echo "$renaming" | cut -d ' ' -f2)
  renaming_expressions+="-e s/$src/$dest/g "
done <<< "$CLASS_RENAMINGS"

# create expressions for class mappings
classes_expressions=''
while read -r mapping;
do
  src=$(echo "$mapping" | cut -d ' ' -f1 | sed -e 's/\./\\\./g')
  dest=$(echo "$mapping" | cut -d ' ' -f2)
  classes=$(echo "$mapping" | cut -d ' ' -f3-)
  for clazz in $classes;
  do
    classes_expressions+="-e s/$src\.$clazz/$dest.$clazz/g "
  done
done <<< "$CLASS_MAPPINGS"

# create expressions for package mappings
packages_expressions=''
while read -r mapping;
do
  src=$(echo "$mapping" | cut -d ' ' -f1 | sed -e 's/\./\\\./g')
  dest=$(echo "$mapping" | cut -d ' ' -f2)
  packages_expressions+="-e s/$src/$dest/g "
done <<< "$PACKAGE_MAPPINGS"

# do search and replace with expressions in each selected file
while read -r file;
do
  echo "migrating $file"
  expr="$renaming_expressions $classes_expressions $packages_expressions"
  $SED_CMD_INPLACE $expr $file
  rm ${file}.bak
done <<< "$files"

# create expressions for dependencies in gradle files
EXOPLAYER_GROUP="com\.google\.android\.exoplayer"
MEDIA3_GROUP="androidx.media3"
dependency_expressions=""
while read -r mapping
do
  OLD=$(echo "$mapping" | cut -d ' ' -f1 | sed -e 's/\./\\\./g')
  NEW=$(echo "$mapping" | cut -d ' ' -f2)
  dependency_expressions="$dependency_expressions -e s/$EXOPLAYER_GROUP:$OLD:.*\"/$MEDIA3_GROUP:$NEW:$MEDIA3_VERSION\"/g -e s/$EXOPLAYER_GROUP:$OLD:.*'/$MEDIA3_GROUP:$NEW:$MEDIA3_VERSION'/"
done <<< "$DEPENDENCY_MAPPINGS"

## do search and replace for dependencies in gradle files
while read -r build_file;
do
  echo "migrating build file $build_file"
  $SED_CMD_INPLACE $dependency_expressions $build_file
  rm ${build_file}.bak
done <<< "$(find . -type f -name 'build\.gradle')"
