import 'package:flutter/material.dart';
import 'package:flutter_svg/svg.dart';
import 'package:lidaverse/common/images/images.dart';
import 'package:lidaverse/common/theme/app_colors.dart';
import 'package:lidaverse/common/theme/app_dimens.dart';
import 'package:lidaverse/common/utils.dart';
import 'package:lidaverse/screen/bar/custom_video.dart';
import 'package:lidaverse/screen/bar/information.dart';
import 'package:lidaverse/widget/custom_back_ground_paint.dart';

import '../customOpenSource.dart';

const painBack = BackCustomPainter();

class BottomNaviBar extends StatefulWidget {
  @override
  _BottomNaviBarState createState() => _BottomNaviBarState();
}

class _BottomNaviBarState extends State<BottomNaviBar> {
  int _selectedIndex = 0;
  PageController pageController = PageController();

  void _onItemTapped(int index) {
    setState(() {
      pageController.jumpToPage(index);
      _selectedIndex = index;
    });
  }

  @override
  Widget build(BuildContext context) {
    List<Widget> screen = [
      MyHomePageForScreen(),
      MyListVideo(),
      InformationBar(), // man thong tin
    ];
    return Scaffold(
      backgroundColor: AppColors.white,
      body: PageView(
        controller: pageController,
        onPageChanged: _onItemTapped,
        children: screen,
      ),
      bottomNavigationBar: Container(
        height: 60,
        padding: EdgeInsets.symmetric(horizontal: 15),
        decoration: BoxDecoration(
            color: AppColors.white,
            borderRadius: BorderRadius.only(
                topLeft: Radius.circular(20),
                topRight: Radius.circular(20),
                bottomLeft: Radius.circular(0),
                bottomRight: Radius.circular(0)),
            boxShadow: [
              BoxShadow(
                  color: Color.fromRGBO(0, 0, 0, 0.25),
                  blurRadius: 4,
                  offset: Offset(0, 0))
            ]),
        child: ClipRRect(
          clipBehavior: Clip.hardEdge,
          borderRadius: BorderRadius.only(
              topLeft: Radius.circular(25),
              topRight: Radius.circular(25),
              bottomLeft: Radius.circular(0),
              bottomRight: Radius.circular(0)),
          child: BottomNavigationBar(
            backgroundColor: Colors.white,
            showUnselectedLabels: false,
            showSelectedLabels: false,
            type: BottomNavigationBarType.fixed,
            elevation: 0,
            unselectedLabelStyle: TextStyle(
                color: AppColors.grey74, fontSize: AppDimens.textSize12),
            // AppTextStyles.regularW400(context,
            //     size: AppDimens.textSize12,
            //     color: AppColors.grey74,
            //     lineHeight: 17),
            selectedLabelStyle: TextStyle(
                color: AppColors.primary, fontSize: AppDimens.textSize12),
            // AppTextStyles.regularW400(context,
            //     size: AppDimens.textSize12,
            //     color: AppColors.primary,
            //     lineHeight: 17),
            items: [
              _item(Images.img_home, 0),
              _item(Images.img_set, 5),
              _item(Images.ic_infor, 0),
              // _item(Images.ic_perrsonal, 0),
            ],
            currentIndex: _selectedIndex,
            onTap: _onItemTapped,
          ),
        ),
      ),
    );
  }

  BottomNavigationBarItem _item(String image, int sumNoti) {
    return BottomNavigationBarItem(
      label: '',
      icon: Stack(
        clipBehavior: Clip.none,
        children: [
          SvgPicture.asset(
            image,
            color: AppColors.blue,
          ),
          sumNoti != 0
              ? Positioned(
            top: -5,
            right: -5,
            child: Container(
                height: 18,
                width: 18,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                    shape: BoxShape.circle, color: AppColors.redEB),
                child: Padding(
                  padding: const EdgeInsets.only(bottom: 4, left: 1),
                  child: Text(
                    sumNoti > 5 ? '5+' : '$sumNoti',
                    style: TextStyle(
                        color: AppColors.blue,
                        fontSize: AppDimens.textSize12),
                  ),
                )),
          )
              : SizedBox.shrink(),
        ],
      ),
      activeIcon: Stack(
        clipBehavior: Clip.none,
        children: [
          Positioned(
            top: -15.5,
            left: -10,
            child: SizedBox(
              width: width * 0.12,
              height: 5,
              child: Container(
                decoration: BoxDecoration(
                  color: AppColors.primary,
                  borderRadius:
                  BorderRadius.vertical(bottom: Radius.circular(4)),
                ),
              ),
            ),
          ),
          SvgPicture.asset(
            image,
            color: AppColors.primary,
            height: 24,
          ),
        ],
      ),
    );
  }
}