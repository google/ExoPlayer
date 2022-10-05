import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:lidaverse/PlayerPage.dart';
import 'package:lidaverse/common/theme/app_colors.dart';
import 'package:lidaverse/common/theme/app_dimens.dart';
import 'package:lidaverse/common/utils.dart';
import 'package:lidaverse/model/media.dart';
import 'package:lidaverse/widget/custom_back_ground_paint.dart';
import 'package:lidaverse/widget/custom_header.dart';

//import 'model/media.dart';
//
// class MyCustomList extends StatelessWidget {
//   @override
//   Widget build(BuildContext context) {
//     final appTitle = 'Lidaverse';
//
//     return MaterialApp(
//       title: appTitle,
//       debugShowCheckedModeBanner: false,
//       home: MyHomePage(title: appTitle),
//     );
//   }
// }
//
// class MyHomePage extends StatefulWidget {
//   final String title;
//
//   MyHomePage({Key key, this.title}) : super(key: key);
//
//   @override
//   _MyHomePageState createState() => _MyHomePageState();
// }
//
// class _MyHomePageState extends State<MyHomePage> {
//   bool dataReturned;
//   List<Media> media;
//
//   Future<List<Media>> loadMediaFiles() async {
//     String jsonString =
//         await rootBundle.loadString('assets/data/media.exolist.json');
//     setState(() {
//       dataReturned = true;
//       media = Media.parseMediaLists(jsonString);
//     });
//     return Media.parseMediaLists(jsonString);
//   }
//
//   @override
//   void initState() {
//     // TODO: implement initState
//     super.initState();
//     dataReturned = false;
//     media = new List();
//     loadMediaFiles();
//   }
//
//   @override
//   Widget build(BuildContext context) {
//     SystemChrome.setPreferredOrientations([
//       DeviceOrientation.portraitUp,
//     ]);
//
//     return Scaffold(
//       appBar: AppBar(
//         title: Text(widget.title),
//         backgroundColor: AppColors.blue,
//         toolbarHeight: 40,
//         leading: SvgPicture.asset(
//           Images.img_set,
//           color: AppColors.white,
//           alignment: Alignment.center,
//         ),
//       ),
//       body:
//           //!dataReturned
//           //?
//           /* FutureBuilder<List<Media>>(
//         future: loadMediaFiles(),
//         builder: (context, snapshot) {
//           if (snapshot.hasError) print(snapshot.error);
//           return snapshot.hasData
//               ? PhotosList(medias: snapshot.data)
//               : Center(child: CircularProgressIndicator());
//         },
//       )*/
//           // Container()
//           // :
//           Container(height: height,
//               child: PhotosList(medias: media)),
//     );
//   }
// }
//
// class PhotosList extends StatelessWidget {
//   final List<Media> medias;
//
//   PhotosList({Key key, this.medias}) : super(key: key);
//
//   @override
//   Widget build(BuildContext context) {
//     return ListView.builder(
//       itemCount: medias.length,
//       itemBuilder: (context, index) {
//         return StuffInTiles(medias[index], index, medias);
//       },
//     );
//   }
// }
//
// class StuffInTiles extends StatefulWidget {
//   final Media myTile;
//   final List<Media> medias;
//   final int index;
//   BuildContext _context;
//
//   StuffInTiles(this.myTile, this.index, this.medias);
//
//   @override
//   _StuffInTilesState createState() => _StuffInTilesState();
// }
//
// class _StuffInTilesState extends State<StuffInTiles> {
//   Sample returnSample;
//
//   Widget _buildSubTiles(Sample t) {
//     return Row(
//       children: [
//         // SizedBox(
//         //     height: height * 0.02,
//         //     width: width * 0.02,
//         //     child: Expanded(child: SvgPicture.asset(Images.img_fileItem))),
//         SizedBox(
//           child: ListTile(
//               dense: true,
//               enabled: true,
//               isThreeLine: false,
//              // onLongPress: () => print("long press"),
//               onTap: () {
//                 // print("url ${t.uri ?? ""}");
//                 // print("url ${t.drm_license_url ?? ""}");
//                 // print("url ${t.playedLength ?? ""}");
//
//                 // Sample result = await Navigator.push(
//                 //     widget._context,
//                 //     MaterialPageRoute(
//                 //         builder: (_) => Player(
//                 //               sampleVideo: returnSample != null
//                 //                   ? returnSample.name == t.name
//                 //                       ? returnSample
//                 //                       : t
//                 //                   : t,
//                 //             )));
//                 Navigator.push(context, MaterialPageRoute(
//                     builder: (context) => Player(sampleVideo: t,)),);
//
//               },
//
//                 // await Navigator.push(
//                 //     context,
//                 //     MaterialPageRoute(
//                 //         builder: (_) => Player(
//                 //           sampleVideo: t,
//                 //         )));
//                 // SystemChrome.setEnabledSystemUIOverlays(SystemUiOverlay.values);
//                 // SystemChrome.setPreferredOrientations([
//                 //   DeviceOrientation.portraitUp,
//                 //   DeviceOrientation.portraitDown,
//                 // ]);
//
// //                 if (result != null) {
// // //            SystemChrome.setEnabledSystemUIOverlays(SystemUiOverlay.values);
// //                   print("back from player");
// //                   setState(() {
// //                     returnSample = result;
// //                     print(t.playedLength);
// //                     SystemChrome.setPreferredOrientations([
// //                       DeviceOrientation.portraitUp,
// //                     ]);
// //                   });
// //                 }
// //               },
//               subtitle: Text(
//                 // 'test',
//                 t.extension ?? "",
//               ),
//               selected: true,
//               title: Text(
//                   // 'test' ,
//                   t.name,
//                   style: TextStyle(fontSize: 18.0, color: Colors.black54))),
//         ),
//       ],
//     );
//   }
//
//   Widget _buildTiles(Media t) {
//     return ExpansionTile(
//       key: PageStorageKey<int>(3),
//       title: Text(t.name),
//       children: t.samples.map(_buildSubTiles).toList(),
//     );
//   }
//
//   @override
//   Widget build(BuildContext context) {
//     widget._context = context;
//     return _buildTiles(widget.myTile);
//   }
// }

