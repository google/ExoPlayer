---
name: Question
about: Issue template for a question.
title: ''
labels: question, needs triage
assignees: ''
---

Unfortunately we can't answer all questions. Unclear questions or questions with
insufficient information may not get attention.

Please only file a question here if you're using classes in the
`com.google.android.exoplayer2` package. If you're using classes in the
`androidx.media3` package (including `com.google.android.exoplayer2`), please file a
question on the AndroidX Media tracker instead:
https://github.com/androidx/media/issues/new/choose

Before filing a question:
-------------------------

-   Ask general Android development questions on Stack Overflow
-   Search existing issues, including issues that are closed
    -   On this tracker: https://github.com/google/ExoPlayer/issues?q=is%3Aissue
    -   On the AndroidX Media tracker:
        https://github.com/androidx/media/issues?q=is%3Aissue
-   Consult our developer website (https://exoplayer.dev/) and Javadoc
    (https://exoplayer.dev/doc/reference/)

When filing a question:
-------------------------

Describe your question in detail.

In case your question refers to a problem you are seeing in your app:

- Output of running `$ adb bugreport` in the console

In case your question is related to a piece of media:

- URI to test content
- For protected content:
  - DRM scheme and license server URL
  - Authentication HTTP headers

Don't forget to check supported formats and devices
(https://exoplayer.dev/supported-formats.html).

If there's something you don't want to post publicly, please submit the issue,
then email the link/bug report to dev.exoplayer@gmail.com using a subject in the
format "Issue #1234", where #1234 is your issue number (we don't reply to
emails).
