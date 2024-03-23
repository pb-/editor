FROM clojure:tools-deps-1.11.1.1165 AS frontend-builder

ARG version=UNKNOWN
RUN mkdir /build
WORKDIR /build
COPY frontend/deps.edn /build
RUN clojure -P
COPY frontend /build
RUN sed -i "s/development/${version}/g" /build/resources/public/app-version.js
RUN sed -i "s/development/${version}/g" /build/resources/public/worker.js
RUN make release


# ----------------------------------------------------
FROM clojure:tools-deps-1.11.1.1165 AS backend-builder

RUN mkdir -p /build
WORKDIR /build

# cache deps
COPY backend/deps.edn /build
RUN clojure -P
RUN clojure -P -T:build

COPY backend/build.clj /build
COPY backend/src src/
COPY backend/resources resources/
COPY --from=frontend-builder /build/resources/public /build/resources/public
COPY --from=frontend-builder /build/out/main.js /build/resources/public

RUN clojure -T:build uber


# ----------------------------------------------------
FROM gcr.io/distroless/java17-debian11
WORKDIR /
EXPOSE 8080
VOLUME /data
ENV STORAGE_PATH=/data
COPY --from=backend-builder /build/target/editor.jar /
ENTRYPOINT ["java", "-jar", "editor.jar"]
