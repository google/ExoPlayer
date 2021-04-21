(function() {
  var SOURCES = window.TEXT_VARIABLES.sources;
  var TOC_SELECTOR = window.TEXT_VARIABLES.site.toc.selectors;
  window.Lazyload.js(SOURCES.jquery, function() {
    var $window = $(window);
    var $articleContent = $('.js-article-content');
    var $tocRoot = $('.js-toc-root'), $col2 = $('.js-col-aside');
    var toc;
    var tocDisabled = false;
    var hasSidebar = $('.js-page-root').hasClass('layout--page--sidebar');
    var hasToc = $articleContent.find(TOC_SELECTOR).length > 0;

    function disabled() {
      return $col2.css('display') === 'none' || !hasToc;
    }

    tocDisabled = disabled();

    toc = $tocRoot.toc({
      selectors: TOC_SELECTOR,
      container: $articleContent,
      scrollTarget: hasSidebar ? '.js-page-main' : null,
      scroller: hasSidebar ? '.js-page-main' : null,
      disabled: tocDisabled
    });

    $window.on('resize', window.throttle(function() {
      tocDisabled = disabled();
      toc && toc.setOptions({
        disabled: tocDisabled
      });
    }, 100));

  });
})();