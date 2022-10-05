
import 'package:flutter/material.dart';
import 'package:lidaverse/models/style/subtitle_border_style.dart';
import 'package:lidaverse/models/style/subtitle_position.dart';

class SubtitleStyle {
  final bool hasBorder;
  final SubtitleBorderStyle borderStyle;
  final double fontSize;
  final Color textColor;
  final SubtitlePosition position;

  const SubtitleStyle(
      {this.hasBorder = false,
      this.borderStyle = const SubtitleBorderStyle(),
      this.fontSize = 16,
      this.textColor = Colors.black,
      this.position = const SubtitlePosition()});
}
