
import 'package:flutter/material.dart';
import 'package:flutterexodrmplayer/flutterexodrmplayer.dart';
import 'package:lidaverse/subtitle_controller.dart';

import 'models/style/subtitle_style.dart';
import 'models/subtitle.dart';
import 'models/subtitles.dart';

class SubtitleTextView extends StatefulWidget {
  final SubtitleController subtitleController;
  final VideoPlayerController videoPlayerController;
  final SubtitleStyle subtitleStyle;

  const SubtitleTextView({Key key,
    @required this.subtitleController,
    this.videoPlayerController,
    this.subtitleStyle})
      : super(key: key);

  @override
  _SubtitleTextViewState createState() =>
      _SubtitleTextViewState(videoPlayerController);
}

class _SubtitleTextViewState extends State<SubtitleTextView> {
  final VideoPlayerController videoPlayerController;
  Subtitle subtitle;
  Function listener;

  _SubtitleTextViewState(this.videoPlayerController);

  @override
  void initState() {
    listener = () => _subtitleWatcher(videoPlayerController);
    videoPlayerController.addListener(listener);

    _subtitleWatcher(videoPlayerController);
    super.initState();
  }

  _subtitleWatcher(VideoPlayerController videoPlayerController) async {
    Subtitles subtitles = await widget.subtitleController.getSubtitles();
    VideoPlayerValue latestValue = videoPlayerController.value;

    Duration videoPlayerPosition = latestValue.position;
    if (videoPlayerPosition != null) {
      subtitles.subtitles.forEach((Subtitle subtitleItem) {
        if (videoPlayerPosition.inMilliseconds >
            subtitleItem.startTime.inMilliseconds &&
            videoPlayerPosition.inMilliseconds <
                subtitleItem.endTime.inMilliseconds) {
          if (this.mounted) {
            setState(() {
              subtitle = subtitleItem;
            });
          }
        }
      });
    }
  }

  @override
  void dispose() {
    videoPlayerController.removeListener(listener);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return subtitle != null
        ? Container(
      child: Stack(
        children: <Widget>[
          widget.subtitleStyle.hasBorder
              ? Center(
            child: Text(
              subtitle.text,
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: widget.subtitleStyle.fontSize,
                foreground: Paint()
                  ..style = widget.subtitleStyle.borderStyle.style
                  ..strokeWidth =
                      widget.subtitleStyle.borderStyle.strokeWidth
                  ..color = widget.subtitleStyle.borderStyle.color,
              ),
            ),
          )
              : Container(
            child: null,
          ),
          Center(
            child: Text(
              subtitle.text,
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: widget.subtitleStyle.fontSize,
                color: widget.subtitleStyle.textColor,
              ),
            ),
          ),
        ],
      ),
    )
        : Container(
      child: null,
    );
  }
}
