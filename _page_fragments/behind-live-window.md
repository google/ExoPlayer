## BehindLiveWindowException ##

In case a live stream with limited availability is played, the player may fall
behind this live window if the player is paused or buffering for a long enough
period of time. In this case a `BehindLiveWindowException` is thrown, which can
be caught to resume the player at the live edge. The [PlayerActivity][] of the
demo app exemplifies this approach.

~~~
@Override
public void onPlayerError(ExoPlaybackException e) {
  if (isBehindLiveWindow(e)) {
    // Re-initialize player at the live edge.
  } else {
    // Handle other errors
  }
}

private static boolean isBehindLiveWindow(ExoPlaybackException e) {
  if (e.type != ExoPlaybackException.TYPE_SOURCE) {
    return false;
  }
  Throwable cause = e.getSourceException();
  while (cause != null) {
    if (cause instanceof BehindLiveWindowException) {
      return true;
    }
    cause = cause.getCause();
  }
  return false;
}
~~~
{: .language-java}

[PlayerActivity]: {{ site.release_v2 }}/demos/main/src/main/java/com/google/android/exoplayer2/demo/PlayerActivity.java
