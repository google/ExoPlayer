---
name: Question
about: Issue template for a question.
title: ''
labels: question, needs triage
assignees: ''
---

Before filing a question:
-----------------------
- This issue tracker is intended ExoPlayer specific questions. If you're asking
  a general Android development question, please do so on Stack Overflow.
- Search existing issues, including issues that are closed. It’s often the
  quickest way to get an answer!
  https://github.com/google/ExoPlayer/issues?q=is%3Aissue
- Consult our developer website, which can be found at https://exoplayer.dev/.
  It provides detailed information about supported formats, devices as well as
  information about how to use the ExoPlayer library.
- The ExoPlayer library Javadoc can be found at
  https://exoplayer.dev/doc/reference/

When filing a question:
-----------------------
Fill out the sections below, leaving the headers but replacing the content. If
you're unable to provide certain information, please explain why in the relevant
section. We may close issues if they do not include sufficient information.

### [REQUIRED] Searched documentation and issues
Tell us where you’ve already looked for an answer to your question. It’s
important for us to know this so that we can improve our documentation.

### [REQUIRED] Question
Describe your question in detail.

### A full bug report captured from the device
In case your question refers to a problem you are seeing in your app, capture a
full bug report using "adb bugreport". Please attach the captured bug report as
a file. If you don't wish to post it publicly, please submit the issue, then
email the bug report to dev.exoplayer@gmail.com using a subject in the format
"Issue #1234", where "#1234" should be replaced with your issue number.

### Link to test content
In case your question is related to a piece of media, which you are trying to
play, please provide a JSON snippet for the demo app’s media.exolist.json file,
or a link to media that reproduces the issue. If you don't wish to post it
publicly, please submit the issue, then email the link to
dev.exoplayer@gmail.com using a subject in the format "Issue #1234", where
"#1234" should be replaced with your issue number. Provide all the metadata we'd
need to play the content like drm license urls or similar. If the content is
accessible only in certain countries or regions, please say so.
