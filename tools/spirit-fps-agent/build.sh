#!/bin/sh
# Rebuilds the two jars of the "spirit-fps" component (Java 8 bytecode, no dependencies):
#   spirit-fps.jar       the -javaagent (agent + class patcher)
#   spirit-fps-boot.jar  only SpiritFpsCounter; the agent appends it to the bootstrap class path
set -e
cd "$(dirname "$0")"
rm -rf out/classes out/boot out/*.jar && mkdir -p out/classes out/boot
javac --release 8 -nowarn -d out/classes src/git/spirit/fps/*.java 2>&1 | grep -v "warning\|Picked" || true
mkdir -p out/boot/git/spirit/fps
mv out/classes/git/spirit/fps/SpiritFpsCounter.class out/boot/git/spirit/fps/
printf 'Premain-Class: git.spirit.fps.SpiritFpsAgent\n' > out/manifest.txt
jar cfm out/spirit-fps.jar out/manifest.txt -C out/classes .
jar cf out/spirit-fps-boot.jar -C out/boot .
echo built out/spirit-fps.jar out/spirit-fps-boot.jar
