/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.exoplayer2.transformer;

import android.os.Build;
import com.google.android.exoplayer2.util.Util;

/** Provides encoder bitrates that should target 0.95 SSIM or higher, accounting for device used. */
public class DeviceMappedEncoderBitrateProvider implements EncoderBitrateProvider {

  @Override
  public int getBitrate(String encoderName, int width, int height, float frameRate) {
    double bitrateMultiplier =
        getBitrateMultiplierFromMapping(
            encoderName,
            Util.SDK_INT,
            Build.MODEL,
            "" + width + "x" + height,
            Math.round(frameRate));
    return (int) (width * height * frameRate * bitrateMultiplier);
  }

  private static double getBitrateMultiplierFromMapping(
      String encoderName,
      int deviceSdkVersion,
      String deviceModel,
      String resolution,
      int framerate) {
    switch (encoderName) {
      case "OMX.Exynos.AVC.Encoder":
        switch (deviceSdkVersion) {
          case 24:
            switch (deviceModel) {
              case "SM-G920V":
                return 0.245;
              case "SM-G935F":
                return 0.2625;
              default:
                return 0.37188;
            }
          case 26:
            switch (deviceModel) {
              case "SM-A520F":
              case "SM-J600G":
                return 0.3325;
              case "SM-G930F":
                return 0.20125;
              case "SM-G935F":
                switch (resolution) {
                  case "1920x1080":
                    return 0.1225;
                  default:
                    return 0.315;
                }
              case "SM-G950F":
                return 0.2975;
              case "SM-G955F":
                switch (resolution) {
                  case "640x480":
                    return 0.245;
                  case "1920x1080":
                    switch (framerate) {
                      case 60:
                        return 0.14;
                      default:
                        return 0.175;
                    }
                  case "3840x2160":
                    return 0.0914;
                  default:
                    return 0.2975;
                }
              case "SM-G960F":
                switch (resolution) {
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.1575;
                      default:
                        return 0.175;
                    }
                  default:
                    return 0.35;
                }
              default:
                return 0.35;
            }
          case 27:
            switch (deviceModel) {
              case "SM-G610F":
              case "SM-J260F":
                return 0.3325;
              case "SM-J260G":
                switch (resolution) {
                  case "640x480":
                    return 0.41563;
                  case "1920x1080":
                    return 0.4375;
                  default:
                    return 0.525;
                }
              case "SM-M205F":
                switch (resolution) {
                  case "640x480":
                    return 0.41563;
                  case "1920x1080":
                    switch (framerate) {
                      case 60:
                        return 0.28;
                      default:
                        return 0.315;
                    }
                  default:
                    return 0.56875;
                }
              default:
                return 0.56875;
            }
          case 28:
            switch (deviceModel) {
              case "SM-A105FN":
                switch (resolution) {
                  case "640x480":
                    return 0.41563;
                  case "1920x1080":
                    return 0.35;
                  default:
                    return 0.7;
                }
              case "SM-G950F":
                switch (resolution) {
                  case "1280x720":
                    return 0.1925;
                  case "1920x1080":
                    switch (framerate) {
                      case 60:
                        return 0.175;
                      default:
                        return 0.21;
                    }
                  default:
                    return 0.245;
                }
              case "SM-G955F":
                return 0.1925;
              case "SM-G965F":
                switch (resolution) {
                  case "640x480":
                    return 0.21;
                  case "1920x1080":
                    return 0.11375;
                  default:
                    return 0.39375;
                }
              case "SM-G965N":
                switch (resolution) {
                  case "640x480":
                    return 0.21;
                  case "1920x1080":
                    return 0.175;
                  case "3840x2160":
                    return 0.08269;
                  default:
                    return 0.39375;
                }
              case "SM-J701F":
                return 0.3325;
              case "SM-N960F":
              case "SM-N960N":
                switch (resolution) {
                  case "640x480":
                    return 0.21;
                  case "1920x1080":
                    switch (framerate) {
                      case 60:
                        return 0.175;
                      default:
                        return 0.1925;
                    }
                  default:
                    return 0.39375;
                }
              default:
                return 0.7;
            }
          case 29:
            switch (deviceModel) {
              case "SM-A105FN":
                switch (resolution) {
                  case "640x480":
                    return 0.4375;
                  case "1280x720":
                    return 0.7;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 1.05;
                      default:
                        return 1.4;
                    }
                  default:
                    return 1.4;
                }
              case "SM-G977N":
                switch (resolution) {
                  case "1280x720":
                    return 0.4375;
                  default:
                    return 0.7;
                }
              case "SM-N970F":
                return 0.245;
              default:
                return 1.4;
            }
          case 30:
            switch (deviceModel) {
              case "SM-A127F":
              case "SM-A217F":
                return 0.35;
              case "SM-A505F":
              case "SM-A515F":
              case "SM-A515U1":
              case "SM-M315F":
                return 0.1925;
              default:
                return 0.37188;
            }
          case 31:
            return 0.245;
          default:
            return 1.4;
        }
      case "OMX.Exynos.HEVC.Encoder":
        switch (deviceSdkVersion) {
          case 28:
            switch (deviceModel) {
              case "SM-G965N":
                return 0.0525;
              default:
                return 0.07;
            }
          default:
            return 0.07;
        }
      case "OMX.IMG.TOPAZ.VIDEO.Encoder":
        switch (deviceModel) {
          case "ANE-LX1":
          case "ANE-LX2":
            switch (resolution) {
              case "1280x720":
                return 0.245;
              case "1920x1080":
                switch (framerate) {
                  case 60:
                    return 0.1925;
                  default:
                    return 0.2625;
                }
              case "3840x2160":
                return 0.23208;
              default:
                return 0.28;
            }
          case "FIG-LX1":
            return 0.245;
          default:
            return 0.28;
        }
      case "OMX.MTK.VIDEO.ENCODER.AVC":
        switch (deviceSdkVersion) {
          case 22:
            return 0.245;
          case 23:
            switch (deviceModel) {
              case "LG-K430":
                return 0.28;
              case "Redmi Note 4":
                return 0.35;
              case "SM-G532F":
                return 0.39375;
              case "SM-G532G":
                switch (resolution) {
                  case "1280x720":
                    return 0.39375;
                  case "1920x1080":
                    return 0.525;
                  default:
                    return 0.525;
                }
              default:
                return 0.525;
            }
          case 24:
            switch (deviceModel) {
              case "Moto C":
                return 0.2625;
              default:
                return 0.28;
            }
          case 27:
            switch (deviceModel) {
              case "CPH1920":
                return 0.2625;
              case "Infinix X650":
                switch (resolution) {
                  case "640x480":
                    return 0.28;
                  default:
                    return 0.4375;
                }
              case "Nokia 1":
                switch (resolution) {
                  case "1280x720":
                    return 0.21;
                  default:
                    return 0.2975;
                }
              case "Redmi 6A":
                switch (resolution) {
                  case "1920x1080":
                    switch (framerate) {
                      case 60:
                        return 0.14875;
                      default:
                        return 0.175;
                    }
                  default:
                    return 0.315;
                }
              default:
                return 0.4375;
            }
          case 28:
            return 0.2625;
          case 29:
            switch (deviceModel) {
              case "CPH2179":
              case "Infinix X688B":
              case "LM-K500":
              case "LM-Q730":
              case "M2006C3LG":
              case "SM-A013M":
              case "SM-A215U1":
              case "TECNO KE5":
                return 0.28;
              case "k61v1_basic_ref":
                switch (resolution) {
                  case "640x480":
                    return 0.35;
                  case "1280x720":
                    return 0.525;
                  default:
                    return 0.7;
                }
              default:
                return 0.7;
            }
          case 30:
            switch (deviceModel) {
              case "M1908C3JGG":
              case "SM-A325F":
                return 0.2625;
              case "SM-A037U":
              case "SM-A107F":
              case "SM-A107M":
              case "SM-A125F":
              case "W-K610-TVM":
              case "vivo 1904":
                return 0.28;
              case "wembley_2GB_full":
                switch (resolution) {
                  case "640x480":
                    return 0.35;
                  case "1280x720":
                    return 0.525;
                  default:
                    return 0.7;
                }
              default:
                return 0.7;
            }
          default:
            return 0.7;
        }
      case "OMX.hisi.video.encoder.avc":
        switch (deviceSdkVersion) {
          case 24:
            switch (resolution) {
              case "640x480":
                return 0.525;
              case "1920x1080":
                return 0.245;
              default:
                return 0.56875;
            }
          case 27:
            switch (deviceModel) {
              case "CLT-L29":
              case "EML-AL00":
                return 0.1925;
              case "COR-L29":
                switch (resolution) {
                  case "1280x720":
                    return 0.1925;
                  case "1920x1080":
                    switch (framerate) {
                      case 60:
                        return 0.13125;
                      default:
                        return 0.14;
                    }
                  default:
                    return 0.245;
                }
              default:
                return 0.245;
            }
          case 28:
            switch (deviceModel) {
              case "HMA-L29":
                return 0.175;
              default:
                return 0.21;
            }
          default:
            return 0.56875;
        }
      case "OMX.qcom.video.encoder.avc":
        switch (deviceSdkVersion) {
          case 23:
            switch (deviceModel) {
              case "LG-AS110":
                switch (resolution) {
                  case "1280x720":
                    return 0.39375;
                  case "1920x1080":
                    return 0.35;
                  default:
                    return 0.48125;
                }
              case "Moto G (4)":
                switch (resolution) {
                  case "1920x1080":
                    switch (framerate) {
                      case 60:
                        return 0.3325;
                      default:
                        return 0.35;
                    }
                  default:
                    return 0.4375;
                }
              case "Moto G Play":
                switch (resolution) {
                  case "1920x1080":
                    switch (framerate) {
                      case 60:
                        return 0.3325;
                      default:
                        return 0.35;
                    }
                  default:
                    return 0.37188;
                }
              case "MotoG3":
                return 0.37188;
              case "Nexus 5":
                switch (resolution) {
                  case "640x480":
                    return 0.3325;
                  case "1920x1080":
                    switch (framerate) {
                      case 60:
                        return 0.28;
                      default:
                        return 0.35;
                    }
                  default:
                    return 0.48125;
                }
              case "Nexus 6P":
                switch (resolution) {
                  case "640x480":
                    return 0.3325;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.35;
                      default:
                        return 0.39375;
                    }
                  default:
                    return 0.6125;
                }
              case "SM-G900F":
                return 0.35;
              case "vivo 1610":
                switch (resolution) {
                  case "1920x1080":
                    switch (framerate) {
                      case 60:
                        return 0.315;
                      default:
                        return 0.4375;
                    }
                  default:
                    return 0.54688;
                }
              default:
                return 0.6125;
            }
          case 24:
            switch (deviceModel) {
              case "Moto G (5)":
                return 0.39375;
              case "Nexus 6P":
                switch (resolution) {
                  case "640x480":
                    return 0.3325;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.35;
                      default:
                        return 0.39375;
                    }
                  case "3840x2160":
                    return 0.20891;
                  default:
                    return 0.39375;
                }
              case "SM-G935V":
                return 0.3325;
              case "XT1650":
                switch (framerate) {
                  case 60:
                    return 0.39375;
                  default:
                    return 0.48125;
                }
              default:
                return 0.48125;
            }
          case 25:
            switch (deviceModel) {
              case "CPH1801":
              case "ONEPLUS A5000":
              case "Redmi 5 Plus":
              case "SM-J510FN":
                return 0.35;
              case "G8142":
              case "Pixel":
              case "Pixel XL":
                switch (resolution) {
                  case "1280x720":
                    return 0.39375;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.48125;
                      default:
                        return 0.525;
                    }
                  default:
                    return 0.525;
                }
              case "LM-X210(G)":
              case "Redmi 4A":
              case "Redmi 4X":
              case "Redmi 5A":
                return 0.39375;
              case "PH-1":
                switch (framerate) {
                  case 60:
                    return 0.39375;
                  default:
                    return 0.48125;
                }
              default:
                return 0.525;
            }
          case 26:
            switch (deviceModel) {
              case "F8331":
                switch (resolution) {
                  case "640x480":
                    return 0.2275;
                  case "1920x1080":
                    switch (framerate) {
                      case 60:
                        return 0.21;
                      default:
                        return 0.245;
                    }
                  case "3840x2160":
                    return 0.08704;
                  default:
                    return 0.245;
                }
              case "F8332":
              case "Pixel":
              case "SM-G935T":
              case "SO-01J":
                switch (resolution) {
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.245;
                      default:
                        return 0.28;
                    }
                  default:
                    return 0.28;
                }
              case "G8342":
              case "MI 5s":
              case "SAMSUNG-SM-G930A":
                switch (resolution) {
                  case "640x480":
                    return 0.2275;
                  case "1920x1080":
                    switch (framerate) {
                      case 60:
                        return 0.21;
                      default:
                        return 0.245;
                    }
                  default:
                    return 0.245;
                }
              case "G8441":
              case "Pixel 2":
                switch (resolution) {
                  case "640x480":
                    return 0.2275;
                  default:
                    return 0.28;
                }
              case "H8314":
              case "LG-H932":
                switch (resolution) {
                  case "1920x1080":
                    return 0.21;
                  default:
                    return 0.2275;
                }
              case "H8324":
                switch (framerate) {
                  case 30:
                    return 0.18375;
                  default:
                    return 0.21;
                }
              case "HTC 10":
              case "SM-G930V":
              case "SM-G955W":
              case "moto g(6)":
                return 0.2275;
              case "HTC U11 plus":
                switch (resolution) {
                  case "640x480":
                    return 0.2275;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.245;
                      default:
                        return 0.28;
                    }
                  default:
                    return 0.28;
                }
              case "MI 5s Plus":
                return 0.2275;
              case "Mi MIX 2":
              case "ONEPLUS A3003":
              case "SAMSUNG-SM-G930AZ":
              case "SM-G892U":
              case "SM-G935R4":
                switch (framerate) {
                  case 60:
                    return 0.21;
                  default:
                    return 0.245;
                }
              case "Nokia 8 Sirocco":
                switch (resolution) {
                  case "640x480":
                    return 0.21;
                  case "1920x1080":
                    switch (framerate) {
                      case 60:
                        return 0.21;
                      default:
                        return 0.245;
                    }
                  default:
                    return 0.28;
                }
              case "SAMSUNG-SM-G891A":
                return 0.28;
              case "SM-A9200":
              case "SM-A920F":
                switch (resolution) {
                  case "1920x1080":
                    switch (framerate) {
                      case 60:
                        return 0.21;
                      default:
                        return 0.245;
                    }
                  default:
                    return 0.28;
                }
              case "SM-G8850":
                switch (resolution) {
                  case "640x480":
                    return 0.28;
                  case "1920x1080":
                    switch (framerate) {
                      case 60:
                        return 0.21;
                      default:
                        return 0.245;
                    }
                  default:
                    return 0.35;
                }
              case "SM-G885S":
                switch (resolution) {
                  case "640x480":
                    return 0.28;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.245;
                      default:
                        return 0.28;
                    }
                  default:
                    return 0.35;
                }
              case "SM-G892A":
                switch (resolution) {
                  case "640x480":
                    return 0.21;
                  case "1920x1080":
                    switch (framerate) {
                      case 60:
                        return 0.21;
                      default:
                        return 0.245;
                    }
                  default:
                    return 0.245;
                }
              case "SM-G950U1":
              case "SM-G955U":
              case "SM-N950U":
                return 0.21;
              case "SM-G955U1":
                switch (resolution) {
                  case "640x480":
                    return 0.21;
                  case "1280x720":
                    return 0.2625;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.245;
                      default:
                        return 0.28;
                    }
                  default:
                    return 0.28;
                }
              case "SM-G960U1":
                switch (resolution) {
                  case "640x480":
                    return 0.21;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.18375;
                      default:
                        return 0.21;
                    }
                  case "3840x2160":
                    return 0.09575;
                  default:
                    return 0.35;
                }
              case "SM-G9650":
                switch (resolution) {
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.18375;
                      default:
                        return 0.21;
                    }
                  default:
                    return 0.21;
                }
              case "SM-G965U1":
                switch (resolution) {
                  case "640x480":
                    return 0.21;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.18375;
                      default:
                        return 0.21;
                    }
                  case "3840x2160":
                    return 0.09575;
                  default:
                    return 0.2275;
                }
              case "SOV33":
                switch (resolution) {
                  case "640x480":
                    return 0.2275;
                  case "3840x2160":
                    return 0.08704;
                  default:
                    return 0.28;
                }
              case "moto e5 play":
                switch (resolution) {
                  case "640x480":
                    return 0.39375;
                  case "1920x1080":
                    return 0.35;
                  default:
                    return 0.48125;
                }
              default:
                return 0.48125;
            }
          case 27:
            switch (deviceModel) {
              case "ASUS_X00TD":
                switch (resolution) {
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.28;
                      default:
                        return 0.4375;
                    }
                  default:
                    return 0.45937;
                }
              case "DUB-LX1":
                return 0.2275;
              case "F-01L":
                switch (resolution) {
                  case "640x480":
                    return 0.315;
                  case "1280x720":
                    return 0.48125;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.28;
                      default:
                        return 0.7;
                    }
                  default:
                    return 0.7;
                }
              case "Lenovo TB-8504F":
              case "moto e5 play":
                switch (resolution) {
                  case "640x480":
                    return 0.39375;
                  case "1920x1080":
                    return 0.35;
                  default:
                    return 0.48125;
                }
              case "Moto Z3 Play":
                switch (resolution) {
                  case "640x480":
                    return 0.315;
                  default:
                    return 0.7;
                }
              case "Nokia 2.1":
                switch (resolution) {
                  case "640x480":
                    return 0.39375;
                  case "1920x1080":
                    return 0.35;
                  default:
                    return 0.48125;
                }
              case "Pixel 2":
                return 0.21;
              case "Pixel 2 XL":
                switch (resolution) {
                  case "640x480":
                    return 0.21;
                  case "1920x1080":
                    switch (framerate) {
                      case 60:
                        return 0.21;
                      default:
                        return 0.245;
                    }
                  default:
                    return 0.245;
                }
              case "Redmi 5 Plus":
                switch (resolution) {
                  case "1920x1080":
                    switch (framerate) {
                      case 60:
                        return 0.245;
                      default:
                        return 0.28;
                    }
                  case "3840x2160":
                    return 0.17409;
                  default:
                    return 0.315;
                }
              case "Redmi Note 5":
                switch (framerate) {
                  case 60:
                    return 0.245;
                  default:
                    return 0.28;
                }
              case "SM-J260MU":
                return 0.3325;
              case "SM-J727V":
                switch (resolution) {
                  case "1920x1080":
                    return 0.21;
                  default:
                    return 0.28;
                }
              case "SM-N960U1":
                switch (resolution) {
                  case "640x480":
                    return 0.21;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.18375;
                      default:
                        return 0.21;
                    }
                  default:
                    return 0.35;
                }
              case "TC77":
                switch (resolution) {
                  case "640x480":
                    return 0.39375;
                  case "1280x720":
                    return 0.6125;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.28;
                      default:
                        return 0.7;
                    }
                  case "3840x2160":
                    return 0.1915;
                  default:
                    return 0.7;
                }
              case "vivo 1805":
                switch (resolution) {
                  case "640x480":
                  case "1920x1080":
                    return 0.21;
                  default:
                    return 0.35;
                }
              default:
                return 0.7;
            }
          case 28:
            switch (deviceModel) {
              case "801SO":
              case "H9493":
                switch (resolution) {
                  case "1920x1080":
                    return 0.21;
                  default:
                    return 0.35;
                }
              case "ASUS_X00TD":
                switch (resolution) {
                  case "1280x720":
                    return 0.4375;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.245;
                      default:
                        return 0.7;
                    }
                  default:
                    return 0.7;
                }
              case "H8216":
                switch (resolution) {
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.35;
                      default:
                        return 0.525;
                    }
                  default:
                    return 0.525;
                }
              case "H8266":
                switch (resolution) {
                  case "1920x1080":
                    return 0.35;
                  default:
                    return 0.525;
                }
              case "H8416":
                switch (resolution) {
                  case "1920x1080":
                    return 0.35;
                  default:
                    return 0.39375;
                }
              case "LM-Q910":
              case "Pixel 2 XL":
              case "SM-T837V":
                switch (resolution) {
                  case "640x480":
                    return 0.21;
                  case "1280x720":
                    return 0.35;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.245;
                      default:
                        return 0.7;
                    }
                  default:
                    return 0.7;
                }
              case "LM-V405":
                switch (resolution) {
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.175;
                      default:
                        return 0.21;
                    }
                  default:
                    return 0.35;
                }
              case "MI 8 Pro":
              case "SC-02K":
                switch (resolution) {
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.175;
                      default:
                        return 0.21;
                    }
                  default:
                    return 0.21;
                }
              case "MI MAX 3":
                switch (resolution) {
                  case "640x480":
                    return 0.28;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.245;
                      default:
                        return 0.7;
                    }
                  default:
                    return 0.7;
                }
              case "Mi A1":
                switch (resolution) {
                  case "1280x720":
                    return 0.2275;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.28;
                      default:
                        return 0.7;
                    }
                  default:
                    return 0.7;
                }
              case "Nokia 7.2":
              case "Redmi Note 6 Pro":
                switch (resolution) {
                  case "640x480":
                    return 0.28;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.245;
                      default:
                        return 0.7;
                    }
                  case "3840x2160":
                    return 0.1915;
                  default:
                    return 0.7;
                }
              case "Nokia 9":
                switch (framerate) {
                  case 30:
                    return 0.175;
                  default:
                    return 0.21;
                }
              case "ONEPLUS A5010":
                switch (resolution) {
                  case "640x480":
                    return 0.21;
                  default:
                    return 0.2275;
                }
              case "ONEPLUS A6013":
                switch (resolution) {
                  case "640x480":
                    return 0.21;
                  case "1920x1080":
                    return 0.175;
                  default:
                    return 0.35;
                }
              case "Pixel 3":
                switch (resolution) {
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.175;
                      default:
                        return 0.21;
                    }
                  case "3840x2160":
                    return 0.09575;
                  default:
                    return 0.21;
                }
              case "Pixel 3a":
                switch (framerate) {
                  case 30:
                    return 0.245;
                  default:
                    return 0.7;
                }
              case "SC-03K":
                switch (resolution) {
                  case "640x480":
                  case "1920x1080":
                    return 0.21;
                  default:
                    return 0.35;
                }
              case "SH-01L":
                switch (resolution) {
                  case "640x480":
                    return 0.315;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.28;
                      default:
                        return 0.7;
                    }
                  default:
                    return 0.7;
                }
              case "SH-03K":
                switch (resolution) {
                  case "1280x720":
                    return 0.525;
                  case "1920x1080":
                    switch (framerate) {
                      case 60:
                        return 0.7;
                      default:
                        return 1.05;
                    }
                  default:
                    return 1.05;
                }
              case "SHV39":
                switch (resolution) {
                  case "640x480":
                    return 0.2625;
                  case "1280x720":
                    return 0.37188;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.245;
                      default:
                        return 0.7;
                    }
                  default:
                    return 0.7;
                }
              case "SM-J415F":
              case "SM-J415FN":
              case "U693CL":
                return 0.3325;
              case "SM-N950U1":
                return 0.2275;
              case "SM-T720":
                switch (resolution) {
                  case "640x480":
                    return 0.21;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.18375;
                      default:
                        return 0.21;
                    }
                  default:
                    return 0.35;
                }
              case "SM-T827V":
                switch (resolution) {
                  case "640x480":
                    return 0.2275;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.245;
                      default:
                        return 0.7;
                    }
                  default:
                    return 0.7;
                }
              case "moto g(7) plus":
                switch (resolution) {
                  case "640x480":
                    return 0.315;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.28;
                      default:
                        return 0.7;
                    }
                  case "3840x2160":
                    return 0.20891;
                  default:
                    return 0.7;
                }
              default:
                return 1.05;
            }
          case 29:
            switch (deviceModel) {
              case "CPH1931":
              case "Redmi Note 9 Pro":
                return 0.2275;
              case "Pixel 2 XL":
                switch (resolution) {
                  case "640x480":
                    return 0.315;
                  case "1280x720":
                    return 0.48125;
                  default:
                    return 0.7;
                }
              case "Pixel XL":
              case "SM-G960U1":
                return 0.2625;
              case "Redmi 8":
                return 0.35;
              case "SM-G981U1":
                switch (resolution) {
                  case "1280x720":
                    return 0.35;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.2275;
                      default:
                        return 0.7;
                    }
                  default:
                    return 0.7;
                }
              case "SM-N9750":
                return 0.21;
              case "moto g(7)":
              case "moto g(7) play":
                return 0.315;
              default:
                return 0.7;
            }
          case 30:
            switch (deviceModel) {
              case "CPH2127":
              case "M2101K7AG":
              case "Redmi Note 8":
              case "Redmi Note 9S":
              case "SM-A715F":
                return 0.2275;
              case "SM-A207F":
              case "SM-M115F":
              case "SM-S115DL":
                return 0.315;
              case "SM-F916U1":
                switch (resolution) {
                  case "640x480":
                    return 0.175;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.2275;
                      default:
                        return 0.35;
                    }
                  default:
                    return 0.35;
                }
              case "SM-N986U":
                return 0.1925;
              default:
                return 0.35;
            }
          case 31:
            return 0.21;
          case 32:
            switch (resolution) {
              case "640x480":
                return 0.14;
              case "1920x1080":
                switch (framerate) {
                  case 30:
                    return 0.2275;
                  default:
                    return 0.35;
                }
              default:
                return 0.35;
            }
          default:
            return 1.05;
        }
      case "OMX.qcom.video.encoder.hevc":
        switch (deviceSdkVersion) {
          case 26:
            switch (deviceModel) {
              case "F8331":
              case "MI 5s":
                return 0.105;
              default:
                return 0.1575;
            }
          case 27:
            return 0.1575;
          case 28:
            switch (deviceModel) {
              case "Nokia 7.2":
                return 0.1575;
              default:
                return 0.35;
            }
          default:
            return 0.35;
        }
      case "c2.exynos.h264.encoder":
        switch (deviceSdkVersion) {
          case 31:
            switch (resolution) {
              case "1280x720":
                return 0.245;
              case "1920x1080":
                return 0.7;
              default:
                return 0.7;
            }
          case 32:
            switch (deviceModel) {
              case "Pixel 6":
                switch (resolution) {
                  case "640x480":
                    return 0.28;
                  case "1280x720":
                    return 0.245;
                  case "1920x1080":
                    return 0.7;
                  default:
                    return 0.7;
                }
              case "Pixel 6 Pro":
                switch (resolution) {
                  case "1280x720":
                    return 0.245;
                  case "1920x1080":
                    return 0.7;
                  default:
                    return 0.7;
                }
              default:
                return 0.7;
            }
          case 33:
            return 0.245;
          default:
            return 0.7;
        }
      case "c2.qti.avc.encoder":
        switch (deviceSdkVersion) {
          case 29:
            switch (deviceModel) {
              case "Pixel 3":
                switch (resolution) {
                  case "640x480":
                    return 0.21;
                  case "1280x720":
                    return 0.245;
                  default:
                    return 0.7;
                }
              case "Pixel 4":
                switch (resolution) {
                  case "640x480":
                  case "1280x720":
                    return 0.1925;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.21;
                      default:
                        return 1.4;
                    }
                  case "3840x2160":
                    return 0.08704;
                  default:
                    return 1.4;
                }
              default:
                return 1.4;
            }
          case 30:
            switch (deviceModel) {
              case "Pixel 4":
                switch (resolution) {
                  case "1280x720":
                    return 0.21;
                  default:
                    return 0.35;
                }
              case "Pixel 4 XL":
                return 0.21;
              case "Pixel 5":
                switch (resolution) {
                  case "640x480":
                    return 0.20125;
                  case "1280x720":
                    return 0.2275;
                  default:
                    return 0.28;
                }
              case "Pixel 5a":
                switch (resolution) {
                  case "1280x720":
                    return 0.2275;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.21;
                      default:
                        return 0.28;
                    }
                  default:
                    return 0.28;
                }
              case "SM-F711U1":
                switch (resolution) {
                  case "640x480":
                    return 0.21;
                  case "1280x720":
                    return 0.2275;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.245;
                      default:
                        return 0.7;
                    }
                  default:
                    return 0.7;
                }
              case "SM-F926U1":
                switch (resolution) {
                  case "640x480":
                    return 0.21;
                  case "1280x720":
                    return 0.2275;
                  default:
                    return 0.7;
                }
              case "SM-G991U1":
                return 0.2275;
              default:
                return 0.7;
            }
          case 31:
            switch (deviceModel) {
              case "Pixel 3":
              case "Pixel 3 XL":
              case "Pixel 3a":
              case "Pixel 3a XL":
                return 0.245;
              case "Pixel 4":
                return 0.20563;
              case "Pixel 4a":
              case "SM-G991U1":
                return 0.21;
              case "Pixel 4a (5G)":
              case "Pixel 5a":
              case "SM-F711U1":
              case "SM-F926U1":
              case "SM-G998U1":
                return 0.2275;
              case "Pixel 5":
                switch (resolution) {
                  case "1280x720":
                    return 0.2275;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.21;
                      default:
                        return 0.28;
                    }
                  default:
                    return 0.28;
                }
              case "SM-A528B":
                return 0.20125;
              case "SM-S908U1":
                return 0.14;
              default:
                return 0.28;
            }
          case 32:
            switch (deviceModel) {
              case "Pixel 4":
                return 0.21;
              case "Pixel 5":
                switch (resolution) {
                  case "1280x720":
                    return 0.2275;
                  case "1920x1080":
                    switch (framerate) {
                      case 30:
                        return 0.21;
                      default:
                        return 0.28;
                    }
                  default:
                    return 0.28;
                }
              default:
                return 0.28;
            }
          default:
            return 1.4;
        }
      case "c2.qti.hevc.encoder":
        switch (deviceModel) {
          case "Pixel 4":
            return 0.0875;
          default:
            return 0.105;
        }
      default:
        return 1.4;
    }
  }
}
