import 'dart:convert';

import 'package:meta/meta.dart';

class Video {
  final String name;
  final List<SampleVideo> samples;

  Video({@required this.name, @required this.samples});

  factory Video.fromJson(Map<String, dynamic> parsedJson) {
    List<SampleVideo> samplefiles = SampleVideo.parseSampleLists(parsedJson['samples']);
    return Video(name: parsedJson['name'], samples: samplefiles);
  }

  static List<Video> parseVideoLists(String responseBody) {
    final parsed = json.decode(responseBody).cast<Map<String, dynamic>>();
    return parsed.map<Video>((json) => Video.fromJson(json)).toList();
  }

  @override
  String toString() {
    // TODO: implement toString
    return 'Content Type : $name \n'
        '${samples.toString()}';
  }
}

class SampleVideo {
  final String uri;
  final List<String> playlist;
  final String spherical_stereo_mode;
  final int playedLength;

  factory SampleVideo.fromJson(Map<String, dynamic> parsedJson) {
    List<String> playlistfiles = null;
    if (parsedJson['playlist'] != null) {
      playlistfiles = parsePlayLists(parsedJson['playlist']);
    }

    return SampleVideo(
        uri: parsedJson['uri'],
        spherical_stereo_mode: parsedJson['spherical_stereo_mode'],
        playlist: playlistfiles,
        playedLength: 0);
  }

  static List<SampleVideo> parseSampleLists(parsedresponseBody) {
    return parsedresponseBody
        .map<SampleVideo>((json) => SampleVideo.fromJson(json))
        .toList();
  }

  SampleVideo(
      {
        @required  this.uri,
        this.spherical_stereo_mode,
        this.playlist,
        this.playedLength});

  @override
  String toString() {
    // TODO: implement toString
    return
        'Video Link :$uri \n'
        'Playlist :${playlist == null ? null : playlist.toString()}';
  }

  static List<String> parsePlayLists(parsedresponseBody) {
    return parsedresponseBody
        .map<String>((json) => playListfromJson(json))
        .toList();
  }

  static String playListfromJson(json) {
    return json['uri'];
  }
}
