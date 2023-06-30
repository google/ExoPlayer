---
name: Question
about: Issue template for a question.
title: ''
labels: question, needs triage
assignees: ''
---

Unfortunately we can't answer all questions. Unclear questions or questions with
insufficient information may not get attention.

Before filing a question:
-------------------------

- Ask general Android development questions on Stack Overflow
- Search existing issues, including issues that are closed
  https://github.com/androidx/media/issues?q=is%3Aissue
- For ExoPlayer-related questions, please also check for existing questions on
  the ExoPlayer tracker:
  https://github.com/google/ExoPlayer/issues?q=is%3Aissue

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

Don't forget to check ExoPlayer's supported formats and devices, if applicable
(https://developer.android.com/guide/topics/media/exoplayer/supported-formats).

If there's something you don't want to post publicly, please submit the issue,
then email the link/bug report to android-media-github@google.com using a
subject in the format "Issue #1234", where #1234 is your issue number (we don't
reply to emails).
