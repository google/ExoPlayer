// import 'package:flutter/material.dart';
// import 'package:flutter/services.dart';
// import 'package:flutterexodrmplayer/flutterexodrmplayer.dart';
// import 'package:flutterexodrmplayer/model/secured_video_content.dart';
// import 'package:lidaverse/model/media.dart';
// import 'package:lidaverse/model/video.dart';
// import 'package:lidaverse/widget/Player2.dart';
//
// import '../../PlayerPage.dart';
// import '../customOpenSource.dart';
//
// class MyHomePageDetail extends StatefulWidget {
//   // final String title;
//   final List<VideoPlayer> medias;
//
//   MyHomePageDetail({Key key,this.medias}) : super(key: key);
//
//   @override
//   _MyHomePageDetailState createState() => _MyHomePageDetailState();
// }
//
// class _MyHomePageDetailState extends State<MyHomePageDetail> {
//   bool dataReturned;
//   List<Video> video;
//
//   // Sample returnSample;
//
//   Future<List<Video>> loadVideoFiles() async {
//     String jsonString =
//         await rootBundle.loadString('assets/data/media.exolist.json');
//     setState(() {
//       dataReturned = true;
//       video = Video.parseVideoLists(jsonString);
//       // media = Media.parseMediaLists(jsonString);
//     });
//     return Video.parseVideoLists(jsonString);
//   }
//
//   VideoPlayerController _controller;
//
//   @override
//   void initState() {
//     // TODO: implement initState
//     super.initState();
//     dataReturned = false;
//     video = new List();
//     _controller = VideoPlayerController.exoplayerMeidaFrameWork(MediaContent(
//      // name: 'Google Play (MP4,H264)',
//       uri:
//           'https://www.youtube.com/api/manifest/dash/id/3aa39fa2cc27967f/source/youtube?as=fmp4_audio_clear,fmp4_sd_hd_clear&sparams=ip,ipbits,expire,source,id,as&ip=0.0.0.0&ipbits=0&expire=19000000000&signature=A2716F75795F5D2AF0E88962FFCD10DB79384F29.84308FF04844498CE6FBCE4731507882B8307798&key=ik0',
//     ));
//     loadVideoFiles();
//   }
//   SampleVideo returnSample;
//
// //   @override
// //   void initState() {
// //     super.initState();
// //     print('Màn Item ok ');
// //     _controller = VideoPlayerController.exoplayerMeidaFrameWork(MediaContent(
// //       name: 'Google Play (MP4,H264)',
// //       uri: 'https://www.youtube.com/api/manifest/dash/id/3aa39fa2cc27967f/source/youtube?as=fmp4_audio_clear,fmp4_sd_hd_clear&sparams=ip,ipbits,expire,source,id,as&ip=0.0.0.0&ipbits=0&expire=19000000000&signature=A2716F75795F5D2AF0E88962FFCD10DB79384F29.84308FF04844498CE6FBCE4731507882B8307798&key=ik0',
// //     ))
// //       ..initialize().then((_) {
// //         // Ensure the first frame is shown after the video is initialized, even before the play button has been pressed.
// //         setState(() {});
// //       });
// //
// // /*    _controller = VideoPlayerController.network(
// //         widget.sampleVideo.uri)
// //       ..initialize().then((_) {
// //         // Ensure the first frame is shown after the video is initialized, even before the play button has been pressed.
// //         setState(() {});
// //       });*/
// //     SystemChrome.setEnabledSystemUIOverlays([]);
// //     SystemChrome.setPreferredOrientations(
// //         [DeviceOrientation.landscapeLeft, DeviceOrientation.landscapeRight]);
// //   }
//
//   @override
//   Widget build(BuildContext context) {
//     return Container(
//        child: Player2(sampleVideo: ,);
//        // VideoPlayer(_controller,),
//
//     );
//     //   Column(
//     //   children: [
//     //     SizedBox(height: 30,),
//     //     Text('Xin chào'),
//     //     Padding(
//     //       padding: EdgeInsets.only(top: 200, left: 10, right: 10),
//     //       child: ListView.builder(
//     //         shrinkWrap: true,
//     //         primary: false,
//     //         itemCount: 1,
//     //         itemBuilder: (context, index) {
//     //           return Player2();
//     //         },
//     //       ),
//     //       // child: Player2(),
//     //     ),
//     //   ],
//     // );
//   }
//
// }
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:lidaverse/PlayerPage.dart';
import 'package:lidaverse/common/images/images.dart';
import 'package:lidaverse/common/theme/app_colors.dart';
import 'package:lidaverse/common/utils.dart';
import 'package:lidaverse/model/media.dart';
import 'package:lidaverse/widget/Player2.dart';
import 'package:lidaverse/widget/Player4.dart';
import 'package:lidaverse/widget/Player5.dart';
import 'package:lidaverse/widget/Playey3.dart';
import 'package:lidaverse/widget/custom_back_ground_paint.dart';
import 'package:lidaverse/widget/custom_header.dart';

// class MyCustomList extends StatelessWidget {
//   @override
//   Widget build(BuildContext context) {
//     final appTitle = 'VIDEO STREAMING ';
//
//     return MaterialApp(
//       title: appTitle,
//       debugShowCheckedModeBanner: false,
//       home: MyLisstVieo(),
//     );
//   }
// }
const painBack = BackCustomPainter();

class MyListVideo extends StatefulWidget {
  final String title;

  MyListVideo({Key key, this.title}) : super(key: key);

  @override
  _MyListVideoState createState() => _MyListVideoState();
}

class _MyListVideoState extends State<MyListVideo> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SingleChildScrollView(
        child: Column(
          children: [
            CustomPaint(
              size: Size(width, (width * 0.43997524752475247).toDouble()),
              //You can Replace [WIDTH] with your desired width for Custom Paint and height will be calculated automatically
              painter: painBack,
            ),
            CustomHeader(title: 'TRENDING'),
            SizedBox(
              height: 20,
            ),
            Container(height: height * 0.5, child: Player2()),
            SizedBox(
              height: 20,
            ),
            Container(height: height * 0.5, child: Player3()),
            SizedBox(
              height: 20,
            ),
            Container(height: height * 0.5, child: Player4()),
            SizedBox(
              height: 20,
            ),
            Container(height: height * 0.5, child: Player5()),
          ],
        ),
        //     ListView.separated(
        //         shrinkWrap: true,
        //         primary: false,
        //         itemBuilder: (context, index) => Player2(),
        //         separatorBuilder: (context, index) => SizedBox(
        //     width: 15,
        //   ),
        //   itemCount: 2,
        // ),
        //     // ListView.builder(
        //   itemCount: 2,
        //   itemBuilder: (context, index) {
        //     return Player2();
        //   },
        // ),
      ),
    );
  }
}
/// List link IPTV
//http://bom.to/360sportfunny (by Hoài Thanh)
//http://gg.gg/vyc3g (by Nguyen Xuan Dat)
