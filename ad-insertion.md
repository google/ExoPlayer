---
title: Ad insertion
---

Intro. Client-side vs Server-side ad insertion.

### Client-side ad insertion ###

Intro. Single player vs swapping player.

#### Using the IMA extension ####

TODO. Mention how to try it out in the main demo app (withExtensions variant).

#### Using a third party ads SDK ####

TODO. Mention the SDK provider may already have an ExoPlayer extension, so check
with them first. Else need to implement AdsLoader. Use IMA extension as a
reference. Alternatively you can just use ConcatenatingMediaSource and manage
everything yourself?

### Server-side ad insertion ###

Multi-period DASH
Discontinuity sequence in HLS
Possibly need some way to identify when in an ad break. Not sure how to do this.

[main demo app]: {{ site.release_v2 }}/demos/main
