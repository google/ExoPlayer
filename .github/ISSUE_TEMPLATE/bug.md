---
name: Bug report
about: Issue template for a bug report.
title: ''
labels: bug, needs triage
assignees: ''
---

We can only process bug reports that are actionable. Unclear bug reports or
reports with insufficient information may not get attention.

Before filing a bug:
-------------------------

- Search existing issues, including issues that are closed:
  https://github.com/google/ExoPlayer/issues?q=is%3Aissue
- Consult our developer website: https://exoplayer.dev/
- Check the supported formats: https://exoplayer.dev/supported-formats.html
- Try playing problematic media in the demo app:
  http://exoplayer.dev/demo-application.html

When reporting a bug:
-------------------------

Describe how the issue can be reproduced, ideally using the ExoPlayer demo app
or a small sample app that you’re able to share as source code on GitHub. To
increase the chance of your issue getting attention, please also include:

- Clear reproduction steps including observed and expected behavior
- Output of running "adb bugreport" in the console shortly after encountering
  the issue
- URI to test content for reproduction
- For protected content:
  - DRM scheme and license server URL
  - Authentication HTTP headers

- ExoPlayer version number
- Android version
- Android device

If there's something you don't want to post publicly, please submit the issue,
then email the link/bug report to dev.exoplayer@gmail.com using a subject in the
format "Issue #1234", where #1234 is your issue number (we don't reply to
emails).
