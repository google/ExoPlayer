$(document).ready(function() {
  // prep nav expandos
  var pagePath = document.location.pathname;
  if (pagePath.indexOf(SITE_ROOT) == 0) {
    pagePath = pagePath.substr(SITE_ROOT.length);
    if (pagePath == '' || pagePath.charAt(pagePath.length - 1) == '/') {
      pagePath += 'index.html';
    }
  }

  if (SITE_ROOT.match(/\.\.\//) || SITE_ROOT == '') {
    // If running locally, SITE_ROOT will be a relative path, so account for that by
    // finding the relative URL to this page. This will allow us to find links on the page
    // leading back to this page.
    var pathParts = pagePath.split('/');
    var relativePagePathParts = [];
    var upDirs = (SITE_ROOT.match(/(\.\.\/)+/) || [''])[0].length / 3;
    for (var i = 0; i < upDirs; i++) {
      relativePagePathParts.push('..');
    }
    for (var i = 0; i < upDirs; i++) {
      relativePagePathParts.push(pathParts[pathParts.length - (upDirs - i) - 1]);
    }
    relativePagePathParts.push(pathParts[pathParts.length - 1]);
    pagePath = relativePagePathParts.join('/');
  } else {
    // Otherwise the page path should be an absolute URL.
    pagePath = SITE_ROOT + pagePath;
  }

  // select current page in sidenav and set up prev/next links if they exist
  var $selNavLink = $('.nav-y').find('a[href="' + pagePath + '"]');
  if ($selNavLink.length) {
    $selListItem = $selNavLink.closest('li');

    $selListItem.addClass('selected');
    $selListItem.closest('li>ul').addClass('expanded');

    // set up prev links
    var $prevLink = [];
    var $prevListItem = $selListItem.prev('li');
    if ($prevListItem.length) {
      if ($prevListItem.hasClass('nav-section')) {
        // jump to last topic of previous section
        $prevLink = $prevListItem.find('a:last');
      } else {
        // jump to previous topic in this section
        $prevLink = $prevListItem.find('a:eq(0)');
      }
    } else {
      // jump to this section's index page (if it exists)
      $prevLink = $selListItem.parents('li').find('a');
    }

    if ($prevLink.length) {
      var prevHref = $prevLink.attr('href');
      if (prevHref == SITE_ROOT + 'index.html') {
        // Don't show Previous when it leads to the homepage
        $('.prev-page-link').hide();
      } else {
        $('.prev-page-link').attr('href', prevHref).show();
      }
    } else {
      $('.prev-page-link').hide();
    }

    // set up next links
    var $nextLink = [];
    if ($selListItem.hasClass('nav-section')) {
      // we're on an index page, jump to the first topic
      $nextLink = $selListItem.find('ul').find('a:eq(0)')
    } else {
      // jump to the next topic in this section (if it exists)
      $nextLink = $selListItem.next('li').find('a:eq(0)');
      if (!$nextLink.length) {
        // no more topics in this section, jump to the first topic in the next section
        $nextLink = $selListItem.parents('li').next('li.nav-section').find('a:eq(0)');
      }
    }
    if ($nextLink.length) {
      $('.next-page-link').attr('href', $nextLink.attr('href')).show();
    } else {
      $('.next-page-link').hide();
    }
  }

  // Set up expand/collapse behavior
  $('.nav-y li').has('ul').click(function() {
    if ($(this).hasClass('expanded')) {
      return;
    }

    // hide other
    var $old = $('.nav-y li.expanded');
    if ($old.length) {
      var $oldUl = $old.children('ul');
      $oldUl.css('height', $oldUl.height() + 'px');
      window.setTimeout(function() {
        $oldUl
            .addClass('animate-height')
            .css('height', '');
      }, 0);
      $old.removeClass('expanded');
    }

    // show me
    $(this).addClass('expanded');
    var $ul = $(this).children('ul');
    var expandedHeight = $ul.height();
    $ul
        .removeClass('animate-height')
        .css('height', 0);
    window.setTimeout(function() {
      $ul
          .addClass('animate-height')
          .css('height', expandedHeight + 'px');
    }, 0);
  });

  // Stop expand/collapse behavior when clicking on nav section links (since we're navigating away
  // from the page)
  $('.nav-y li').has('ul').find('a:eq(0)').click(function(evt) {
    window.location.href = $(this).attr('href');
    return false;
  });

  // Set up play-on-hover <video> tags.
  $('video.play-on-hover').bind('click', function(){
    $(this).get(0).load(); // in case the video isn't seekable
    $(this).get(0).play();
  });

  // Set up tooltips
  var TOOLTIP_MARGIN = 10;
  $('acronym').each(function() {
    var $target = $(this);
    var $tooltip = $('<div>')
        .addClass('tooltip-box')
        .text($target.attr('title'))
        .hide()
        .appendTo('body');
    $target.removeAttr('title');

    $target.hover(function() {
      // in
      var targetRect = $target.offset();
      targetRect.width = $target.width();
      targetRect.height = $target.height();

      $tooltip.css({
        left: targetRect.left,
        top: targetRect.top + targetRect.height + TOOLTIP_MARGIN
      });
      $tooltip.addClass('below');
      $tooltip.show();
    }, function() {
      // out
      $tooltip.hide();
    });
  });

  // Set up <h2> deeplinks
  $('h2').click(function() {
    var id = $(this).attr('id');
    if (id) {
      document.location.hash = id;
    }
  });

  // Set up fixed navbar
  var navBarIsFixed = false;
  $(window).scroll(function() {
    var scrollTop = $(window).scrollTop();
    var navBarShouldBeFixed = (scrollTop > (100 - 40));
    if (navBarIsFixed != navBarShouldBeFixed) {
      if (navBarShouldBeFixed) {
        $('#nav')
            .addClass('fixed')
            .prependTo('#page-container');
      } else {
        $('#nav')
            .removeClass('fixed')
            .prependTo('#nav-container');
      }
      navBarIsFixed = navBarShouldBeFixed;
    }
  });
});