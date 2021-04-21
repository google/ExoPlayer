var SOURCES = window.TEXT_VARIABLES.sources;
window.Lazyload.js(SOURCES.jquery, function() {
  /* global google */
  var search = (window.search || (window.search = {}));
  var searchBox, searchInput, clearIcon, searchModal;

  search.clear = function() {
    searchBox && searchBox.clearAllResults();
  };
  search.onShow = function() {
    searchInput && searchInput.focus();
  };
  search.onHide = function() {
    searchInput && searchInput.blur();
  };

  window.__gcse = {
    callback: function() {
      searchBox = google.search.cse.element.getElement('search-box');
      searchInput = document.getElementById('gsc-i-id1');
      clearIcon = document.getElementById('gs_cb50');
      searchModal = search.searchModal;
      searchModal && searchModal.$el && searchModal.$el.on('click', function(e) {
        (e.target === this || e.target === clearIcon || e.target.className === 'gs-title') && searchModal.hide();
      });
    }
  };
  var cx = '{{ site.search.google.custom_search_engine_id }}'; // Insert your own Custom Search Engine ID here
  var gcse = document.createElement('script'); gcse.type = 'text/javascript'; gcse.async = true;
  gcse.src = (document.location.protocol == 'https:' ? 'https:' : 'http:') +
    '//cse.google.com/cse.js?cx=' + cx;
  var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(gcse, s);
});
