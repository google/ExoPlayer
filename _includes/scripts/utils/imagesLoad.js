(function() {
  window.imagesLoad = function(images) {
    images = images || document.getElementsByTagName('img');
    var imagesCount = images.length, loadedCount = 0, image;
    var i, j, loaded = false, cbs = [];
    imagesCount < 1 && (loaded = true);
    for (i = 0; i < imagesCount; i++) {
      image = images[i];
      image.complete ? handleImageLoad() : image.addEventListener('load', handleImageLoad);
    }
    function handleImageLoad() {
      loadedCount++;
      if (loadedCount === imagesCount) {
        loaded = true;
        if (cbs.length > 0) {
          for (j = 0; j < cbs.length; j++) {
            cbs[j]();
          }
        }
      }
    }
    return {
      then: function(cb) {
        cb && (loaded ? cb() : (cbs.push(cb)));
      }
    };
  };
})();