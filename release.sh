#!/bin/bash
#  Licensed to the Apache Software Foundation (ASF) under one or more
#  contributor license agreements.  See the NOTICE file distributed with
#  this work for additional information regarding copyright ownership.
#  The ASF licenses this file to You under the Apache License, Version 2.0
#  (the "License"); you may not use this file except in compliance with
#  the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

# set -e
# set -x

function fail {
    >&2 echo "\033[31m
FATAL ERROR
-----------
$1
\033[0m"
    exit 1
}

function getJavaVersionFromPom {
    cat << EOF | xmllint --noent --shell pom.xml | grep content | cut -f2 -d=
setns pom=http://maven.apache.org/POM/4.0.0
xpath /pom:project/pom:properties/pom:java_source_version/text()
EOF
}

function getProjectVersionFromPom {
    cat << EOF | xmllint --noent --shell pom.xml | grep content | cut -f2 -d=
setns pom=http://maven.apache.org/POM/4.0.0
xpath /pom:project/pom:version/text()
EOF
}

function generate_promotion_script {
    echo "Generating release promotion script 'promote-$version.sh'"
read -d '' script <<- EOF
#!/bin/bash
echo "Promoting release $version
Actions about to be performed:
------------------------------
\$(cat \$0 | tail -n +14)
------------------------------------------"
read -p "Press enter to continue or CTRL-C to abort"
# push the release tag to ASF git repo
git push origin $tag
# promote the source distribution by moving it from the staging area to the release area
svn mv https://dist.apache.org/repos/dist/dev/wicket/$version https://dist.apache.org/repos/dist/release/wicket -m "Upload release to the mirrors"
mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.7:rc-release -DstagingRepositoryId=$stagingrepoid -DnexusUrl=https://repository.apache.org -DserverId=apache.releases.https -Ddescription="Release vote has passed"
# Renumber the next development iteration $next_version:
git checkout $GIT_BRANCH
mvn release:update-versions --batch-mode
mvn versions:set versions:commit -DnewVersion=$next_version
git add --all
echo "
Check the new versions and commit and push them to origin:
  git commit -m \"Start next development version\"
  git push
Remove the previous version of Wicket using this command:
  svn rm https://dist.apache.org/repos/dist/release/wicket/$previous_version -m \\\"Remove previous version from mirrors\\\"
"
EOF

echo "$script" > promote-$version.sh
    chmod +x promote-$version.sh
    git add promote-$version.sh
}

function generate_rollback_script {
	echo "Generating release rollback script 'revert-$version.sh'"
read -d '' script <<- EOF
#!/bin/bash
echo -n "Reverting release $version
Actions about to be performed:
------------------------------
\$(cat \$0 | tail -n +14)
------------------------------------------
Press enter to continue or CTRL-C to abort"
read
# clean up local repository
git checkout $GIT_BRANCH
git branch -D $branch
git tag -d $tag
# clean up staging repository
git push staging --delete refs/heads/$branch
git push staging --delete $tag
# clean up staging dist area
svn rm https://dist.apache.org/repos/dist/dev/wicket/$version -m "Release vote has failed"
# clean up staging maven repository
mvn org.sonatype.plugins:nexus-staging-maven-plugin:LATEST:rc-drop -DstagingRepositoryId=$stagingrepoid -DnexusUrl=https://repository.apache.org -DserverId=apache.releases.https -Ddescription="Release vote has failed"
# clean up remaining release files
find . -name "*.releaseBackup" -exec rm {} \\;
[ -f release.properties ] && rm release.properties
EOF
echo "$script" > revert-$version.sh

	chmod +x revert-$version.sh
	git add revert-$version.sh
}


echo "Cleaning up any release artifacts that might linger"
mvn -q release:clean

JAVA_VERSION=$(getJavaVersionFromPom)
POM_VERSION=$(getProjectVersionFromPom)

echo $JAVA_VERSION
echo $POM_VERSION

# the branch on which the code base lives for this version
read -p 'Input the branch on which the code base lives for this version: ' GIT_BRANCH
echo $GIT_BRANCH

version=$(getProjectVersionFromPom)
while [[ ! $version =~ ^[0-9]+\.[0-9]+\.[0-9]+(-M[0-9]+)?$ ]]
do
    read -p "Version to release (in pom now is $version): " -e t1
    if [ -n "$t1" ]; then
      version="$t1"
    fi
done

tag=dubbo-$version
branch=$version-release

echo $(generate_promotion_script)

major_version=$(expr $version : '\(.*\)\..*\..*')
minor_version=$(expr $version : '.*\.\(.*\)\..*')
bugfix_version=$(expr $version : '.*\..*\.\(.*\)')

next_version="$major_version.$(expr $minor_version + 1).0-SNAPSHOT"
previous_minor_version=$(expr $minor_version - 1)
if [ $previous_minor_version -lt 0 ] ; then
    previous_version="$major_version.0.0-SNAPSHOT"
else
    previous_version="$major_version.$(expr $minor_version - 1).0"
fi

echo $previous_version

log=$(pwd)/release.out

if [ -f $log ] ; then
    rm $log
fi

if [ ! $( git config --get remote.staging.url ) ] ; then
    fail "
No staging remote git repository found. The staging repository is used to temporarily
publish the build artifacts during the voting process. Since no staging repository is
available at Apache, it is best to use a git mirror on your personal github account.
First fork the github Apache Wicket mirror (https://github.com/apache/wicket) and then
add the remote staging repository with the following command:
    $ git remote add staging git@github.com:<your github username>/wicket.git
    $ git fetch staging
    $ git push staging
This will bring the staging area in sync with the origin and the release script can
push the build branch and the tag to the staging area.
"
fi

echo "Removing previous release tag $tag (if exists)"
oldtag=`git tag -l |grep -e "$tag"|wc -l` >> release.out
[ "$oldtag" -ne 0 ] && git tag -d $tag >> release.out

echo "Removing previous build branch $branch (if exists)"
oldbranch=`git branch |grep -e "$branch"|wc -l` >> release.out
[ "$oldbranch" -ne 0 ] && git branch -D $branch >> release.out

echo "Removing previous staging branch (if exists)"
git push staging :$branch >> release.out
git push staging :refs/tags/$tag >> release.out

echo "Creating release branch"
git checkout -b $branch $GIT_BRANCH >> release.out

# Change version from SNAPSHOT to release ready
#mvn release:update-versions --batch-mode
# Add tag
mvn release:prepare -Darguments="-DskipTests" -DautoVersionSubmodules=true -Dusername=chickenlj -DupdateWorkingCopyVersions=false -DpushChanges=false -DdryRun=true
if [ $? -ne 0 ] ; then
    fail "ERROR: mvn release:prepare was not successful"
fi
mvn -Prelease release:perform  -Darguments="-DskipTests -Dmaven.deploy.skip=true" -DautoVersionSubmodules=true -Dusername=chickenlj -DdryRun=true
if [ $? -ne 0 ] ; then
    fail "ERROR: mvn release:perform was not successful"
fi

cd ./distribution
mvn clean install -Prelease
shasum -a 512 apache-dubbo-incubating-${version}-source-release.zip >> apache-dubbo-incubating-${version}-source-release.zip.sha512
shasum -a 512 apache-dubbo-incubating-${version}-bin-release.zip >> apache-dubbo-incubating-${version}-bin-release.zip.sha512

generate_promotion_script
generate_rollback_script

cd ./target
svn mkdir https://dist.apache.org/repos/dist/dev/incubator/dubbo/$version-test -m "Create $version release staging area"
svn co --force --depth=empty https://dist.apache.org/repos/dist/dev/incubator/dubbo/$version .
svn add *
#svn commit -m "Upload dubbo-$version to staging area"


generate_release_vote_email


echo "
The release has been created. It is up to you to check if the release is up
to par, and perform the following commands yourself when you start the vote
to enable future development during the vote and after.
A vote email has been generated in release-vote.txt, you can copy/paste it using:
    cat release-vote.txt | pbcopy
You can find the distribution in target/dist.
Failed release
--------------
To rollback a release due to a failed vote or some other complication use:
    $ ./revert-$version.sh
This will clean up the artfifacts from the staging areas, including Nexus,
dist.apache.org and the release branch and tag.
Successful release
------------------
Congratulations on the successful release vote!
Use the release-announce.txt as a starter for the release announcement:
    cat release-announce.txt | pbcopy
A Markdown file called wicket-$version-released.md has been also generated.
You can use it to update the site with the release announcement.
To promote the release after a successful vote, run:
    $ ./promote-$version.sh
This will promote the Nexus staging repository to Maven Central, and move
the release artifacts from the staging area to dist.apache.org. It will
also sign the release tag and push the release branch to git.apache.org
You can read this message at a later time using:
    $ cat release.txt
Happy voting!
" > release.txt

git add release.txt

echo "Adding post-release scripts and vote/release email templates to build branch"
git commit -m "Added post-release scripts and vote/release email templates"

echo "Signing the release tag"
git checkout $tag
git tag --sign --force --message \"Signed release tag for Apache Wicket $version\" $tag >> $log
git checkout $branch

echo "Pushing build artifacts to the staging repository"
git push staging $branch:refs/heads/$branch

echo "Pushing release tag to the staging repository"
git push staging $tag

# 创建新分支并checkout
# 检查tag是否存在，如果存在都删除？
# 调用release:prepare dryRun
# 调用 release:clean
# 调用 release:prepare
# 调用 release:clean恢复
# 到distribution目录下执行mvn clean install
# 拷贝target目录下zip包到svn目录
# 生成 sha512
# svn commit
# nexus staging 插件添加，完成自动发布

# 检查tag是否存在
# 检查tag是否是SNAPSHOT版本
## 检查tag的commit是否和
# 检查签名是否正确
# 检查source中没有jar
# 检查source中没有空目录
# 输出source和tag目录内容的对比表格
# 检查LICENSE NOTICE DISCLAIMER存在