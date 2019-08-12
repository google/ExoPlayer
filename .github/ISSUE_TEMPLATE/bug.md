---
name: Bug report
about: Issue template for a bug report.
title: ''
labels: bug, needs triage
assignees: ''
---

Before filing a bug:
-----------------------
- Search existing issues, including issues that are closed:
  https://github.com/google/ExoPlayer/issues?q=is%3Aissue
- Consult our developer website, which can be found at https://exoplayer.dev/.
  It provides detailed information about supported formats and devices.
- Learn how to create useful log output by using the EventLogger:
  https://exoplayer.dev/listening-to-player-events.html#using-eventlogger
- Rule out issues in your own code. A good way to do this is to try and
  reproduce the issue in the ExoPlayer demo app. Information about the ExoPlayer
  demo app can be found here:
  http://exoplayer.dev/demo-application.html.

When reporting a bug:
-----------------------
Fill out the sections below, leaving the headers but replacing the content. If
you're unable to provide certain information, please explain why in the relevant
section. We may close issues if they do not include sufficient information.

### [REQUIRED] Issue description
Describe the issue in detail, including observed and expected behavior.

### [REQUIRED] Reproduction steps
Describe how the issue can be reproduced, ideally using the ExoPlayer demo app
or a small sample app that you’re able to share as source code on GitHub.

### [REQUIRED] Link to test content
Provide a JSON snippet for the demo app’s media.exolist.json file, or a link to
media that reproduces the issue. If you don't wish to post it publicly, please
submit the issue, then email the link to dev.exoplayer@gmail.com using a subject
in the format "Issue #1234", where "#1234" should be replaced with your issue
number. Provide all the metadata we'd need to play the content like drm license
urls or similar. If the content is accessible only in certain countries or
regions, please say so.

### [REQUIRED] A full bug report captured from the device
Capture a full bug report using "adb bugreport". Output from "adb logcat" or a
log snippet is NOT sufficient. Please attach the captured bug report as a file.
If you don't wish to post it publicly, please submit the issue, then email the
bug report to dev.exoplayer@gmail.com using a subject in the format
"Issue #1234", where "#1234" should be replaced with your issue number.

### [REQUIRED] Version of ExoPlayer being used
Specify the absolute version number. Avoid using terms such as "latest".

### [REQUIRED] Device(s) and version(s) of Android being used
Specify the devices and versions of Android on which the issue can be
reproduced, and how easily it reproduces. If possible, please test on multiple
devices and Android versions.

<!-- DO NOT DELETE
validate_template=true
template_path=.github/ISSUE_TEMPLATE/bug.md
-->
