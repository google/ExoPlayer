module Jekyll
  class AndroidSdkLinkTag < Liquid::Tag
    def initialize(tag_name, text, tokens)
      super
      @text = text
    end
    def render(context)
      "#{@text} #{Time.now}"
    end
  end
end

Liquid::Template.register_tag('sdk_link', Jekyll::AndroidSdkLinkTag)
