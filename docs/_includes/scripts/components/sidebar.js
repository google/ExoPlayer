(function() {
  var SOURCES = window.TEXT_VARIABLES.sources;

  window.Lazyload.js(SOURCES.jquery, function() {
    var $pageMask = $('.js-page-mask');
    var $pageRoot = $('.js-page-root');
    var $pageMain = $('.js-page-main');
    var $sidebarShow = $('.js-sidebar-show');
    var $sidebarHide = $('.js-sidebar-hide');

    function freeze(e) {
      if (e.target === $pageMask[0]) {
        e.preventDefault();
      }
    }
    function stopBodyScrolling(bool) {
      if (bool === true) {
        window.addEventListener('touchmove', freeze, { passive: false });
      } else {
        window.removeEventListener('touchmove', freeze, { passive: false });
      }
    }

    $sidebarShow.on('click', function() {
      stopBodyScrolling(true); $pageRoot.addClass('show-sidebar');
    });
    $sidebarHide.on('click', function() {
      stopBodyScrolling(false); $pageRoot.removeClass('show-sidebar');
    });
  });
})();