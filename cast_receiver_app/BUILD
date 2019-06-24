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

load("@io_bazel_rules_closure//closure:defs.bzl", "closure_js_library")
load("@io_bazel_rules_closure//closure:defs.bzl", "closure_js_binary")
load("@io_bazel_rules_closure//closure:defs.bzl", "closure_js_test")
load("@io_bazel_rules_closure//closure:defs.bzl", "closure_css_library")
load("@io_bazel_rules_closure//closure:defs.bzl", "closure_css_binary")

licenses(["notice"])  # Apache 2.0

# The Shaka player library - 2.5.0-beta2 (needs to be cloned from Github).
closure_js_library(
    name = "shaka_player_library",
    srcs = glob(
        [
            "external-js/shaka-player/lib/**/*.js",
            "external-js/shaka-player/externs/**/*.js",
        ],
        exclude = [
            "external-js/shaka-player/lib/debug/asserts.js",
            "external-js/shaka-player/externs/mediakeys.js",
            "external-js/shaka-player/externs/networkinformation.js",
            "external-js/shaka-player/externs/vtt_region.js",
        ],
    ),
    suppress = [
        "strictMissingRequire",
        "missingSourcesWarnings",
        "analyzerChecks",
        "strictCheckTypes",
        "checkTypes",
    ],
    deps = [
        "@io_bazel_rules_closure//closure/library",
    ],
)

# The plain player not depending on the cast library.
closure_js_library(
    name = "player_lib",
    srcs = [
        "externs/protocol.js",
        "src/configuration_factory.js",
        "src/constants.js",
        "src/playback_info_view.js",
        "src/player.js",
        "src/timeout.js",
        "src/util.js",
    ],
    suppress = [
        "missingSourcesWarnings",
        "analyzerChecks",
        "strictCheckTypes",
    ],
    deps = [
        ":shaka_player_library",
        "@io_bazel_rules_closure//closure/library",
    ],
)

# A debug app to test the player with a desktop browser.
closure_js_library(
    name = "app_desktop_lib",
    srcs = [
        "app-desktop/src/main.js",
        "app-desktop/src/player_controls.js",
        "app-desktop/src/samples.js",
        "externs/shaka.js",
    ],
    suppress = [
        "reportUnknownTypes",
        "strictCheckTypes",
    ],
    deps = [
        ":player_lib",
        ":shaka_player_library",
        "@io_bazel_rules_closure//closure/library",
    ],
)

# Includes the javascript files of the cast receiver app.
closure_js_library(
    name = "app_lib",
    srcs = [
        "app/src/main.js",
        "app/src/message_dispatcher.js",
        "app/src/receiver.js",
        "app/src/validation.js",
        "externs/cast.js",
        "externs/shaka.js",
    ],
    suppress = [
        "missingSourcesWarnings",
        "analyzerChecks",
        "strictCheckTypes",
    ],
    deps = [
        ":player_lib",
        ":shaka_player_library",
        "@io_bazel_rules_closure//closure/library",
    ],
)

# Test utils like mocks.
closure_js_library(
    name = "test_util_lib",
    testonly = 1,
    srcs = [
        "externs/protocol.js",
        "test/externs.js",
        "test/mocks.js",
        "test/util.js",
    ],
    suppress = [
        "checkTypes",
        "strictCheckTypes",
        "reportUnknownTypes",
        "accessControls",
        "analyzerChecks",
        "missingSourcesWarnings",
    ],
    deps = [
        ":shaka_player_library",
        "@io_bazel_rules_closure//closure/library",
        "@io_bazel_rules_closure//closure/library/testing:jsunit",
    ],
)

# Unit test for the player.
closure_js_test(
    name = "player_tests",
    srcs = glob([
        "test/player_test.js",
    ]),
    entry_points = [
        "exoplayer.cast.test",
    ],
    suppress = [
        "checkTypes",
        "strictCheckTypes",
        "reportUnknownTypes",
        "accessControls",
        "analyzerChecks",
        "missingSourcesWarnings",
    ],
    deps = [
        ":app_lib",
        ":player_lib",
        ":test_util_lib",
        "@io_bazel_rules_closure//closure/library/testing:asserts",
        "@io_bazel_rules_closure//closure/library/testing:jsunit",
        "@io_bazel_rules_closure//closure/library/testing:testsuite",
    ],
)

