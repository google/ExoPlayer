/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.metadata.id3;

/**
 * ID3 utility methods.
 */
public final class Id3Util {

  private static final String[] STANDARD_GENRES = new String[] {
      // These are the official ID3v1 genres.
      "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge",
      "Hip-Hop", "Jazz", "Metal", "New Age", "Oldies", "Other", "Pop", "R&B", "Rap",
      "Reggae", "Rock", "Techno", "Industrial", "Alternative", "Ska",
      "Death Metal", "Pranks", "Soundtrack", "Euro-Techno", "Ambient",
      "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion", "Trance", "Classical",
      "Instrumental", "Acid", "House", "Game", "Sound Clip", "Gospel", "Noise",
      "AlternRock", "Bass", "Soul", "Punk", "Space", "Meditative",
      "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic", "Darkwave",
      "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream",
      "Southern Rock", "Comedy", "Cult", "Gangsta", "Top 40", "Christian Rap",
      "Pop/Funk", "Jungle", "Native American", "Cabaret", "New Wave",
      "Psychadelic", "Rave", "Showtunes", "Trailer", "Lo-Fi", "Tribal",
      "Acid Punk", "Acid Jazz", "Polka", "Retro", "Musical", "Rock & Roll",
      "Hard Rock",
      // These were made up by the authors of Winamp but backported into the ID3 spec.
      "Folk", "Folk-Rock", "National Folk", "Swing", "Fast Fusion",
      "Bebob", "Latin", "Revival", "Celtic", "Bluegrass", "Avantgarde",
      "Gothic Rock", "Progressive Rock", "Psychedelic Rock", "Symphonic Rock",
      "Slow Rock", "Big Band", "Chorus", "Easy Listening", "Acoustic", "Humour",
      "Speech", "Chanson", "Opera", "Chamber Music", "Sonata", "Symphony",
      "Booty Bass", "Primus", "Porn Groove", "Satire", "Slow Jam", "Club",
      "Tango", "Samba", "Folklore", "Ballad", "Power Ballad", "Rhythmic Soul",
      "Freestyle", "Duet", "Punk Rock", "Drum Solo", "A capella", "Euro-House",
      "Dance Hall",
      // These were also invented by the Winamp folks but ignored by the ID3 authors.
      "Goa", "Drum & Bass", "Club-House", "Hardcore", "Terror", "Indie",
      "BritPop", "Negerpunk", "Polsk Punk", "Beat", "Christian Gangsta Rap",
      "Heavy Metal", "Black Metal", "Crossover", "Contemporary Christian",
      "Christian Rock", "Merengue", "Salsa", "Thrash Metal", "Anime", "Jpop",
      "Synthpop"
  };

  private Id3Util() {}

  public static String decodeGenre(int code) {
    return (0 < code && code <= STANDARD_GENRES.length) ? STANDARD_GENRES[code - 1] : null;
  }

}
