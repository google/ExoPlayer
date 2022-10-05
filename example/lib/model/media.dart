import 'dart:convert';

import 'package:meta/meta.dart';

class Media {
  final String name;
  final List<Sample> samples;

  Media({@required this.name, @required this.samples});

  factory Media.fromJson(Map<String, dynamic> parsedJson) {
    List<Sample> samplefiles = Sample.parseSampleLists(parsedJson['samples']);
    return Media(name: parsedJson['name'], samples: samplefiles);
  }

  static List<Media> parseMediaLists(String responseBody) {
    final parsed = json.decode(responseBody).cast<Map<String, dynamic>>();
    return parsed.map<Media>((json) => Media.fromJson(json)).toList();
  }

  @override
  String toString() {
    // TODO: implement toString
    return 'Content Type : $name \n'
        '${samples.toString()}';
  }
}

class Sample {
  final String name;
  final String uri;
  final String extension;
  final String drm_scheme;
  final String drm_license_url;
  final String ad_tag_uri;
  final List<String> playlist;
  final String spherical_stereo_mode;
  final int playedLength;

  factory Sample.fromJson(Map<String, dynamic> parsedJson) {
    List<String> playlistfiles = null;
    if (parsedJson['playlist'] != null) {
      playlistfiles = parsePlayLists(parsedJson['playlist']);
    }

    return Sample(
        name: parsedJson['name'],
        uri: parsedJson['uri'],
        extension: parsedJson['extension'],
        drm_scheme: parsedJson['drm_scheme'],
        drm_license_url: parsedJson['drm_license_url'],
        ad_tag_uri: parsedJson['ad_tag_uri'],
        spherical_stereo_mode: parsedJson['spherical_stereo_mode'],
        playlist: playlistfiles,
        playedLength: 0);
  }

  static List<Sample> parseSampleLists(parsedresponseBody) {
    return parsedresponseBody
        .map<Sample>((json) => Sample.fromJson(json))
        .toList();
  }

  Sample(
      {@required this.name,
      this.uri,
      this.extension,
      this.drm_scheme,
      this.drm_license_url,
      this.ad_tag_uri,
      this.spherical_stereo_mode,
      this.playlist,
      this.playedLength});

  @override
  String toString() {
    // TODO: implement toString
    return 'Video Name :$name \n'
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
