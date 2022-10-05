import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:introduction_screen/introduction_screen.dart';
import 'package:lidaverse/common/images/images.dart';
import 'package:lidaverse/common/utils.dart';
import 'package:lidaverse/widget/custom_back_ground_paint.dart';

import 'customOpenSource.dart';
import 'custom_widget/bottomBar.dart';

const painBack = BackCustomPainter();
const painBackEnd = BackCustomPainterEnd();
class IntroScreen extends StatefulWidget {
  @override
  _OnBoardingPageState createState() => _OnBoardingPageState();
}

class _OnBoardingPageState extends State<IntroScreen> {
  final introKey = GlobalKey<IntroductionScreenState>();

  // void _onIntroEnd(context) {
  //   Navigator.of(context).push(
  //     MaterialPageRoute(builder: (_) => HomePage()),
  //   );
  // }

  // Widget _buildFullscrenImage() {
  //   return Image.asset(
  //     'assets/images/introa.svg',
  //     fit: BoxFit.cover,
  //     height: double.infinity,
  //     width: double.infinity,
  //     alignment: Alignment.center,
  //   );
  // }

  Widget _buildImage(String assetName, [double width = 350]) {
    return SvgPicture.asset(assetName);
    //return Image.asset(assetName, width: width);
  }

  @override
  Widget build(BuildContext context) {
    const bodyStyle = TextStyle(fontSize: 19.0);

    const pageDecoration = const PageDecoration(
      titleTextStyle: TextStyle(fontSize: 28.0, fontWeight: FontWeight.w700),
      bodyTextStyle: bodyStyle,
      descriptionPadding: EdgeInsets.fromLTRB(16.0, 0.0, 16.0, 16.0),
      pageColor: Colors.white,
      imagePadding: EdgeInsets.zero,
    );

    return Scaffold(
      body: Column(
        children: [
          CustomPaint(
            size: Size(width, (width * 0.47733333333333333).toDouble()),
            //You can Replace [WIDTH] with your desired width for Custom Paint and height will be calculated automatically
            painter: painBack,
          ),
          Expanded(
            child: IntroductionScreen(
              key: introKey,
              globalBackgroundColor: Colors.white,
              // globalHeader: Align(
              //   alignment: Alignment.topRight,
              //   child: SafeArea(
              //     child: Padding(
              //       padding: const EdgeInsets.only(top: 16, right: 16),
              //       child: _buildImage('introa.svg', 100),
              //     ),
              //   ),
              // ),
              // globalFooter: SizedBox(
              //   width: double.infinity,
              //   height: 60,
              //   child: ElevatedButton(
              //     child: const Text(
              //       'Let\s go right away!',
              //       style: TextStyle(fontSize: 16.0, fontWeight: FontWeight.bold),
              //     ),
              //     onPressed: () => _onIntroEnd(context),
              //   ),
              // ),
              pages: [
                PageViewModel(
                  title: "",
                  body: "STREAM VIDEO - một trong những kỹ thuật truyền video phổ biến nhất",
                  image: _buildImage(Images.img_introa),
                  decoration: pageDecoration,
                ),
                PageViewModel(
                  title: "",
                  body: "EXOPLAYER-mã nguồn mở của google cho phép truyền video Stream ",
                  image: _buildImage(Images.img_introb),
                  decoration: pageDecoration,
                ),
                PageViewModel(
                  title: "",
                  body: "Ứng dụng LINDAVEST - ứng dụng hàng đầu xem video Stream!",
                  image: _buildImage(Images.img_introc),
                  decoration: pageDecoration,
                ),
                // PageViewModel(
                //   title: "Full Screen Page",
                //   body:
                //   "Pages can be full screen as well.\n\nLorem ipsum dolor sit amet, consectetur adipiscing elit. Nunc id euismod lectus, non tempor felis. Nam rutrum rhoncus est ac venenatis.",
                //   image: _buildFullscrenImage(),
                //   decoration: pageDecoration.copyWith(
                //     contentMargin: const EdgeInsets.symmetric(horizontal: 16),
                //     fullScreen: true,
                //     bodyFlex: 2,
                //     imageFlex: 3,
                //   ),
                // ),
                // PageViewModel(
                //   title: "Another title page",
                //   body: "Another beautiful body text for this example onboarding",
                //   image: _buildImage('img2.jpg'),
                //   footer: ElevatedButton(
                //     onPressed: () {
                //       introKey.currentState?.animateScroll(0);
                //     },
                //     child: const Text(
                //       'FooButton',
                //       style: TextStyle(color: Colors.white),
                //     ),
                //     style: ElevatedButton.styleFrom(
                //       primary: Colors.lightBlue,
                //       shape: RoundedRectangleBorder(
                //         borderRadius: BorderRadius.circular(8.0),
                //       ),
                //     ),
                //   ),
                //   decoration: pageDecoration,
                // ),
                // PageViewModel(
                //   title: "Title of last page - reversed",
                //   bodyWidget: Row(
                //     mainAxisAlignment: MainAxisAlignment.center,
                //     children: const [
                //       Text("Click on ", style: bodyStyle),
                //       Icon(Icons.edit),
                //       Text(" to edit a post", style: bodyStyle),
                //     ],
                //   ),
                //   decoration: pageDecoration.copyWith(
                //     bodyFlex: 2,
                //     imageFlex: 4,
                //     bodyAlignment: Alignment.bottomCenter,
                //     imageAlignment: Alignment.topCenter,
                //   ),
                //   image: _buildImage('img1.jpg'),
                //   reverse: true,
                // ),
              ],
              onDone: () => {
                Navigator.push(
                  context,
                  PageRouteBuilder(
                    transitionDuration: Duration(seconds: 2),
                    transitionsBuilder:
                        (context, animation, secondaryAnimation, child) {
                      return ScaleTransition(
                        alignment: Alignment.center,
                        scale: animation,
                        child: child,
                      );
                    },
                    pageBuilder: (BuildContext context, Animation<double> animation,
                        Animation<double> secondaryAnimation) {
                      return BottomNaviBar();
                        //MyCustomList();
                    },
                    // MaterialPageRoute(
                    // builder: (context) => MyCustomList(),
                  ),
                ),
              },
              //onSkip: () => _onIntroEnd(context), // You can override onSkip callback
              showSkipButton: true,
              skipFlex: 0,
              nextFlex: 0,
              //rtl: true, // Display as right-to-left
              skip: const Text('Skip'),
              next: const Icon(Icons.arrow_forward),
              done:
                  const Text('Done', style: TextStyle(fontWeight: FontWeight.w600)),
              curve: Curves.fastLinearToSlowEaseIn,
              controlsMargin: const EdgeInsets.all(16),
              controlsPadding: kIsWeb
                  ? const EdgeInsets.all(12.0)
                  : const EdgeInsets.fromLTRB(8.0, 4.0, 8.0, 4.0),
              dotsDecorator: const DotsDecorator(
                size: Size(10.0, 10.0),
                color: Color(0xFFBDBDBD),
                activeSize: Size(22.0, 10.0),
                activeShape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.all(Radius.circular(25.0)),
                ),
              ),
              dotsContainerDecorator: const ShapeDecoration(
                color: Colors.white,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.all(Radius.circular(8.0)),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
//
// class HomePage extends StatelessWidget {
//   @override
//   Widget build(BuildContext context) {
//     return Scaffold(
//       appBar: AppBar(title: const Text('Home')),
//       body: const Center(child: Text("")),
//     );
//   }
// }
