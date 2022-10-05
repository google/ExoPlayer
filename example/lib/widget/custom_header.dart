import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:lidaverse/common/theme/app_colors.dart';
import 'package:lidaverse/common/theme/app_dimens.dart';

class CustomHeader extends StatelessWidget {
  // final Color? color;
  // final Color? textColor;
  final String title;

  // final VoidCallback? onPressed;
  // final bool? hasRadius;
  // final double radius;
  // final double? lineHeight;
  // final Color? colorBorder;
  // final bool? isBorder;
  // final EdgeInsetsGeometry? padding;

  const CustomHeader({
    // this.color,
    this.title,
    // this.textColor,
    // this.onPressed,
    // this.hasRadius = true,
    // this.lineHeight = 21,
    // this.colorBorder = AppColors.primary,
    // this.isBorder = false,
    // this.padding = const EdgeInsets.symmetric(vertical: 10, horizontal: 10),
    // this.radius = 8,
  }) ;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(left: 20,right: 20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            decoration: BoxDecoration(
              color: AppColors.blue,
              borderRadius: BorderRadius.only(
                  topLeft: Radius.circular(40), bottomRight: Radius.circular(40)),
            ),
            child: Padding(
              padding: EdgeInsets.only(top: 8, bottom: 8, left: 30, right: 70),
              child: Text(
                title,
                style: TextStyle(color: AppColors.white,fontSize:AppDimens.padding16 ),
                // AppTextStyles.regularW700(context,
                //     size: AppDimens.padding16,
                //     lineHeight: 20,
                //     color: AppColors.white),
              ),
            ),
          ),
          SizedBox(
            height: 3,
          ),
          Container(
            height: 2,
            decoration: BoxDecoration(
              color: AppColors.primary,
              borderRadius: BorderRadius.only(
                  topLeft: Radius.circular(40), bottomRight: Radius.circular(40)),
            ),
          ),
        ],
      ),
    );
  }
}