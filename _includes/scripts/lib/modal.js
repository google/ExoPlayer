(function() {
  var SOURCES = window.TEXT_VARIABLES.sources;
  window.Lazyload.js(SOURCES.jquery, function() {
    var $body = $('body'), $window = $(window);
    var $pageRoot = $('.js-page-root'), $pageMain = $('.js-page-main');
    var activeCount = 0;
    function modal(options) {
      var $root = this, visible, onChange, hideWhenWindowScroll = false;
      var scrollTop;
      function setOptions(options) {
        var _options = options || {};
        visible = _options.initialVisible === undefined ? false : show;
        onChange = _options.onChange;
        hideWhenWindowScroll = _options.hideWhenWindowScroll;
      }
      function init() {
        setState(visible);
      }
      function setState(isShow) {
        if (isShow === visible) {
          return;
        }
        visible = isShow;
        if (visible) {
          activeCount++;
          scrollTop = $(window).scrollTop() || $pageMain.scrollTop();
          $root.addClass('modal--show');
          $pageMain.scrollTop(scrollTop);
          activeCount === 1 && ($pageRoot.addClass('show-modal'), $body.addClass('of-hidden'));
          hideWhenWindowScroll && window.hasEvent('touchstart') && $window.on('scroll', hide);
          $window.on('keyup', handleKeyup);
        } else {
          activeCount > 0 && activeCount--;
          $root.removeClass('modal--show');
          $window.scrollTop(scrollTop);
          activeCount === 0 && ($pageRoot.removeClass('show-modal'), $body.removeClass('of-hidden'));
          hideWhenWindowScroll && window.hasEvent('touchstart') && $window.off('scroll', hide);
          $window.off('keyup', handleKeyup);
        }
        onChange && onChange(visible);
      }
      function show() {
        setState(true);
      }
      function hide() {
        setState(false);
      }
      function handleKeyup(e) {
        // Char Code: 27  ESC
        if (e.which ===  27) {
          hide();
        }
      }
      setOptions(options);
      init();
      return {
        show: show,
        hide: hide,
        $el: $root
      };
    }
    $.fn.modal = modal;
  });
})();