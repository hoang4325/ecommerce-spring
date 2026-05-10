# E-Commerce Foundation and Eureka Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the repository foundation and the first runnable microservice, `eureka-server`.

**Architecture:** This plan creates a Maven multi-module monorepo with Spring Boot 3.5.14 and Spring Cloud 2025.0.2 dependency management. It then adds a standalone Eureka Server module that can be tested with Maven, packaged as a jar, and run locally or through Docker Compose.

**Tech Stack:** Java 21, Maven, Spring Boot 3.5.14, Spring Cloud 2025.0.2, Spring Cloud Netflix Eureka Server, Spring Boot Actuator, JUnit 5, Docker, Docker Compose.

---

## Scope Check

The approved system spec covers ten services plus infrastructure. This plan intentionally covers only the first executable slice:

- Repository hygiene.
- Root Maven parent project.
- `eureka-server`.
- Dockerfile for `eureka-server`.
- Minimal Docker Compose entry for `eureka-server`.
- README instructions for the first milestone.

Subsequent plans should cover these slices in order:

1. `api-gateway`.
2. `auth-service` with JWT.
3. `product-service`.
4. `inventory-service`.
5. `cart-service` with Redis.
6. `order-service`.
7. `payment-service`.
8. `notification-service`.
9. Kafka event flow.
10. Full Docker Compose wiring and critical integration tests.

## Version Notes

Use these versions for this plan:

- Spring Boot: `3.5.14`
- Spring Cloud: `2025.0.2`

Reasoning:

- Spring Boot `3.5.14` was announced by Spring on 2026-04-23 and is available from Maven Central.
- Spring Cloud documents `2025.0.x` as the release train for Spring Boot `3.5.x`.
- Spring Cloud `2025.0.2` release notes list current `2025.0.x` component versions, including Spring Cloud Netflix `4.3.2`.

## File Structure

Repository root: `D:/spring`

Files created by this plan:

- `D:/spring/.gitattributes`: line-ending policy for consistent Git behavior.
- `D:/spring/.gitignore`: excludes build output, IDE files, logs, and local environment files.
- `D:/spring/pom.xml`: root Maven parent and dependency management.
- `D:/spring/eureka-server/pom.xml`: Eureka module build file.
- `D:/spring/eureka-server/src/main/java/com/example/ecommerce/eurekaserver/EurekaServerApplication.java`: Eureka boot application.
- `D:/spring/eureka-server/src/main/resources/application.yml`: Eureka runtime configuration.
- `D:/spring/eureka-server/src/test/java/com/example/ecommerce/eurekaserver/EurekaServerApplicationTests.java`: Spring context test.
- `D:/spring/eureka-server/Dockerfile`: Docker image for Eureka.
- `D:/spring/docker-compose.yml`: local Compose entry for Eureka.
- `D:/spring/README.md`: commands for the first milestone.

Files modified by this plan:

- `D:/spring/pom.xml`: add `eureka-server` as a module after the module directory exists.

---

## Task 1: Root Repository Hygiene and Maven Parent

**Files:**

- Create: `D:/spring/.gitattributes`
- Create: `D:/spring/.gitignore`
- Create: `D:/spring/pom.xml`

- [ ] **Step 1: Confirm the repo starts without a Maven parent**

Run:

```powershell
Test-Path pom.xml
```

Expected: `False`

- [ ] **Step 2: Create `.gitattributes`**

Write this exact content to `D:/spring/.gitattributes`:

```gitattributes
* text=auto eol=lf
*.bat text eol=crlf
*.cmd text eol=crlf
*.ps1 text eol=crlf
```

- [ ] **Step 3: Create `.gitignore`**

Write this exact content to `D:/spring/.gitignore`:

```gitignore
# Maven
target/

# Java
*.class
*.log
hs_err_pid*
replay_pid*

# IDEs
.idea/
*.iml
.vscode/
.settings/
.classpath
.project

# OS files
.DS_Store
Thumbs.db

# Local environment
.env
.env.*
!.env.example

# Docker and generated artifacts
docker-data/
*.pid
```

- [ ] **Step 4: Create the root Maven parent**

Write this exact content to `D:/spring/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.14</version>
        <relativePath/>
    </parent>

    <groupId>com.example.ecommerce</groupId>
    <artifactId>ecommerce-microservices</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>ecommerce-microservices</name>
    <description>Spring Boot microservices e-commerce system</description>

    <properties>
        <java.version>21</java.version>
        <spring-cloud.version>2025.0.2</spring-cloud.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <configuration>
                        <release>${java.version}</release>
                    </configuration>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

- [ ] **Step 5: Verify the parent Maven project**

Run:

```powershell
mvn -N validate
```

Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit the repository foundation**

Run:

```powershell
git add .gitattributes .gitignore pom.xml
git commit -m "chore: add root maven project"
```

Expected: commit succeeds.

---

## Task 2: Eureka Server Test First

**Files:**

- Create: `D:/spring/eureka-server/pom.xml`
- Create: `D:/spring/eureka-server/src/test/java/com/example/ecommerce/eurekaserver/EurekaServerApplicationTests.java`
- Modify: `D:/spring/pom.xml`

- [ ] **Step 1: Create the Eureka module build file**

Create directory `D:/spring/eureka-server`.

Write this exact content to `D:/spring/eureka-server/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.example.ecommerce</groupId>
        <artifactId>ecommerce-microservices</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>eureka-server</artifactId>
    <name>eureka-server</name>
    <description>Service discovery server for the e-commerce microservices system</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Register `eureka-server` in the root parent**

