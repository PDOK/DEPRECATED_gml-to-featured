#!/bin/bash

set -ev
export VERSION=$(printf $(cat VERSION))

if [ ! -f artifacts/gml-to-featured-$VERSION-standalone.jar ]; then
  $lein with-profile +cli build
  cp target/gml-to-featured-$VERSION-standalone.jar artifacts/
fi


if [ ! -f artifacts/gml-to-featured-$VERSION-web.jar ]; then
  $lein with-profile +web-jar build
  cp target/gml-to-featured-$VERSION-web.jar artifacts/
fi
