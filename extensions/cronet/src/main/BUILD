# Description:
# cronet extension for ExoPlayer.

licenses(["notice"])  # License: ../../../../LICENSE

load("//third_party/java_src/android_libs/exoplayer/v1:builddefs.bzl", "internal_filegroup")
load("//tools/build_rules:build_test.bzl", "build_test")

android_library(
    name = "library",
    srcs = glob(["java/**/*.java"]),
    visibility = ["//third_party/java_src/android_libs/exoplayer/v1:visibility_internal"],
    deps = [
        "//third_party/java/android_libs/cronet:cronet_api",
        "//third_party/java_src/android_libs/exoplayer/v1/library/src/main:library",
    ],
)

build_test(
    name = "library_build_test",
    targets = [":library"],
)

internal_filegroup(name = "internal_files")
