import 'package:flutter/material.dart';
import 'package:flutterexodrmplayer/flutterexodrmplayer.dart';
import 'package:flutterexodrmplayer/model/secured_video_content.dart';
import 'package:lidaverse/common/utils.dart';

class Player2 extends StatefulWidget {
  // SampleVideo sampleVideo;

  Player2({Key key}) : super(key: key);

  @override
  _PlayerState createState() => _PlayerState();
}

class _PlayerState extends State<Player2> {
  VideoPlayerController _controller;

  //String name;

  @override
  void initState() {
    super.initState();

    print('Màn Item ivdeo  ok ');

    String uri =
        'https://www.youtube.com/api/manifest/dash/id/3aa39fa2cc27967f/source/youtube?as=fmp4_audio_clear,fmp4_sd_hd_clear&sparams=ip,ipbits,expire,source,id,as&ip=0.0.0.0&ipbits=0&expire=19000000000&signature=A2716F75795F5D2AF0E88962FFCD10DB79384F29.84308FF04844498CE6FBCE4731507882B8307798&key=ik0';
     // 'https://www.youtube.com/api/manifest/dash/id/bf5bb2419360daf1/source/youtube?as=fmp4_audio_clear,fmp4_sd_hd_clear&sparams=ip,ipbits,expire,source,id,as&ip=0.0.0.0&ipbits=0&expire=19000000000&signature=51AF5F39AB0CEC3E5497CD9C900EBFEAECCCB5C7.8506521BFC350652163895D4C26DEE124209AA9E&key=ik0';
    _controller = VideoPlayerController.exoplayerMeidaFrameWork(MediaContent(
      // name: widget.sampleVideo.name,
      // uri: widget.sampleVideo.uri,
      uri: uri,
      // 'https://www.youtube.com/api/manifest/dash/id/bf5bb2419360daf1/source/youtube?as=fmp4_audio_clear,fmp4_sd_hd_clear&sparams=ip,ipbits,expire,source,id,as&ip=0.0.0.0&ipbits=0&expire=19000000000&signature=51AF5F39AB0CEC3E5497CD9C900EBFEAECCCB5C7.8506521BFC350652163895D4C26DEE124209AA9E&key=ik0',
    ))
      ..initialize().then((_) {
        // Ensure the first frame is shown after the video is initialized, even before the play button has been pressed.
        setState(() {
          _controller.play();
        });
      });

/*    _controller = VideoPlayerController.network(
        widget.sampleVideo.uri)
      ..initialize().then((_) {
        // Ensure the first frame is shown after the video is initialized, even before the play button has been pressed.
        setState(() {});
      });*/
    // SystemChrome.setEnabledSystemUIOverlays([]);
    // SystemChrome.setPreferredOrientations(
    //     [DeviceOrientation.landscapeLeft, DeviceOrientation.landscapeRight]);
  }

