FROM openjdk:8-jre

ARG version=unknown
LABEL nl.pdok.application="gml-to-featured"
LABEL nl.pdok.version=$version

COPY artifacts/gml-to-featured-web.jar /opt
COPY run.sh /opt
RUN chmod 0500 /opt/run.sh
WORKDIR /opt
CMD ["./run.sh", "gml-to-featured-web.jar"]
