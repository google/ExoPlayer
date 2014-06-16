var gSelectedIndex = -1;
var gSelectedID = -1;
var gMatches = new Array();
var gLastText = "";
var ROW_COUNT = 20;
var gInitialized = false;
var DEFAULT_TEXT = "search developer docs";

function set_row_selected(row, selected)
{
    var c1 = row.cells[0];
  //  var c2 = row.cells[1];
    if (selected) {
        c1.className = "jd-autocomplete jd-selected";
  //      c2.className = "jd-autocomplete jd-selected jd-linktype";
    } else {
        c1.className = "jd-autocomplete";
  //      c2.className = "jd-autocomplete jd-linktype";
    }
}

function set_row_values(toroot, row, match)
{
    var link = row.cells[0].childNodes[0];
    link.innerHTML = match.__hilabel || match.label;
    link.href = toroot + match.link
  //  row.cells[1].innerHTML = match.type;
}

function sync_selection_table(toroot)
{
    var filtered = document.getElementById("search_filtered");
    var r; //TR DOM object
    var i; //TR iterator
    gSelectedID = -1;

    filtered.onmouseover = function() { 
        if(gSelectedIndex >= 0) {
          set_row_selected(this.rows[gSelectedIndex], false);
          gSelectedIndex = -1;
        }
    }

    //initialize the table; draw it for the first time (but not visible).
    if (!gInitialized) {
        for (i=0; i<ROW_COUNT; i++) {
            var r = filtered.insertRow(-1);
            var c1 = r.insertCell(-1);
        //    var c2 = r.insertCell(-1);
            c1.className = "jd-autocomplete";
         //   c2.className = "jd-autocomplete jd-linktype";
            var link = document.createElement("a");
            c1.onmousedown = function() {
                window.location = this.firstChild.getAttribute("href");
            }
            c1.onmouseover = function() {
                this.className = this.className + " jd-selected";
            }
            c1.onmouseout = function() {
                this.className = "jd-autocomplete";
            }
            c1.appendChild(link);
        }
  /*      var r = filtered.insertRow(-1);
        var c1 = r.insertCell(-1);
        c1.className = "jd-autocomplete jd-linktype";
        c1.colSpan = 2; */
        gInitialized = true;
    }

    //if we have results, make the table visible and initialize result info
    if (gMatches.length > 0) {
        document.getElementById("search_filtered_div").className = "showing";
        var N = gMatches.length < ROW_COUNT ? gMatches.length : ROW_COUNT;
        for (i=0; i<N; i++) {
            r = filtered.rows[i];
            r.className = "show-row";
            set_row_values(toroot, r, gMatches[i]);
            set_row_selected(r, i == gSelectedIndex);
            if (i == gSelectedIndex) {
                gSelectedID = gMatches[i].id;
            }
        }
        //start hiding rows that are no longer matches
        for (; i<ROW_COUNT; i++) {
            r = filtered.rows[i];
            r.className = "no-display";
        }
        //if there are more results we're not showing, so say so.
/*      if (gMatches.length > ROW_COUNT) {
            r = filtered.rows[ROW_COUNT];
            r.className = "show-row";
            c1 = r.cells[0];
            c1.innerHTML = "plus " + (gMatches.length-ROW_COUNT) + " more"; 
        } else {
            filtered.rows[ROW_COUNT].className = "hide-row";
        }*/
    //if we have no results, hide the table
    } else {
        document.getElementById("search_filtered_div").className = "no-display";
    }
}

function search_changed(e, kd, toroot)
{
    var search = document.getElementById("search_autocomplete");
    var text = search.value.replace(/(^ +)|( +$)/g, '');

    // 13 = enter
    if (e.keyCode == 13) {
        document.getElementById("search_filtered_div").className = "no-display";
        if (kd && gSelectedIndex >= 0) {
            window.location = toroot + gMatches[gSelectedIndex].link;
            return false;
        } else if (gSelectedIndex < 0) {
            return true;
        }
    }
    // 38 -- arrow up
    else if (kd && (e.keyCode == 38)) {
        if (gSelectedIndex >= 0) {
            gSelectedIndex--;
        }
        sync_selection_table(toroot);
        return false;
    }
    // 40 -- arrow down
    else if (kd && (e.keyCode == 40)) {
        if (gSelectedIndex < gMatches.length-1
                        && gSelectedIndex < ROW_COUNT-1) {
            gSelectedIndex++;
        }
        sync_selection_table(toroot);
        return false;
    }
    else if (!kd) {
        gMatches = new Array();
        matchedCount = 0;
        gSelectedIndex = -1;
        for (var i=0; i<DATA.length; i++) {
            var s = DATA[i];
            if (text.length != 0 &&
                  s.label.toLowerCase().indexOf(text.toLowerCase()) != -1) {
                gMatches[matchedCount] = s;
                matchedCount++;
            }
        }
        rank_autocomplete_results(text);
        for (var i=0; i<gMatches.length; i++) {
            var s = gMatches[i];
            if (gSelectedID == s.id) {
                gSelectedIndex = i;
            }
        }
        highlight_autocomplete_result_labels(text);
        sync_selection_table(toroot);
        return true; // allow the event to bubble up to the search api
    }
}

function rank_autocomplete_results(query) {
    query = query || '';
    if (!gMatches || !gMatches.length)
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

    for (var i=0; i<gMatches.length; i++) {
        gMatches[i].__resultScore = _resultScoreFn(gMatches[i]);
    }

    gMatches.sort(function(a,b){
        var n = b.__resultScore - a.__resultScore;
        if (n == 0) // lexicographical sort if scores are the same
            n = (a.label < b.label) ? -1 : 1;
        return n;
    });
}

function highlight_autocomplete_result_labels(query) {
    query = query || '';
    if (!gMatches || !gMatches.length)
      return;

    var queryLower = query.toLowerCase();
    var queryAlnumDot = (queryLower.match(/[\w\.]+/) || [''])[0];
    var queryRE = new RegExp(
        '(' + queryAlnumDot.replace(/\./g, '\\.') + ')', 'ig');
    for (var i=0; i<gMatches.length; i++) {
        gMatches[i].__hilabel = gMatches[i].label.replace(
            queryRE, '<b>$1</b>');
    }
}

function search_focus_changed(obj, focused)
{
    if (focused) {
        if(obj.value == DEFAULT_TEXT){
            obj.value = "";
            obj.style.color="#000000";
        }
    } else {
        if(obj.value == ""){
          obj.value = DEFAULT_TEXT;
          obj.style.color="#aaaaaa";
        }
        document.getElementById("search_filtered_div").className = "no-display";
    }
}

function submit_search() {
  var query = document.getElementById('search_autocomplete').value;
  document.location = toRoot + 'search.html#q=' + query + '&t=0';
  return false;
}
