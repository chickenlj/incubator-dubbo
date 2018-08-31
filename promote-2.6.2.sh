#!/bin/bash
echo "Promoting release 2.6.2
Actions about to be performed:
------------------------------
$(cat $0 | tail -n +14)
------------------------------------------"
read -p "Press enter to continue or CTRL-C to abort"
# promote the source distribution by moving it from the staging area to the release area
svn mv https://dist.apache.org/repos/dist/dev/incubator/dubbo/2.6.2 https://dist.apache.org/repos/dist/release/incubator/dubbo/ -m "Upload release to the mirrors"
mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.7:rc-release -DstagingRepositoryId= -DnexusUrl=https://repository.apache.org -DserverId=apache.releases.https -Ddescription="Release vote has passed"
# Renumber the next development iteration 2.6.3-SNAPSHOT:
git checkout 2.6.2-release
mvn release:update-versions --batch-mode
mvn versions:set versions:commit -DprocessAllModules=true -DnewVersion=2.6.3-SNAPSHOT
git add --all
git commit -m 'Start the next development version'
echo "
Please check the new versions and merge 2.6.2-release to the base branch.
"
