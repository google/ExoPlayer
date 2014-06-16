var classesNav;
var devdocNav;
var sidenav;
var cookie_namespace = 'android_developer';
var NAV_PREF_TREE = "tree";
var NAV_PREF_PANELS = "panels";
var nav_pref;
var isMobile = false; // true if mobile, so we can adjust some layout
var mPagePath; // initialized in ready() function

var basePath = getBaseUri(location.pathname);
var SITE_ROOT = toRoot + basePath.substring(1,basePath.indexOf("/",1));
var GOOGLE_DATA; // combined data for google service apis, used for search suggest

// Ensure that all ajax getScript() requests allow caching
$.ajaxSetup({
  cache: true
});

/******  ON LOAD SET UP STUFF *********/

var navBarIsFixed = false;
$(document).ready(function() {

  // load json file for JD doc search suggestions
  $.getScript(toRoot + 'jd_lists_unified.js');
  // load json file for Android API search suggestions
  $.getScript(toRoot + 'reference/lists.js');
  // load json files for Google services API suggestions
  $.getScript(toRoot + 'reference/gcm_lists.js', function(data, textStatus, jqxhr) {
      // once the GCM json (GCM_DATA) is loaded, load the GMS json (GMS_DATA) and merge the data
      if(jqxhr.status === 200) {
          $.getScript(toRoot + 'reference/gms_lists.js', function(data, textStatus, jqxhr) {
              if(jqxhr.status === 200) {
                  // combine GCM and GMS data
                  GOOGLE_DATA = GMS_DATA;
                  var start = GOOGLE_DATA.length;
                  for (var i=0; i<GCM_DATA.length; i++) {
                      GOOGLE_DATA.push({id:start+i, label:GCM_DATA[i].label,
                              link:GCM_DATA[i].link, type:GCM_DATA[i].type});
                  }
              }
          });
      }
  });

  // setup keyboard listener for search shortcut
  $('body').keyup(function(event) {
    if (event.which == 191) {
      $('#search_autocomplete').focus();
    }
  });

  // init the fullscreen toggle click event
  $('#nav-swap .fullscreen').click(function(){
    if ($(this).hasClass('disabled')) {
      toggleFullscreen(true);
    } else {
      toggleFullscreen(false);
    }
  });

  // initialize the divs with custom scrollbars
  $('.scroll-pane').jScrollPane( {verticalGutter:0} );

  // add HRs below all H2s (except for a few other h2 variants)
  $('h2').not('#qv h2').not('#tb h2').not('.sidebox h2').not('#devdoc-nav h2').not('h2.norule').css({marginBottom:0}).after('<hr/>');

  // set up the search close button
  $('.search .close').click(function() {
    $searchInput = $('#search_autocomplete');
    $searchInput.attr('value', '');
    $(this).addClass("hide");
    $("#search-container").removeClass('active');
    $("#search_autocomplete").blur();
    search_focus_changed($searchInput.get(), false);
    hideResults();
  });

  // Set up quicknav
  var quicknav_open = false;
  $("#btn-quicknav").click(function() {
    if (quicknav_open) {
      $(this).removeClass('active');
      quicknav_open = false;
      collapse();
    } else {
      $(this).addClass('active');
      quicknav_open = true;
      expand();
    }
  })

  var expand = function() {
   $('#header-wrap').addClass('quicknav');
   $('#quicknav').stop().show().animate({opacity:'1'});
  }

  var collapse = function() {
    $('#quicknav').stop().animate({opacity:'0'}, 100, function() {
      $(this).hide();
      $('#header-wrap').removeClass('quicknav');
    });
  }


  //Set up search
  $("#search_autocomplete").focus(function() {
    $("#search-container").addClass('active');
  })
  $("#search-container").mouseover(function() {
    $("#search-container").addClass('active');
    $("#search_autocomplete").focus();
  })
  $("#search-container").mouseout(function() {
    if ($("#search_autocomplete").is(":focus")) return;
    if ($("#search_autocomplete").val() == '') {
      setTimeout(function(){
        $("#search-container").removeClass('active');
        $("#search_autocomplete").blur();
      },250);
    }
  })
  $("#search_autocomplete").blur(function() {
    if ($("#search_autocomplete").val() == '') {
      $("#search-container").removeClass('active');
    }
  })


  // prep nav expandos
  var pagePath = document.location.pathname;
  // account for intl docs by removing the intl/*/ path
  if (pagePath.indexOf("/intl/") == 0) {
    pagePath = pagePath.substr(pagePath.indexOf("/",6)); // start after intl/ to get last /
  }

  if (pagePath.indexOf(SITE_ROOT) == 0) {
    if (pagePath == '' || pagePath.charAt(pagePath.length - 1) == '/') {
      pagePath += 'index.html';
    }
  }

  // Need a copy of the pagePath before it gets changed in the next block;
  // it's needed to perform proper tab highlighting in offline docs (see rootDir below)
  var pagePathOriginal = pagePath;
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
    // Otherwise the page path is already an absolute URL
  }

  // Highlight the header tabs...
  // highlight Design tab
  if ($("body").hasClass("design")) {
    $("#header li.design a").addClass("selected");

  // highlight Develop tab
  } else if ($("body").hasClass("develop") || $("body").hasClass("google")) {
    $("#header li.develop a").addClass("selected");
    // In Develop docs, also highlight appropriate sub-tab
    var rootDir = pagePathOriginal.substring(1,pagePathOriginal.indexOf('/', 1));
    if (rootDir == "training") {
      $("#nav-x li.training a").addClass("selected");
    } else if (rootDir == "guide") {
      $("#nav-x li.guide a").addClass("selected");
    } else if (rootDir == "reference") {
      // If the root is reference, but page is also part of Google Services, select Google
      if ($("body").hasClass("google")) {
        $("#nav-x li.google a").addClass("selected");
      } else {
        $("#nav-x li.reference a").addClass("selected");
      }
    } else if ((rootDir == "tools") || (rootDir == "sdk")) {
      $("#nav-x li.tools a").addClass("selected");
    } else if ($("body").hasClass("google")) {
      $("#nav-x li.google a").addClass("selected");
    } else if ($("body").hasClass("samples")) {
      $("#nav-x li.samples a").addClass("selected");
    }

  // highlight Distribute tab
  } else if ($("body").hasClass("distribute")) {
    $("#header li.distribute a").addClass("selected");
  }

  // set global variable so we can highlight the sidenav a bit later (such as for google reference)
  // and highlight the sidenav
  mPagePath = pagePath;
  highlightSidenav();

  // set up prev/next links if they exist
  var $selNavLink = $('#nav').find('a[href="' + pagePath + '"]');
  var $selListItem;
  if ($selNavLink.length) {
    $selListItem = $selNavLink.closest('li');

    // set up prev links
    var $prevLink = [];
    var $prevListItem = $selListItem.prev('li');

    var crossBoundaries = ($("body.design").length > 0) || ($("body.guide").length > 0) ? true :
false; // navigate across topic boundaries only in design docs
    if ($prevListItem.length) {
      if ($prevListItem.hasClass('nav-section')) {
        // jump to last topic of previous section
        $prevLink = $prevListItem.find('a:last');
      } else if (!$selListItem.hasClass('nav-section')) {
        // jump to previous topic in this section
        $prevLink = $prevListItem.find('a:eq(0)');
      }
    } else {
      // jump to this section's index page (if it exists)
      var $parentListItem = $selListItem.parents('li');
      $prevLink = $selListItem.parents('li').find('a');

      // except if cross boundaries aren't allowed, and we're at the top of a section already
      // (and there's another parent)
      if (!crossBoundaries && $parentListItem.hasClass('nav-section')
                           && $selListItem.hasClass('nav-section')) {
        $prevLink = [];
      }
    }

    // set up next links
    var $nextLink = [];
    var startClass = false;
    var training = $(".next-class-link").length; // decides whether to provide "next class" link
    var isCrossingBoundary = false;

    if ($selListItem.hasClass('nav-section') && $selListItem.children('div.empty').length == 0) {
      // we're on an index page, jump to the first topic
      $nextLink = $selListItem.find('ul:eq(0)').find('a:eq(0)');

      // if there aren't any children, go to the next section (required for About pages)
      if($nextLink.length == 0) {
        $nextLink = $selListItem.next('li').find('a');
      } else if ($('.topic-start-link').length) {
        // as long as there's a child link and there is a "topic start link" (we're on a landing)
        // then set the landing page "start link" text to be the first doc title
        $('.topic-start-link').text($nextLink.text().toUpperCase());
      }

      // If the selected page has a description, then it's a class or article homepage
      if ($selListItem.find('a[description]').length) {
        // this means we're on a class landing page
        startClass = true;
      }
    } else {
      // jump to the next topic in this section (if it exists)
      $nextLink = $selListItem.next('li').find('a:eq(0)');
      if ($nextLink.length == 0) {
        isCrossingBoundary = true;
        // no more topics in this section, jump to the first topic in the next section
        $nextLink = $selListItem.parents('li:eq(0)').next('li.nav-section').find('a:eq(0)');
        if (!$nextLink.length) {  // Go up another layer to look for next page (lesson > class > course)
          $nextLink = $selListItem.parents('li:eq(1)').next('li.nav-section').find('a:eq(0)');
          if ($nextLink.length == 0) {
            // if that doesn't work, we're at the end of the list, so disable NEXT link
            $('.next-page-link').attr('href','').addClass("disabled")
                                .click(function() { return false; });
          }
        }
      }
    }

    if (startClass) {
      $('.start-class-link').attr('href', $nextLink.attr('href')).removeClass("hide");

      // if there's no training bar (below the start button),
      // then we need to add a bottom border to button
      if (!$("#tb").length) {
        $('.start-class-link').css({'border-bottom':'1px solid #DADADA'});
      }
    } else if (isCrossingBoundary && !$('body.design').length) {  // Design always crosses boundaries
      $('.content-footer.next-class').show();
      $('.next-page-link').attr('href','')
                          .removeClass("hide").addClass("disabled")
                          .click(function() { return false; });
      if ($nextLink.length) {
        $('.next-class-link').attr('href',$nextLink.attr('href'))
                             .removeClass("hide").append($nextLink.html());
        $('.next-class-link').find('.new').empty();
      }
    } else {
      $('.next-page-link').attr('href', $nextLink.attr('href')).removeClass("hide");
    }

    if (!startClass && $prevLink.length) {
      var prevHref = $prevLink.attr('href');
      if (prevHref == SITE_ROOT + 'index.html') {
        // Don't show Previous when it leads to the homepage
      } else {
        $('.prev-page-link').attr('href', $prevLink.attr('href')).removeClass("hide");
      }
    }

    // If this is a training 'article', there should be no prev/next nav
    // ... if the grandparent is the "nav" ... and it has no child list items...
    if (training && $selListItem.parents('ul').eq(1).is('[id="nav"]') &&
        !$selListItem.find('li').length) {
      $('.next-page-link,.prev-page-link').attr('href','').addClass("disabled")
                          .click(function() { return false; });
    }

  }



  // Set up the course landing pages for Training with class names and descriptions
  if ($('body.trainingcourse').length) {
    var $classLinks = $selListItem.find('ul li a').not('#nav .nav-section .nav-section ul a');
    var $classDescriptions = $classLinks.attr('description');

    var $olClasses  = $('<ol class="class-list"></ol>');
    var $liClass;
    var $imgIcon;
    var $h2Title;
    var $pSummary;
    var $olLessons;
    var $liLesson;
    $classLinks.each(function(index) {
      $liClass  = $('<li></li>');
      $h2Title  = $('<a class="title" href="'+$(this).attr('href')+'"><h2>' + $(this).html()+'</h2><span></span></a>');
      $pSummary = $('<p class="description">' + $(this).attr('description') + '</p>');

      $olLessons  = $('<ol class="lesson-list"></ol>');

      $lessons = $(this).closest('li').find('ul li a');

      if ($lessons.length) {
        $imgIcon = $('<img src="'+toRoot+'assets/images/resource-tutorial.png" '
            + ' width="64" height="64" alt=""/>');
        $lessons.each(function(index) {
          $olLessons.append('<li><a href="'+$(this).attr('href')+'">' + $(this).html()+'</a></li>');
        });
      } else {
        $imgIcon = $('<img src="'+toRoot+'assets/images/resource-article.png" '
            + ' width="64" height="64" alt=""/>');
        $pSummary.addClass('article');
      }

      $liClass.append($h2Title).append($imgIcon).append($pSummary).append($olLessons);
      $olClasses.append($liClass);
    });
    $('.jd-descr').append($olClasses);
  }

  // Set up expand/collapse behavior
  initExpandableNavItems("#nav");


  $(".scroll-pane").scroll(function(event) {
      event.preventDefault();
      return false;
  });

  /* Resize nav height when window height changes */
  $(window).resize(function() {
    if ($('#side-nav').length == 0) return;
    var stylesheet = $('link[rel="stylesheet"][class="fullscreen"]');
    setNavBarLeftPos(); // do this even if sidenav isn't fixed because it could become fixed
    // make sidenav behave when resizing the window and side-scolling is a concern
    if (navBarIsFixed) {
      if ((stylesheet.attr("disabled") == "disabled") || stylesheet.length == 0) {
        updateSideNavPosition();
      } else {
        updateSidenavFullscreenWidth();
      }
    }
    resizeNav();
  });


  // Set up fixed navbar
  var prevScrollLeft = 0; // used to compare current position to previous position of horiz scroll
  $(window).scroll(function(event) {
    if ($('#side-nav').length == 0) return;
    if (event.target.nodeName == "DIV") {
      // Dump scroll event if the target is a DIV, because that means the event is coming
      // from a scrollable div and so there's no need to make adjustments to our layout
      return;
    }
    var scrollTop = $(window).scrollTop();
    var headerHeight = $('#header').outerHeight();
    var subheaderHeight = $('#nav-x').outerHeight();
    var searchResultHeight = $('#searchResults').is(":visible") ?
                             $('#searchResults').outerHeight() : 0;
    var totalHeaderHeight = headerHeight + subheaderHeight + searchResultHeight;
    // we set the navbar fixed when the scroll position is beyond the height of the site header...
    var navBarShouldBeFixed = scrollTop > totalHeaderHeight;
    // ... except if the document content is shorter than the sidenav height.
    // (this is necessary to avoid crazy behavior on OSX Lion due to overscroll bouncing)
    if ($("#doc-col").height() < $("#side-nav").height()) {
      navBarShouldBeFixed = false;
    }

    var scrollLeft = $(window).scrollLeft();
    // When the sidenav is fixed and user scrolls horizontally, reposition the sidenav to match
    if (navBarIsFixed && (scrollLeft != prevScrollLeft)) {
      updateSideNavPosition();
      prevScrollLeft = scrollLeft;
    }

    // Don't continue if the header is sufficently far away
    // (to avoid intensive resizing that slows scrolling)
    if (navBarIsFixed && navBarShouldBeFixed) {
      return;
    }

    if (navBarIsFixed != navBarShouldBeFixed) {
      if (navBarShouldBeFixed) {
        // make it fixed
        var width = $('#devdoc-nav').width();
        $('#devdoc-nav')
            .addClass('fixed')
            .css({'width':width+'px'})
            .prependTo('#body-content');
        // add neato "back to top" button
        $('#devdoc-nav a.totop').css({'display':'block','width':$("#nav").innerWidth()+'px'});

        // update the sidenaav position for side scrolling
        updateSideNavPosition();
      } else {
        // make it static again
        $('#devdoc-nav')
            .removeClass('fixed')
            .css({'width':'auto','margin':''})
            .prependTo('#side-nav');
        $('#devdoc-nav a.totop').hide();
      }
      navBarIsFixed = navBarShouldBeFixed;
    }

    resizeNav(250); // pass true in order to delay the scrollbar re-initialization for performance
  });


  var navBarLeftPos;
  if ($('#devdoc-nav').length) {
    setNavBarLeftPos();
  }


  // Set up play-on-hover <video> tags.
  $('video.play-on-hover').bind('click', function(){
    $(this).get(0).load(); // in case the video isn't seekable
    $(this).get(0).play();
  });

  // Set up tooltips
  var TOOLTIP_MARGIN = 10;
  $('acronym,.tooltip-link').each(function() {
    var $target = $(this);
    var $tooltip = $('<div>')
        .addClass('tooltip-box')
        .append($target.attr('title'))
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

  //Loads the +1 button
  var po = document.createElement('script'); po.type = 'text/javascript'; po.async = true;
  po.src = 'https://apis.google.com/js/plusone.js';
  var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(po, s);


  // Revise the sidenav widths to make room for the scrollbar
  // which avoids the visible width from changing each time the bar appears
  var $sidenav = $("#side-nav");
  var sidenav_width = parseInt($sidenav.innerWidth());

  $("#devdoc-nav  #nav").css("width", sidenav_width - 4 + "px"); // 4px is scrollbar width


  $(".scroll-pane").removeAttr("tabindex"); // get rid of tabindex added by jscroller

  if ($(".scroll-pane").length > 1) {
    // Check if there's a user preference for the panel heights
    var cookieHeight = readCookie("reference_height");
    if (cookieHeight) {
      restoreHeight(cookieHeight);
    }
  }

  resizeNav();

  /* init the language selector based on user cookie for lang */
  loadLangPref();
  changeNavLang(getLangPref());

  /* setup event handlers to ensure the overflow menu is visible while picking lang */
  $("#language select")
      .mousedown(function() {
        $("div.morehover").addClass("hover"); })
      .blur(function() {
        $("div.morehover").removeClass("hover"); });

  /* some global variable setup */
  resizePackagesNav = $("#resize-packages-nav");
  classesNav = $("#classes-nav");
  devdocNav = $("#devdoc-nav");

  var cookiePath = "";
  if (location.href.indexOf("/reference/") != -1) {
    cookiePath = "reference_";
  } else if (location.href.indexOf("/guide/") != -1) {
    cookiePath = "guide_";
  } else if (location.href.indexOf("/tools/") != -1) {
    cookiePath = "tools_";
  } else if (location.href.indexOf("/training/") != -1) {
    cookiePath = "training_";
  } else if (location.href.indexOf("/design/") != -1) {
    cookiePath = "design_";
  } else if (location.href.indexOf("/distribute/") != -1) {
    cookiePath = "distribute_";
  }

});
// END of the onload event


function initExpandableNavItems(rootTag) {
  $(rootTag + ' li.nav-section .nav-section-header').click(function() {
    var section = $(this).closest('li.nav-section');
    if (section.hasClass('expanded')) {
    /* hide me and descendants */
      section.find('ul').slideUp(250, function() {
        // remove 'expanded' class from my section and any children
        section.closest('li').removeClass('expanded');
        $('li.nav-section', section).removeClass('expanded');
        resizeNav();
      });
    } else {
    /* show me */
      // first hide all other siblings
      var $others = $('li.nav-section.expanded', $(this).closest('ul')).not('.sticky');
      $others.removeClass('expanded').children('ul').slideUp(250);

      // now expand me
      section.closest('li').addClass('expanded');
      section.children('ul').slideDown(250, function() {
        resizeNav();
      });
    }
  });

  // Stop expand/collapse behavior when clicking on nav section links
  // (since we're navigating away from the page)
  // This selector captures the first instance of <a>, but not those with "#" as the href.
  $('.nav-section-header').find('a:eq(0)').not('a[href="#"]').click(function(evt) {
    window.location.href = $(this).attr('href');
    return false;
  });
}

/** Highlight the current page in sidenav, expanding children as appropriate */
function highlightSidenav() {
  // if something is already highlighted, undo it. This is for dynamic navigation (Samples index)
  if ($("ul#nav li.selected").length) {
    unHighlightSidenav();
  }
  // look for URL in sidenav, including the hash
  var $selNavLink = $('#nav').find('a[href="' + mPagePath + location.hash + '"]');

  // If the selNavLink is still empty, look for it without the hash
  if ($selNavLink.length == 0) {
    $selNavLink = $('#nav').find('a[href="' + mPagePath + '"]');
  }

  var $selListItem;
  if ($selNavLink.length) {
    // Find this page's <li> in sidenav and set selected
    $selListItem = $selNavLink.closest('li');
    $selListItem.addClass('selected');

    // Traverse up the tree and expand all parent nav-sections
    $selNavLink.parents('li.nav-section').each(function() {
      $(this).addClass('expanded');
      $(this).children('ul').show();
    });
  }
}

function unHighlightSidenav() {
  $("ul#nav li.selected").removeClass("selected");
  $('ul#nav li.nav-section.expanded').removeClass('expanded').children('ul').hide();
}

function toggleFullscreen(enable) {
  var delay = 20;
  var enabled = true;
  var stylesheet = $('link[rel="stylesheet"][class="fullscreen"]');
  if (enable) {
    // Currently NOT USING fullscreen; enable fullscreen
    stylesheet.removeAttr('disabled');
    $('#nav-swap .fullscreen').removeClass('disabled');
    $('#devdoc-nav').css({left:''});
    setTimeout(updateSidenavFullscreenWidth,delay); // need to wait a moment for css to switch
    enabled = true;
  } else {
    // Currently USING fullscreen; disable fullscreen
    stylesheet.attr('disabled', 'disabled');
    $('#nav-swap .fullscreen').addClass('disabled');
    setTimeout(updateSidenavFixedWidth,delay); // need to wait a moment for css to switch
    enabled = false;
  }
  writeCookie("fullscreen", enabled, null, null);
  setNavBarLeftPos();
  resizeNav(delay);
  updateSideNavPosition();
  setTimeout(initSidenavHeightResize,delay);
}


function setNavBarLeftPos() {
  navBarLeftPos = $('#body-content').offset().left;
}


function updateSideNavPosition() {
  var newLeft = $(window).scrollLeft() - navBarLeftPos;
  $('#devdoc-nav').css({left: -newLeft});
  $('#devdoc-nav .totop').css({left: -(newLeft - parseInt($('#side-nav').css('margin-left')))});
}

// TODO: use $(document).ready instead
function addLoadEvent(newfun) {
  var current = window.onload;
  if (typeof window.onload != 'function') {
    window.onload = newfun;
  } else {
    window.onload = function() {
      current();
      newfun();
    }
  }
}

var agent = navigator['userAgent'].toLowerCase();
// If a mobile phone, set flag and do mobile setup
if ((agent.indexOf("mobile") != -1) ||      // android, iphone, ipod
    (agent.indexOf("blackberry") != -1) ||
    (agent.indexOf("webos") != -1) ||
    (agent.indexOf("mini") != -1)) {        // opera mini browsers
  isMobile = true;
}


$(document).ready(function() {
  $("pre:not(.no-pretty-print)").addClass("prettyprint");
  prettyPrint();
});




/* ######### RESIZE THE SIDENAV HEIGHT ########## */

function resizeNav(delay) {
  var $nav = $("#devdoc-nav");
  var $window = $(window);
  var navHeight;

  // Get the height of entire window and the total header height.
  // Then figure out based on scroll position whether the header is visible
  var windowHeight = $window.height();
  var scrollTop = $window.scrollTop();
  var headerHeight = $('#header').outerHeight();
  var subheaderHeight = $('#nav-x').outerHeight();
  var headerVisible = (scrollTop < (headerHeight + subheaderHeight));

  // get the height of space between nav and top of window.
  // Could be either margin or top position, depending on whether the nav is fixed.
  var topMargin = (parseInt($nav.css('margin-top')) || parseInt($nav.css('top'))) + 1;
  // add 1 for the #side-nav bottom margin

  // Depending on whether the header is visible, set the side nav's height.
  if (headerVisible) {
    // The sidenav height grows as the header goes off screen
    navHeight = windowHeight - (headerHeight + subheaderHeight - scrollTop) - topMargin;
  } else {
    // Once header is off screen, the nav height is almost full window height
    navHeight = windowHeight - topMargin;
  }



  $scrollPanes = $(".scroll-pane");
  if ($scrollPanes.length > 1) {
    // subtract the height of the api level widget and nav swapper from the available nav height
    navHeight -= ($('#api-nav-header').outerHeight(true) + $('#nav-swap').outerHeight(true));

    $("#swapper").css({height:navHeight + "px"});
    if ($("#nav-tree").is(":visible")) {
      $("#nav-tree").css({height:navHeight});
    }

    var classesHeight = navHeight - parseInt($("#resize-packages-nav").css("height")) - 10 + "px";
    //subtract 10px to account for drag bar

    // if the window becomes small enough to make the class panel height 0,
    // then the package panel should begin to shrink
    if (parseInt(classesHeight) <= 0) {
      $("#resize-packages-nav").css({height:navHeight - 10}); //subtract 10px for drag bar
      $("#packages-nav").css({height:navHeight - 10});
    }

    $("#classes-nav").css({'height':classesHeight, 'margin-top':'10px'});
    $("#classes-nav .jspContainer").css({height:classesHeight});


  } else {
    $nav.height(navHeight);
  }

  if (delay) {
    updateFromResize = true;
    delayedReInitScrollbars(delay);
  } else {
    reInitScrollbars();
  }

}

var updateScrollbars = false;
var updateFromResize = false;

/* Re-initialize the scrollbars to account for changed nav size.
 * This method postpones the actual update by a 1/4 second in order to optimize the
 * scroll performance while the header is still visible, because re-initializing the
 * scroll panes is an intensive process.
 */
function delayedReInitScrollbars(delay) {
  // If we're scheduled for an update, but have received another resize request
  // before the scheduled resize has occured, just ignore the new request
  // (and wait for the scheduled one).
  if (updateScrollbars && updateFromResize) {
    updateFromResize = false;
    return;
  }

  // We're scheduled for an update and the update request came from this method's setTimeout
  if (updateScrollbars && !updateFromResize) {
    reInitScrollbars();
    updateScrollbars = false;
  } else {
    updateScrollbars = true;
    updateFromResize = false;
    setTimeout('delayedReInitScrollbars()',delay);
  }
}

/* Re-initialize the scrollbars to account for changed nav size. */
function reInitScrollbars() {
  var pane = $(".scroll-pane").each(function(){
    var api = $(this).data('jsp');
    if (!api) { setTimeout(reInitScrollbars,300); return;}
    api.reinitialise( {verticalGutter:0} );
  });
  $(".scroll-pane").removeAttr("tabindex"); // get rid of tabindex added by jscroller
}


/* Resize the height of the nav panels in the reference,
 * and save the new size to a cookie */
function saveNavPanels() {
  var basePath = getBaseUri(location.pathname);
  var section = basePath.substring(1,basePath.indexOf("/",1));
  writeCookie("height", resizePackagesNav.css("height"), section, null);
}



function restoreHeight(packageHeight) {
    $("#resize-packages-nav").height(packageHeight);
    $("#packages-nav").height(packageHeight);
  //  var classesHeight = navHeight - packageHeight;
 //   $("#classes-nav").css({height:classesHeight});
  //  $("#classes-nav .jspContainer").css({height:classesHeight});
}



/* ######### END RESIZE THE SIDENAV HEIGHT ########## */





/** Scroll the jScrollPane to make the currently selected item visible
    This is called when the page finished loading. */
function scrollIntoView(nav) {
  var $nav = $("#"+nav);
  var element = $nav.jScrollPane({/* ...settings... */});
  var api = element.data('jsp');

  if ($nav.is(':visible')) {
    var $selected = $(".selected", $nav);
    if ($selected.length == 0) {
      // If no selected item found, exit
      return;
    }
    // get the selected item's offset from its container nav by measuring the item's offset
    // relative to the document then subtract the container nav's offset relative to the document
    var selectedOffset = $selected.offset().top - $nav.offset().top;
    if (selectedOffset > $nav.height() * .8) { // multiply nav height by .8 so we move up the item
                                               // if it's more than 80% down the nav
      // scroll the item up by an amount equal to 80% the container nav's height
      api.scrollTo(0, selectedOffset - ($nav.height() * .8), false);
    }
  }
}






/* Show popup dialogs */
function showDialog(id) {
  $dialog = $("#"+id);
  $dialog.prepend('<div class="box-border"><div class="top"> <div class="left"></div> <div class="right"></div></div><div class="bottom"> <div class="left"></div> <div class="right"></div> </div> </div>');
  $dialog.wrapInner('<div/>');
  $dialog.removeClass("hide");
}





/* #########    COOKIES!     ########## */

function readCookie(cookie) {
  var myCookie = cookie_namespace+"_"+cookie+"=";
  if (document.cookie) {
    var index = document.cookie.indexOf(myCookie);
    if (index != -1) {
      var valStart = index + myCookie.length;
      var valEnd = document.cookie.indexOf(";", valStart);
      if (valEnd == -1) {
        valEnd = document.cookie.length;
      }
      var val = document.cookie.substring(valStart, valEnd);
      return val;
    }
  }
  return 0;
}

function writeCookie(cookie, val, section, expiration) {
  if (val==undefined) return;
  section = section == null ? "_" : "_"+section+"_";
  if (expiration == null) {
    var date = new Date();
    date.setTime(date.getTime()+(10*365*24*60*60*1000)); // default expiration is one week
    expiration = date.toGMTString();
  }
  var cookieValue = cookie_namespace + section + cookie + "=" + val
                    + "; expires=" + expiration+"; path=/";
  document.cookie = cookieValue;
}

/* #########     END COOKIES!     ########## */



















/*      MISC LIBRARY FUNCTIONS     */





function toggle(obj, slide) {
  var ul = $("ul:first", obj);
  var li = ul.parent();
  if (li.hasClass("closed")) {
    if (slide) {
      ul.slideDown("fast");
    } else {
      ul.show();
    }
    li.removeClass("closed");
    li.addClass("open");
    $(".toggle-img", li).attr("title", "hide pages");
  } else {
    ul.slideUp("fast");
    li.removeClass("open");
    li.addClass("closed");
    $(".toggle-img", li).attr("title", "show pages");
  }
}


function buildToggleLists() {
  $(".toggle-list").each(
    function(i) {
      $("div:first", this).append("<a class='toggle-img' href='#' title='show pages' onClick='toggle(this.parentNode.parentNode, true); return false;'></a>");
      $(this).addClass("closed");
    });
}



function hideNestedItems(list, toggle) {
  $list = $(list);
  // hide nested lists
  if($list.hasClass('showing')) {
    $("li ol", $list).hide('fast');
    $list.removeClass('showing');
  // show nested lists
  } else {
    $("li ol", $list).show('fast');
    $list.addClass('showing');
  }
  $(".more,.less",$(toggle)).toggle();
}




























/*      REFERENCE NAV SWAP     */


function getNavPref() {
  var v = readCookie('reference_nav');
  if (v != NAV_PREF_TREE) {
    v = NAV_PREF_PANELS;
  }
  return v;
}

function chooseDefaultNav() {
  nav_pref = getNavPref();
  if (nav_pref == NAV_PREF_TREE) {
    $("#nav-panels").toggle();
    $("#panel-link").toggle();
    $("#nav-tree").toggle();
    $("#tree-link").toggle();
  }
}

function swapNav() {
  if (nav_pref == NAV_PREF_TREE) {
    nav_pref = NAV_PREF_PANELS;
  } else {
    nav_pref = NAV_PREF_TREE;
    init_default_navtree(toRoot);
  }
  var date = new Date();
  date.setTime(date.getTime()+(10*365*24*60*60*1000)); // keep this for 10 years
  writeCookie("nav", nav_pref, "reference", date.toGMTString());

  $("#nav-panels").toggle();
  $("#panel-link").toggle();
  $("#nav-tree").toggle();
  $("#tree-link").toggle();

  resizeNav();

  // Gross nasty hack to make tree view show up upon first swap by setting height manually
  $("#nav-tree .jspContainer:visible")
      .css({'height':$("#nav-tree .jspContainer .jspPane").height() +'px'});
  // Another nasty hack to make the scrollbar appear now that we have height
  resizeNav();

  if ($("#nav-tree").is(':visible')) {
    scrollIntoView("nav-tree");
  } else {
    scrollIntoView("packages-nav");
    scrollIntoView("classes-nav");
  }
}



/* ############################################ */
/* ##########     LOCALIZATION     ############ */
/* ############################################ */

function getBaseUri(uri) {
  var intlUrl = (uri.substring(0,6) == "/intl/");
  if (intlUrl) {
    base = uri.substring(uri.indexOf('intl/')+5,uri.length);
    base = base.substring(base.indexOf('/')+1, base.length);
      //alert("intl, returning base url: /" + base);
    return ("/" + base);
  } else {
      //alert("not intl, returning uri as found.");
    return uri;
  }
}

function requestAppendHL(uri) {
//append "?hl=<lang> to an outgoing request (such as to blog)
  var lang = getLangPref();
  if (lang) {
    var q = 'hl=' + lang;
    uri += '?' + q;
    window.location = uri;
    return false;
  } else {
    return true;
  }
}


function changeNavLang(lang) {
  var $links = $("#devdoc-nav,#header,#nav-x,.training-nav-top,.content-footer").find("a["+lang+"-lang]");
  $links.each(function(i){ // for each link with a translation
    var $link = $(this);
    if (lang != "en") { // No need to worry about English, because a language change invokes new request
      // put the desired language from the attribute as the text
      $link.text($link.attr(lang+"-lang"))
    }
  });
}

function changeLangPref(lang, submit) {
  var date = new Date();
  expires = date.toGMTString(date.setTime(date.getTime()+(10*365*24*60*60*1000)));
  // keep this for 50 years
  //alert("expires: " + expires)
  writeCookie("pref_lang", lang, null, expires);

  //  #######  TODO:  Remove this condition once we're stable on devsite #######
  //  This condition is only needed if we still need to support legacy GAE server
  if (devsite) {
    // Switch language when on Devsite server
    if (submit) {
      $("#setlang").submit();
    }
  } else {
    // Switch language when on legacy GAE server
    if (submit) {
      window.location = getBaseUri(location.pathname);
    }
  }
}

function loadLangPref() {
  var lang = readCookie("pref_lang");
  if (lang != 0) {
    $("#language").find("option[value='"+lang+"']").attr("selected",true);
  }
}

function getLangPref() {
  var lang = $("#language").find(":selected").attr("value");
  if (!lang) {
    lang = readCookie("pref_lang");
  }
  return (lang != 0) ? lang : 'en';
}

/* ##########     END LOCALIZATION     ############ */






/* Used to hide and reveal supplemental content, such as long code samples.
   See the companion CSS in android-developer-docs.css */
function toggleContent(obj) {
  var div = $(obj).closest(".toggle-content");
  var toggleMe = $(".toggle-content-toggleme:eq(0)",div);
  if (div.hasClass("closed")) { // if it's closed, open it
    toggleMe.slideDown();
    $(".toggle-content-text:eq(0)", obj).toggle();
    div.removeClass("closed").addClass("open");
    $(".toggle-content-img:eq(0)", div).attr("title", "hide").attr("src", toRoot
                  + "assets/images/triangle-opened.png");
  } else { // if it's open, close it
    toggleMe.slideUp('fast', function() {  // Wait until the animation is done before closing arrow
      $(".toggle-content-text:eq(0)", obj).toggle();
      div.removeClass("open").addClass("closed");
      div.find(".toggle-content").removeClass("open").addClass("closed")
              .find(".toggle-content-toggleme").hide();
      $(".toggle-content-img", div).attr("title", "show").attr("src", toRoot
                  + "assets/images/triangle-closed.png");
    });
  }
  return false;
}


/* New version of expandable content */
function toggleExpandable(link,id) {
  if($(id).is(':visible')) {
    $(id).slideUp();
    $(link).removeClass('expanded');
  } else {
    $(id).slideDown();
    $(link).addClass('expanded');
  }
}

function hideExpandable(ids) {
  $(ids).slideUp();
  $(ids).prev('h4').find('a.expandable').removeClass('expanded');
}





/*
 *  Slideshow 1.0
 *  Used on /index.html and /develop/index.html for carousel
 *
 *  Sample usage:
 *  HTML -
 *  <div class="slideshow-container">
 *   <a href="" class="slideshow-prev">Prev</a>
 *   <a href="" class="slideshow-next">Next</a>
 *   <ul>
 *       <li class="item"><img src="images/marquee1.jpg"></li>
 *       <li class="item"><img src="images/marquee2.jpg"></li>
 *       <li class="item"><img src="images/marquee3.jpg"></li>
 *       <li class="item"><img src="images/marquee4.jpg"></li>
 *   </ul>
 *  </div>
 *
 *   <script type="text/javascript">
 *   $('.slideshow-container').dacSlideshow({
 *       auto: true,
 *       btnPrev: '.slideshow-prev',
 *       btnNext: '.slideshow-next'
 *   });
 *   </script>
 *
 *  Options:
 *  btnPrev:    optional identifier for previous button
 *  btnNext:    optional identifier for next button
 *  btnPause:   optional identifier for pause button
 *  auto:       whether or not to auto-proceed
 *  speed:      animation speed
 *  autoTime:   time between auto-rotation
 *  easing:     easing function for transition
 *  start:      item to select by default
 *  scroll:     direction to scroll in
 *  pagination: whether or not to include dotted pagination
 *
 */

 (function($) {
 $.fn.dacSlideshow = function(o) {

     //Options - see above
     o = $.extend({
         btnPrev:   null,
         btnNext:   null,
         btnPause:  null,
         auto:      true,
         speed:     500,
         autoTime:  12000,
         easing:    null,
         start:     0,
         scroll:    1,
         pagination: true

     }, o || {});

     //Set up a carousel for each
     return this.each(function() {

         var running = false;
         var animCss = o.vertical ? "top" : "left";
         var sizeCss = o.vertical ? "height" : "width";
         var div = $(this);
         var ul = $("ul", div);
         var tLi = $("li", ul);
         var tl = tLi.size();
         var timer = null;

         var li = $("li", ul);
         var itemLength = li.size();
         var curr = o.start;

         li.css({float: o.vertical ? "none" : "left"});
         ul.css({margin: "0", padding: "0", position: "relative", "list-style-type": "none", "z-index": "1"});
         div.css({position: "relative", "z-index": "2", left: "0px"});

         var liSize = o.vertical ? height(li) : width(li);
         var ulSize = liSize * itemLength;
         var divSize = liSize;

         li.css({width: li.width(), height: li.height()});
         ul.css(sizeCss, ulSize+"px").css(animCss, -(curr*liSize));

         div.css(sizeCss, divSize+"px");

         //Pagination
         if (o.pagination) {
             var pagination = $("<div class='pagination'></div>");
             var pag_ul = $("<ul></ul>");
             if (tl > 1) {
               for (var i=0;i<tl;i++) {
                    var li = $("<li>"+i+"</li>");
                    pag_ul.append(li);
                    if (i==o.start) li.addClass('active');
                        li.click(function() {
                        go(parseInt($(this).text()));
                    })
                }
                pagination.append(pag_ul);
                div.append(pagination);
             }
         }

         //Previous button
         if(o.btnPrev)
             $(o.btnPrev).click(function(e) {
                 e.preventDefault();
                 return go(curr-o.scroll);
             });

         //Next button
         if(o.btnNext)
             $(o.btnNext).click(function(e) {
                 e.preventDefault();
                 return go(curr+o.scroll);
             });

         //Pause button
         if(o.btnPause)
             $(o.btnPause).click(function(e) {
                 e.preventDefault();
                 if ($(this).hasClass('paused')) {
                     startRotateTimer();
                 } else {
                     pauseRotateTimer();
                 }
             });

         //Auto rotation
         if(o.auto) startRotateTimer();

         function startRotateTimer() {
             clearInterval(timer);
             timer = setInterval(function() {
                  if (curr == tl-1) {
                    go(0);
                  } else {
                    go(curr+o.scroll);
                  }
              }, o.autoTime);
             $(o.btnPause).removeClass('paused');
         }

         function pauseRotateTimer() {
             clearInterval(timer);
             $(o.btnPause).addClass('paused');
         }

         //Go to an item
         function go(to) {
             if(!running) {

                 if(to<0) {
                    to = itemLength-1;
                 } else if (to>itemLength-1) {
                    to = 0;
                 }
                 curr = to;

                 running = true;

                 ul.animate(
                     animCss == "left" ? { left: -(curr*liSize) } : { top: -(curr*liSize) } , o.speed, o.easing,
                     function() {
                         running = false;
                     }
                 );

                 $(o.btnPrev + "," + o.btnNext).removeClass("disabled");
                 $( (curr-o.scroll<0 && o.btnPrev)
                     ||
                    (curr+o.scroll > itemLength && o.btnNext)
                     ||
                    []
                  ).addClass("disabled");


                 var nav_items = $('li', pagination);
                 nav_items.removeClass('active');
                 nav_items.eq(to).addClass('active');


             }
             if(o.auto) startRotateTimer();
             return false;
         };
     });
 };

 function css(el, prop) {
     return parseInt($.css(el[0], prop)) || 0;
 };
 function width(el) {
     return  el[0].offsetWidth + css(el, 'marginLeft') + css(el, 'marginRight');
 };
 function height(el) {
     return el[0].offsetHeight + css(el, 'marginTop') + css(el, 'marginBottom');
 };

 })(jQuery);


/*
 *  dacSlideshow 1.0
 *  Used on develop/index.html for side-sliding tabs
 *
 *  Sample usage:
 *  HTML -
 *  <div class="slideshow-container">
 *   <a href="" class="slideshow-prev">Prev</a>
 *   <a href="" class="slideshow-next">Next</a>
 *   <ul>
 *       <li class="item"><img src="images/marquee1.jpg"></li>
 *       <li class="item"><img src="images/marquee2.jpg"></li>
 *       <li class="item"><img src="images/marquee3.jpg"></li>
 *       <li class="item"><img src="images/marquee4.jpg"></li>
 *   </ul>
 *  </div>
 *
 *   <script type="text/javascript">
 *   $('.slideshow-container').dacSlideshow({
 *       auto: true,
 *       btnPrev: '.slideshow-prev',
 *       btnNext: '.slideshow-next'
 *   });
 *   </script>
 *
 *  Options:
 *  btnPrev:    optional identifier for previous button
 *  btnNext:    optional identifier for next button
 *  auto:       whether or not to auto-proceed
 *  speed:      animation speed
 *  autoTime:   time between auto-rotation
 *  easing:     easing function for transition
 *  start:      item to select by default
 *  scroll:     direction to scroll in
 *  pagination: whether or not to include dotted pagination
 *
 */
 (function($) {
 $.fn.dacTabbedList = function(o) {

     //Options - see above
     o = $.extend({
         speed : 250,
         easing: null,
         nav_id: null,
         frame_id: null
     }, o || {});

     //Set up a carousel for each
     return this.each(function() {

         var curr = 0;
         var running = false;
         var animCss = "margin-left";
         var sizeCss = "width";
         var div = $(this);

         var nav = $(o.nav_id, div);
         var nav_li = $("li", nav);
         var nav_size = nav_li.size();
         var frame = div.find(o.frame_id);
         var content_width = $(frame).find('ul').width();
         //Buttons
         $(nav_li).click(function(e) {
           go($(nav_li).index($(this)));
         })

         //Go to an item
         function go(to) {
             if(!running) {
                 curr = to;
                 running = true;

                 frame.animate({ 'margin-left' : -(curr*content_width) }, o.speed, o.easing,
                     function() {
                         running = false;
                     }
                 );


                 nav_li.removeClass('active');
                 nav_li.eq(to).addClass('active');


             }
             return false;
         };
     });
 };

 function css(el, prop) {
     return parseInt($.css(el[0], prop)) || 0;
 };
 function width(el) {
     return  el[0].offsetWidth + css(el, 'marginLeft') + css(el, 'marginRight');
 };
 function height(el) {
     return el[0].offsetHeight + css(el, 'marginTop') + css(el, 'marginBottom');
 };

 })(jQuery);





/* ######################################################## */
/* ################  SEARCH SUGGESTIONS  ################## */
/* ######################################################## */



var gSelectedIndex = -1;  // the index position of currently highlighted suggestion
var gSelectedColumn = -1;  // which column of suggestion lists is currently focused

var gMatches = new Array();
var gLastText = "";
var gInitialized = false;
var ROW_COUNT_FRAMEWORK = 20;       // max number of results in list
var gListLength = 0;


var gGoogleMatches = new Array();
var ROW_COUNT_GOOGLE = 15;          // max number of results in list
var gGoogleListLength = 0;

var gDocsMatches = new Array();
var ROW_COUNT_DOCS = 100;          // max number of results in list
var gDocsListLength = 0;

function onSuggestionClick(link) {
  // When user clicks a suggested document, track it
  _gaq.push(['_trackEvent', 'Suggestion Click', 'clicked: ' + $(link).text(),
            'from: ' + $("#search_autocomplete").val()]);
}

function set_item_selected($li, selected)
{
    if (selected) {
        $li.attr('class','jd-autocomplete jd-selected');
    } else {
        $li.attr('class','jd-autocomplete');
    }
}

function set_item_values(toroot, $li, match)
{
    var $link = $('a',$li);
    $link.html(match.__hilabel || match.label);
    $link.attr('href',toroot + match.link);
}

function set_item_values_jd(toroot, $li, match)
{
    var $link = $('a',$li);
    $link.html(match.title);
    $link.attr('href',toroot + match.url);
}

function new_suggestion($list) {
    var $li = $("<li class='jd-autocomplete'></li>");
    $list.append($li);

    $li.mousedown(function() {
        window.location = this.firstChild.getAttribute("href");
    });
    $li.mouseover(function() {
        $('.search_filtered_wrapper li').removeClass('jd-selected');
        $(this).addClass('jd-selected');
        gSelectedColumn = $(".search_filtered:visible").index($(this).closest('.search_filtered'));
        gSelectedIndex = $("li", $(".search_filtered:visible")[gSelectedColumn]).index(this);
    });
    $li.append("<a onclick='onSuggestionClick(this)'></a>");
    $li.attr('class','show-item');
    return $li;
}

function sync_selection_table(toroot)
{
    var $li; //list item jquery object
    var i; //list item iterator

    // if there are NO results at all, hide all columns
    if (!(gMatches.length > 0) && !(gGoogleMatches.length > 0) && !(gDocsMatches.length > 0)) {
        $('.suggest-card').hide(300);
        return;
    }

    // if there are api results
    if ((gMatches.length > 0) || (gGoogleMatches.length > 0)) {
      // reveal suggestion list
      $('.suggest-card.dummy').show();
      $('.suggest-card.reference').show();
      var listIndex = 0; // list index position

      // reset the lists
      $(".search_filtered_wrapper.reference li").remove();

      // ########### ANDROID RESULTS #############
      if (gMatches.length > 0) {

          // determine android results to show
          gListLength = gMatches.length < ROW_COUNT_FRAMEWORK ?
                        gMatches.length : ROW_COUNT_FRAMEWORK;
          for (i=0; i<gListLength; i++) {
              var $li = new_suggestion($(".suggest-card.reference ul"));
              set_item_values(toroot, $li, gMatches[i]);
              set_item_selected($li, i == gSelectedIndex);
          }
      }

      // ########### GOOGLE RESULTS #############
      if (gGoogleMatches.length > 0) {
          // show header for list
          $(".suggest-card.reference ul").append("<li class='header'>in Google Services:</li>");

          // determine google results to show
          gGoogleListLength = gGoogleMatches.length < ROW_COUNT_GOOGLE ? gGoogleMatches.length : ROW_COUNT_GOOGLE;
          for (i=0; i<gGoogleListLength; i++) {
              var $li = new_suggestion($(".suggest-card.reference ul"));
              set_item_values(toroot, $li, gGoogleMatches[i]);
              set_item_selected($li, i == gSelectedIndex);
          }
      }
    } else {
      $('.suggest-card.reference').hide();
      $('.suggest-card.dummy').hide();
    }

    // ########### JD DOC RESULTS #############
    if (gDocsMatches.length > 0) {
        // reset the lists
        $(".search_filtered_wrapper.docs li").remove();

        // determine google results to show
        // NOTE: The order of the conditions below for the sugg.type MUST BE SPECIFIC:
        // The order must match the reverse order that each section appears as a card in
        // the suggestion UI... this may be only for the "develop" grouped items though.
        gDocsListLength = gDocsMatches.length < ROW_COUNT_DOCS ? gDocsMatches.length : ROW_COUNT_DOCS;
        for (i=0; i<gDocsListLength; i++) {
            var sugg = gDocsMatches[i];
            var $li;
            if (sugg.type == "design") {
                $li = new_suggestion($(".suggest-card.design ul"));
            } else
            if (sugg.type == "distribute") {
                $li = new_suggestion($(".suggest-card.distribute ul"));
            } else
            if (sugg.type == "samples") {
                $li = new_suggestion($(".suggest-card.develop .child-card.samples"));
            } else
            if (sugg.type == "training") {
                $li = new_suggestion($(".suggest-card.develop .child-card.training"));
            } else
            if (sugg.type == "about"||"guide"||"tools"||"google") {
                $li = new_suggestion($(".suggest-card.develop .child-card.guides"));
            } else {
              continue;
            }

            set_item_values_jd(toroot, $li, sugg);
            set_item_selected($li, i == gSelectedIndex);
        }

        // add heading and show or hide card
        if ($(".suggest-card.design li").length > 0) {
          $(".suggest-card.design ul").prepend("<li class='header'>Design:</li>");
          $(".suggest-card.design").show(300);
        } else {
          $('.suggest-card.design').hide(300);
        }
        if ($(".suggest-card.distribute li").length > 0) {
          $(".suggest-card.distribute ul").prepend("<li class='header'>Distribute:</li>");
          $(".suggest-card.distribute").show(300);
        } else {
          $('.suggest-card.distribute').hide(300);
        }
        if ($(".child-card.guides li").length > 0) {
          $(".child-card.guides").prepend("<li class='header'>Guides:</li>");
          $(".child-card.guides li").appendTo(".suggest-card.develop ul");
        }
        if ($(".child-card.training li").length > 0) {
          $(".child-card.training").prepend("<li class='header'>Training:</li>");
          $(".child-card.training li").appendTo(".suggest-card.develop ul");
        }
        if ($(".child-card.samples li").length > 0) {
          $(".child-card.samples").prepend("<li class='header'>Samples:</li>");
          $(".child-card.samples li").appendTo(".suggest-card.develop ul");
        }

        if ($(".suggest-card.develop li").length > 0) {
          $(".suggest-card.develop").show(300);
        } else {
          $('.suggest-card.develop').hide(300);
        }

    } else {
      $('.search_filtered_wrapper.docs .suggest-card:not(.dummy)').hide(300);
    }
}

/** Called by the search input's onkeydown and onkeyup events.
  * Handles navigation with keyboard arrows, Enter key to invoke search,
  * otherwise invokes search suggestions on key-up event.
  * @param e       The JS event
  * @param kd      True if the event is key-down
  * @param toroot  A string for the site's root path
  * @returns       True if the event should bubble up
  */
function search_changed(e, kd, toroot)
{
    var currentLang = getLangPref();
    var search = document.getElementById("search_autocomplete");
    var text = search.value.replace(/(^ +)|( +$)/g, '');
    // get the ul hosting the currently selected item
    gSelectedColumn = gSelectedColumn >= 0 ? gSelectedColumn :  0;
    var $columns = $(".search_filtered_wrapper").find(".search_filtered:visible");
    var $selectedUl = $columns[gSelectedColumn];

    // show/hide the close button
    if (text != '') {
        $(".search .close").removeClass("hide");
    } else {
        $(".search .close").addClass("hide");
    }
    // 27 = esc
    if (e.keyCode == 27) {
        // close all search results
        if (kd) $('.search .close').trigger('click');
        return true;
    }
    // 13 = enter
    else if (e.keyCode == 13) {
        if (gSelectedIndex < 0) {
            $('.suggest-card').hide();
            if ($("#searchResults").is(":hidden") && (search.value != "")) {
              // if results aren't showing (and text not empty), return true to allow search to execute
              return true;
            } else {
              // otherwise, results are already showing, so allow ajax to auto refresh the results
              // and ignore this Enter press to avoid the reload.
              return false;
            }
        } else if (kd && gSelectedIndex >= 0) {
            // click the link corresponding to selected item
            $("a",$("li",$selectedUl)[gSelectedIndex]).get()[0].click();
            return false;
        }
    }
    // Stop here if Google results are showing
    else if ($("#searchResults").is(":visible")) {
        return true;
    }
    // 38 UP ARROW
    else if (kd && (e.keyCode == 38)) {
        // if the next item is a header, skip it
        if ($($("li", $selectedUl)[gSelectedIndex-1]).hasClass("header")) {
            gSelectedIndex--;
        }
        if (gSelectedIndex >= 0) {
            $('li', $selectedUl).removeClass('jd-selected');
            gSelectedIndex--;
            $('li:nth-child('+(gSelectedIndex+1)+')', $selectedUl).addClass('jd-selected');
            // If user reaches top, reset selected column
            if (gSelectedIndex < 0) {
              gSelectedColumn = -1;
            }
        }
        return false;
    }
    // 40 DOWN ARROW
    else if (kd && (e.keyCode == 40)) {
        // if the next item is a header, skip it
        if ($($("li", $selectedUl)[gSelectedIndex+1]).hasClass("header")) {
            gSelectedIndex++;
        }
        if ((gSelectedIndex < $("li", $selectedUl).length-1) ||
                        ($($("li", $selectedUl)[gSelectedIndex+1]).hasClass("header"))) {
            $('li', $selectedUl).removeClass('jd-selected');
            gSelectedIndex++;
            $('li:nth-child('+(gSelectedIndex+1)+')', $selectedUl).addClass('jd-selected');
        }
        return false;
    }
    // Consider left/right arrow navigation
    // NOTE: Order of suggest columns are reverse order (index position 0 is on right)
    else if (kd && $columns.length > 1 && gSelectedColumn >= 0) {
      // 37 LEFT ARROW
      // go left only if current column is not left-most column (last column)
      if (e.keyCode == 37 && gSelectedColumn < $columns.length - 1) {
        $('li', $selectedUl).removeClass('jd-selected');
        gSelectedColumn++;
        $selectedUl = $columns[gSelectedColumn];
        // keep or reset the selected item to last item as appropriate
        gSelectedIndex = gSelectedIndex >
                $("li", $selectedUl).length-1 ?
                $("li", $selectedUl).length-1 : gSelectedIndex;
        // if the corresponding item is a header, move down
        if ($($("li", $selectedUl)[gSelectedIndex]).hasClass("header")) {
          gSelectedIndex++;
        }
        // set item selected
        $('li:nth-child('+(gSelectedIndex+1)+')', $selectedUl).addClass('jd-selected');
        return false;
      }
      // 39 RIGHT ARROW
      // go right only if current column is not the right-most column (first column)
      else if (e.keyCode == 39 && gSelectedColumn > 0) {
        $('li', $selectedUl).removeClass('jd-selected');
        gSelectedColumn--;
        $selectedUl = $columns[gSelectedColumn];
        // keep or reset the selected item to last item as appropriate
        gSelectedIndex = gSelectedIndex >
                $("li", $selectedUl).length-1 ?
                $("li", $selectedUl).length-1 : gSelectedIndex;
        // if the corresponding item is a header, move down
        if ($($("li", $selectedUl)[gSelectedIndex]).hasClass("header")) {
          gSelectedIndex++;
        }
        // set item selected
        $('li:nth-child('+(gSelectedIndex+1)+')', $selectedUl).addClass('jd-selected');
        return false;
      }
    }

    // if key-up event and not arrow down/up/left/right,
    // read the search query and add suggestions to gMatches
    else if (!kd && (e.keyCode != 40)
                 && (e.keyCode != 38)
                 && (e.keyCode != 37)
                 && (e.keyCode != 39)) {
        gSelectedIndex = -1;
        gMatches = new Array();
        matchedCount = 0;
        gGoogleMatches = new Array();
        matchedCountGoogle = 0;
        gDocsMatches = new Array();
        matchedCountDocs = 0;

        // Search for Android matches
        for (var i=0; i<DATA.length; i++) {
            var s = DATA[i];
            if (text.length != 0 &&
                  s.label.toLowerCase().indexOf(text.toLowerCase()) != -1) {
                gMatches[matchedCount] = s;
                matchedCount++;
            }
        }
        rank_autocomplete_api_results(text, gMatches);
        for (var i=0; i<gMatches.length; i++) {
            var s = gMatches[i];
        }


        // Search for Google matches
        for (var i=0; i<GOOGLE_DATA.length; i++) {
            var s = GOOGLE_DATA[i];
            if (text.length != 0 &&
                  s.label.toLowerCase().indexOf(text.toLowerCase()) != -1) {
                gGoogleMatches[matchedCountGoogle] = s;
                matchedCountGoogle++;
            }
        }
        rank_autocomplete_api_results(text, gGoogleMatches);
        for (var i=0; i<gGoogleMatches.length; i++) {
            var s = gGoogleMatches[i];
        }

        highlight_autocomplete_result_labels(text);



        // Search for matching JD docs
        if (text.length >= 3) {
          // Regex to match only the beginning of a word
          var textRegex = new RegExp("\\b" + text.toLowerCase(), "g");


          // Search for Training classes
          for (var i=0; i<TRAINING_RESOURCES.length; i++) {
            // current search comparison, with counters for tag and title,
            // used later to improve ranking
            var s = TRAINING_RESOURCES[i];
            s.matched_tag = 0;
            s.matched_title = 0;
            var matched = false;

            // Check if query matches any tags; work backwards toward 1 to assist ranking
            for (var j = s.keywords.length - 1; j >= 0; j--) {
              // it matches a tag
              if (s.keywords[j].toLowerCase().match(textRegex)) {
                matched = true;
                s.matched_tag = j + 1; // add 1 to index position
              }
            }
            // Don't consider doc title for lessons (only for class landing pages),
            // unless the lesson has a tag that already matches
            if ((s.lang == currentLang) &&
                  (!(s.type == "training" && s.url.indexOf("index.html") == -1) || matched)) {
              // it matches the doc title
              if (s.title.toLowerCase().match(textRegex)) {
                matched = true;
                s.matched_title = 1;
              }
            }
            if (matched) {
              gDocsMatches[matchedCountDocs] = s;
              matchedCountDocs++;
            }
          }


          // Search for API Guides
          for (var i=0; i<GUIDE_RESOURCES.length; i++) {
            // current search comparison, with counters for tag and title,
            // used later to improve ranking
            var s = GUIDE_RESOURCES[i];
            s.matched_tag = 0;
            s.matched_title = 0;
            var matched = false;

            // Check if query matches any tags; work backwards toward 1 to assist ranking
            for (var j = s.keywords.length - 1; j >= 0; j--) {
              // it matches a tag
              if (s.keywords[j].toLowerCase().match(textRegex)) {
                matched = true;
                s.matched_tag = j + 1; // add 1 to index position
              }
            }
            // Check if query matches the doc title, but only for current language
            if (s.lang == currentLang) {
              // if query matches the doc title
              if (s.title.toLowerCase().match(textRegex)) {
                matched = true;
                s.matched_title = 1;
              }
            }
            if (matched) {
              gDocsMatches[matchedCountDocs] = s;
              matchedCountDocs++;
            }
          }


          // Search for Tools Guides
          for (var i=0; i<TOOLS_RESOURCES.length; i++) {
            // current search comparison, with counters for tag and title,
            // used later to improve ranking
            var s = TOOLS_RESOURCES[i];
            s.matched_tag = 0;
            s.matched_title = 0;
            var matched = false;

            // Check if query matches any tags; work backwards toward 1 to assist ranking
            for (var j = s.keywords.length - 1; j >= 0; j--) {
              // it matches a tag
              if (s.keywords[j].toLowerCase().match(textRegex)) {
                matched = true;
                s.matched_tag = j + 1; // add 1 to index position
              }
            }
            // Check if query matches the doc title, but only for current language
            if (s.lang == currentLang) {
              // if query matches the doc title
              if (s.title.toLowerCase().match(textRegex)) {
                matched = true;
                s.matched_title = 1;
              }
            }
            if (matched) {
              gDocsMatches[matchedCountDocs] = s;
              matchedCountDocs++;
            }
          }


          // Search for About docs
          for (var i=0; i<ABOUT_RESOURCES.length; i++) {
            // current search comparison, with counters for tag and title,
            // used later to improve ranking
            var s = ABOUT_RESOURCES[i];
            s.matched_tag = 0;
            s.matched_title = 0;
            var matched = false;

            // Check if query matches any tags; work backwards toward 1 to assist ranking
            for (var j = s.keywords.length - 1; j >= 0; j--) {
              // it matches a tag
              if (s.keywords[j].toLowerCase().match(textRegex)) {
                matched = true;
                s.matched_tag = j + 1; // add 1 to index position
              }
            }
            // Check if query matches the doc title, but only for current language
            if (s.lang == currentLang) {
              // if query matches the doc title
              if (s.title.toLowerCase().match(textRegex)) {
                matched = true;
                s.matched_title = 1;
              }
            }
            if (matched) {
              gDocsMatches[matchedCountDocs] = s;
              matchedCountDocs++;
            }
          }


          // Search for Design guides
          for (var i=0; i<DESIGN_RESOURCES.length; i++) {
            // current search comparison, with counters for tag and title,
            // used later to improve ranking
            var s = DESIGN_RESOURCES[i];
            s.matched_tag = 0;
            s.matched_title = 0;
            var matched = false;

            // Check if query matches any tags; work backwards toward 1 to assist ranking
            for (var j = s.keywords.length - 1; j >= 0; j--) {
              // it matches a tag
              if (s.keywords[j].toLowerCase().match(textRegex)) {
                matched = true;
                s.matched_tag = j + 1; // add 1 to index position
              }
            }
            // Check if query matches the doc title, but only for current language
            if (s.lang == currentLang) {
              // if query matches the doc title
              if (s.title.toLowerCase().match(textRegex)) {
                matched = true;
                s.matched_title = 1;
              }
            }
            if (matched) {
              gDocsMatches[matchedCountDocs] = s;
              matchedCountDocs++;
            }
          }


          // Search for Distribute guides
          for (var i=0; i<DISTRIBUTE_RESOURCES.length; i++) {
            // current search comparison, with counters for tag and title,
            // used later to improve ranking
            var s = DISTRIBUTE_RESOURCES[i];
            s.matched_tag = 0;
            s.matched_title = 0;
            var matched = false;

            // Check if query matches any tags; work backwards toward 1 to assist ranking
            for (var j = s.keywords.length - 1; j >= 0; j--) {
              // it matches a tag
              if (s.keywords[j].toLowerCase().match(textRegex)) {
                matched = true;
                s.matched_tag = j + 1; // add 1 to index position
              }
            }
            // Check if query matches the doc title, but only for current language
            if (s.lang == currentLang) {
              // if query matches the doc title
              if (s.title.toLowerCase().match(textRegex)) {
                matched = true;
                s.matched_title = 1;
              }
            }
            if (matched) {
              gDocsMatches[matchedCountDocs] = s;
              matchedCountDocs++;
            }
          }


          // Search for Google guides
          for (var i=0; i<GOOGLE_RESOURCES.length; i++) {
            // current search comparison, with counters for tag and title,
            // used later to improve ranking
            var s = GOOGLE_RESOURCES[i];
            s.matched_tag = 0;
            s.matched_title = 0;
            var matched = false;

            // Check if query matches any tags; work backwards toward 1 to assist ranking
            for (var j = s.keywords.length - 1; j >= 0; j--) {
              // it matches a tag
              if (s.keywords[j].toLowerCase().match(textRegex)) {
                matched = true;
                s.matched_tag = j + 1; // add 1 to index position
              }
            }
            // Check if query matches the doc title, but only for current language
            if (s.lang == currentLang) {
              // if query matches the doc title
              if (s.title.toLowerCase().match(textRegex)) {
                matched = true;
                s.matched_title = 1;
              }
            }
            if (matched) {
              gDocsMatches[matchedCountDocs] = s;
              matchedCountDocs++;
            }
          }


          // Search for Samples
          for (var i=0; i<SAMPLES_RESOURCES.length; i++) {
            // current search comparison, with counters for tag and title,
            // used later to improve ranking
            var s = SAMPLES_RESOURCES[i];
            s.matched_tag = 0;
            s.matched_title = 0;
            var matched = false;
            // Check if query matches any tags; work backwards toward 1 to assist ranking
            for (var j = s.keywords.length - 1; j >= 0; j--) {
              // it matches a tag
              if (s.keywords[j].toLowerCase().match(textRegex)) {
                matched = true;
                s.matched_tag = j + 1; // add 1 to index position
              }
            }
            // Check if query matches the doc title, but only for current language
            if (s.lang == currentLang) {
              // if query matches the doc title.t
              if (s.title.toLowerCase().match(textRegex)) {
                matched = true;
                s.matched_title = 1;
              }
            }
            if (matched) {
              gDocsMatches[matchedCountDocs] = s;
              matchedCountDocs++;
            }
          }

          // Rank/sort all the matched pages
          rank_autocomplete_doc_results(text, gDocsMatches);
        }

        // draw the suggestions
        sync_selection_table(toroot);
        return true; // allow the event to bubble up to the search api
    }
}

/* Order the jd doc result list based on match quality */
function rank_autocomplete_doc_results(query, matches) {
    query = query || '';
    if (!matches || !matches.length)
      return;

    var _resultScoreFn = function(match) {
        var score = 1.0;

        // if the query matched a tag
        if (match.matched_tag > 0) {
          // multiply score by factor relative to position in tags list (max of 3)
          score *= 3 / match.matched_tag;

          // if it also matched the title
          if (match.matched_title > 0) {
            score *= 2;
          }
        } else if (match.matched_title > 0) {
          score *= 3;
        }

        return score;
    };

    for (var i=0; i<matches.length; i++) {
        matches[i].__resultScore = _resultScoreFn(matches[i]);
    }

    matches.sort(function(a,b){
        var n = b.__resultScore - a.__resultScore;
        if (n == 0) // lexicographical sort if scores are the same
            n = (a.label < b.label) ? -1 : 1;
        return n;
    });
}

/* Order the result list based on match quality */
function rank_autocomplete_api_results(query, matches) {
    query = query || '';
    if (!matches || !matches.length)
      return;

    // helper function that gets the last occurence index of the given regex
    // in the given string, or -1 if not found
    var _lastSearch = function(s, re) {
      if (s == '')
        return -1;
      var l = -1;
      var tmp;
      while ((tmp = s.search(re)) >= 0) {
        if (l < 0) l = 0;
        l += tmp;
        s = s.substr(tmp + 1);
      }
      return l;
    };

    // helper function that counts the occurrences of a given character in
    // a given string
    var _countChar = function(s, c) {
      var n = 0;
      for (var i=0; i<s.length; i++)
        if (s.charAt(i) == c) ++n;
      return n;
    };

    var queryLower = query.toLowerCase();
    var queryAlnum = (queryLower.match(/\w+/) || [''])[0];
    var partPrefixAlnumRE = new RegExp('\\b' + queryAlnum);
    var partExactAlnumRE = new RegExp('\\b' + queryAlnum + '\\b');

    var _resultScoreFn = function(result) {
        // scores are calculated based on exact and prefix matches,
        // and then number of path separators (dots) from the last
        // match (i.e. favoring classes and deep package names)
        var score = 1.0;
        var labelLower = result.label.toLowerCase();
        var t;
        t = _lastSearch(labelLower, partExactAlnumRE);
        if (t >= 0) {
            // exact part match
            var partsAfter = _countChar(labelLower.substr(t + 1), '.');
            score *= 200 / (partsAfter + 1);
        } else {
            t = _lastSearch(labelLower, partPrefixAlnumRE);
            if (t >= 0) {
                // part prefix match
                var partsAfter = _countChar(labelLower.substr(t + 1), '.');
                score *= 20 / (partsAfter + 1);
            }
        }

        return score;
    };

    for (var i=0; i<matches.length; i++) {
        // if the API is deprecated, default score is 0; otherwise, perform scoring
        if (matches[i].deprecated == "true") {
          matches[i].__resultScore = 0;
        } else {
          matches[i].__resultScore = _resultScoreFn(matches[i]);
        }
    }

    matches.sort(function(a,b){
        var n = b.__resultScore - a.__resultScore;
        if (n == 0) // lexicographical sort if scores are the same
            n = (a.label < b.label) ? -1 : 1;
        return n;
    });
}

/* Add emphasis to part of string that matches query */
function highlight_autocomplete_result_labels(query) {
    query = query || '';
    if ((!gMatches || !gMatches.length) && (!gGoogleMatches || !gGoogleMatches.length))
      return;

    var queryLower = query.toLowerCase();
    var queryAlnumDot = (queryLower.match(/[\w\.]+/) || [''])[0];
    var queryRE = new RegExp(
        '(' + queryAlnumDot.replace(/\./g, '\\.') + ')', 'ig');
    for (var i=0; i<gMatches.length; i++) {
        gMatches[i].__hilabel = gMatches[i].label.replace(
            queryRE, '<b>$1</b>');
    }
    for (var i=0; i<gGoogleMatches.length; i++) {
        gGoogleMatches[i].__hilabel = gGoogleMatches[i].label.replace(
            queryRE, '<b>$1</b>');
    }
}

function search_focus_changed(obj, focused)
{
    if (!focused) {
        if(obj.value == ""){
          $(".search .close").addClass("hide");
        }
        $(".suggest-card").hide();
    }
}

function submit_search() {
  var query = document.getElementById('search_autocomplete').value;
  location.hash = 'q=' + query;
  loadSearchResults();
  $("#searchResults").slideDown('slow');
  return false;
}


function hideResults() {
  $("#searchResults").slideUp();
  $(".search .close").addClass("hide");
  location.hash = '';

  $("#search_autocomplete").val("").blur();

  // reset the ajax search callback to nothing, so results don't appear unless ENTER
  searchControl.setSearchStartingCallback(this, function(control, searcher, query) {});

  // forcefully regain key-up event control (previously jacked by search api)
  $("#search_autocomplete").keyup(function(event) {
    return search_changed(event, false, toRoot);
  });

  return false;
}



/* ########################################################## */
/* ################  CUSTOM SEARCH ENGINE  ################## */
/* ########################################################## */

var searchControl;
google.load('search', '1', {"callback" : function() {
            searchControl = new google.search.SearchControl();
          } });

function loadSearchResults() {
  document.getElementById("search_autocomplete").style.color = "#000";

  searchControl = new google.search.SearchControl();

  // use our existing search form and use tabs when multiple searchers are used
  drawOptions = new google.search.DrawOptions();
  drawOptions.setDrawMode(google.search.SearchControl.DRAW_MODE_TABBED);
  drawOptions.setInput(document.getElementById("search_autocomplete"));

  // configure search result options
  searchOptions = new google.search.SearcherOptions();
  searchOptions.setExpandMode(GSearchControl.EXPAND_MODE_OPEN);

  // configure each of the searchers, for each tab
  devSiteSearcher = new google.search.WebSearch();
  devSiteSearcher.setUserDefinedLabel("All");
  devSiteSearcher.setSiteRestriction("001482626316274216503:zu90b7s047u");

  designSearcher = new google.search.WebSearch();
  designSearcher.setUserDefinedLabel("Design");
  designSearcher.setSiteRestriction("http://developer.android.com/design/");

  trainingSearcher = new google.search.WebSearch();
  trainingSearcher.setUserDefinedLabel("Training");
  trainingSearcher.setSiteRestriction("http://developer.android.com/training/");

  guidesSearcher = new google.search.WebSearch();
  guidesSearcher.setUserDefinedLabel("Guides");
  guidesSearcher.setSiteRestriction("http://developer.android.com/guide/");

  referenceSearcher = new google.search.WebSearch();
  referenceSearcher.setUserDefinedLabel("Reference");
  referenceSearcher.setSiteRestriction("http://developer.android.com/reference/");

  googleSearcher = new google.search.WebSearch();
  googleSearcher.setUserDefinedLabel("Google Services");
  googleSearcher.setSiteRestriction("http://developer.android.com/google/");

  blogSearcher = new google.search.WebSearch();
  blogSearcher.setUserDefinedLabel("Blog");
  blogSearcher.setSiteRestriction("http://android-developers.blogspot.com");

  // add each searcher to the search control
  searchControl.addSearcher(devSiteSearcher, searchOptions);
  searchControl.addSearcher(designSearcher, searchOptions);
  searchControl.addSearcher(trainingSearcher, searchOptions);
  searchControl.addSearcher(guidesSearcher, searchOptions);
  searchControl.addSearcher(referenceSearcher, searchOptions);
  searchControl.addSearcher(googleSearcher, searchOptions);
  searchControl.addSearcher(blogSearcher, searchOptions);

  // configure result options
  searchControl.setResultSetSize(google.search.Search.LARGE_RESULTSET);
  searchControl.setLinkTarget(google.search.Search.LINK_TARGET_SELF);
  searchControl.setTimeoutInterval(google.search.SearchControl.TIMEOUT_SHORT);
  searchControl.setNoResultsString(google.search.SearchControl.NO_RESULTS_DEFAULT_STRING);

  // upon ajax search, refresh the url and search title
  searchControl.setSearchStartingCallback(this, function(control, searcher, query) {
    updateResultTitle(query);
    var query = document.getElementById('search_autocomplete').value;
    location.hash = 'q=' + query;
  });

  // once search results load, set up click listeners
  searchControl.setSearchCompleteCallback(this, function(control, searcher, query) {
    addResultClickListeners();
  });

  // draw the search results box
  searchControl.draw(document.getElementById("leftSearchControl"), drawOptions);

  // get query and execute the search
  searchControl.execute(decodeURI(getQuery(location.hash)));

  document.getElementById("search_autocomplete").focus();
  addTabListeners();
}
// End of loadSearchResults


google.setOnLoadCallback(function(){
  if (location.hash.indexOf("q=") == -1) {
    // if there's no query in the url, don't search and make sure results are hidden
    $('#searchResults').hide();
    return;
  } else {
    // first time loading search results for this page
    $('#searchResults').slideDown('slow');
    $(".search .close").removeClass("hide");
    loadSearchResults();
  }
}, true);

// when an event on the browser history occurs (back, forward, load) requery hash and do search
$(window).hashchange( function(){
  // Exit if the hash isn't a search query or there's an error in the query
  if ((location.hash.indexOf("q=") == -1) || (query == "undefined")) {
    // If the results pane is open, close it.
    if (!$("#searchResults").is(":hidden")) {
      hideResults();
    }
    return;
  }

  // Otherwise, we have a search to do
  var query = decodeURI(getQuery(location.hash));
  searchControl.execute(query);
  $('#searchResults').slideDown('slow');
  $("#search_autocomplete").focus();
  $(".search .close").removeClass("hide");

  updateResultTitle(query);
});

function updateResultTitle(query) {
  $("#searchTitle").html("Results for <em>" + escapeHTML(query) + "</em>");
}

// forcefully regain key-up event control (previously jacked by search api)
$("#search_autocomplete").keyup(function(event) {
  return search_changed(event, false, toRoot);
});

// add event listeners to each tab so we can track the browser history
function addTabListeners() {
  var tabHeaders = $(".gsc-tabHeader");
  for (var i = 0; i < tabHeaders.length; i++) {
    $(tabHeaders[i]).attr("id",i).click(function() {
    /*
      // make a copy of the page numbers for the search left pane
      setTimeout(function() {
        // remove any residual page numbers
        $('#searchResults .gsc-tabsArea .gsc-cursor-box.gs-bidi-start-align').remove();
        // move the page numbers to the left position; make a clone,
        // because the element is drawn to the DOM only once
        // and because we're going to remove it (previous line),
        // we need it to be available to move again as the user navigates
        $('#searchResults .gsc-webResult .gsc-cursor-box.gs-bidi-start-align:visible')
                        .clone().appendTo('#searchResults .gsc-tabsArea');
        }, 200);
      */
    });
  }
  setTimeout(function(){$(tabHeaders[0]).click()},200);
}

// add analytics tracking events to each result link
function addResultClickListeners() {
  $("#searchResults a.gs-title").each(function(index, link) {
    // When user clicks enter for Google search results, track it
    $(link).click(function() {
      _gaq.push(['_trackEvent', 'Google Click', 'clicked: ' + $(this).text(),
                'from: ' + $("#search_autocomplete").val()]);
    });
  });
}


function getQuery(hash) {
  var queryParts = hash.split('=');
  return queryParts[1];
}

/* returns the given string with all HTML brackets converted to entities
    TODO: move this to the site's JS library */
function escapeHTML(string) {
  return string.replace(/</g,"&lt;")
                .replace(/>/g,"&gt;");
}







/* ######################################################## */
/* #################  JAVADOC REFERENCE ################### */
/* ######################################################## */

/* Initialize some droiddoc stuff, but only if we're in the reference */
if (location.pathname.indexOf("/reference") == 0) {
  if(!(location.pathname.indexOf("/reference-gms/packages.html") == 0)
    && !(location.pathname.indexOf("/reference-gcm/packages.html") == 0)
    && !(location.pathname.indexOf("/reference/com/google") == 0)) {
    $(document).ready(function() {
      // init available apis based on user pref
      changeApiLevel();
      initSidenavHeightResize()
      });
  }
}

var API_LEVEL_COOKIE = "api_level";
var minLevel = 1;
var maxLevel = 1;

/******* SIDENAV DIMENSIONS ************/

  function initSidenavHeightResize() {
    // Change the drag bar size to nicely fit the scrollbar positions
    var $dragBar = $(".ui-resizable-s");
    $dragBar.css({'width': $dragBar.parent().width() - 5 + "px"});

    $( "#resize-packages-nav" ).resizable({
      containment: "#nav-panels",
      handles: "s",
      alsoResize: "#packages-nav",
      resize: function(event, ui) { resizeNav(); }, /* resize the nav while dragging */
      stop: function(event, ui) { saveNavPanels(); } /* once stopped, save the sizes to cookie  */
      });

  }

function updateSidenavFixedWidth() {
  if (!navBarIsFixed) return;
  $('#devdoc-nav').css({
    'width' : $('#side-nav').css('width'),
    'margin' : $('#side-nav').css('margin')
  });
  $('#devdoc-nav a.totop').css({'display':'block','width':$("#nav").innerWidth()+'px'});

  initSidenavHeightResize();
}

function updateSidenavFullscreenWidth() {
  if (!navBarIsFixed) return;
  $('#devdoc-nav').css({
    'width' : $('#side-nav').css('width'),
    'margin' : $('#side-nav').css('margin')
  });
  $('#devdoc-nav .totop').css({'left': 'inherit'});

  initSidenavHeightResize();
}

function buildApiLevelSelector() {
  maxLevel = SINCE_DATA.length;
  var userApiLevel = parseInt(readCookie(API_LEVEL_COOKIE));
  userApiLevel = userApiLevel == 0 ? maxLevel : userApiLevel; // If there's no cookie (zero), use the max by default

  minLevel = parseInt($("#doc-api-level").attr("class"));
  // Handle provisional api levels; the provisional level will always be the highest possible level
  // Provisional api levels will also have a length; other stuff that's just missing a level won't,
  // so leave those kinds of entities at the default level of 1 (for example, the R.styleable class)
  if (isNaN(minLevel) && minLevel.length) {
    minLevel = maxLevel;
  }
  var select = $("#apiLevelSelector").html("").change(changeApiLevel);
  for (var i = maxLevel-1; i >= 0; i--) {
    var option = $("<option />").attr("value",""+SINCE_DATA[i]).append(""+SINCE_DATA[i]);
  //  if (SINCE_DATA[i] < minLevel) option.addClass("absent"); // always false for strings (codenames)
    select.append(option);
  }

  // get the DOM element and use setAttribute cuz IE6 fails when using jquery .attr('selected',true)
  var selectedLevelItem = $("#apiLevelSelector option[value='"+userApiLevel+"']").get(0);
  selectedLevelItem.setAttribute('selected',true);
}

function changeApiLevel() {
  maxLevel = SINCE_DATA.length;
  var selectedLevel = maxLevel;

  selectedLevel = parseInt($("#apiLevelSelector option:selected").val());
  toggleVisisbleApis(selectedLevel, "body");

  var date = new Date();
  date.setTime(date.getTime()+(10*365*24*60*60*1000)); // keep this for 10 years
  var expiration = date.toGMTString();
  writeCookie(API_LEVEL_COOKIE, selectedLevel, null, expiration);

  if (selectedLevel < minLevel) {
    var thing = ($("#jd-header").html().indexOf("package") != -1) ? "package" : "class";
    $("#naMessage").show().html("<div><p><strong>This " + thing
              + " requires API level " + minLevel + " or higher.</strong></p>"
              + "<p>This document is hidden because your selected API level for the documentation is "
              + selectedLevel + ". You can change the documentation API level with the selector "
              + "above the left navigation.</p>"
              + "<p>For more information about specifying the API level your app requires, "
              + "read <a href='" + toRoot + "training/basics/supporting-devices/platforms.html'"
              + ">Supporting Different Platform Versions</a>.</p>"
              + "<input type='button' value='OK, make this page visible' "
              + "title='Change the API level to " + minLevel + "' "
              + "onclick='$(\"#apiLevelSelector\").val(\"" + minLevel + "\");changeApiLevel();' />"
              + "</div>");
  } else {
    $("#naMessage").hide();
  }
}

function toggleVisisbleApis(selectedLevel, context) {
  var apis = $(".api",context);
  apis.each(function(i) {
    var obj = $(this);
    var className = obj.attr("class");
    var apiLevelIndex = className.lastIndexOf("-")+1;
    var apiLevelEndIndex = className.indexOf(" ", apiLevelIndex);
    apiLevelEndIndex = apiLevelEndIndex != -1 ? apiLevelEndIndex : className.length;
    var apiLevel = className.substring(apiLevelIndex, apiLevelEndIndex);
    if (apiLevel.length == 0) { // for odd cases when the since data is actually missing, just bail
      return;
    }
    apiLevel = parseInt(apiLevel);

    // Handle provisional api levels; if this item's level is the provisional one, set it to the max
    var selectedLevelNum = parseInt(selectedLevel)
    var apiLevelNum = parseInt(apiLevel);
    if (isNaN(apiLevelNum)) {
        apiLevelNum = maxLevel;
    }

    // Grey things out that aren't available and give a tooltip title
    if (apiLevelNum > selectedLevelNum) {
      obj.addClass("absent").attr("title","Requires API Level \""
            + apiLevel + "\" or higher. To reveal, change the target API level "
              + "above the left navigation.");
    }
    else obj.removeClass("absent").removeAttr("title");
  });
}




/* #################  SIDENAV TREE VIEW ################### */

function new_node(me, mom, text, link, children_data, api_level)
{
  var node = new Object();
  node.children = Array();
  node.children_data = children_data;
  node.depth = mom.depth + 1;

  node.li = document.createElement("li");
  mom.get_children_ul().appendChild(node.li);

  node.label_div = document.createElement("div");
  node.label_div.className = "label";
  if (api_level != null) {
    $(node.label_div).addClass("api");
    $(node.label_div).addClass("api-level-"+api_level);
  }
  node.li.appendChild(node.label_div);

  if (children_data != null) {
    node.expand_toggle = document.createElement("a");
    node.expand_toggle.href = "javascript:void(0)";
    node.expand_toggle.onclick = function() {
          if (node.expanded) {
            $(node.get_children_ul()).slideUp("fast");
            node.plus_img.src = me.toroot + "assets/images/triangle-closed-small.png";
            node.expanded = false;
          } else {
            expand_node(me, node);
          }
       };
    node.label_div.appendChild(node.expand_toggle);

    node.plus_img = document.createElement("img");
    node.plus_img.src = me.toroot + "assets/images/triangle-closed-small.png";
    node.plus_img.className = "plus";
    node.plus_img.width = "8";
    node.plus_img.border = "0";
    node.expand_toggle.appendChild(node.plus_img);

    node.expanded = false;
  }

  var a = document.createElement("a");
  node.label_div.appendChild(a);
  node.label = document.createTextNode(text);
  a.appendChild(node.label);
  if (link) {
    a.href = me.toroot + link;
  } else {
    if (children_data != null) {
      a.className = "nolink";
      a.href = "javascript:void(0)";
      a.onclick = node.expand_toggle.onclick;
      // This next line shouldn't be necessary.  I'll buy a beer for the first
      // person who figures out how to remove this line and have the link
      // toggle shut on the first try. --joeo@android.com
      node.expanded = false;
    }
  }


  node.children_ul = null;
  node.get_children_ul = function() {
      if (!node.children_ul) {
        node.children_ul = document.createElement("ul");
        node.children_ul.className = "children_ul";
        node.children_ul.style.display = "none";
        node.li.appendChild(node.children_ul);
      }
      return node.children_ul;
    };

  return node;
}




function expand_node(me, node)
{
  if (node.children_data && !node.expanded) {
    if (node.children_visited) {
      $(node.get_children_ul()).slideDown("fast");
    } else {
      get_node(me, node);
      if ($(node.label_div).hasClass("absent")) {
        $(node.get_children_ul()).addClass("absent");
      }
      $(node.get_children_ul()).slideDown("fast");
    }
    node.plus_img.src = me.toroot + "assets/images/triangle-opened-small.png";
    node.expanded = true;

    // perform api level toggling because new nodes are new to the DOM
    var selectedLevel = $("#apiLevelSelector option:selected").val();
    toggleVisisbleApis(selectedLevel, "#side-nav");
  }
}

function get_node(me, mom)
{
  mom.children_visited = true;
  for (var i in mom.children_data) {
    var node_data = mom.children_data[i];
    mom.children[i] = new_node(me, mom, node_data[0], node_data[1],
        node_data[2], node_data[3]);
  }
}

function this_page_relative(toroot)
{
  var full = document.location.pathname;
  var file = "";
  if (toroot.substr(0, 1) == "/") {
    if (full.substr(0, toroot.length) == toroot) {
      return full.substr(toroot.length);
    } else {
      // the file isn't under toroot.  Fail.
      return null;
    }
  } else {
    if (toroot != "./") {
      toroot = "./" + toroot;
    }
    do {
      if (toroot.substr(toroot.length-3, 3) == "../" || toroot == "./") {
        var pos = full.lastIndexOf("/");
        file = full.substr(pos) + file;
        full = full.substr(0, pos);
        toroot = toroot.substr(0, toroot.length-3);
      }
    } while (toroot != "" && toroot != "/");
    return file.substr(1);
  }
}

function find_page(url, data)
{
  var nodes = data;
  var result = null;
  for (var i in nodes) {
    var d = nodes[i];
    if (d[1] == url) {
      return new Array(i);
    }
    else if (d[2] != null) {
      result = find_page(url, d[2]);
      if (result != null) {
        return (new Array(i).concat(result));
      }
    }
  }
  return null;
}

function init_default_navtree(toroot) {
  // load json file for navtree data
  $.getScript(toRoot + 'navtree_data.js', function(data, textStatus, jqxhr) {
      // when the file is loaded, initialize the tree
      if(jqxhr.status === 200) {
          init_navtree("tree-list", toroot, NAVTREE_DATA);
      }
  });

  // perform api level toggling because because the whole tree is new to the DOM
  var selectedLevel = $("#apiLevelSelector option:selected").val();
  toggleVisisbleApis(selectedLevel, "#side-nav");
}

function init_navtree(navtree_id, toroot, root_nodes)
{
  var me = new Object();
  me.toroot = toroot;
  me.node = new Object();

  me.node.li = document.getElementById(navtree_id);
  me.node.children_data = root_nodes;
  me.node.children = new Array();
  me.node.children_ul = document.createElement("ul");
  me.node.get_children_ul = function() { return me.node.children_ul; };
  //me.node.children_ul.className = "children_ul";
  me.node.li.appendChild(me.node.children_ul);
  me.node.depth = 0;

  get_node(me, me.node);

  me.this_page = this_page_relative(toroot);
  me.breadcrumbs = find_page(me.this_page, root_nodes);
  if (me.breadcrumbs != null && me.breadcrumbs.length != 0) {
    var mom = me.node;
    for (var i in me.breadcrumbs) {
      var j = me.breadcrumbs[i];
      mom = mom.children[j];
      expand_node(me, mom);
    }
    mom.label_div.className = mom.label_div.className + " selected";
    addLoadEvent(function() {
      scrollIntoView("nav-tree");
      });
  }
}








/* TODO: eliminate redundancy with non-google functions */
function init_google_navtree(navtree_id, toroot, root_nodes)
{
  var me = new Object();
  me.toroot = toroot;
  me.node = new Object();

  me.node.li = document.getElementById(navtree_id);
  me.node.children_data = root_nodes;
  me.node.children = new Array();
  me.node.children_ul = document.createElement("ul");
  me.node.get_children_ul = function() { return me.node.children_ul; };
  //me.node.children_ul.className = "children_ul";
  me.node.li.appendChild(me.node.children_ul);
  me.node.depth = 0;

  get_google_node(me, me.node);
}

function new_google_node(me, mom, text, link, children_data, api_level)
{
  var node = new Object();
  var child;
  node.children = Array();
  node.children_data = children_data;
  node.depth = mom.depth + 1;
  node.get_children_ul = function() {
      if (!node.children_ul) {
        node.children_ul = document.createElement("ul");
        node.children_ul.className = "tree-list-children";
        node.li.appendChild(node.children_ul);
      }
      return node.children_ul;
    };
  node.li = document.createElement("li");

  mom.get_children_ul().appendChild(node.li);


  if(link) {
    child = document.createElement("a");

  }
  else {
    child = document.createElement("span");
    child.className = "tree-list-subtitle";

  }
  if (children_data != null) {
    node.li.className="nav-section";
    node.label_div = document.createElement("div");
    node.label_div.className = "nav-section-header-ref";
    node.li.appendChild(node.label_div);
    get_google_node(me, node);
    node.label_div.appendChild(child);
  }
  else {
    node.li.appendChild(child);
  }
  if(link) {
    child.href = me.toroot + link;
  }
  node.label = document.createTextNode(text);
  child.appendChild(node.label);

  node.children_ul = null;

  return node;
}

function get_google_node(me, mom)
{
  mom.children_visited = true;
  var linkText;
  for (var i in mom.children_data) {
    var node_data = mom.children_data[i];
    linkText = node_data[0];

    if(linkText.match("^"+"com.google.android")=="com.google.android"){
      linkText = linkText.substr(19, linkText.length);
    }
      mom.children[i] = new_google_node(me, mom, linkText, node_data[1],
          node_data[2], node_data[3]);
  }
}






/****** NEW version of script to build google and sample navs dynamically ******/
// TODO: update Google reference docs to tolerate this new implementation

var NODE_NAME = 0;
var NODE_HREF = 1;
var NODE_GROUP = 2;
var NODE_TAGS = 3;
var NODE_CHILDREN = 4;

function init_google_navtree2(navtree_id, data)
{
  var $containerUl = $("#"+navtree_id);
  for (var i in data) {
    var node_data = data[i];
    $containerUl.append(new_google_node2(node_data));
  }

  // Make all third-generation list items 'sticky' to prevent them from collapsing
  $containerUl.find('li li li.nav-section').addClass('sticky');

  initExpandableNavItems("#"+navtree_id);
}

function new_google_node2(node_data)
{
  var linkText = node_data[NODE_NAME];
  if(linkText.match("^"+"com.google.android")=="com.google.android"){
    linkText = linkText.substr(19, linkText.length);
  }
  var $li = $('<li>');
  var $a;
  if (node_data[NODE_HREF] != null) {
    $a = $('<a href="' + toRoot + node_data[NODE_HREF] + '" title="' + linkText + '" >'
        + linkText + '</a>');
  } else {
    $a = $('<a href="#" onclick="return false;" title="' + linkText + '" >'
        + linkText + '/</a>');
  }
  var $childUl = $('<ul>');
  if (node_data[NODE_CHILDREN] != null) {
    $li.addClass("nav-section");
    $a = $('<div class="nav-section-header">').append($a);
    if (node_data[NODE_HREF] == null) $a.addClass('empty');

    for (var i in node_data[NODE_CHILDREN]) {
      var child_node_data = node_data[NODE_CHILDREN][i];
      $childUl.append(new_google_node2(child_node_data));
    }
    $li.append($childUl);
  }
  $li.prepend($a);

  return $li;
}











function showGoogleRefTree() {
  init_default_google_navtree(toRoot);
  init_default_gcm_navtree(toRoot);
}

function init_default_google_navtree(toroot) {
  // load json file for navtree data
  $.getScript(toRoot + 'gms_navtree_data.js', function(data, textStatus, jqxhr) {
      // when the file is loaded, initialize the tree
      if(jqxhr.status === 200) {
          init_google_navtree("gms-tree-list", toroot, GMS_NAVTREE_DATA);
          highlightSidenav();
          resizeNav();
      }
  });
}

function init_default_gcm_navtree(toroot) {
  // load json file for navtree data
  $.getScript(toRoot + 'gcm_navtree_data.js', function(data, textStatus, jqxhr) {
      // when the file is loaded, initialize the tree
      if(jqxhr.status === 200) {
          init_google_navtree("gcm-tree-list", toroot, GCM_NAVTREE_DATA);
          highlightSidenav();
          resizeNav();
      }
  });
}

function showSamplesRefTree() {
  init_default_samples_navtree(toRoot);
}

function init_default_samples_navtree(toroot) {
  // load json file for navtree data
  $.getScript(toRoot + 'samples_navtree_data.js', function(data, textStatus, jqxhr) {
      // when the file is loaded, initialize the tree
      if(jqxhr.status === 200) {
          // hack to remove the "about the samples" link then put it back in
          // after we nuke the list to remove the dummy static list of samples
          var $firstLi = $("#nav.samples-nav > li:first-child").clone();
          $("#nav.samples-nav").empty();
          $("#nav.samples-nav").append($firstLi);

          init_google_navtree2("nav.samples-nav", SAMPLES_NAVTREE_DATA);
          highlightSidenav();
          resizeNav();
          if ($("#jd-content #samples").length) {
            showSamples();
          }
      }
  });
}

/* TOGGLE INHERITED MEMBERS */

/* Toggle an inherited class (arrow toggle)
 * @param linkObj  The link that was clicked.
 * @param expand  'true' to ensure it's expanded. 'false' to ensure it's closed.
 *                'null' to simply toggle.
 */
function toggleInherited(linkObj, expand) {
    var base = linkObj.getAttribute("id");
    var list = document.getElementById(base + "-list");
    var summary = document.getElementById(base + "-summary");
    var trigger = document.getElementById(base + "-trigger");
    var a = $(linkObj);
    if ( (expand == null && a.hasClass("closed")) || expand ) {
        list.style.display = "none";
        summary.style.display = "block";
        trigger.src = toRoot + "assets/images/triangle-opened.png";
        a.removeClass("closed");
        a.addClass("opened");
    } else if ( (expand == null && a.hasClass("opened")) || (expand == false) ) {
        list.style.display = "block";
        summary.style.display = "none";
        trigger.src = toRoot + "assets/images/triangle-closed.png";
        a.removeClass("opened");
        a.addClass("closed");
    }
    return false;
}

/* Toggle all inherited classes in a single table (e.g. all inherited methods)
 * @param linkObj  The link that was clicked.
 * @param expand  'true' to ensure it's expanded. 'false' to ensure it's closed.
 *                'null' to simply toggle.
 */
function toggleAllInherited(linkObj, expand) {
  var a = $(linkObj);
  var table = $(a.parent().parent().parent()); // ugly way to get table/tbody
  var expandos = $(".jd-expando-trigger", table);
  if ( (expand == null && a.text() == "[Expand]") || expand ) {
    expandos.each(function(i) {
      toggleInherited(this, true);
    });
    a.text("[Collapse]");
  } else if ( (expand == null && a.text() == "[Collapse]") || (expand == false) ) {
    expandos.each(function(i) {
      toggleInherited(this, false);
    });
    a.text("[Expand]");
  }
  return false;
}

/* Toggle all inherited members in the class (link in the class title)
 */
function toggleAllClassInherited() {
  var a = $("#toggleAllClassInherited"); // get toggle link from class title
  var toggles = $(".toggle-all", $("#body-content"));
  if (a.text() == "[Expand All]") {
    toggles.each(function(i) {
      toggleAllInherited(this, true);
    });
    a.text("[Collapse All]");
  } else {
    toggles.each(function(i) {
      toggleAllInherited(this, false);
    });
    a.text("[Expand All]");
  }
  return false;
}

/* Expand all inherited members in the class. Used when initiating page search */
function ensureAllInheritedExpanded() {
  var toggles = $(".toggle-all", $("#body-content"));
  toggles.each(function(i) {
    toggleAllInherited(this, true);
  });
  $("#toggleAllClassInherited").text("[Collapse All]");
}


/* HANDLE KEY EVENTS
 * - Listen for Ctrl+F (Cmd on Mac) and expand all inherited members (to aid page search)
 */
var agent = navigator['userAgent'].toLowerCase();
var mac = agent.indexOf("macintosh") != -1;

$(document).keydown( function(e) {
var control = mac ? e.metaKey && !e.ctrlKey : e.ctrlKey; // get ctrl key
  if (control && e.which == 70) {  // 70 is "F"
    ensureAllInheritedExpanded();
  }
});






/* On-demand functions */

/** Move sample code line numbers out of PRE block and into non-copyable column */
function initCodeLineNumbers() {
  var numbers = $("#codesample-block a.number");
  if (numbers.length) {
    $("#codesample-line-numbers").removeClass("hidden").append(numbers);
  }

  $(document).ready(function() {
    // select entire line when clicked
    $("span.code-line").click(function() {
      if (!shifted) {
        selectText(this);
      }
    });
    // invoke line link on double click
    $(".code-line").dblclick(function() {
      document.location.hash = $(this).attr('id');
    });
    // highlight the line when hovering on the number
    $("#codesample-line-numbers a.number").mouseover(function() {
      var id = $(this).attr('href');
      $(id).css('background','#e7e7e7');
    });
    $("#codesample-line-numbers a.number").mouseout(function() {
      var id = $(this).attr('href');
      $(id).css('background','none');
    });
  });
}

// create SHIFT key binder to avoid the selectText method when selecting multiple lines
var shifted = false;
$(document).bind('keyup keydown', function(e){shifted = e.shiftKey; return true;} );

// courtesy of jasonedelman.com
function selectText(element) {
    var doc = document
        , range, selection
    ;
    if (doc.body.createTextRange) { //ms
        range = doc.body.createTextRange();
        range.moveToElementText(element);
        range.select();
    } else if (window.getSelection) { //all others
        selection = window.getSelection();
        range = doc.createRange();
        range.selectNodeContents(element);
        selection.removeAllRanges();
        selection.addRange(range);
    }
}




/** Display links and other information about samples that match the
    group specified by the URL */
function showSamples() {
  var group = $("#samples").attr('class');
  $("#samples").html("<p>Here are some samples for <b>" + group + "</b> apps:</p>");

  var $ul = $("<ul>");
  $selectedLi = $("#nav li.selected");

  $selectedLi.children("ul").children("li").each(function() {
      var $li = $("<li>").append($(this).find("a").first().clone());
      $ul.append($li);
  });

  $("#samples").append($ul);

}
