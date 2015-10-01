---
layout: default
title: HowTo &#35;1 - Reporting an issue
author: Oliver Woodman
disqus: true
---

As ExoPlayer becomes more popular, it becomes more important that the issues reported on our
[GitHub issue tracker](https://github.com/google/ExoPlayer/issues) contain the information required
for us to efficiently triage, reproduce and (hopefully!) fix the problem. So, what information
should you include when reporting an issue?

<!--more-->

In general all of the following are useful, and should be included unless you're sure that they're
unnecessary for the specific issue being reported:

* A description of the issue itself.
* Steps describing how the issue can be reproduced, ideally in the ExoPlayer demo app.
* If the issue only reproduces with certain content, a link to some content that we can use to
  reproduce. If you don't wish to post a link publicly, please send the link by email to
  `dev.exoplayer@gmail.com`, including the issue number in the subject line.
* The version of ExoPlayer being used.
* The device(s) and version(s) of Android on which the issue can be reproduced, and how easily it
  reproduces. Does it happen every time, or only occassionally? If possible, please test on multiple
  devices and Android versions. If the issue does not reproduce on some combinations, that's useful
  information!
* A full bug report taken from the device just after the issue occurs, attached to the issue as a 
  file. A bug report can be captured from the command line using `adb bugreport > bugreport.txt`.
  Note that the output from `adb logcat` or a log snippet is typically **not** sufficient.
* Anything else you think might be relevant!

Thanks!
