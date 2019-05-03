(function() {
  {%- include scripts/lib/swiper.js -%}
  var SOURCES = window.TEXT_VARIABLES.sources;
  window.Lazyload.js(SOURCES.jquery, function() {
    var template =
      '<div class="swiper gallery__swiper">' +
        '<div class="swiper__wrapper">' +
        '</div>' +
        '<div class="swiper__button swiper__button--prev fas fa-chevron-left"></div>' +
        '<div class="swiper__button swiper__button--next fas fa-chevron-right"></div>' +
      '</div>';
    function setState($item, zoom, translate) {
      $item.css('transform', 'scale(' + zoom + ') translate(' + translate.x +  'px,' + translate.y + 'px)');
    }
    function Gallery(root, items) {
      this.$root = $(root);
      this.$swiper = null;
      this.$swiperWrapper = null;
      this.$activeItem = null;
      this.$items = [];
      this.contentWidth = 0;
      this.contentHeight = 0;
      this.swiper = null;
      this.items = items;
      this.disabled = false;
      this.curIndex = 0;
      this.touchCenter = null;
      this.lastTouchCenter = null;
      this.zoomRect = null;
      this.lastZoomRect = null;
      this.lastTranslate = null;
      this.translate = null;
      this.lastZoom = 1;
      this.preZoom = 1;
      this.zoom = 1;
    }
    Gallery.prototype.init = function() {
      var i, item, items = this.items, size, self = this, touchstartFingerCount = 0;
      this.$root.append(template);
      this.$swiper = this.$root.find('.gallery__swiper');
      this.$swiperWrapper = this.$root.find('.swiper__wrapper');
      this.contentWidth = this.$swiperWrapper && this.$swiperWrapper.width();
      this.contentHeight = this.$swiperWrapper && this.$swiperWrapper.height();
      for (i = 0; i < items.length; i++) {
        item = items[i];
        size = this._calculateImageSize(item.w, item.h);
        this.$items.push($(
          '<div class="swiper__slide">' +
            '<div class="gallery-item">' +
              '<div class="gallery-item__content">' +
                '<img src="' + item.src + '" style="width:' + size.w + 'px;height:' + size.h +  'px"/>' +
              '</div>' +
            '</div>' +
          '</div>'
        ));
      }
      this.$swiperWrapper && this.$swiperWrapper.append(this.$items);
      this.swiper = this.$swiper && this.$swiper.swiper({
        onChangeEnd: function() {
          self._handleChangeEnd.apply(self, Array.prototype.slice.call(arguments));
        }
      });
      $(window).on('resize', function() {
        if (self.disabled) { return; }
        self._resizeImageSize();
      });
      // Char Code: 37  ⬅, 39  ➡
      $(window).on('keyup', function(e) {
        if (window.isFormElement(e.target || e.srcElement) || self.disabled) { return; }
        if (e.which === 37) {
          self.swiper && self.swiper.previous();
        } else if (e.which === 39) {
          self.swiper && self.swiper.next();
        }
      });
      function getRect(touch0, touch1) {
        return {
          o: {
            x: (touch0.pageX + touch1.pageX) / 2,
            y: (touch0.pageY + touch1.pageY) / 2
          },
          w: Math.abs(touch0.pageX - touch1.pageX),
          h: Math.abs(touch0.pageY - touch1.pageY)
        };
      }
      function getTouches(e) {
        return e.touches || e;
      }
      function getTouchesCount(e) {
        if (e.touches) {
          return e.touches.length;
        } else {
          return 1;
        }
      }
      this.$swiperWrapper.on('touchstart', function(e) {
        var touch0, touch1, rect;
        touchstartFingerCount = getTouchesCount(e);
        if (touchstartFingerCount > 1) {
          touch0 = e.touches[0];
          touch1 = e.touches[1];
          rect = getRect(touch0, touch1);
          self.lastZoomRect = { w: rect.w, h: rect.h };
          self.lastTouchCenter = rect.o;
        } else {
          var touch = getTouches(e)[0];
          self.lastTouchCenter = { x: touch.pageX, y: touch.pageY };
        }
      });
      this.$swiperWrapper.on('touchmove', function(e) {
        if (touchstartFingerCount === getTouchesCount(e)) {
          if (touchstartFingerCount > 1) {
            var touch0 = e.touches[0];
            var touch1 = e.touches[1];
            var rect = getRect(touch0, touch1);
            self.zoomRect = { w: rect.w, h: rect.h };
            self.touchCenter = rect.o;
            self._zoom(); self._translate();
            setState(self.$activeItem, self.zoom, self.translate);
          } else {
            var touch = getTouches(e)[0];
            self.touchCenter = { x: touch.pageX, y: touch.pageY };
            self._translate();
            setState(self.$activeItem, self.zoom, self.translate);
          }
        }
      });
      this.$swiperWrapper.on('touchend', function(e) {
        self.lastZoom = self.zoom;
        self.lastTranslate = self.translate;
        touchstartFingerCount = 0;
      });
      this.$root.on('touchmove', function(e) {
        if (self.disabled) { return; }
        e.preventDefault();
      });
    };

    Gallery.prototype._translate = function() {
      this.translate = this.touchCenter && this.lastTouchCenter && this.lastTranslate ? {
        x: (this.touchCenter.x - this.lastTouchCenter.x) / this.zoom + this.lastTranslate.x,
        y: (this.touchCenter.y - this.lastTouchCenter.y) / this.zoom + this.lastTranslate.y
      } : { x: 0, y: 0 };
    }
    Gallery.prototype._zoom = function() {
      this.zoom = (this.zoomRect.w + this.zoomRect.h) / (this.lastZoomRect.w + this.lastZoomRect.h) * this.lastZoom;
      this.zoom > 1 ? this.$activeItem.addClass('zoom') : this.$activeItem.removeClass('zoom');
      this.preZoom = this.zoom;
    }

    Gallery.prototype._calculateImageSize = function(w, h) {
      var scale = 1;
      if (this.contentWidth > 0 && this.contentHeight > 0 && w > 0 && h > 0) {
        scale = Math.min(
          Math.min(w, this.contentWidth) / w,
          Math.min(h, this.contentHeight) / h);
      }
      return { w: Math.floor(w * scale), h: Math.floor(h * scale) };
    };

    Gallery.prototype._resizeImageSize = function() {
      var i, $item, $items = this.$items, item, size;
      this.contentWidth = this.$swiperWrapper && this.$swiperWrapper.width();
      this.contentHeight = this.$swiperWrapper && this.$swiperWrapper.height();
      if ($items.length < 1) { return; }
      for (i = 0; i < $items.length; i++) {
        item = this.items[i], $item = $items[i];
        size = this._calculateImageSize(item.w, item.h);
        item.width = size.w; item.height = size.h;
        $item && $item.find('img').css({ width: size.w, height: size.h });
      }
    };
    Gallery.prototype._handleChangeEnd = function(index, $dom, preIndex, $preDom) {
      this.curIndex = index;
      this.lastZoomRect = null; this.lastZoomRect = null;
      this.lastTranslate = this.translate = { x: 0, y:0 };
      this.lastZoom = this.preZoom = this.zoom = 1;
      this.$activeItem = $dom.find('.gallery-item__content');
      setState($preDom.find('.gallery-item__content'), this.zoom, this.translate);
    };

    Gallery.prototype.refresh = function() {
      this.swiper && this.swiper.refresh();
      this._resizeImageSize();
    };
    Gallery.prototype.setOptions = function(options) {
      this.disabled = options.disabled;
      this.swiper && this.swiper.setOptions(options);
    };
    window.Gallery = Gallery;
  });
})();