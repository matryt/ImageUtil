# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

RUN apt-get update && apt-get install -y --no-install-recommends curl unzip zip \
    && rm -rf /var/lib/apt/lists/*

COPY pom.xml .
COPY src ./src

RUN mvn -B -Pexe clean package

RUN mkdir -p /out /tmp/portable \
    && cp target/ImageUtil.jar /out/ \
    && cp target/ImageUtil.exe /out/ \
    && cp target/ImageUtil.exe /tmp/portable/ \
    && curl -fSL "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse" -o /tmp/win-jre.zip \
    && unzip -q /tmp/win-jre.zip -d /tmp/jre-extract \
    && mv /tmp/jre-extract/* /tmp/portable/jre \
    && cd /tmp/portable && zip -qr /out/ImageUtil-windows-portable.zip .

FROM scratch AS export
COPY --from=builder /out/ /

