prop() {
  grep "^[[:space:]]*${1}" gradle.properties | cut -d'=' -f2 | sed 's/^[[:space:]]*//; s/\r//'
}

project_id="mili"
project_id_b="Mili"

commitid=$(git log --pretty='%h' -1)
mcversion=$(prop mcVersion)
grdversion=$(prop version)
release=$(prop release)
release_tag="$mcversion-$commitid"
jarName="$project_id-$mcversion-paperclip.jar"
jarName_dir="mili-server/build/libs/$jarName"

flag_release=false
pre=false
make_latest=false

if [ "$release" = "1" ]; then
  pre=true
  flag_release=true
  make_latest=true
elif [ "$release" = "2" ]; then
  flag_release=true
  make_latest=true
fi

if [ "$flag_release" = "false" ] && [[ "$GITHUB_REF_NAME" == ver/* ]]; then
  flag_release=true
  make_latest=true
fi

mv mili-server/build/libs/*-paperclip-$grdversion-mojmap.jar $jarName_dir

echo "project_id=$project_id" >> $GITHUB_ENV
echo "project_id_b=$project_id_b" >> $GITHUB_ENV
echo "commit_id=$commitid" >> $GITHUB_ENV
echo "commit_msg=$(git log --pretty='> [%h] %s' -1)" >> $GITHUB_ENV
echo "mcversion=$mcversion" >> $GITHUB_ENV
echo "pre=$pre" >> $GITHUB_ENV
echo "tag=$release_tag" >> $GITHUB_ENV
echo "jar=$jarName" >> $GITHUB_ENV
echo "jar_dir=$jarName_dir" >> $GITHUB_ENV
echo "flag_release=$flag_release" >> $GITHUB_ENV
echo "make_latest=$make_latest" >> $GITHUB_ENV