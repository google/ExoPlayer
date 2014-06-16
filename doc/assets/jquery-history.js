/**
 * jQuery history event v0.1
 * Copyright (c) 2008 Tom Rodenberg <tarodenberg gmail com>
 * Licensed under the GPL (http://www.gnu.org/licenses/gpl.html) license.
 */
(function($) {
    var currentHash, previousNav, timer, hashTrim = /^.*#/;

    var msie = {
        iframe: null,
        getDoc: function() {
            return msie.iframe.contentWindow.document;
        },
        getHash: function() {
            return msie.getDoc().location.hash;
        },
        setHash: function(hash) {
            var d = msie.getDoc();
            d.open();
            d.close();
            d.location.hash = hash;
        }
    };

    var historycheck = function() {
        var hash = msie.iframe ? msie.getHash() : location.hash;
        if (hash != currentHash) {
            currentHash = hash;
            if (msie.iframe) {
                location.hash = currentHash;
            }
            var current = $.history.getCurrent();
            $.event.trigger('history', [current, previousNav]);
            previousNav = current;
        }
    };

    $.history = {
        add: function(hash) {
            hash = '#' + hash.replace(hashTrim, '');
            if (currentHash != hash) {
                var previous = $.history.getCurrent();
                location.hash = currentHash = hash;
                if (msie.iframe) {
                    msie.setHash(currentHash);
                }
                $.event.trigger('historyadd', [$.history.getCurrent(), previous]);
            }
            if (!timer) {
                timer = setInterval(historycheck, 100);
            }
        },
        getCurrent: function() {
            if (currentHash) {
              return currentHash.replace(hashTrim, '');
            } else { 
              return ""; 
            }
        }
    };

    $.fn.history = function(fn) {
        $(this).bind('history', fn);
    };

    $.fn.historyadd = function(fn) {
        $(this).bind('historyadd', fn);
    };

    $(function() {
        currentHash = location.hash;
        if ($.browser.msie) {
            msie.iframe = $('<iframe style="display:none" src="javascript:false;"></iframe>').prependTo('body')[0];
            msie.setHash(currentHash);
            currentHash = msie.getHash();
        }
    });
})(jQuery);