Replace `D:/spring/pom.xml` with this exact content:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.14</version>
        <relativePath/>
    </parent>

    <groupId>com.example.ecommerce</groupId>
    <artifactId>ecommerce-microservices</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>ecommerce-microservices</name>
    <description>Spring Boot microservices e-commerce system</description>

    <modules>
        <module>eureka-server</module>
    </modules>

    <properties>
        <java.version>21</java.version>
        <spring-cloud.version>2025.0.2</spring-cloud.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <configuration>
                        <release>${java.version}</release>
                    </configuration>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

- [ ] **Step 3: Write the failing Spring context test**

Create directory `D:/spring/eureka-server/src/test/java/com/example/ecommerce/eurekaserver`.

Write this exact content to `D:/spring/eureka-server/src/test/java/com/example/ecommerce/eurekaserver/EurekaServerApplicationTests.java`:

```java
package com.example.ecommerce.eurekaserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = EurekaServerApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false"
    }
)
class EurekaServerApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 4: Run the failing test**

Run:

```powershell
mvn -pl eureka-server -am test
```

Expected: `COMPILATION ERROR` because `EurekaServerApplication` does not exist yet.

- [ ] **Step 5: Keep the failing test as an uncommitted checkpoint**

Run:

```powershell
git status --short
```

Expected: output includes `pom.xml`, `eureka-server/pom.xml`, and `EurekaServerApplicationTests.java`.

---

## Task 3: Eureka Server Application

**Files:**

- Create: `D:/spring/eureka-server/src/main/java/com/example/ecommerce/eurekaserver/EurekaServerApplication.java`
- Create: `D:/spring/eureka-server/src/main/resources/application.yml`
- Test: `D:/spring/eureka-server/src/test/java/com/example/ecommerce/eurekaserver/EurekaServerApplicationTests.java`

- [ ] **Step 1: Create the Spring Boot Eureka application**

Create directory `D:/spring/eureka-server/src/main/java/com/example/ecommerce/eurekaserver`.

Write this exact content to `D:/spring/eureka-server/src/main/java/com/example/ecommerce/eurekaserver/EurekaServerApplication.java`:

```java
package com.example.ecommerce.eurekaserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@EnableEurekaServer
@SpringBootApplication
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

- [ ] **Step 2: Create Eureka runtime configuration**

Create directory `D:/spring/eureka-server/src/main/resources`.

Write this exact content to `D:/spring/eureka-server/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: eureka-server

server:
  port: 8761

eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
  instance:
    hostname: localhost
  server:
    wait-time-in-ms-when-sync-empty: 0

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true
```

- [ ] **Step 3: Run the Eureka tests**

Run:

```powershell
mvn -pl eureka-server -am test
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Package the Eureka jar**

Run:

```powershell
mvn -pl eureka-server -am package
```

Expected: `BUILD SUCCESS` and a jar under `D:/spring/eureka-server/target/`.

- [ ] **Step 5: Run Eureka locally**

Run:

```powershell
mvn -pl eureka-server spring-boot:run
```

Expected:

- Application starts on port `8761`.
- `http://localhost:8761/actuator/health` returns JSON with `"status":"UP"`.
- `http://localhost:8761` opens the Eureka dashboard.

Stop the server with `Ctrl+C`.

- [ ] **Step 6: Commit the working Eureka application and its test**

Run:

```powershell
git add pom.xml eureka-server/pom.xml eureka-server/src/main/java/com/example/ecommerce/eurekaserver/EurekaServerApplication.java eureka-server/src/main/resources/application.yml eureka-server/src/test/java/com/example/ecommerce/eurekaserver/EurekaServerApplicationTests.java
git commit -m "feat: add eureka server application"
```

Expected: commit succeeds.

---

## Task 4: Dockerfile and Docker Compose for Eureka

**Files:**

- Create: `D:/spring/eureka-server/Dockerfile`
- Create: `D:/spring/docker-compose.yml`
- Test: `D:/spring/eureka-server/src/test/java/com/example/ecommerce/eurekaserver/EurekaServerApplicationTests.java`

- [ ] **Step 1: Create the Eureka Dockerfile**

Write this exact content to `D:/spring/eureka-server/Dockerfile`:

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml pom.xml
COPY eureka-server/pom.xml eureka-server/pom.xml
COPY eureka-server/src eureka-server/src

