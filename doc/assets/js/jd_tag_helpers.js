function mergeArrays() {
  var arr = arguments[0] || [];
  for (var i = 1; i < arguments.length; i++) {
    arr = arr.concat(arguments[i]);
  }
  return arr;
}

var ALL_RESOURCES = mergeArrays(
  DESIGN_RESOURCES,
  DISTRIBUTE_RESOURCES,
  GOOGLE_RESOURCES,
  GUIDE_RESOURCES,
  SAMPLES_RESOURCES,
  TOOLS_RESOURCES,
  TRAINING_RESOURCES,
  YOUTUBE_RESOURCES,
  BLOGGER_RESOURCES
);

for (var i = 0; i < ALL_RESOURCES.length; i++) {
  ALL_RESOURCES[i].index = i;
}

function mergeMaps() {
  var allRes = {};
  var offset = 0;

  for (var i = 0; i < arguments.length; i++) {
    var r = arguments[i];
    for (var tag in r.map) {
      allRes[tag] = allRes[tag] || [];
      allRes[tag] = allRes[tag].concat(r.map[tag].map(function(i){ return ALL_RESOURCES[i + offset]; }));
    }
    offset += r.arr.length;
  }

  return allRes;
}

function setFromArray(arr) {
  arr = arr || [];
  var set = {};
  for (var i = 0; i < arr.length; i++) {
    set[arr[i]] = true;
  }
  return set;
}

function buildResourceLookupMap(resourceDict) {
  var map = {};
  for (var key in resourceDict) {
    var dictForKey = {};
    var srcArr = resourceDict[key];
    for (var i = 0; i < srcArr.length; i++) {
      dictForKey[srcArr[i].index] = true;
    }
    map[key] = dictForKey;
  }
  return map;
}

// Type lookups

var ALL_RESOURCES_BY_TYPE = {
  'design': DESIGN_RESOURCES,
  'distribute': DISTRIBUTE_RESOURCES,
  'google': GOOGLE_RESOURCES,
  'guide': GUIDE_RESOURCES,
  'samples': SAMPLES_RESOURCES,
  'tools': TOOLS_RESOURCES,
  'training': TRAINING_RESOURCES,
  'youtube': YOUTUBE_RESOURCES,
  'blog': BLOGGER_RESOURCES
};
var IS_RESOURCE_OF_TYPE = buildResourceLookupMap(ALL_RESOURCES_BY_TYPE);

// Tag lookups

var ALL_RESOURCES_BY_TAG = mergeMaps(
  {map:DESIGN_BY_TAG,arr:DESIGN_RESOURCES},
  {map:DISTRIBUTE_BY_TAG,arr:DISTRIBUTE_RESOURCES},
  {map:GOOGLE_BY_TAG,arr:GOOGLE_RESOURCES},
  {map:GUIDE_BY_TAG,arr:GUIDE_RESOURCES},
  {map:SAMPLES_BY_TAG,arr:SAMPLES_RESOURCES},
  {map:TOOLS_BY_TAG,arr:TOOLS_RESOURCES},
  {map:TRAINING_BY_TAG,arr:TRAINING_RESOURCES},
  {map:YOUTUBE_BY_TAG,arr:YOUTUBE_RESOURCES},
  {map:BLOGGER_BY_TAG,arr:BLOGGER_RESOURCES}
);
var IS_RESOURCE_TAGGED = buildResourceLookupMap(ALL_RESOURCES_BY_TAG);

// Language lookups

var ALL_RESOURCES_BY_LANG = {};
for (var i = 0; i < ALL_RESOURCES.length; i++) {
  var res = ALL_RESOURCES[i];
  var lang = res.lang;
  if (!lang) {
    continue;
  }

  ALL_RESOURCES_BY_LANG[lang] = ALL_RESOURCES_BY_LANG[lang] || [];
  ALL_RESOURCES_BY_LANG[lang].push(res);
}
var IS_RESOURCE_IN_LANG = buildResourceLookupMap(ALL_RESOURCES_BY_LANG);