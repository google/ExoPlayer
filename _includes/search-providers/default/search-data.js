window.TEXT_SEARCH_DATA={
  {%- for _collection in site.collections -%}
    {%- unless forloop.first -%},{%- endunless -%}
    '{{ _collection.label }}':[
      {%- for _article in _collection.docs -%}
      {%- unless forloop.first -%},{%- endunless -%}
      {'title':'{{ _article.title | url_encode }}',
      {%- include snippets/prepend-baseurl.html path=_article.url -%}
      {%- assign _url = __return -%}
      'url':'{{ _url | url_encode }}'}
      {%- endfor -%}
    ]
  {%- endfor -%}
};