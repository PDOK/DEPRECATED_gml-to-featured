FROM openjdk:8-jre

ARG version=unknown
LABEL nl.pdok.application="gml-to-featured"
LABEL nl.pdok.version=$version

COPY artifacts/gml-to-featured-cli.jar /opt
COPY run.sh /opt
RUN chmod 0500 /opt/run.sh
WORKDIR /opt
ENTRYPOINT ["./run.sh", "gml-to-featured-cli.jar"]
