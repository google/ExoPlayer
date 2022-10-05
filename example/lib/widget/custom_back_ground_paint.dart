
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
class BackCustomPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    Path path_0 = Path();
    path_0.moveTo(size.width*1.331600,size.height*0.4528117);
    path_0.cubicTo(size.width*1.331600,size.height*0.4528117,size.width*1.238024,size.height*0.7783631,size.width*1.012768,size.height*0.7816927);
    path_0.cubicTo(size.width*0.7875093,size.height*0.7849665,size.width*0.6277520,size.height*0.4631084,size.width*0.4162107,size.height*0.4661682);
    path_0.cubicTo(size.width*0.2046691,size.height*0.4692279,size.width*0.1075619,size.height*0.8559777,size.width*-0.1627517,size.height*0.8599385);
    path_0.lineTo(size.width*-0.2861413,size.height*-0.1849709);
    path_0.lineTo(size.width*1.168104,size.height*-0.7048045);
    path_0.lineTo(size.width*1.331600,size.height*0.4528117);
    path_0.close();

    Paint paint_0_fill = Paint()..style=PaintingStyle.fill;
    paint_0_fill.shader = ui.Gradient.linear(Offset(size.width*1.330315,size.height*0.06547430), Offset(size.width*-28.52427,size.height*0.08908101), [Color(0xffC4C4C4).withOpacity(1),Color(0xffC4C4C4).withOpacity(1),Color(0xffC4C4C4).withOpacity(1)], [0,0.515625,1]);
    canvas.drawPath(path_0,paint_0_fill);

    Path path_1 = Path();
    path_1.moveTo(size.width*1.298827,size.height*0.7120112);
    path_1.cubicTo(size.width*1.298827,size.height*0.7120112,size.width*1.178107,size.height*0.9956425,size.width*0.9564267,size.height*0.9115084);
    path_1.cubicTo(size.width*0.7347733,size.height*0.8273184,size.width*0.6062400,size.height*0.4489944,size.width*0.3980800,size.height*0.3699441);
    path_1.cubicTo(size.width*0.1899200,size.height*0.2908939,size.width*0.08778667,size.height*0.6393296,size.width*-0.1781867,size.height*0.5383240);
    path_1.lineTo(size.width*-0.2345600,size.height*-0.5424022);
    path_1.lineTo(size.width*1.240480,size.height*-0.4890503);
    path_1.lineTo(size.width*1.298827,size.height*0.7120112);
    path_1.close();

    Paint paint_1_fill = Paint()..style=PaintingStyle.fill;
    paint_1_fill.shader = ui.Gradient.radial(Offset(0,0),size.width*0.002666667, [Color(0xff0E7DC2).withOpacity(1),Color(0xff4BA6F4).withOpacity(1),Color(0xff52ABF9).withOpacity(1)], [0,0.553819,1]);
    canvas.drawPath(path_1,paint_1_fill);

    Path path_2 = Path();
    path_2.moveTo(size.width*1.246987,size.height*0.8042458);
    path_2.cubicTo(size.width*1.246987,size.height*0.8042458,size.width*1.233547,size.height*0.8838547,size.width*1.065467,size.height*0.8838547);
    path_2.cubicTo(size.width*0.7432800,size.height*0.8838547,size.width*0.5673067,size.height*0.2932961,size.width*0.3469067,size.height*0.2932961);
    path_2.cubicTo(size.width*0.1265067,size.height*0.2932961,size.width*0.1356533,size.height*0.5498324,size.width*-0.09549333,size.height*0.5361453);
    path_2.cubicTo(size.width*-0.4217067,size.height*0.5361453,size.width*-0.4480000,size.height*-0.1804469,size.width*-0.4480000,size.height*-0.1804469);

    Paint paint_2_stroke = Paint()..style=PaintingStyle.stroke..strokeWidth=size.width*0.002894667;
    paint_2_stroke.shader = ui.Gradient.linear(Offset(size.width*1.248365,size.height*0.3531128), Offset(size.width*-44.94427,size.height*0.3531128), [Colors.white.withOpacity(1),Colors.white.withOpacity(1)], [0,1]);
    canvas.drawPath(path_2,paint_2_stroke);

    // Paint paint_2_fill = Paint()..style=PaintingStyle.fill;
    // paint_2_fill.color = Color(0xff000000).withOpacity(1.0);
    // canvas.drawPath(path_2,paint_2_fill);


  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) {
    return true;
  }

  const BackCustomPainter();
}


