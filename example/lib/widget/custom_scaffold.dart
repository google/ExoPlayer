// import 'package:flutter/material.dart';
// import 'package:flutter_svg/flutter_svg.dart';
// import 'package:hr/common/images.dart';
// import 'package:hr/common/theme/app_colors.dart';
// import 'package:hr/common/theme/app_text_style.dart';
// import 'package:hr/common/utils.dart';
//
// class CustomScaffold extends StatefulWidget {
//   final String title;
//   final double size;
//   final bool isAction;
//   final bool isCenterTitle;
//   final Widget action;
//   final Widget body;
//   const CustomScaffold({Key key, required this.title, required this.size, this.isAction = false, this.action, required this.body, this.isCenterTitle = false}) : super(key: key);
//
//   @override
//   _CustomScaffoldState createState() => _CustomScaffoldState();
// }
//
// class _CustomScaffoldState extends State<CustomScaffold> {
//   @override
//   Widget build(BuildContext context) {
//     return Scaffold(
//       backgroundColor: AppColors.whiteFA,
//       appBar: AppBar(
//         backgroundColor: AppColors.whiteFA,
//         title: Text(widget.title, style: AppTextStyles.regularW600(context, size: widget.size, color: AppColors.textColor),),
//         leading: InkWell(
//           onTap: () => Navigator.pop(context),
//           child: Padding(
//             padding: EdgeInsets.only(left: width*0.045, right: width*0.025),
//             child: SvgPicture.asset(Images.ic_back, width: 24, height: 24,),
//           ),
//         ),
//         toolbarHeight: 60,
//         centerTitle: widget.isCenterTitle ,
//         elevation: 0,
//         actions: [
//           widget.isAction!  widget.action! : SizedBox.shrink(),
//         ],
//       ),
//       body: widget.body,
//     );
//   }
// }
