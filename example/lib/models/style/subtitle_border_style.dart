import 'package:flutter/material.dart';

class SubtitleBorderStyle {
  final double strokeWidth;
  final PaintingStyle style;
  final Color color;

  const SubtitleBorderStyle(
      {this.strokeWidth = 2,
      this.style = PaintingStyle.stroke,
      this.color = Colors.black});
}
