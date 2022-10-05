import 'dart:ui';

import 'package:connectivity/connectivity.dart';
import 'package:flutter/material.dart';

import 'package:intl/intl.dart';
import 'package:lidaverse/common/theme/app_colors.dart';

var pixelRatio = window.devicePixelRatio;

//Size in logical pixels
var logicalScreenSize = window.physicalSize / pixelRatio;
double width = logicalScreenSize.width;
double height = logicalScreenSize.height;

/// Kinh nghiệm bản thân
// List<Item> exp = [
//   Item(id: "1", name: "Chưa có kinh nghiệm"),
//   Item(id: "2", name: "Dưới 1 năm kinh nghiệm"),
//   Item(id: "3", name: "1 năm kinh nghiệm"),
//   Item(id: "4", name: "2 năm kinh nghiệm"),
//   Item(id: "5", name: "3 năm kinh nghiệm"),
//   Item(id: "5", name: "4 năm kinh nghiệm"),
//   Item(id: "10", name: "Trên 5 năm kinh nghiệm"),
// ];
/// Bằng cấp
// List<Item> listDegree2 = [
//   Item(id: "1", name: "Đại học "),
//   Item(id: "2", name: "Không bằng cấp "),
//   Item(id: "3", name: "Cao đẳng"),
//   Item(id: "4", name: "Thạc sĩ"),
//   Item(id: "5", name: "Tiến sĩ"),
//   Item(id: "6", name: "Chứng chỉ"),
// ];

/// Custom
class Utils {
  static String getPhoneVn(String phone) {
    if (phone.startsWith("+84")) phone = phone.replaceFirst("+84", "0");
    return phone;
  }

  static showToast(BuildContext context, String message, Color color) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(
        message,
        textAlign: TextAlign.center,
        // style: AppTextStyles.regularW500(context,
        //     size: AppDimens.textSize16, color: AppColors.primary),
      ),
      behavior: SnackBarBehavior.floating,
      shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(24),
          side: BorderSide(color: color, width: 1, style: BorderStyle.solid)),
      backgroundColor: AppColors.white,
      duration: Duration(milliseconds: 3000),
      elevation: 2,
      margin: EdgeInsets.only(bottom: 50, right: 20, left: 20),
    ));
  }

  //check internet
  static Future<bool> checkConnectionInternet() async {
    var connectivityResult = await (Connectivity().checkConnectivity());
    if (connectivityResult == ConnectivityResult.mobile) {
      return true;
    } else if (connectivityResult == ConnectivityResult.wifi) {
      return true;
    } else {
      return false;
    }
  }

  // Kinh nghiệm
  // static String getExp(String id) {
  //   String nameExp = '';
  //   exp.forEach((element) {
  //     if (id == element.id) {
  //       nameExp = element.name;
  //     }
  //   });
  //   return nameExp;
  // }


  var convert8 = NumberFormat('###,###,###,###,###,###.00000000#', 'en_US');
  var convert2 = NumberFormat('###,###,###,###,###,###.00#', 'en_US');
}