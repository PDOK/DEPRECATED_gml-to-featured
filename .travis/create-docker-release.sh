#!/bin/bash

set -ev
export VERSION=$(printf $(cat VERSION))

docker login -u="$DOCKER_USERNAME" -p="$DOCKER_PASSWORD"

cp artifacts/gml-to-featured-$VERSION-standalone.jar artifacts/gml-to-featured-cli.jar
docker build --build-arg version=$VERSION . -f docker-cli/Dockerfile -t pdok/gml-to-featured-cli:$VERSION
docker push pdok/gml-to-featured-cli:$VERSION

cp artifacts/gml-to-featured-$VERSION-web.jar artifacts/gml-to-featured-web.jar
docker build --build-arg version=$VERSION . -f docker-web/Dockerfile -t pdok/gml-to-featured-web:$VERSION
docker push pdok/gml-to-featured-web:$VERSION