//ChewieDemo(sampleVideo: t,)
const painBack = BackCustomPainter();

class MyHomePageForScreen extends StatefulWidget {
  final String title;

  MyHomePageForScreen({Key key, this.title}) : super(key: key);

  @override
  _MyHomePageState createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePageForScreen> {
  bool dataReturned;
  List<Media> media;

  Future<List<Media>> loadMediaFiles() async {
    String jsonString =
        await rootBundle.loadString('assets/data/media.exolist.json');
    setState(() {
      dataReturned = true;
      media = Media.parseMediaLists(jsonString);
    });
    return Media.parseMediaLists(jsonString);
  }

  @override
  void initState() {
    // TODO: implement initState
    super.initState();
    dataReturned = false;
    media = new List();
    loadMediaFiles();
  }

  @override
  Widget build(BuildContext context) {
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.portraitUp,
    ]);

    return MaterialApp(
      // appBar: AppBar(
      //   title: Text('Lidaverse'),
      //   backgroundColor: AppColors.blue,
      //   leading: Padding(
      //     padding: EdgeInsets.only(left: 6,top: 4,bottom: 4),
      //     child: SvgPicture.asset(
      //       Images.img_set,
      //       color: AppColors.white,
      //       alignment: Alignment.center,
      //     ),
      //   ),
      // ),
      home: !dataReturned
          ? /* FutureBuilder<List<Media>>(
        future: loadMediaFiles(),
        builder: (context, snapshot) {
          if (snapshot.hasError) print(snapshot.error);
          return snapshot.hasData
              ? PhotosList(medias: snapshot.data)
              : Center(child: CircularProgressIndicator());
        },
      )*/
          Container()
          : PhotosList(medias: media),
    );
  }
}

class PhotosList extends StatelessWidget {
  final List<Media> medias;

  PhotosList({Key key, this.medias}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        /// Paint
        CustomPaint(
          size: Size(width, (width * 0.43997524752475247).toDouble()),
          //You can Replace [WIDTH] with your desired width for Custom Paint and height will be calculated automatically
          painter: painBack,
        ),
        Column(
          children: [
            Padding(padding: EdgeInsets.only(top: 50)),
            Center(
              child: CircleAvatar(
                radius: (AppDimens.space50 + AppDimens.space30) / 2,
                backgroundImage: NetworkImage(
                    'https://user-images.githubusercontent.com/8813505/113346085-672a2e00-9301-11eb-84a6-c6d55adc3608.png'),
              ),
            ),
            SizedBox(
              height: 10,
            ),
            Expanded(
                child: Column(
              children: [
                Text(
                  'Wellcome To Exoplayer Play Video Stream',
                  style: TextStyle(
                    fontSize: AppDimens.padding16,
                    color: AppColors.primary,
                  ),
                  textAlign: TextAlign.center,
                ),
                SizedBox(
                  height: 17,
                ),
                CustomHeader(title: 'TẤT CẢ'),
              ],
            )),
          ],
        ),

        Padding(
          padding: EdgeInsets.only(top: 200, left: 10, right: 10),
          child: ListView.builder(
            itemCount: medias.length,
            itemBuilder: (context, index) {
              return StuffInTiles(medias[index], index, medias);
            },
          ),

        ),
      ],
    );
  }
}

class StuffInTiles extends StatefulWidget {
  final Media myTile;
  final List<Media> medias;
  final int index;
  BuildContext _context;

  StuffInTiles(this.myTile, this.index, this.medias);

  @override
  _StuffInTilesState createState() => _StuffInTilesState();
}

class _StuffInTilesState extends State<StuffInTiles> {
  Sample returnSample;

  Widget _buildSubTiles(Sample t) {
    return ListTile(
        dense: true,
        enabled: true,
        isThreeLine: false,
        onLongPress: () => print("long press"),
        onTap: () async {
          print("url ${t.uri ?? ""}");
          print("drm_license_url ${t.drm_license_url ?? ""}");
          print("playedLength ${t.playedLength ?? ""}");
          print("name ${t.name}");
          Sample result = await Navigator.push(
              widget._context,
              MaterialPageRoute(
                  builder: (_) => Player(
                        sampleVideo: returnSample != null
                            ? returnSample.name == t.name
                                ? returnSample
                                : t
                            : t,
                      )));

          if (result != null) {
//            SystemChrome.setEnabledSystemUIOverlays(SystemUiOverlay.values);
            print("back from player");
            setState(() {
              returnSample = result;
              print(t.playedLength);
              SystemChrome.setPreferredOrientations([
                DeviceOrientation.portraitUp,
              ]);
            });
          }
        },
        subtitle: Text(
          t.extension ?? "",
        ),
        selected: true,
        title: Text(t.name,
            style: TextStyle(fontSize: 18.0, color: Colors.black54)));
  }

  Widget _buildTiles(Media t) {
    return ExpansionTile(
      key: PageStorageKey<int>(3),
      title: Text(t.name),
      children: t.samples.map(_buildSubTiles).toList(),
    );
  }

  @override
  Widget build(BuildContext context) {
    widget._context = context;
    return _buildTiles(widget.myTile);
  }
}
