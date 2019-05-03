(function() {
  var SOURCES = window.TEXT_VARIABLES.sources;
  window.Lazyload.js(SOURCES.jquery, function() {
    function scrollToAnchor(anchor, duration, callback) {
      var $root = this;
      $root.animate({ scrollTop: $(anchor).position().top }, duration, function() {
        window.history.replaceState(null, '', window.location.href.split('#')[0] + anchor);
        callback && callback();
      });
    }
    $.fn.scrollToAnchor = scrollToAnchor;
  });
})();