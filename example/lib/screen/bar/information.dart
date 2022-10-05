import 'package:flutter/material.dart';
import 'package:flutter_svg/svg.dart';
import 'package:lidaverse/common/images/images.dart';
import 'package:lidaverse/common/theme/app_colors.dart';
import 'package:lidaverse/common/theme/app_dimens.dart';
import 'package:lidaverse/common/utils.dart';
import 'package:lidaverse/widget/custom_header.dart';

// const painBack = BackCustomPainter();

class InformationBar extends StatefulWidget {
  @override
  _InformationBarState createState() => _InformationBarState();
}

class _InformationBarState extends State<InformationBar> {
  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      child: Stack(children: [
        Column(children: [
          AppBar(
            backgroundColor: AppColors.blue,
            elevation: 0,
            title: Text(
              "",
              style: TextStyle(fontSize: 30),
            ),
            actions: [
              IconButton(
                icon: Icon(
                  Icons.short_text,
                  color: Colors.white,
                  size: 30,
                ),
              )
            ],
          ),
          Container(
            width: width,
            height: 90,
            color: AppColors.blue,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Center(
                  child: CircleAvatar(
                    radius: (AppDimens.space40 + AppDimens.space40) / 2,
                    backgroundImage: NetworkImage(
                        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcT0VQ0u6wCY4g5TE_hpWNbjbLf0pS8rzYscIg&usqp=CAU'),
                  ),
                ),
              ],
            ),
          ),
          SizedBox(
            height: 25,
          ),
          CustomHeader(title: 'GIỚI THIỆU'),
          _customCard3(context,true),
          SizedBox(
            height: 15,
          ),
          _customCard(context, true),
          SizedBox(
            height: 15,
          ),
          _customCard2(context, true),
          SizedBox(
            height: 25,
          ),

          SizedBox(
            height: 25,
          ),
        ]),
      ]),
    );
  }

  Widget _customCard(BuildContext context, bool check) {
    return StatefulBuilder(
      builder: (context, setState) => AnimatedContainer(
        duration: Duration(milliseconds: 400),
        curve: Curves.fastOutSlowIn,
        margin: EdgeInsets.only(left: 10, right: 10, top: 10),
        padding: EdgeInsets.only(
          top: AppDimens.textSize24,
          bottom: AppDimens.space10,
        ),
        decoration: BoxDecoration(
            color: AppColors.white,
            borderRadius: BorderRadius.circular(20),
            boxShadow: [
              BoxShadow(
                  color: AppColors.primary.withOpacity(0.2),
                  offset: Offset(0, 2),
                  blurRadius: 3)
            ]),
        child: Column(
          //mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    child: Column(
                      children: [
                        Row(
                          children: [
                            Padding(
                                padding:
                                EdgeInsets.only(left: AppDimens.padding16)),
                            //Padding(padding: EdgeInsets.only(left: width*0.016, top: width*0.0116)),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    'Thư viện ExoPlayer',
                                    style: TextStyle(
                                      color: AppColors.black,
                                      fontSize: AppDimens.padding16,
                                    ),
                                  ),
                                  SizedBox(
                                    height: 6,
                                  ),
                                  Text(
                                    'Một trong những thư viện mã nguồn mở được sử dụng nhiều nhất.',
                                    style: TextStyle(
                                      color: AppColors.black999999,
                                      fontSize: AppDimens.padding12,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            SizedBox(
                              width: width * 0.16,
                            ),
                            Expanded(
                              child: Column(
                                //crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    'Được cung cấp bởi google',
                                    style: TextStyle(
                                      color: AppColors.black,
                                      fontSize: AppDimens.padding16,
                                    ),
                                    textAlign: TextAlign.right,
                                  ),
                                  SizedBox(
                                    height: 6,
                                  ),
                                  Text(
                                    'Dễ dàng sử dụng opensource cho các ứng dụng mobile',
                                    style: TextStyle(
                                      color: AppColors.black999999,
                                      fontSize: AppDimens.padding12,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            SizedBox(
                              width: 16,
                            ),
                          ],
                        ),
                        check
                            ? SizedBox(
                          child: Padding(
                              padding: EdgeInsets.only(
                                  top: 25,
                                  right: 20,
                                  left: 20,
                                  bottom: 15),
                              child: Image.network(
                                  'https://i.stack.imgur.com/zegNf.jpg')),
                        )
                            : SizedBox.shrink(),
                        InkWell(
                          onTap: () {
                            setState(() {
                              check = !check;
                            });
                          },
                          child: Center(
                            child: !check
                                ? Text(
                              'Xem thêm',
                              // style: AppTextStyles.regularW400(context,
                              //     size: AppDimens.textSize14,
                              //     color: AppColors.grey74,
                              //     lineHeight: 20),
                            )
                                : SvgPicture.asset(
                              Images.ic_top,
                              color: AppColors.grey74,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _customCard2(BuildContext context, bool check) {
    return StatefulBuilder(
      builder: (context, setState) => AnimatedContainer(
        duration: Duration(milliseconds: 400),
        curve: Curves.fastOutSlowIn,
        margin: EdgeInsets.only(left: 10, right: 10, top: 10),
        padding: EdgeInsets.only(
          top: AppDimens.textSize24,
          bottom: AppDimens.space10,
        ),
        decoration: BoxDecoration(
            color: AppColors.white,
            borderRadius: BorderRadius.circular(20),
            boxShadow: [
              BoxShadow(
                  color: AppColors.primary.withOpacity(0.2),
                  offset: Offset(0, 2),
                  blurRadius: 3)
            ]),
        child: Column(
          //mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    child: Column(
                      children: [
                        Row(
                          children: [
                            Padding(
                                padding:
                                EdgeInsets.only(left: AppDimens.padding16)),
                            //Padding(padding: EdgeInsets.only(left: width*0.016, top: width*0.0116)),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    'Được sử dụng trong các ứng dụng của google',
                                    style: TextStyle(
                                      color: AppColors.black,
                                      fontSize: AppDimens.padding16,
                                    ),
                                  ),
                                  SizedBox(
                                    height: 6,
                                  ),
                                  Text(
                                    'Có nhiều ưu điểm hơn đối với MediapPlayer tích hợp sẵn trong android.',
                                    style: TextStyle(
                                      color: AppColors.black999999,
                                      fontSize: AppDimens.padding12,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            SizedBox(
                              width: width * 0.16,
                            ),
                            Expanded(
                              child: Column(
                                //crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    'Khả năng tùy biến và mở rộng cao',
                                    style: TextStyle(
                                      color: AppColors.black,
                                      fontSize: AppDimens.padding16,
                                    ),
                                    textAlign: TextAlign.right,
                                  ),
                                  SizedBox(
                                    height: 6,
                                  ),
                                  Text(
                                    'Hỗ trợ nhiều định dạng hơn so với Mediaplayer',
                                    style: TextStyle(
                                      color: AppColors.black999999,
                                      fontSize: AppDimens.padding12,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            SizedBox(
                              width: 16,
                            ),
                          ],
                        ),
                        check
                            ? SizedBox(
                          child: Padding(
                              padding: EdgeInsets.only(
                                  top: 25,
                                  right: 20,
                                  left: 20,
                                  bottom: 15),
                              child: Image.network(
                                  'https://www.motocms.com/blog/wp-content/uploads/2018/06/EXOPlayer-Live-Streaming-featured.jpg')),
                        )
                            : SizedBox.shrink(),
                        InkWell(
                          onTap: () {
                            setState(() {
                              check = !check;
                            });
                          },
                          child: Center(
                            child: !check
                                ? Text(
                              'Xem thêm',
                              // style: AppTextStyles.regularW400(context,
                              //     size: AppDimens.textSize14,
                              //     color: AppColors.grey74,
                              //     lineHeight: 20),
                            )
                                : SvgPicture.asset(
                              Images.ic_top,
                              color: AppColors.grey74,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
  Widget _customCard3(BuildContext context, bool check) {
    return StatefulBuilder(
      builder: (context, setState) => AnimatedContainer(
        duration: Duration(milliseconds: 400),
        curve: Curves.fastOutSlowIn,
        margin: EdgeInsets.only(left: 10, right: 10, top: 10),
        padding: EdgeInsets.only(
          top: AppDimens.textSize24,
          bottom: AppDimens.space10,
        ),
        decoration: BoxDecoration(
            color: AppColors.white,
            borderRadius: BorderRadius.circular(20),
            boxShadow: [
              BoxShadow(
                  color: AppColors.primary.withOpacity(0.2),
                  offset: Offset(0, 2),
                  blurRadius: 3)
            ]),
        child: Column(
          //mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    child: Column(
                      children: [
                        Row(
                          children: [
                            Padding(
                                padding:
                                EdgeInsets.only(left: AppDimens.padding16)),
                            //Padding(padding: EdgeInsets.only(left: width*0.016, top: width*0.0116)),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    'Streaming Video',
                                    style: TextStyle(
                                      color: AppColors.black,
                                      fontSize: AppDimens.padding16,
                                    ),
                                  ),
                                  SizedBox(
                                    height: 6,
                                  ),
                                  Text(
                                    'Một kỹ thuật được sử dụng phổ biến ở các ứng dụng hiện nay',
                                    style: TextStyle(
                                      color: AppColors.black999999,
                                      fontSize: AppDimens.padding12,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            SizedBox(
                              width: width * 0.16,
                            ),
                            Expanded(
                              child: Column(
                                //crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    'Xem video trực tuyến một cách dễ dàng và tiện lợi',
                                    style: TextStyle(
                                      color: AppColors.black,
                                      fontSize: AppDimens.padding16,
                                    ),
                                    textAlign: TextAlign.right,
                                  ),
                                  SizedBox(
                                    height: 6,
                                  ),
                                  Text(
                                    'Truyền và nhận video thông qua internet',
                                    style: TextStyle(
                                      color: AppColors.black999999,
                                      fontSize: AppDimens.padding12,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            SizedBox(
                              width: 16,
                            ),
                          ],
                        ),
                        check
                            ? SizedBox(
                          child: Padding(
                              padding: EdgeInsets.only(
                                  top: 25,
                                  right: 20,
                                  left: 20,
                                  bottom: 15),
                              child: Image.network(
                                'https://o.rada.vn/data/image/2016/04/18/Stream--pic-2.jpg',
                              )),
                        )
                            : SizedBox.shrink(),
                        InkWell(
                          onTap: () {
                            setState(() {
                              check = !check;
                            });
                          },
                          child: Center(
                            child: !check
                                ? Text(
                              'Xem thêm',
                              // style: AppTextStyles.regularW400(context,
                              //     size: AppDimens.textSize14,
                              //     color: AppColors.grey74,
                              //     lineHeight: 20),
                            )
                                : SvgPicture.asset(
                              Images.ic_top,
                              color: AppColors.grey74,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
  /// list view ngang
  // Widget _card1(BuildContext context){
  //   return Container(
  //     decoration: BoxDecoration(
  //       color: AppColors.primary,
  //       borderRadius:
  //       BorderRadius.vertical(top: Radius.circular(4)),
  //     ),
  //     child: ,
  //
  //   );
  // }
  /// Custom card
  Widget _card1({String image}) {
    return Container(
      //width: width,
      width: (width - AppDimens.padding16 * 3) / 2-20,
      //width - AppDimens.padding16 * 3)
      height: height * 0.35,
      decoration: BoxDecoration(
        color: AppColors.white,
        // border: Border.all(
        //   color: AppColors.primary,
        //   //width: 2.0 ,
        // ),
        borderRadius: BorderRadius.only(
            topLeft: Radius.circular(40), bottomRight: Radius.circular(40)),
        boxShadow: [
          BoxShadow(
              color: AppColors.black.withOpacity(0.25),
              blurRadius: 4,
              offset: Offset(0, 4)),
        ],
      ),
      padding: EdgeInsets.symmetric(vertical: AppDimens.padding16),
      child: Column(
        children: [
          SizedBox(child: Expanded(child: SvgPicture.asset(image))),
          //Images.ic_card1
          SizedBox(
            height: AppDimens.padding16,
          ),
          // SizedBox(child: SvgPicture.asset(Images.ic_enlope)),
        ],
      ),
    );
  }
}