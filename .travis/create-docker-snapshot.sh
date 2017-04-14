#!/bin/bash

if [ -z "$TRAVIS_TAG" ]; then

    set -ev
    export VERSION=$(printf $(cat VERSION))

    docker login -u="$DOCKER_USERNAME" -p="$DOCKER_PASSWORD"

    if [ ! -f artifacts/gml-to-featured-$VERSION-standalone.jar ]; then
      $lein with-profile +cli build
      cp target/gml-to-featured-$VERSION-standalone.jar artifacts/
    fi
    cp artifacts/gml-to-featured-$VERSION-standalone.jar artifacts/gml-to-featured-cli.jar

    docker build --build-arg version=$VERSION . -f docker-cli/Dockerfile -t pdok/gml-to-featured-cli:$TRAVIS_COMMIT
    docker push pdok/gml-to-featured-cli:$TRAVIS_COMMIT

    if [ ! -f artifacts/gml-to-featured-$VERSION-web.jar ]; then
      $lein with-profile +web-jar build
      cp target/gml-to-featured-$VERSION-web.jar artifacts/
    fi
    cp artifacts/gml-to-featured-$VERSION-web.jar artifacts/gml-to-featured-web.jar

    docker build --build-arg version=$VERSION . -f docker-web/Dockerfile -t pdok/gml-to-featured-web:$TRAVIS_COMMIT
    docker push pdok/gml-to-featured-web:$TRAVIS_COMMIT

fi