FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml ./
COPY src ./src

RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/target/classes /app/classes
COPY --from=build /app/src/main/resources/webroot /app/src/main/resources/webroot

EXPOSE 8080

ENTRYPOINT ["java", "-cp", "/app/classes", "escuelaing.edu.arep.microspringboot.MicrosSpringBoot"]
