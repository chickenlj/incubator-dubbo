#!/bin/bash
echo "Promoting release 2.6.3
Actions about to be performed:
------------------------------
$(cat $0 | tail -n +14)
------------------------------------------"
read -p "Press enter to continue or CTRL-C to abort"
# push the release tag to ASF git repo
git push origin dubbo-2.6.3
# promote the source distribution by moving it from the staging area to the release area
svn mv https://dist.apache.org/repos/dist/dev/Dubbo/2.6.3 https://dist.apache.org/repos/dist/release/Dubbo -m "Upload release to the mirrors"
mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.7:rc-release -DstagingRepositoryId= -DnexusUrl=https://repository.apache.org -DserverId=apache.releases.https -Ddescription="Release vote has passed"
# Renumber the next development iteration 2.7.0-SNAPSHOT:
git checkout master
mvn release:update-versions --batch-mode
mvn versions:set versions:commit -DnewVersion=2.7.0-SNAPSHOT
git add --all
echo "
Check the new versions and commit and push them to origin:
  git commit -m "Start next development version"
  git push
Remove the previous version of Dubbo using this command:
  svn rm https://dist.apache.org/repos/dist/release/Dubbo/2.5.0 -m \"Remove previous version from mirrors\"
"