  @override
  Widget build(BuildContext context) {
    return
        //Scaffold(
        // appBar: AppBar(
        //   title: Text('Video'),
        //   backgroundColor: AppColors.blue,
        //   toolbarHeight: 40,
        //   leading: SvgPicture.asset(
        //     Images.img_set,
        //     color: AppColors.white,
        //     alignment: Alignment.center,
        //   ),
        // ),
        Container(
      // height: height/3*1/2 ,
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
    // floatingActionButton: FloatingActionButton(
    //   onPressed: () {
    //     setState(() {
    //       _controller.value.isPlaying
    //           ? _controller.pause()
    //           : _controller.play();
    //     });
    //   },
    //   child: Icon(
    //     _controller.value.isPlaying ? Icons.pause : Icons.play_arrow,
    //   ),
    // ),
    // );

    //     MaterialApp(
    //   title: 'Video Demo',
    //   home: Scaffold(
    //     body: Stack(children: [
    //       Center(
    //         child: _controller.value.initialized
    //             ? AspectRatio(
    //                 aspectRatio: _controller.value.aspectRatio,
    //                 child: VideoPlayer(_controller),
    //               )
    //             : Container(),
    //       ),
    //       MediaVolumeSeekBar(_controller),
    //     ]),
    //     floatingActionButton: FloatingActionButton(
    //       onPressed: () {
    //         setState(() {
    //           _controller.value.isPlaying
    //               ? _controller.pause()
    //               : _controller.play();
    //         });
    //       },
    //       child: Icon(
    //         _controller.value.isPlaying ? Icons.pause : Icons.play_arrow,
    //       ),
    //     ),
    //   ),
    // );
  }

  @override
  void dispose() {
    super.dispose();
    _controller.dispose();
  }
}

// class _PlayerState extends State<Player> {
//   static const int kStartValue = 4;
//   VideoPlayerController _controller;
//   bool _isPlaying = false;
//   bool _showController = false;
//   bool _isControllerVisible = false;
//   Timer _timer;
//   double actualRatio, fullScreenRatio, aspectRatio, prevVolume = 0.0;
//   List<PlaybackValues> playbackValues = [
//     PlaybackValues("0.25x", 0.25),
//     PlaybackValues("0.50x", 0.50),
//     PlaybackValues("0.75x", 0.75),
//     PlaybackValues("Normal", 1.00),
//     PlaybackValues("1.25x", 1.25),
//     PlaybackValues("1.50x", 1.50),
//     PlaybackValues("1.75x", 1.75),
//     PlaybackValues("2.00x", 2.00),
//   ];
//   PlaybackValues selectedPlayback;
//   ResolutionValues selectedResolution;
//   AudioValues selectedAudio;
//   SubtitleValues selectedSubtitle;
//   int playerPosition, playerDuration;
//   Timer _controlTimer;
//   Stopwatch _stopwatch;
//   int orientation = 1;
//   SubtitleController _subtitleController;
//
//   // @override
//   // void initState() {
//   //   super.initState();
//   //   selectedPlayback = new PlaybackValues("Normal", 1.00);
//   //   _controller = VideoPlayerController.exoplayerMeidaFrameWork(MediaContent(
//   //     uri: "https://5dcc3fe0c5e90.streamlock.net/vod/_definst_/mp4:ishakua_soo.mp4/manifest.mpd",
//   //   ))
//   //     ..initialize().then((_) {
//   //       if (_controller.value.initialized) {
//   //         setState(() {
//   //           playerPosition = widget.sampleVideo.playedLength;
//   //           _controller
//   //               .seekTo(Duration(milliseconds: playerPosition));
//   //         });
//   //         _controller.play();
//   //       }
//   //       actualRatio = _controller.value.aspectRatio;
//   //       aspectRatio = actualRatio;
//   //       setState(() {});
//   //     });
//   // }
//   @override
//   void initState() {
//     super.initState();
//
//     _controller = VideoPlayerController.exoplayerMeidaFrameWork(MediaContent(
//       name: widget.sampleVideo.name,
//       uri: widget.sampleVideo.uri,
//       extension: widget.sampleVideo.extension,
//       drm_scheme: widget.sampleVideo.drm_scheme,
//       drm_license_url: widget.sampleVideo.drm_license_url,
//       ad_tag_uri: widget.sampleVideo.ad_tag_uri,
//       spherical_stereo_mode: widget.sampleVideo.spherical_stereo_mode,
//       playlist: widget.sampleVideo.playlist,
//     ))
//       ..initialize().then((_) {
//         // Ensure the first frame is shown after the video is initialized, even before the play button has been pressed.
//         setState(() {});
//       });
//
// /*    _controller = VideoPlayerController.network(
//         widget.sampleVideo.uri)
//       ..initialize().then((_) {
//         // Ensure the first frame is shown after the video is initialized, even before the play button has been pressed.
//         setState(() {});
//       });*/
//     SystemChrome.setEnabledSystemUIOverlays([]);
//     SystemChrome.setPreferredOrientations(
//         [DeviceOrientation.landscapeLeft, DeviceOrientation.landscapeRight]);
//   }
//   @override
//   Widget build(BuildContext context) {
//     double volume;
//     hideControls() {
//       setState(() {
//         _showController = false;
//         _isControllerVisible = false;
//       });
//     }
//
//     showControls({bool hide}) {
//       bool toHide = hide ?? true;
//       setState(() {
//         _showController = true;
//         _isControllerVisible = true;
//         if (_timer != null) _timer.cancel();
//       });
//       if (toHide) {
//         _timer = new Timer(const Duration(seconds: 4), () {
//           hideControls();
//         });
//       }
//     }
//
//     _controller.addListener(() {
//       final bool isPlaying = _controller.value.isPlaying;
//       if (isPlaying != _isPlaying) {
//         setState(() {
//           _isPlaying = isPlaying;
//           playerPosition = _controller.value.position.inMilliseconds;
//           playerDuration = _controller.value.duration.inMilliseconds;
//         });
//         _stopwatch = new Stopwatch();
//         _stopwatch.start();
//         _controlTimer = new Timer.periodic(Duration(seconds: 1), (Timer timer) {
//           if (playerPosition >= playerDuration) {
//             setState(() {
//               playerPosition = 0;
//               _controller.seekTo(Duration(milliseconds: 0));
//               _controller.pause();
//               _stopwatch.stop();
//               showControls();
//             });
//           } else {
//             setState(() {
//               if (_stopwatch.isRunning) {
//                 if (playerPosition <= playerDuration) {
//                   if ((_controller.value.position.inMilliseconds + 1000) <
//                       playerDuration)
//                     playerPosition =
//                         _controller.value.position.inMilliseconds + 1000;
//                   else
//                     playerPosition =
//                         (_controller.value.position.inMilliseconds +
//                             (playerDuration - playerPosition));
//                 } else {
//                   playerPosition = 0;
//                   _controller.seekTo(Duration(milliseconds: 0));
//                   showControls();
//                 }
//               }
//             });
//           }
//         });
//       }
//     });
//
//     SystemChrome.setEnabledSystemUIOverlays([]);
// //    Screen.keepOn(true);
//     double screenWidth = MediaQuery.of(context).size.width;
//     double screenHeight = MediaQuery.of(context).size.height;
//     fullScreenRatio = screenWidth / screenHeight;
//
//     Future<bool> _onBackPressed() {
//       Sample sample = new Sample(
//           name: widget.sampleVideo.name,
//           ad_tag_uri: widget.sampleVideo.ad_tag_uri,
//           drm_license_url: widget.sampleVideo.drm_license_url,
//           drm_scheme: widget.sampleVideo.drm_scheme,
//           extension: widget.sampleVideo.extension,
//           playedLength: playerPosition,
//           playlist: widget.sampleVideo.playlist,
//           spherical_stereo_mode: widget.sampleVideo.spherical_stereo_mode,
//           uri: widget.sampleVideo.uri);
//       Navigator.pop(context, sample);
//       if (_timer != null) _timer.cancel();
//       if (_controlTimer != null) _controlTimer.cancel();
//       _stopwatch.stop();
//       return Future.value(false);
//     }
//
//     Widget volumeIcon(IconData icon) {
//       return InkWell(
//           child: Icon(
//             icon,
//             color: Colors.white,
//           ),
//           onTap: () {
//             if (volume > 0) {
//               setState(() {
//                 prevVolume = volume;
//                 _controller.setVolume(0);
//               });
//             } else {
//               setState(() {
//                 _controller.setVolume(prevVolume);
//               });
//             }
//             showControls();
//           });
//     }
//
//     Widget progressBar(val) {
//       return Slider(
//         value: val,
//         min: 0.0,
//         max: 1.0,
//         activeColor: Colors.green,
//         inactiveColor: Colors.white,
//         onChanged: (double value) {
//           setState(() {
//             if (!_controller.value.initialized) {
//               return;
//             }
//             playerPosition = (playerDuration * value).round();
//             _controller.seekTo(Duration(milliseconds: playerPosition));
//             showControls();
//           });
//         },
//         onChangeStart: (double value) {
//           showControls(hide: false);
//           _stopwatch.stop();
//           if (!_controller.value.initialized) {
//             return;
//           }
//         },
//         onChangeEnd: (double value) {
//           if (!_stopwatch.isRunning) {
//             _stopwatch.start();
//           }
//           showControls();
//         },
//       );
//     }
//
//     void _settingModalBottomSheetPlayback(context) {
//       showModalBottomSheet(
//           isScrollControlled: true,
//           context: context,
//           builder: (BuildContext bc) {
//             return Container(
//                 child: new Wrap(
//               children: playbackValues
//                   .map((value) => InkWell(
//                         child: Padding(
//                           padding: const EdgeInsets.all(5.0),
//                           child: Row(
//                             children: <Widget>[
//                               Icon(Icons.check,
//                                   color: selectedPlayback.name == value.name
//                                       ? Colors.black
//                                       : Colors.transparent),
//                               SizedBox(
//                                 width: 10,
//                               ),
//                               Text(value.name),
//                             ],
//                           ),
//                         ),
//                         onTap: () {
//                           setState(() {
//                             selectedPlayback = value;
//                           });
//                           Navigator.pop(context);
//                           _controller.setSpeed(value.value);
//                         },
//                       ))
//                   .toList(),
//             ));
//           });
//     }
//
//     void _settingModalBottomSheet(context) {
//       showModalBottomSheet(
//           isScrollControlled: true,
//           context: context,
//           builder: (BuildContext bc) {
//             return Container(
//               child: new Wrap(
//                 children: <Widget>[
//                   InkWell(
//                     child: Padding(
//                       padding: EdgeInsets.only(
//                           left: 5, top: 10, bottom: 10, right: 5),
//                       child: Row(
//                         children: <Widget>[
//                           Icon(Icons.play_circle_filled),
//                           SizedBox(
//                             width: 10,
//                           ),
//                           Text('Playback Speed : ${selectedPlayback.name}'),
//                         ],
//                       ),
//                     ),
//                     onTap: () {
//                       Navigator.pop(context);
//                       _settingModalBottomSheetPlayback(context);
//                     },
//                   ),
//                   if (selectedResolution != null)
//                     InkWell(
//                       child: Padding(
//                         padding: EdgeInsets.only(
//                             left: 5, top: 10, bottom: 10, right: 5),
//                         child: Row(
//                           children: <Widget>[
//                             Icon(Icons.high_quality),
//                             SizedBox(
//                               width: 10,
//                             ),
//                             Text('Quality : ${selectedResolution.value}'),
//                           ],
//                         ),
//                       ),
//                       onTap: () {
//                         Navigator.pop(context);
//                       },
//                     ),
//                 ],
//               ),
//             );
//           });
//     }
//
//     Widget videoPlayerControls() {
//       double val = playerPosition.toDouble() / playerDuration.toDouble();
//       int maxBuffering = 0;
//       for (DurationRange range in _controller.value.buffered) {
//         final int end = range.end.inMilliseconds;
//         if (end > maxBuffering) {
//           maxBuffering = end;
//         }
//       }
//       volume = _controller.value.volume;
//       _isControllerVisible = true;
//       return GestureDetector(
//         child: Container(
//             color: Colors.black.withOpacity(0.3),
//             child: Center(
//                 child: Column(
//               children: <Widget>[
//                 Row(
//                   children: <Widget>[
//                     Align(
//                         child: InkWell(
//                             onTap: _onBackPressed,
//                             child: Padding(
//                                 padding: EdgeInsets.all(10.0),
//                                 child: Icon(
//                                   Icons.arrow_back_ios,
//                                   color: Colors.white,
//                                 ))),
//                         alignment: Alignment.topLeft),
//                     Align(
//                       child: Text(
//                         widget.sampleVideo.name,
//                         overflow: TextOverflow.ellipsis,
//                         maxLines: 1,
//                         style: TextStyle(color: Colors.white),
//                       ),
//                       alignment: Alignment.center,
//                     ),
//                     Align(
//                         child: InkWell(
//                             onTap: () {
//                               _settingModalBottomSheet(context);
//                             },
//                             child: Padding(
//                                 padding: EdgeInsets.all(10.0),
//                                 child: Icon(
//                                   Icons.settings,
//                                   color: Colors.white,
//                                 ))),
//                         alignment: Alignment.topRight),
//                   ],
//                   mainAxisAlignment: MainAxisAlignment.spaceBetween,
//                 ),
//                 Expanded(
//                     child: Stack(
//                   children: <Widget>[
//                     Align(
//                         alignment: Alignment.center,
//                         child: Row(
//                           crossAxisAlignment: CrossAxisAlignment.center,
//                           mainAxisAlignment: MainAxisAlignment.center,
//                           children: <Widget>[
//                             Wrap(
//                               spacing: 30.0,
//                               children: <Widget>[
//                                 InkWell(
//                                   child: Container(
//                                     child: Center(
//                                         child: Icon(
//                                       Icons.replay_10,
//                                       color: Colors.white,
//                                       size: 40.0,
//                                     )),
//                                     height: 70.0,
//                                   ),
//                                   onTap: () {
//                                     setState(() {
//                                       if ((playerPosition - 10000) > 0) {
//                                         playerPosition = playerPosition - 10000;
//                                       } else {
//                                         playerPosition =
//                                             (playerPosition) - playerPosition;
//                                       }
//                                       _controller.seekTo(Duration(
//                                           milliseconds: playerPosition));
//                                     });
//                                     showControls();
//                                   },
//                                 ),
//                                 InkWell(
//                                     onTap: () {
//                                       setState(() {
//                                         if (_controller.value.isPlaying) {
//                                           _controller.pause();
//                                           _stopwatch.stop();
//                                           showControls(hide: false);
//                                         } else {
//                                           _controller.play();
//                                           _stopwatch.start();
//                                           hideControls();
//                                         }
//                                       });
//                                     },
//                                     child: Icon(
//                                       _controller.value.isPlaying
//                                           ? Icons.pause_circle_outline
//                                           : Icons.play_circle_outline,
//                                       color: Colors.white,
//                                       size: 70.0,
//                                     )),
//                                 InkWell(
//                                   child: Container(
//                                     child: Center(
//                                         child: Icon(
//                                       Icons.forward_10,
//                                       color: Colors.white,
//                                       size: 40.0,
//                                     )),
//                                     height: 70.0,
//                                   ),
//                                   onTap: () {
//                                     setState(() {
//                                       if ((playerPosition + 10000) <
//                                           playerDuration) {
//                                         playerPosition = playerPosition + 10000;
//                                       } else {
//                                         playerPosition = (playerPosition) +
//                                             (playerDuration - playerPosition);
//                                       }
//                                       _controller.seekTo(Duration(
//                                           milliseconds: playerPosition));
//                                     });
//                                     showControls();
//                                   },
//                                 )
//                               ],
//                             )
//                           ],
//                         )),
//                     Positioned(
//                       right: 0,
//                       bottom: 10,
//                       top: 10,
//                       child: Column(
//                         mainAxisSize: MainAxisSize.max,
//                         crossAxisAlignment: CrossAxisAlignment.center,
//                         mainAxisAlignment: MainAxisAlignment.center,
//                         children: <Widget>[
//                           RotatedBox(
//                               quarterTurns: -1,
//                               child: Slider(
//                                 value: _controller.value.volume,
//                                 min: 0.0,
//                                 max: 1.0,
//                                 activeColor: Colors.green,
//                                 inactiveColor: Colors.white,
//                                 onChanged: (double value) {
//                                   setState(() {
//                                     if (!_controller.value.initialized) {
//                                       return;
//                                     }
//                                     _controller.setVolume(value);
//                                     showControls();
//                                   });
//                                 },
//                                 onChangeStart: (double value) {
//                                   showControls(hide: false);
//                                   if (!_controller.value.initialized) {
//                                     return;
//                                   }
//                                 },
//                                 onChangeEnd: (double value) {
//                                   setState(() {
//                                     volume = value;
//                                   });
//                                   showControls();
//                                 },
//                               )),
//                           if (volume >= 0.6) volumeIcon(Icons.volume_up),
//                           if (volume >= 0.3 && volume < 0.6)
//                             volumeIcon(Icons.volume_down),
//                           if (volume < 0.3 && volume >= 0.01)
//                             volumeIcon(Icons.volume_mute),
//                           if (volume < 0.01) volumeIcon(Icons.volume_off),
//                         ],
//                       ),
//                     )
//                   ],
//                 )),
//                 Align(
//                   child: Padding(
//                       padding: EdgeInsets.only(left: 20.0, right: 20.0),
//                       child: Row(
//                         mainAxisAlignment: MainAxisAlignment.spaceBetween,
//                         children: <Widget>[
//                           Text(
//                             getTime(playerPosition),
//                             style: TextStyle(color: Colors.white),
//                           ),
//                           Expanded(child: progressBar(val)),
//                           Text(
//                             getTime(playerDuration),
//                             style: TextStyle(color: Colors.white),
//                           ),
//                           InkWell(
//                               onTap: () {
//                                 if (orientation == 1) {
//                                   SystemChrome.setPreferredOrientations([
//                                     DeviceOrientation.landscapeRight,
//                                     DeviceOrientation.landscapeLeft,
//                                   ]);
//                                   setState(() {
//                                     orientation = 2;
//                                   });
//                                 } else {
//                                   SystemChrome.setPreferredOrientations([
//                                     DeviceOrientation.portraitUp,
//                                   ]);
//                                   setState(() {
//                                     orientation = 1;
//                                   });
//                                 }
//                               },
//                               child: Padding(
//                                   padding: EdgeInsets.only(left: 10.0),
//                                   child: Icon(
//                                     orientation == 2
//                                         ? Icons.fullscreen_exit
//                                         : Icons.fullscreen,
//                                     color: Colors.white,
//                                   )))
//                         ],
//                       )),
//                   alignment: Alignment.bottomCenter,
//                 )
//               ],
//             ))),
//         onTap: () {
//           if (_showController) {
//             hideControls();
//           }
//         },
//         onDoubleTap: () {
//           if (aspectRatio == actualRatio) {
//             setState(() {
//               aspectRatio = fullScreenRatio;
//             });
//           } else if (aspectRatio == fullScreenRatio) {
//             setState(() {
//               aspectRatio = actualRatio;
//             });
//           }
//         },
//       );
//     }
//
//     return WillPopScope(
//         onWillPop: _onBackPressed,
//         child: MaterialApp(
//           debugShowCheckedModeBanner: false,
//           title: 'Video Demo',
//           home: Scaffold(
//               body: Container(
//             decoration: BoxDecoration(color: Colors.black),
//             child: Stack(
//               fit: StackFit.expand,
//               children: <Widget>[
//                 _controller.value.initialized
//                     ? GestureDetector(
//                         child: Container(
//                             width: screenWidth,
//                             height: screenHeight,
//                             color: Colors.transparent,
//                             child: Center(
//                               child: AspectRatio(
//                                   aspectRatio: aspectRatio,
//                                   child: Card(
//                                     elevation: 2.0,
//                                     color: Colors.transparent,
//                                     child: VideoPlayer(_controller),
//                                   )),
//                             )),
//                         onTap: () {
//                           setState(() {
//                             if (!_showController) {
//                               showControls();
//                             }
//                           });
//                         },
//                         onDoubleTap: () {
//                           if (aspectRatio == actualRatio) {
//                             setState(() {
//                               aspectRatio = fullScreenRatio;
//                             });
//                           } else if (aspectRatio == fullScreenRatio) {
//                             setState(() {
//                               aspectRatio = actualRatio;
//                             });
//                           }
//                         },
//                       )
//                     : Center(
//                         child: Row(
//                         mainAxisAlignment: MainAxisAlignment.center,
//                         crossAxisAlignment: CrossAxisAlignment.center,
//                         children: <Widget>[
//                           CircularProgressIndicator(),
//                           Padding(
//                               padding: EdgeInsets.only(left: 10.0),
//                               child: Text(
//                                 "Loading...",
//                                 style: TextStyle(
//                                     color: Colors.white, fontSize: 20.0),
//                               ))
//                         ],
//                       )),
//                 _controller.value.isBuffering
//                     ? CircularProgressIndicator()
//                     : new Container(),
//                 if (_showController) videoPlayerControls(),
//                 if(_subtitleController != null)
//                 Positioned(
//                   bottom: 50,
//                   left: 0,
//                   right: 0,
//                   child:
// //                      Text("hello", style: TextStyle(color: Colors.green), textAlign: TextAlign.center,),
//                       SubtitleTextView(
//                     subtitleController: _subtitleController,
//                     videoPlayerController: _controller,
//                     subtitleStyle: SubtitleStyle(
//                         fontSize: 16, textColor: Colors.white, hasBorder: true),
//                   ),
//                 )
//               ],
//             ),
//           )),
//         ));
//   }
//
//   @override
//   void dispose() {
//     _controller.dispose();
//     if (_timer != null) _timer.cancel();
//     if (_controlTimer != null) _controlTimer.cancel();
//     _stopwatch.stop();
//     super.dispose();
//   }
//
//   String getTime(int milis) {
//     Duration position = Duration(milliseconds: milis);
//     String twoDigits(int n) {
//       if (n >= 10) return "$n";
//       return "0$n";
//     }
//
//     String twoDigitMinutes = twoDigits(position.inMinutes.remainder(60));
//     String twoDigitSeconds = twoDigits(position.inSeconds.remainder(60));
//     String time;
//     if (twoDigits(position.inHours) == "00") {
//       time = "$twoDigitMinutes:$twoDigitSeconds";
//     } else {
//       time = "${twoDigits(position.inHours)}:$twoDigitMinutes:$twoDigitSeconds";
//     }
//     return time;
//   }
// }
//
// class PlaybackValues {
//   String name;
//   double value;
//
//   PlaybackValues(this.name, this.value);
// }
//
// class ResolutionValues {
//   int width;
//   int height;
//   String value;
//
//   ResolutionValues(this.width, this.height, this.value);
// }
//
// class AudioValues {
//   String name;
//   String code;
//
//   AudioValues(this.name, this.code);
// }
//
// class SubtitleValues {
//   String url;
//   String name;
//   SubtitleType type;
//
//   SubtitleValues(this.url, this.name, this.type);
// }
