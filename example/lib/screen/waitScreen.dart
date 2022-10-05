// import 'dart:async';
//
// import 'package:flutter/foundation.dart';
// import 'package:flutter/material.dart';
// import 'package:flutter/services.dart';
// import 'package:flutterexodrmplayer_example/PlayerPage.dart';
//
// import 'model/media.dart';
//
// void main() => runApp(MyApp());
// ///ok
// class MyApp extends StatelessWidget {
//   @override
//   Widget build(BuildContext context) {
//     final appTitle = 'Flutter Exoplayer (Beta)';
//
//     return MaterialApp(
//       title: appTitle,
//       debugShowCheckedModeBanner: false,
//       home: MyHomePage(title: appTitle),
//     );
//   }
// }
// ///ok
// class MyHomePage extends StatefulWidget {
//   final String title;
//
//   MyHomePage({Key key, this.title}) : super(key: key);
//
//   @override
//   _MyHomePageState createState() => _MyHomePageState();
// }
// ///ok
// class _MyHomePageState extends State<MyHomePage> {
//   bool dataReturned;
//   List<Media> media;
//
//   Future<List<Media>> loadMediaFiles() async {
//     String jsonString =
//     await rootBundle.loadString('assets/media.exolist.json');
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
//       ),
//       body: !dataReturned
//           ? /* FutureBuilder<List<Media>>(
//         future: loadMediaFiles(),
//         builder: (context, snapshot) {
//           if (snapshot.hasError) print(snapshot.error);
//           return snapshot.hasData
//               ? PhotosList(medias: snapshot.data)
//               : Center(child: CircularProgressIndicator());
//         },
//       )*/
//       Container()
//           : PhotosList(medias: media),
//     );
//   }
// }
// ///ok
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
// ///ok
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
// ///
// class _StuffInTilesState extends State<StuffInTiles> {
//   Sample returnSample;
//   Widget _buildSubTiles(Sample t) {
//     return ListTile(
//         dense: true,
//         enabled: true,
//         isThreeLine: false,
//         onLongPress: () => print("long press"),
//         onTap: () async {
//           print("url ${t.uri ?? ""}");
//           print("url ${t.drm_license_url ?? ""}");
//           print("url ${t.playedLength ?? ""}");
//           Sample result = await Navigator.push(
//               widget._context,
//               MaterialPageRoute(
//                   builder: (_) =>
//                       Player(
//                         sampleVideo: returnSample != null ? returnSample.name == t.name ? returnSample : t : t,
//                       )));
//           if (result != null) {
// //            SystemChrome.setEnabledSystemUIOverlays(SystemUiOverlay.values);
//             print("back from player");
//             setState(() {
//               returnSample = result;
//               print(t.playedLength);
//               SystemChrome.setPreferredOrientations([
//                 DeviceOrientation.portraitUp,
//               ]);
//             });
//           }
//         },
//         subtitle: Text(
//           t.extension ?? "",
//         ),
//         selected: true,
//         title: Text(t.name,
//             style: TextStyle(fontSize: 18.0, color: Colors.black54)));
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
//
// //ChewieDemo(sampleVideo: t,)