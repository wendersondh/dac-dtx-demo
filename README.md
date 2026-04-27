# JPA Persistence Project (No ORM Magic)

This project demonstrates how to use JPA with Spring Boot using `EntityManager` directly, avoiding Spring Data JPA repositories.

## Prerequisites
- Java 16 (Environment constraint)
- Gradle 7

## Running the Application
```bash
./gradlew bootRun
```

The application runs on **port 8081**.

## API Endpoints
- `POST /users`: Create a user
- `GET /users`: List all users
- `GET /users/{id}`: Get a user by ID
- `PUT /users/{id}`: Update a user
- `DELETE /users/{id}`: Delete a user

## Configuration
Database configuration is located in `src/main/resources/application.properties`.
Default is H2 in-memory database.
