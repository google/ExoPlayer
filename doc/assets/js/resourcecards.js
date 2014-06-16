// Requires jd_tag_helpers.js and the data JS to be loaded.

$(document).ready(function() {
  $('.resource-widget').each(function() {
    initResourceWidget(this);
  });
});


function initResourceWidget(widget) {
  var $widget = $(widget);
  var isFlow, isCarousel;
  isFlow = $widget.hasClass('resource-flow-layout');
  if (!isFlow) {
    isCarousel = $widget.hasClass('resource-carousel-layout');
  }

  // find size of widget by pulling out its class name
  var sizeCols = 1;
  var m = $widget.get(0).className.match(/\bcol-(\d+)\b/);
  if (m) {
    sizeCols = parseInt(m[1], 10);
  }

  var opts = {
    source: $widget.data('source'),
    cardSizes: ($widget.data('cardsizes') || '').split(','),
    maxResults: parseInt($widget.data('maxresults') || '100'),
    itemsPerPage: $widget.data('itemsperpage'),
    sortOrder: $widget.data('sortorder'),
    query: $widget.data('query'),
    collectionId: $widget.data('collectionid'),
    sizeCols: sizeCols
  };

  // run the search for the set of resources to show
  var resources = buildResourceList(opts);

  if (isFlow) {
    drawResourcesFlowWidget($widget, opts, resources);
  }
}


function drawResourcesFlowWidget($widget, opts, resources) {
  $widget.empty();
  var cardSizes = opts.cardSizes || ['4x3'];

  for (var i = 0; i < resources.length; i++) {
    var resource = resources[i];

    var cardSize = i >= cardSizes.length ? cardSizes[cardSizes.length - 1] : cardSizes[i];
    cardSize = cardSize.replace(/^\s+|\s+$/,'');

    var $card = $('<a>')
        .addClass('resource-card resource-card-' + cardSize + ' resource-card-' + resource.type)
        .attr('href', resource.url);

    $('<img>')
        .addClass('photo')
        .attr('src', resource.image || '')
        .appendTo($card);

    var subtitle = resource.type;
    if (resource.timestamp) {
      var d = new Date(resource.timestamp);
      // TODO: localize, humanize
      subtitle = (1 + d.getMonth()) + '/' + d.getDate() + '/' + d.getFullYear() + ' on ' + subtitle;
    }

    $('<div>')
        .addClass('resource-card-text')
        .append($('<div>').addClass('icon'))
        .append($('<div>').addClass('title').text(resource.title))
        .append($('<div>').addClass('subtitle').text(subtitle))
        .append($('<div>').addClass('abstract').text(resource.summary))
        .appendTo($card);

    $card.appendTo($widget);
  }

  $widget.find('.resource-card .photo').each(function() {
    var src = $(this).attr('src');
    if (!src) {
      $(this).parents('.resource-card').addClass('nophoto');
      $(this).replaceWith($('<div>')
          .addClass('photo'));
    } else {
      $(this).replaceWith($('<div>')
          .addClass('photo')
          .css('background-image', 'url(' + $(this).attr('src') + ')'));
    }
  });
}


function buildResourceList(opts) {
  var maxResults = opts.maxResults || 100;

  switch (opts.source) {
    case 'query':
      var query = opts.query || '';
      var expressions = parseResourceQuery(query);
      var alreadyAddedResources = {};
      var allResources = [];
      for (var i = 0; i < expressions.length; i++) {
        var clauses = expressions[i];

        // build initial set of resources from first clause
        var firstClause = clauses[0];
        var resources = [];
        switch (firstClause.attr) {
          case 'type':
            resources = ALL_RESOURCES_BY_TYPE[firstClause.value];
            break;
          case 'lang':
            resources = ALL_RESOURCES_BY_LANG[firstClause.value];
            break;
          case 'tag':
            resources = ALL_RESOURCES_BY_TAG[firstClause.value];
            break;
        }
        resources = resources || [];

        // use additional clauses to filter corpus
        if (clauses.length > 1) {
          var otherClauses = clauses.slice(1);
          resources = resources.filter(getResourceMatchesClausesFilter(otherClauses));
        }

        // filter out resources already added
        if (i > 1) {
          resources = resources.filter(getResourceNotAlreadyAddedFilter(alreadyAddedResources));
        }

        allResources = allResources.concat(resources);
        if (allResources.length > maxResults) {
          break;
        }
      }
      if (opts.sortOrder) {
        var attr = opts.sortOrder;
        var desc = attr.charAt(0) == '-';
        if (desc) {
          attr = attr.substring(1);
        }
        allResources = allResources.sort(function(x,y) {
          return (desc ? -1 : 1) * (parseInt(x[attr], 10) - parseInt(y[attr], 10));
        });
      }
      return allResources.slice(0, maxResults);

    case 'related':
      // TODO
      break;

    case 'collection':
      // TODO
      break;
  }
}


function getResourceNotAlreadyAddedFilter(addedResources) {
  return function(x) {
    return !!addedResources[x];
  };
}


function getResourceMatchesClausesFilter(clauses) {
  return function(x) {
    return doesResourceMatchClauses(x, clauses);
  };
}


function doesResourceMatchClauses(resource, clauses) {
  for (var i = 0; i < clauses.length; i++) {
    var map;
    switch (clauses[i].attr) {
      case 'type':
        map = IS_RESOURCE_OF_TYPE[clauses[i].value];
        break;
      case 'lang':
        map = IS_RESOURCE_IN_LANG[clauses[i].value];
        break;
      case 'tag':
        map = IS_RESOURCE_TAGGED[clauses[i].value];
        break;
    }

    if (!map || (!!clauses[i].negative ? map[resource.index] : !map[resource.index])) {
      return false;
    }
  }
  return true;
}


function parseResourceQuery(query) {
  // Parse query into array of expressions (expression e.g. 'tag:foo + type:video')
  var expressions = [];
  var expressionStrs = query.split(',') || [];
  for (var i = 0; i < expressionStrs.length; i++) {
    var expr = expressionStrs[i] || '';

    // Break expression into clauses (clause e.g. 'tag:foo')
    var clauses = [];
    var clauseStrs = expr.split(/(?=[\+\-])/);
    for (var j = 0; j < clauseStrs.length; j++) {
      var clauseStr = clauseStrs[j] || '';

      // Get attribute and value from clause (e.g. attribute='tag', value='foo')
      var parts = clauseStr.split(':');
      var clause = {};

      clause.attr = parts[0].replace(/\s+/g,'');
      if (clause.attr) {
        if (clause.attr.charAt(0) == '+') {
          clause.attr = clause.attr.substring(1);
        } else if (clause.attr.charAt(0) == '-') {
          clause.negative = true;
          clause.attr = clause.attr.substring(1);
        }
      }

      if (parts.length > 1) {
        clause.value = parts[1].replace(/\s+/g,'');
      }

      clauses.push(clause);
    }

    if (!clauses.length) {
      continue;
    }

    expressions.push(clauses);
  }

  return expressions;
}

