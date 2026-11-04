# FrameBucket starter projects

This workspace contains a Spring Boot backend and a React frontend for the photographer user module.

## What is included

- Public home page, photographer registration and login.
- Session-based authentication with an HTTP-only session cookie and CSRF protection.
- Welcome page with Customer module and Data module tabs.
- PostgreSQL schema migrations and example SQL queries. `fb_user` references an optional `fb_subscription` plan.
- Local Vite proxy from the React development server to Spring Boot.

The Customer and Data tabs are starting points only. Their business workflows, photo storage and processing are not implemented in this user-module starter.

## Local requirements

- JDK 17 or newer. JDK 21 is a good default.
- Maven 3.6.3 or newer.
- Node.js 20.19+ or 22.12+ and npm.
- PostgreSQL 15 or newer.

## 1. Create the local database

Start PostgreSQL, then create a database and user. In psql, run:

    CREATE USER framebucket WITH PASSWORD 'framebucket';
    CREATE DATABASE framebucket OWNER framebucket;

The backend defaults to these local credentials. To use different values, set DB_URL, DB_USERNAME and DB_PASSWORD as environment variables.

## 2. Run the backend

Open the backend folder in IntelliJ IDEA as a Maven project, or run from a terminal:

    cd backend
    mvn spring-boot:run

The backend runs at http://localhost:8080. Flyway applies the SQL migrations automatically on startup, including the rename to `fb_user` and creation of `fb_subscription`.

## 3. Run the frontend

Open the frontend folder in Visual Studio Code, or run:

    cd frontend
    npm install
    npm run dev

Open http://localhost:5173. Vite proxies /api requests to Spring Boot at localhost:8080.

To call a different backend URL, copy frontend/.env.example to frontend/.env.local and set VITE_API_BASE_URL.

## API endpoints

- GET /api/auth/csrf — issues the CSRF cookie used for state-changing requests.
- POST /api/auth/register — creates a photographer account.
- POST /api/auth/login — authenticates and creates a server-side session.
- GET /api/auth/me — returns the signed-in photographer profile.
- POST /api/auth/logout — ends the session.

Registration fields: fullName, studioName (optional), email and password (minimum 12 characters). The API normalizes email addresses to lowercase and stores only a BCrypt password hash.

The `fb_user.subscription_id` foreign key is nullable for now because subscription selection is not part of registration yet. Subscription plans can use monthly, yearly or lifetime billing cycles; no sample prices or plans are inserted.

## Importing the projects

- IntelliJ IDEA: open the backend folder or its pom.xml.
- Visual Studio Code: open the frontend folder for the React project, or the workspace root to see both projects.

## Local development security notes

The session cookie is HTTP-only and SameSite=Lax. The local Vite proxy keeps frontend API calls on the same browser origin. For a deployed HTTPS site, set the session cookie Secure flag and replace the local database credentials with secret-managed values.

Customer and Data tabs are placeholders. File uploads, subscriptions, customer tracking, notifications and WhatsApp delivery need their own implementation before production use.
