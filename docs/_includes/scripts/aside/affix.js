(function() {
  var SOURCES = window.TEXT_VARIABLES.sources;
  window.Lazyload.js(SOURCES.jquery, function() {
    var $window = $(window), $pageFooter = $('.js-page-footer');
    var $pageAside = $('.js-page-aside');
    var affix;
    var tocDisabled = false;
    var hasSidebar = $('.js-page-root').hasClass('layout--page--sidebar');

    affix = $pageAside.affix({
      offsetBottom: $pageFooter.outerHeight(),
      scrollTarget: hasSidebar ? '.js-page-main' : null,
      scroller: hasSidebar ? '.js-page-main' : null,
      scroll: hasSidebar ? $('.js-page-main').children() : null,
      disabled: tocDisabled
    });

    $window.on('resize', window.throttle(function() {
      affix && affix.setOptions({
        disabled: tocDisabled
      });
    }, 100));

    window.pageAsideAffix = affix;
  });
})();