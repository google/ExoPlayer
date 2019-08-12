---
name: Content not playing correctly
about: Issue template for a content not playing issue.
title: ''
labels: content not playing, needs triage
assignees: ''
---

Before filing a content issue:
------------------------------
- Search existing issues, including issues that are closed:
  https://github.com/google/ExoPlayer/issues?q=is%3Aissue
- Consult our supported formats page, which can be found at
  https://exoplayer.dev/supported-formats.html.
- Learn how to create useful log output by using the EventLogger:
  https://exoplayer.dev/listening-to-player-events.html#using-eventlogger
- Try playing your content in the ExoPlayer demo app. Information about the
  ExoPlayer demo app can be found here:
  http://exoplayer.dev/demo-application.html.

When reporting a content issue:
-----------------------------
Fill out the sections below, leaving the headers but replacing the content. If
you're unable to provide certain information, please explain why in the relevant
section. We may close issues if they do not include sufficient information.

### [REQUIRED] Content description
Describe the content and any specifics you expected to play but did not. This
could be the container or sample format itself or any features the stream has
and you expect to play, like 5.1 audio track, text tracks or drm systems.

### [REQUIRED] Link to test content
Provide a JSON snippet for the demo app’s media.exolist.json file, or a link to
media that reproduces the issue. If you don't wish to post it publicly, please
submit the issue, then email the link to dev.exoplayer@gmail.com using a subject
in the format "Issue #1234", where "#1234" should be replaced with your issue
number. Provide all the metadata we'd need to play the content like drm license
urls or similar. If the content is accessible only in certain countries or
regions, please say so.

### [REQUIRED] Version of ExoPlayer being used
Specify the absolute version number. Avoid using terms such as "latest".

### [REQUIRED] Device(s) and version(s) of Android being used
Specify the devices and versions of Android on which you expect the content to
play. If possible, please test on multiple devices and Android versions.

### [REQUIRED] A full bug report captured from the device
Capture a full bug report using "adb bugreport". Output from "adb logcat" or a
log snippet is NOT sufficient. Please attach the captured bug report as a file.
If you don't wish to post it publicly, please submit the issue, then email the
bug report to dev.exoplayer@gmail.com using a subject in the format
"Issue #1234", where "#1234" should be replaced with your issue number.

<!-- DO NOT DELETE
validate_template=true
template_path=.github/ISSUE_TEMPLATE/content_not_playing.md
-->