RUN mvn -pl eureka-server -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/eureka-server/target/*.jar app.jar

EXPOSE 8761

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

- [ ] **Step 2: Create Docker Compose with Eureka only**

Write this exact content to `D:/spring/docker-compose.yml`:

```yaml
services:
  eureka-server:
    build:
      context: .
      dockerfile: eureka-server/Dockerfile
    container_name: ecommerce-eureka-server
    ports:
      - "8761:8761"
```

- [ ] **Step 3: Re-run Maven tests before building the image**

Run:

```powershell
mvn -pl eureka-server -am test
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Build the Eureka image through Compose**

Run:

```powershell
docker compose build eureka-server
```

Expected: image build succeeds.

- [ ] **Step 5: Run Eureka through Compose**

Run:

```powershell
docker compose up eureka-server
```

Expected:

- Container starts.
- Logs show Tomcat started on port `8761`.
- `http://localhost:8761/actuator/health` returns `"status":"UP"`.
- `http://localhost:8761` opens the Eureka dashboard.

Stop the service with `Ctrl+C`.

- [ ] **Step 6: Commit container support**

Run:

```powershell
git add eureka-server/Dockerfile docker-compose.yml
git commit -m "chore: containerize eureka server"
```

Expected: commit succeeds.

---

## Task 5: README for the First Milestone

**Files:**

- Create: `D:/spring/README.md`

- [ ] **Step 1: Create README with exact local commands**

Write this exact content to `D:/spring/README.md`:

````markdown
# E-Commerce Microservices

Java Spring Boot microservices e-commerce system.

## Current Milestone

The repository currently contains the Maven parent project and `eureka-server`.

## Stack

- Java 21
- Spring Boot 3.5.14
- Spring Cloud 2025.0.2
- Maven
- Docker Compose
- Eureka Server

## Prerequisites

- Java 21
- Maven 3.9+
- Docker Desktop or Docker Engine with Docker Compose

## Build

```powershell
mvn -pl eureka-server -am clean package
```

## Test

```powershell
mvn -pl eureka-server -am test
```

## Run Eureka Locally

```powershell
mvn -pl eureka-server spring-boot:run
```

Eureka dashboard:

```text
http://localhost:8761
```

Health endpoint:

```text
http://localhost:8761/actuator/health
```

## Run Eureka with Docker Compose

```powershell
docker compose up --build eureka-server
```
````

- [ ] **Step 2: Verify README commands**

Run:

```powershell
mvn -pl eureka-server -am test
```

Expected: `BUILD SUCCESS`

Run:

```powershell
mvn -pl eureka-server -am package
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit README**

Run:

```powershell
git add README.md
git commit -m "docs: add foundation run instructions"
```

Expected: commit succeeds.

---

## Task 6: Review and Handoff

**Files:**

- Inspect: `D:/spring/.gitattributes`
- Inspect: `D:/spring/.gitignore`
- Inspect: `D:/spring/pom.xml`
- Inspect: `D:/spring/eureka-server/pom.xml`
- Inspect: `D:/spring/eureka-server/src/main/java/com/example/ecommerce/eurekaserver/EurekaServerApplication.java`
- Inspect: `D:/spring/eureka-server/src/main/resources/application.yml`
- Inspect: `D:/spring/eureka-server/src/test/java/com/example/ecommerce/eurekaserver/EurekaServerApplicationTests.java`
- Inspect: `D:/spring/eureka-server/Dockerfile`
- Inspect: `D:/spring/docker-compose.yml`
- Inspect: `D:/spring/README.md`

- [ ] **Step 1: Run formatting and diff safety check**

Run:

```powershell
git diff --check
```

Expected: no output and exit code `0`.

- [ ] **Step 2: Run tests**

Run:

```powershell
mvn -pl eureka-server -am test
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Build package**

Run:

```powershell
mvn -pl eureka-server -am package
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Confirm working tree state**

Run:

```powershell
git status --short
```

Expected: no output.

- [ ] **Step 5: Report completion**

In the response to the user, include:

- Files changed.
- Why each file changed.
- Commands run.
- Test results.
- How to run Eureka locally.
- How to run Eureka with Docker Compose.
- Next recommended plan: `api-gateway`.

## Plan Self-Review

Spec coverage in this plan:

- Covers the approved monorepo strategy.
- Covers Java 21 and Maven foundation.
- Covers the first service, `eureka-server`.
- Covers Dockerfile for the first service.
- Starts Docker Compose with the first runnable service.
- Adds README commands for local development.
- Uses TDD for the first Spring Boot service by creating the context test before application code.

Requirements assigned to subsequent plans:

- `api-gateway` routing and JWT validation.
- `auth-service` register, login, JWT, and roles.
- `user-service` profile and address management.
- `product-service` product and category management.
- `inventory-service` stock management and reservation.
- `cart-service` Redis cart.
- `order-service` checkout and order state.
- `payment-service` simulated payment.
- `notification-service` event-based notification logging.
- Kafka event flow.
- Full PostgreSQL, Kafka, Redis, and service wiring in Docker Compose.
- Critical flow tests across checkout, inventory, payment, and order status.