# Unit test for the queue in the player.
closure_js_test(
    name = "queue_tests",
    srcs = glob([
        "test/queue_test.js",
    ]),
    entry_points = [
        "exoplayer.cast.test.queue",
    ],
    suppress = [
        "checkTypes",
        "strictCheckTypes",
        "reportUnknownTypes",
        "accessControls",
        "analyzerChecks",
        "missingSourcesWarnings",
    ],
    deps = [
        ":app_lib",
        ":player_lib",
        ":test_util_lib",
        "@io_bazel_rules_closure//closure/library/testing:asserts",
        "@io_bazel_rules_closure//closure/library/testing:jsunit",
        "@io_bazel_rules_closure//closure/library/testing:testsuite",
    ],
)

# Unit test for the receiver.
closure_js_test(
    name = "receiver_tests",
    srcs = glob([
        "test/receiver_test.js",
    ]),
    entry_points = [
        "exoplayer.cast.test.receiver",
    ],
    suppress = [
        "checkTypes",
        "strictCheckTypes",
        "reportUnknownTypes",
        "accessControls",
        "analyzerChecks",
        "missingSourcesWarnings",
    ],
    deps = [
        ":app_lib",
        ":player_lib",
        ":test_util_lib",
        "@io_bazel_rules_closure//closure/library/testing:asserts",
        "@io_bazel_rules_closure//closure/library/testing:jsunit",
        "@io_bazel_rules_closure//closure/library/testing:testsuite",
    ],
)

# Unit test for the validations.
closure_js_test(
    name = "validation_tests",
    srcs = [
        "test/validation_test.js",
    ],
    entry_points = [
        "exoplayer.cast.test.validation",
    ],
    suppress = [
        "checkTypes",
        "strictCheckTypes",
        "reportUnknownTypes",
        "accessControls",
        "analyzerChecks",
        "missingSourcesWarnings",
    ],
    deps = [
        ":app_lib",
        ":player_lib",
        ":test_util_lib",
        "@io_bazel_rules_closure//closure/library/testing:asserts",
        "@io_bazel_rules_closure//closure/library/testing:jsunit",
        "@io_bazel_rules_closure//closure/library/testing:testsuite",
    ],
)

# The receiver app as a compiled binary.
closure_js_binary(
    name = "app",
    entry_points = [
        "exoplayer.cast.app",
        "shaka.dash.DashParser",
        "shaka.hls.HlsParser",
        "shaka.abr.SimpleAbrManager",
        "shaka.net.HttpFetchPlugin",
        "shaka.net.HttpXHRPlugin",
        "shaka.media.AdaptationSetCriteria",
    ],
    deps = [":app_lib"],
)

# The debug app for the player as a compiled binary.
closure_js_binary(
    name = "app_desktop",
    entry_points = [
        "exoplayer.cast.debug",
        "exoplayer.cast.samples",
        "shaka.dash.DashParser",
        "shaka.hls.HlsParser",
        "shaka.abr.SimpleAbrManager",
        "shaka.net.HttpFetchPlugin",
        "shaka.net.HttpXHRPlugin",
        "shaka.media.AdaptationSetCriteria",
    ],
    deps = [":app_desktop_lib"],
)

# Defines the css style of the receiver app.
closure_css_library(
    name = "app_styles_lib",
    srcs = [
        "app/html/index.css",
        "app/html/playback_info_view.css",
    ],
)

# Defines the css styles of the debug app.
closure_css_library(
    name = "app_desktop_styles_lib",
    srcs = [
        "app-desktop/html/index.css",
        "app/html/playback_info_view.css",
    ],
)

# Compiles the css styles of the receiver app.
closure_css_binary(
    name = "app_styles",
    renaming = False,
    deps = ["app_styles_lib"],
)

# Compiles the css styles of the debug app.
closure_css_binary(
    name = "app_desktop_styles",
    renaming = False,
    deps = ["app_desktop_styles_lib"],
)
