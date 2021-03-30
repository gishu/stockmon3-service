FROM clojure as build
RUN mkdir -p /gishu/src/app
WORKDIR /gishu/src/app
COPY project.clj /gishu/src/app/
RUN lein deps
COPY . /gishu/src/app
RUN mv "$(lein uberjar | sed -n 's/^Created \(.*standalone\.jar\)/\1/p')" st3-svc-standalone.jar

#Stage Run
FROM openjdk:11.0.10-jre-slim-buster
LABEL maintainer="gishu@hotmail.com"
RUN mkdir -p /app/resources
WORKDIR /app
COPY --from=build /gishu/src/app/st3-svc-standalone.jar /app
COPY --from=build /gishu/src/app/resources /app/resources
CMD ["java", "-jar", "st3-svc-standalone.jar"]