class BackCustomPainterEnd extends CustomPainter{
  @override
  void paint(Canvas canvas, Size size) {

    Path path_0 = Path();
    path_0.moveTo(size.width*0.1050757,size.height*0.1374004);
    path_0.cubicTo(size.width*0.1116435,size.height*0.1393212,size.width*0.2559027,size.height*0.3292460,size.width*0.4657440,size.height*0.3217412);
    path_0.cubicTo(size.width*0.6755867,size.height*0.3142363,size.width*0.7400133,size.height*0.04535752,size.width*0.9240960,size.height*0.03879102);
    path_0.cubicTo(size.width*1.108181,size.height*0.03222469,size.width*1.167589,size.height*0.3812000,size.width*1.535760,size.height*0.3680673);
    path_0.lineTo(size.width*1.543325,size.height*0.9508319);
    path_0.lineTo(size.width*0.1593813,size.height*1.000292);
    path_0.lineTo(size.width*0.1050757,size.height*0.1374004);
    path_0.close();

    Paint paint_0_fill = Paint()..style=PaintingStyle.fill;
    paint_0_fill.shader = ui.Gradient.linear(Offset(size.width*0.1102243,size.height*0.5349558), Offset(size.width*1.537267,size.height*0.4839646), [Color(0xffC4C4C4).withOpacity(1),Color(0xffC4C4C4).withOpacity(1)], [0,1]);
    canvas.drawPath(path_0,paint_0_fill);

    Path path_1 = Path();
    path_1.moveTo(size.width*1.049707,size.height*0.1364611);
    path_1.cubicTo(size.width*1.043173,size.height*0.1386292,size.width*0.7127733,size.height*0.3336292,size.width*0.4750933,size.height*0.3336292);
    path_1.cubicTo(size.width*0.2374133,size.height*0.3336292,size.width*0.1446933,size.height*0.06708053,size.width*-0.03944000,size.height*0.06708053);
    path_1.cubicTo(size.width*-0.2235733,size.height*0.06708053,size.width*-0.3437067,size.height*0.4180982,size.width*-0.7120000,size.height*0.4180982);
    path_1.lineTo(size.width*-0.6466933,size.height*1.000973);
    path_1.lineTo(size.width*1.006613,size.height*1.000973);
    path_1.lineTo(size.width*1.049707,size.height*0.1364611);
    path_1.close();

    Paint paint_1_fill = Paint()..style=PaintingStyle.fill;
    paint_1_fill.shader = ui.Gradient.radial(Offset(0,0),size.width*0.002666667, [Color(0xff0E7DC2).withOpacity(1),Color(0xff4BA6F4).withOpacity(1),Color(0xff52ABF9).withOpacity(1)], [0,0.553819,1]);
    canvas.drawPath(path_1,paint_1_fill);

    Path path_2 = Path();
    path_2.moveTo(size.width*1.097749,size.height*0.3667487);
    path_2.cubicTo(size.width*1.097749,size.height*0.3667487,size.width*0.7005733,size.height*0.5026460,size.width*0.4501120,size.height*0.4541726);
    path_2.cubicTo(size.width*0.1996496,size.height*0.4057009,size.width*0.1645176,size.height*0.1160239,size.width*-0.02719600,size.height*0.07892212);
    path_2.cubicTo(size.width*-0.2189099,size.height*0.04181991,size.width*-0.4481307,size.height*0.3809704,size.width*-0.4481307,size.height*0.3809704);

    Paint paint_2_stroke = Paint()..style=PaintingStyle.stroke..strokeWidth=size.width*0.002022400;
    paint_2_stroke.shader = ui.Gradient.linear(Offset(size.width*1.089656,size.height*0.4865531), Offset(size.width*-43.55387,size.height*0.1913863), [Colors.white.withOpacity(1),Colors.white.withOpacity(1)], [0,1]);
    canvas.drawPath(path_2,paint_2_stroke);

    // Paint paint_2_fill = Paint()..style=PaintingStyle.fill;
    // paint_2_fill.color = Color(0xff000000).withOpacity(1.0);
    // canvas.drawPath(path_2,paint_2_fill);

  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) {
    return true;
  }

  const BackCustomPainterEnd();
}


/// custom lại ở các UI
// CustomPaint(
// size: Size(width, (width * 0.47733333333333333).toDouble()),
// //You can Replace [WIDTH] with your desired width for Custom Paint and height will be calculated automatically
// painter: painBackEnd,
// ),
/// Trong đó khai báo hằng số "painBackEnd"ở đầu sau các thư viên
// const painBackEnd = BackCustomPainterEnd();
