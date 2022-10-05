import 'package:flutter/material.dart';
import 'package:flutterexodrmplayer/flutterexodrmplayer.dart';
import 'package:flutterexodrmplayer/model/secured_video_content.dart';
import 'package:lidaverse/common/utils.dart';

class Player4 extends StatefulWidget {
  // SampleVideo sampleVideo;

  Player4({Key key}) : super(key: key);

  @override
  _PlayerState createState() => _PlayerState();
}

class _PlayerState extends State<Player4> {
  VideoPlayerController _controller;

  //String name;

  @override
  void initState() {
    super.initState();

    print('Màn Item ivdeo  ok ');

    String uri =
        'https://storage.googleapis.com/wvmedia/clear/h264/tears/tears.mpd';
    _controller = VideoPlayerController.exoplayerMeidaFrameWork(MediaContent(
      uri: uri,
    ))
      ..initialize().then((_) {
        setState(() {
          _controller.play();
        });
      });
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      width: width,
      child: Column(children: [
        Center(
          child: _controller.value.initialized
              ? AspectRatio(
                  aspectRatio: _controller.value.aspectRatio,
                  child: VideoPlayer(_controller),
                )
              : Container(),
        ),
        MediaVolumeSeekBar(_controller),
      ]),
    );
  }

  @override
  void dispose() {
    super.dispose();
    _controller.dispose();
  }
}
