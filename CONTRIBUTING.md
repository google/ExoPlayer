# How to contribute

## Reporting issues

We use the [AndroidX Media issue tracker][] to track bugs, feature requests and
questions.

Before filing a new issue, please search the trackers to check if it's already
covered by an existing report. Avoiding duplicates helps us maximize the time we
can spend fixing bugs and adding new features. You will also find older issues
on our [ExoPlayer GitHub issue tracker][].

When filing an issue, be sure to provide enough information for us to
efficiently diagnose and reproduce the problem. In particular, please include
all of the information requested in the issue template.

[AndroidX Media issue tracker]: https://github.com/androidx/media/issues
[ExoPlayer GitHub issue tracker]: https://github.com/google/ExoPlayer/issues

## Pull requests

We will also consider high quality pull requests. These should merge
into the `main` branch. Before a pull request can be accepted you must submit
a Contributor License Agreement, as described below.

### Code style

We follow the
[Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
and use [`google-java-format`](https://github.com/google/google-java-format) to
automatically reformat the code. Please consider auto-formatting your changes
before opening a PR (we will otherwise do this ourselves before merging). You
can use the various IDE integrations available, or bulk-reformat all the changes
you made on top of `main` using
[`google-java-format-diff.py`](https://github.com/google/google-java-format/blob/master/scripts/google-java-format-diff.py):

```shell
$ git diff -U0 main... | google-java-format-diff.py -p1 -i
```

### Push access to PR branches

Please ensure maintainers of this repository have push access to your PR branch
by ticking the `Allow edits from maintainers` checkbox when creating the PR (or
after it's created). See the
[GitHub docs](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/creating-a-pull-request-from-a-fork)
for more info. This allows us to make changes and fixes to the PR while it goes
through internal review, and ensures we don't create an
['evil' merge](https://git-scm.com/docs/gitglossary#Documentation/gitglossary.txt-aiddefevilmergeaevilmerge)
when it gets merged.

This checkbox only appears on PRs from individual-owned forks
(https://github.com/orgs/community/discussions/5634). If you open a PR from an
organization-owned fork we will ask you to open a new one from an
individual-owned fork. If this isn't possible we can still merge the PR, but it
will result in an 'evil' merge because the changes and fixes we make during
internal review will be part of the merge commit.

## Contributor license agreement

Contributions to any Google project must be accompanied by a Contributor
License Agreement. This is not a copyright **assignment**, it simply gives
Google permission to use and redistribute your contributions as part of the
project.

  * If you are an individual writing original source code and you're sure you
    own the intellectual property, then you'll need to sign an [individual
    CLA][].

  * If you work for a company that wants to allow you to contribute your work,
    then you'll need to sign a [corporate CLA][].

You generally only need to submit a CLA once, so if you've already submitted
one (even if it was for a different project), you probably don't need to do it
again.

[individual CLA]: https://developers.google.com/open-source/cla/individual
[corporate CLA]: https://developers.google.com/open-source/cla/corporate
