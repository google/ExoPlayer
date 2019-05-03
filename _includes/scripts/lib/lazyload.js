(function() {
  var Set = (function() {
    var add = function(item) {
      var i, data = this._data;
      for (i = 0; i < data.length; i++) {
        if (data[i] === item) {
          return;
        }
      }
      this.size ++;
      data.push(item);
      return data;
    };

    var Set = function(data) {
      this.size = 0;
      this._data = [];
      var i;
      if (data.length > 0) {
        for (i = 0; i < data.length; i++) {
          add.call(this, data[i]);
        }
      }
    };
    Set.prototype.add = add;
    Set.prototype.get = function(index) { return this._data[index]; };
    Set.prototype.has = function(item) {
      var i, data = this._data;
      for (i = 0; i < data.length; i++) {
        if (this.get(i) === item) {
          return true;
        }
      }
      return false;
    };
    Set.prototype.is = function(map) {
      if (map._data.length !== this._data.length) { return false; }
      var i, j, flag, tData = this._data, mData = map._data;
      for (i = 0; i < tData.length; i++) {
        for (flag = false, j = 0; j < mData.length; j++) {
          if (tData[i] === mData[j]) {
            flag = true;
            break;
          }
        }
        if (!flag) { return false; }
      }
      return true;
    };
    Set.prototype.values = function() {
      return this._data;
    };
    return Set;
  })();

  window.Lazyload = (function(doc) {
    var queue = {js: [], css: []}, sources = {js: {}, css: {}}, context = this;
    var createNode = function(name, attrs) {
      var node = doc.createElement(name), attr;
      for (attr in attrs) {
        if (attrs.hasOwnProperty(attr)) {
          node.setAttribute(attr, attrs[attr]);
        }
      }
      return node;
    };
    var end = function(type, url) {
      var s, q, qi, cbs, i, j, cur, val, flag;
      if (type === 'js' || type ==='css') {
        s = sources[type], q = queue[type];
        s[url] = true;
        for (i = 0; i < q.length; i++) {
          cur = q[i];
          if (cur.urls.has(url)) {
            qi = cur, val = qi.urls.values();
            qi && (cbs = qi.callbacks);
            for (flag = true, j = 0; j < val.length; j++) {
              cur = val[j];
              if (!s[cur]) {
                flag = false;
              }
            }
            if (flag && cbs && cbs.length > 0) {
              for (j = 0; j < cbs.length; j++) {
                cbs[j].call(context);
              }
              qi.load = true;
            }
          }
        }
      }
    };
    var load = function(type, urls, callback) {
      var s, q, qi, node, i, cur,
        _urls = typeof urls === 'string' ? new Set([urls]) : new Set(urls), val, url;
      if (type === 'js' || type ==='css') {
        s = sources[type], q = queue[type];
        for (i = 0; i < q.length; i++) {
          cur = q[i];
          if (_urls.is(cur.urls)) {
            qi = cur;
            break;
          }
        }
        val = _urls.values();
        if (qi) {
          callback && (qi.load || qi.callbacks.push(callback));
          callback && (qi.load && callback());
        } else {
          q.push({
            urls: _urls,
            callbacks: callback ? [callback] : [],
            load: false
          });
          for (i = 0; i < val.length; i++) {
            node = null, url = val[i];
            if (s[url] === undefined) {
              (type === 'js' ) && (node = createNode('script', { src: url }));
              (type === 'css') && (node = createNode('link', { rel: 'stylesheet', href: url }));
              if (node) {
                node.onload = (function(type, url) {
                  return function() {
                    end(type, url);
                  };
                })(type, url);
                (doc.head || doc.body).appendChild(node);
                s[url] = false;
              }
            }
          }
        }
      }
    };
    return {
      js: function(url, callback) {
        load('js', url, callback);
      },
      css: function(url, callback) {
        load('css', url, callback);
      }
    };
  })(this.document);
})();