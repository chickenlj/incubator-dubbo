#!/bin/bash
echo "Promoting release 2.6.2-SNAPSHOT
Actions about to be performed:
------------------------------
$(cat $0 | tail -n +14)
------------------------------------------"
read -p "Press enter to continue or CTRL-C to abort"
# push the release tag to ASF git repo
git push origin 
# promote the source distribution by moving it from the staging area to the release area
svn mv https://dist.apache.org/repos/dist/dev/wicket/2.6.2-SNAPSHOT https://dist.apache.org/repos/dist/release/wicket -m "Upload release to the mirrors"
mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.7:rc-release -DstagingRepositoryId= -DnexusUrl=https://repository.apache.org -DserverId=apache.releases.https -Ddescription="Release vote has passed"
# Renumber the next development iteration :
git checkout 2.6.2-SNAPSHOT-release
mvn release:update-versions --batch-mode
mvn versions:set versions:commit -DnewVersion=
git add --all
echo "
Check the new versions and commit and push them to origin:
  git commit -m "Start next development version"
  git push
Remove the previous version of Wicket using this command:
  svn rm https://dist.apache.org/repos/dist/release/wicket/ -m \"Remove previous version from mirrors\"
"
