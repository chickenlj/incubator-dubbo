#!/bin/bash
echo -n "Reverting release 2.6.3
Actions about to be performed:
------------------------------
$(cat $0 | tail -n +14)
------------------------------------------
Press enter to continue or CTRL-C to abort"
read
# clean up local repository
git checkout master
git branch -D 2.6.3-release
git tag -d dubbo-2.6.3
# clean up staging repository
git push staging --delete refs/heads/2.6.3-release
git push staging --delete dubbo-2.6.3
# clean up staging dist area
svn rm https://dist.apache.org/repos/dist/dev/Dubbo/2.6.3 -m "Release vote has failed"
# clean up staging maven repository
mvn org.sonatype.plugins:nexus-staging-maven-plugin:LATEST:rc-drop -DstagingRepositoryId= -DnexusUrl=https://repository.apache.org -DserverId=apache.releases.https -Ddescription="Release vote has failed"
# clean up remaining release files
find . -name "*.releaseBackup" -exec rm {} ;
[ -f release.properties ] && rm release.properties
