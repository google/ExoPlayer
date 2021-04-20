(function() {
  var SOURCES = window.TEXT_VARIABLES.sources;
  window.Lazyload.js(SOURCES.jquery, function() {
    function toc(options) {
      var $root = this, $window = $(window), $scrollTarget, $scroller, $tocUl = $('<ul class="toc toc--ellipsis"></ul>'), $tocLi, $headings, $activeLast, $activeCur,
        selectors = 'h1,h2,h3', container = 'body', scrollTarget = window, scroller = 'html, body', disabled = false,
        headingsPos, scrolling = false, hasRendered = false, hasInit = false;

      function setOptions(options) {
        var _options = options || {};
        _options.selectors && (selectors = _options.selectors);
        _options.container && (container = _options.container);
        _options.scrollTarget && (scrollTarget = _options.scrollTarget);
        _options.scroller && (scroller = _options.scroller);
        _options.disabled !== undefined && (disabled = _options.disabled);
        $headings = $(container).find(selectors).filter('[id]');
        $scrollTarget = $(scrollTarget);
        $scroller = $(scroller);
      }
      function calc() {
        headingsPos = [];
        $headings.each(function() {
          headingsPos.push(Math.floor($(this).position().top));
        });
      }
      function setState(element, disabled) {
        var scrollTop = $scrollTarget.scrollTop(), i;
        if (disabled || !headingsPos || headingsPos.length < 1) { return; }
        if (element) {
          $activeCur = element;
        } else {
          for (i = 0; i < headingsPos.length; i++) {
            if (scrollTop >= headingsPos[i]) {
              $activeCur = $tocLi.eq(i);
            } else {
              $activeCur || ($activeCur = $tocLi.eq(i));
              break;
            }
          }
        }
        $activeLast && $activeLast.removeClass('active');
        ($activeLast = $activeCur).addClass('active');
      }
      function render() {
        if(!hasRendered) {
          $root.append($tocUl);
          $headings.each(function() {
            var $this = $(this);
            $tocUl.append($('<li></li>').addClass('toc-' + $this.prop('tagName').toLowerCase())
              .append($('<a></a>').text($this.text()).attr('href', '#' + $this.prop('id'))));
          });
          $tocLi = $tocUl.children('li');
          $tocUl.on('click', 'a', function(e) {
            e.preventDefault();
            var $this = $(this);
            scrolling = true;
            setState($this.parent());
            $scroller.scrollToAnchor($this.attr('href'), 400, function() {
              scrolling = false;
            });
          });
        }
        hasRendered = true;
      }
      function init() {
        var interval, timeout;
        if(!hasInit) {
          render(); calc(); setState(null, scrolling);
          // run calc every 100 millisecond
          interval = setInterval(function() {
            calc();
          }, 100);
          timeout = setTimeout(function() {
            clearInterval(interval);
          }, 45000);
          window.pageLoad.then(function() {
            setTimeout(function() {
              clearInterval(interval);
              clearTimeout(timeout);
            }, 3000);
          });
          $scrollTarget.on('scroll', function() {
            disabled || setState(null, scrolling);
          });
          $window.on('resize', window.throttle(function() {
            if (!disabled) {
              render(); calc(); setState(null, scrolling);
            }
          }, 100));
        }
        hasInit = true;
      }

      setOptions(options);
      if (!disabled) {
        init();
      }
      $window.on('resize', window.throttle(function() {
        init();
      }, 200));
      return {
        setOptions: setOptions
      };
    }
    $.fn.toc = toc;
  });
})();