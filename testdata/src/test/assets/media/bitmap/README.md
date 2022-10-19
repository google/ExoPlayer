Expected first frame after a
[Transformer](https://github.com/androidx/media/tree/main/libraries/transformer)
transformation. Used to validate that frame operations produce expected output
in
[pixel tests](https://github.com/androidx/media/tree/main/libraries/transformer/src/androidTest/java/com/google/android/exoplayer2/transformer).

<!-- copybara:strip_begin -->

To generate new "expected" assets:

1.  Start an emulator with the same configuration as on presubmit. The emulator
    is given
    [here](cs/target_device%20f:media/libraries/transformer/src/androidTest/BUILD)
    and can be run locally, using go/crow:

    ```shell
    crow --device generic_phone --api_level 30 --arch x86
    ```

2.  Run the test.

3.  Copy the file from the device to the assets directory, e.g.,

    ```shell
    adb pull \
    /sdcard/Android/data/com.google.android.exoplayer2.effect.test/cache/drawFrame_rotate90_actual.png \
    third_party/java_src/android_libs/media/libraries/test_data/src/test/assets/media/bitmap/sample_mp4_first_frame/electrical_colors/rotate90.png
    ```

<!-- copybara:strip_end -->
