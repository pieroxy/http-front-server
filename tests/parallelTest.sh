#!/bin/bash
set -e

echo BUILDING
mvn package
mv target/nullbird-hfs-core-1.0-SNAPSHOT.jar tests/

echo LAUNCHING DUMMY SERVER
mvn test -Dtest=utils.testTomcat.StandaloneTestTomcat &

echo LAUNCHING HFS
java -jar tests/nullbird-hfs-core-1.0-SNAPSHOT.jar tests/sample.config.json &

sleep 2

echo LAUNCHING TEST
mvn test -Dtest=utils.integration.ParallelImplementationCheck

kill $(jobs -p)
sleep 0.5
echo "DONE"
