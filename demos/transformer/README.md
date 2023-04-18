# Transformer demo

This app demonstrates how to use the [Transformer][] API to modify videos, for
example by removing audio or video.

See the [demos README](../README.md) for instructions on how to build and run
this demo.

## MediaPipe frame processing demo

Building the demo app with [MediaPipe][] integration enabled requires some extra
manual steps.

1.  Follow the
    [instructions](https://google.github.io/mediapipe/getting_started/install.html)
    to install MediaPipe.
1.  Copy the Transformer demo's build configuration and MediaPipe graph text
    protocol buffer under the MediaPipe source tree. This makes it easy to
    [build an AAR][] with bazel by reusing MediaPipe's workspace.

    ```sh
    cd "<path to MediaPipe checkout>"
    MEDIAPIPE_ROOT="$(pwd)"
    MEDIAPIPE_TRANSFORMER_ROOT="${MEDIAPIPE_ROOT}/mediapipe/java/com/google/mediapipe/transformer"
    cd "<path to the transformer demo (containing this readme)>"
    TRANSFORMER_DEMO_ROOT="$(pwd)"
    mkdir -p "${MEDIAPIPE_TRANSFORMER_ROOT}"
    mkdir -p "${TRANSFORMER_DEMO_ROOT}/libs"
    cp ${TRANSFORMER_DEMO_ROOT}/BUILD.bazel ${MEDIAPIPE_TRANSFORMER_ROOT}/BUILD
    cp ${TRANSFORMER_DEMO_ROOT}/src/withMediaPipe/assets/edge_detector_mediapipe_graph.pbtxt \
      ${MEDIAPIPE_TRANSFORMER_ROOT}
    ```

1.  Build the AAR and the binary proto for the demo's MediaPipe graph, then copy
    them to Transformer.

    ```sh
    cd ${MEDIAPIPE_ROOT}
    bazel build -c opt --strip=ALWAYS \
      --host_crosstool_top=@bazel_tools//tools/cpp:toolchain \
      --fat_apk_cpu=arm64-v8a,armeabi-v7a \
      --legacy_whole_archive=0 \
      --features=-legacy_whole_archive \
      --copt=-fvisibility=hidden \
      --copt=-ffunction-sections \
      --copt=-fdata-sections \
      --copt=-fstack-protector \
      --copt=-Oz \
      --copt=-fomit-frame-pointer \
      --copt=-DABSL_MIN_LOG_LEVEL=2 \
      --linkopt=-Wl,--gc-sections,--strip-all \
      mediapipe/java/com/google/mediapipe/transformer:edge_detector_mediapipe_aar.aar
    cp bazel-bin/mediapipe/java/com/google/mediapipe/transformer/edge_detector_mediapipe_aar.aar \
      ${TRANSFORMER_DEMO_ROOT}/libs
    bazel build mediapipe/java/com/google/mediapipe/transformer:edge_detector_binary_graph
    cp bazel-bin/mediapipe/java/com/google/mediapipe/transformer/edge_detector_mediapipe_graph.binarypb \
      ${TRANSFORMER_DEMO_ROOT}/src/withMediaPipe/assets
    ```

1.  In Android Studio, gradle sync and select the `withMediaPipe` build variant
    (this will only appear if the AAR is present), then build and run the demo
    app and select a MediaPipe-based effect.

[Transformer]: https://developer.android.com/guide/topics/media/transforming-media
[MediaPipe]: https://google.github.io/mediapipe/
[build an AAR]: https://google.github.io/mediapipe/getting_started/android_archive_library.html
