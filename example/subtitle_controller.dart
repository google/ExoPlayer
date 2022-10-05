import 'dart:convert';
import 'package:lidaverse/models/subtitle.dart';
import 'package:http/http.dart' as http;

import 'models/subtitles.dart';

class SubtitleController {
  String subtitlesContent;
  String subtitleUrl;
  SubtitleType type;
  final bool showSubtitles;

  SubtitleController({
    this.subtitleUrl,
    this.subtitlesContent,
    this.showSubtitles = true,
    this.type = SubtitleType.VTT,
  });

  Future<Subtitles> getSubtitles() async {
    RegExp regExp = type == SubtitleType.VTT ? new RegExp(
      r"(\d{1,2}):(\d{2}):(\d{2})\.(\d+) --> (\d{1,2}):(\d{2}):(\d{2})\.(\d+)(?:.*)(\D*)",
      caseSensitive: false,
      multiLine: true,
    ) : RegExp(
      r"(\d{1,2}):(\d{2}):(\d{2})\,(\d+) --> (\d{1,2}):(\d{2}):(\d{2})\,(\d+)(?:.*)(\D*)",
      caseSensitive: false,
      multiLine: true,
    ) ;

    if (subtitlesContent == null && subtitleUrl != null) {
      http.Response response = await http.get(subtitleUrl);
      if (response.statusCode == 200) {
        subtitlesContent = utf8.decode(response.bodyBytes);
      }
    }
    print(subtitlesContent);

    List<RegExpMatch> matches = regExp.allMatches(subtitlesContent).toList();
    List<Subtitle> subtitleList = List();

    matches.forEach((RegExpMatch regExpMatch) {
      int startTimeHours = int.parse(regExpMatch.group(1));
      int startTimeMinutes = int.parse(regExpMatch.group(2));
      int startTimeSeconds = int.parse(regExpMatch.group(3));
      int startTimeMilliseconds = int.parse(regExpMatch.group(4));

      int endTimeHours = int.parse(regExpMatch.group(5));
      int endTimeMinutes = int.parse(regExpMatch.group(6));
      int endTimeSeconds = int.parse(regExpMatch.group(7));
      int endTimeMilliseconds = int.parse(regExpMatch.group(8));
      String text = regExpMatch.group(9);

      Duration startTime = Duration(
          hours: startTimeHours,
          minutes: startTimeMinutes,
          seconds: startTimeSeconds,
          milliseconds: startTimeMilliseconds);
      Duration endTime = Duration(
          hours: endTimeHours,
          minutes: endTimeMinutes,
          seconds: endTimeSeconds,
          milliseconds: endTimeMilliseconds);

      subtitleList.add(
          Subtitle(startTime: startTime, endTime: endTime, text: text.trim()));
    });
    print(subtitleList);

    Subtitles subtitles = Subtitles(subtitles: subtitleList);
    return subtitles;
  }
}

enum SubtitleType{
  VTT,
  SRT
}
