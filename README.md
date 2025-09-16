# Trading Bot Application

A full-stack trading bot application with Spring Boot backend and React frontend.

## Prerequisites

- Docker
- Docker Compose

## Quick Start

1. Clone this repository
2. Run the application: `docker-compose up --build`
3. Access the applications:
   - Frontend: http://localhost:3000
   - Backend API: http://localhost:8080
   - phpMyAdmin: http://localhost:8081 (username: root, password: 123)
   - MySQL: localhost:3306

## Project Structure

- `backend/`: Spring Boot application
- `frontend/`: React application
- `docker-compose.yml`: Docker configuration
- `init.sql`: Database initialization script

## Development

### Backend Development
```bash
cd backend
mvn spring-boot:run