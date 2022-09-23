# syntax=docker/dockerfile:1
FROM alpine:3

WORKDIR .
COPY . .
RUN apk add --no-cache clojure
ENV IN_DOCKER="TRUE"
ENV PERSIST_PATH="/etc/clojurepod/"
RUN ./build.sh
CMD ["java", "-jar", "uber.jar"]
