library subtitle_wrapper_package;


import 'package:flutter/material.dart';
import 'package:flutterexodrmplayer/flutterexodrmplayer.dart';
import 'package:lidaverse/subtitle_controller.dart';
import 'package:lidaverse/subtitle_text_view.dart';
import 'models/style/subtitle_style.dart';

class SubTitleWrapper extends StatefulWidget {
  final Widget videoChild;
  final SubtitleController subtitleController;
  final VideoPlayerController videoPlayerController;
  final SubtitleStyle subtitleStyle;

  SubTitleWrapper(
      {Key key,
      @required this.videoChild,
      @required this.subtitleController,
      @required this.videoPlayerController,
      this.subtitleStyle = const SubtitleStyle()})
      : super(key: key);

  @override
  _SubTitleWrapperState createState() =>
      _SubTitleWrapperState(videoPlayerController);
}

class _SubTitleWrapperState extends State<SubTitleWrapper> {
  final VideoPlayerController videoPlayerController;

  _SubTitleWrapperState(this.videoPlayerController);

  @override
  void initState() {
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: <Widget>[
        widget.videoChild,
        widget.subtitleController.showSubtitles
            ? Positioned(
                top: widget.subtitleStyle.position.top,
                bottom: widget.subtitleStyle.position.bottom,
                left: widget.subtitleStyle.position.left,
                right: widget.subtitleStyle.position.right,
                child: SubtitleTextView(
                  subtitleController: widget.subtitleController,
                  videoPlayerController: videoPlayerController,
                  subtitleStyle: widget.subtitleStyle,
                ),
              )
            : Container(
                child: null,
              )
      ],
    );
  }

  @override
  void dispose() {
    _dispose();
    super.dispose();
  }

  void _dispose() {}
}
