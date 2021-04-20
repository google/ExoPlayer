for FILE in $(git ls-files | grep 'java$' | xargs grep -l  @Deprecated);
do
    BASE=$(basename "$FILE")
    echo "Blaming $BASE" >&2
    git blame -f --date=short "$FILE" |
    awk  -F '[ ]' '
      BEGIN {class="'"${BASE%.*}"'"}
      /@Deprecated/{ c=$1 }
      {if(c){ $2=class; print c,$0} }
      /[{;]/{ if(c){print c,"--"};c=0 }'
done |
  tee /tmp/oldApis |
     awk '{print $1}' |
     xargs git describe --long --contains --match='r2.[0-9]*.[0-9]*' --always |sed 's/[~^].*//' |
     paste -d ' ' - /tmp/oldApis |
  awk  -F '[ ]' '{$2=""; print}' |
  sort -sVk1,1 |
  tee oldApis