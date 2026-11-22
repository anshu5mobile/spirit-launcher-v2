#!/bin/sh
# Builds assets/components/spirit-nametag/spirit-nametag.jar (the stubs are only used to compile and are not packaged).
set -e
cd "$(dirname "$0")"
rm -rf out && mkdir -p out/classes
javac --release 17 -nowarn -d out/classes $(find stubs src -name '*.java') 2>&1 | grep -v "warning\|Picked" || true
# package only our own classes + resources
mkdir -p out/jar && cp -r out/classes/git out/jar/ && cp res/* out/jar/
jar cf out/spirit-nametag.jar -C out/jar .
echo built out/spirit-nametag.jar
