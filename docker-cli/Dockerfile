FROM openjdk:8-jre

ARG version=unknown
LABEL nl.pdok.application="gml-to-featured"
LABEL nl.pdok.version=$version

COPY artifacts/gml-to-featured-cli.jar /opt
WORKDIR /opt
ENTRYPOINT ["java", "-jar", "gml-to-featured-cli.jar"]