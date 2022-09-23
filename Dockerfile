# syntax=docker/dockerfile:1
FROM alpine:3
WORKDIR .
COPY . .
RUN apk add --no-cache npm java-jdk bash curl rlwrap
RUN curl https://download.clojure.org/install/linux-install-1.11.1.1165.sh | sh
RUN npm install
ENV IN_DOCKER="TRUE"
ENV PERSIST_PATH="/etc/clojurepod/"
RUN ./build.sh
CMD ["java", "-jar", "uber.jar"]
