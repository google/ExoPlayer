// import 'package:flutter/material.dart';
//
// import 'app_colors.dart';
// import 'app_dimens.dart';
//
// class AppTextStyles {
//   // Text style with font Normal
//   static TextStyle regularW500(BuildContext context,
//       {@required double? size,
//         FontStyle? fontStyle,
//         Color color = AppColors.black,
//         double lineHeight = AppDimens.lineHeightXSmall}) {
//     var height = 1.0;
//     if (lineHeight > size!) {
//       height = lineHeight / size;
//     }
//     return Theme.of(context).textTheme.subtitle1!.copyWith(
//       fontSize: size,
//       fontWeight: FontWeight.w500,
//       color: color,
//       height: height,
//     );
//   }
//
//   static TextStyle regularW700(
//       BuildContext context, {
//         @required double? size,
//         FontStyle? fontStyle,
//         Color color = AppColors.black,
//         double lineHeight = AppDimens.lineHeightSmall,
//       }) {
//     var height = 1.0;
//     if (lineHeight > size!) {
//       height = lineHeight / size;
//     }
//     return Theme.of(context).textTheme.subtitle1!.copyWith(
//       fontSize: size,
//       fontWeight: FontWeight.w700,
//       color: color,
//       height: height,
//     );
//   }
//
//   static TextStyle regularW400(BuildContext context,
//       {@required double? size,
//         FontStyle? fontStyle,
//         Color color = AppColors.black,
//         double lineHeight = AppDimens.lineHeightXSmall}) {
//     var height = 1.0;
//     if (lineHeight > size!) {
//       height = lineHeight / size;
//     }
//     return Theme.of(context).textTheme.subtitle1!.copyWith(
//       fontSize: size,
//       fontWeight: FontWeight.w400,
//       color: color,
//       height: height,
//       fontStyle: fontStyle,
//     );
//   }
//
//   static TextStyle regularW600(BuildContext context,
//       {@required double? size,
//         FontStyle? fontStyle,
//         Color color = AppColors.black,
//         double lineHeight = AppDimens.lineHeightXSmall}) {
//     var height = 1.0;
//     if (lineHeight > size!) {
//       height = lineHeight / size;
//     }
//     return Theme.of(context).textTheme.subtitle1!.copyWith(
//       fontSize: size,
//       fontWeight: FontWeight.w600,
//       color: color,
//       height: height,
//       fontStyle: fontStyle,
//     );
//   }
//
//   static TextStyle regular(BuildContext context,
//       {@required double? size,
//         FontWeight? fontWeight,
//         Color color = AppColors.black,
//         double lineHeight = AppDimens.lineHeightXSmall}) {
//     var height = 1.0;
//     if (lineHeight > size!) {
//       height = lineHeight / size;
//     }
//     return Theme.of(context).textTheme.subtitle1!.copyWith(
//       fontSize: size,
//       fontWeight: FontWeight.w300,
//       color: color,
//       height: height,
//     );
//   }
// }
