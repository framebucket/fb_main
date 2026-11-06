# FrameBucket starter projects

This workspace contains a Spring Boot backend and a React frontend for the photographer user module.

## What is included

- Public home page, photographer registration and login.
- Photographer email verification with a local console-link mode and configurable SMTP delivery.
- Required E.164 mobile numbers, local console OTP verification, and password confirmation on registration.
- FrameBucket logo and navy/coral brand styling from `frontend/public/framebucket-logo.png`.
- PostgreSQL-backed Bronze, Silver, Gold and Platinum pricing with a seven-day free-trial assignment.
- Pricing and checkout-preview pages with annual discount and GST breakdown.
- Session-based authentication with an HTTP-only session cookie and CSRF protection.
- Welcome page with Customer module and Data module tabs.
- PostgreSQL schema migrations and example SQL queries. Photographer account, subscription and verification Java code is grouped under `com.framebucket.user`; shared security configuration stays under `com.framebucket.config`.
- The photographer and subscription schema uses `fb_user`, `fb_user_subscription`, `fb_user_email` and `fb_user_mobile` tables.
- Local Vite proxy from the React development server to Spring Boot.

The Customer and Data tabs are starting points only. Their business workflows, photo storage and processing are not implemented in this user-module starter. The payment page is a preview and does not collect payment or activate a paid plan.

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

The backend runs at http://localhost:8080. Flyway applies the SQL migrations automatically on startup, including the rename to `fb_user`, subscription catalog, and seeded plans.

## 3. Run the frontend

Open the frontend folder in Visual Studio Code, or run:

    cd frontend
    npm.cmd install
    npm.cmd run dev

To create the production frontend bundle, run `npm.cmd run build`. Vite writes it to `frontend/dist`.

Open http://localhost:5173. Vite proxies /api requests to Spring Boot at localhost:8080.

To call a different backend URL, copy frontend/.env.example to frontend/.env.local and set VITE_API_BASE_URL.

## API endpoints

- GET /api/auth/csrf — issues the CSRF cookie used for state-changing requests.
- GET /api/subscriptions — returns active paid plans and monthly/yearly prices.
- POST /api/auth/register — creates a photographer account.
- POST /api/auth/verify-email — consumes a verification token.
- POST /api/auth/resend-verification — sends a new link for an unverified account.
- POST /api/auth/verify-mobile — verifies a six-digit mobile code.
- POST /api/auth/resend-mobile-code — sends a replacement mobile code when needed.
- POST /api/auth/login — authenticates and creates a server-side session.
- GET /api/auth/me — returns the signed-in photographer profile.
- POST /api/auth/logout — ends the session.

Registration fields: fullName, studioName (optional), email, mobileNumber in E.164 format (for example `+919876543210`), password and confirmPassword (both minimum 12 characters). The API normalizes email addresses to lowercase, compares the password confirmation, and stores only a BCrypt password hash.

New registrations must verify both email and mobile before signing in. Email verification links expire after 24 hours, and only a SHA-256 hash of each token is stored. In local development, the default `APP_EMAIL_MODE=console` writes the email verification link to the backend log; open that link in a browser and select **Confirm my email**. The sign-in page also has a resend link.

The default `APP_SMS_MODE=console` writes the six-digit mobile code to the backend log for local development. Codes expire after 10 minutes and allow up to five attempts. The email verification page asks for this code after confirming the email; it can also be resent there or from sign-in if the link was verified in another session. Set `OTP_HMAC_SECRET` to a long random secret outside local development. A real SMS provider adapter must be configured before sending codes to customers in a deployed environment.

To send real email, configure a reachable SMTP provider before starting the backend:

    APP_EMAIL_MODE=smtp
    APP_FRONTEND_BASE_URL=https://your-frontend-host
    APP_EMAIL_FROM=verified-sender@your-domain
    MAIL_HOST=your-smtp-host
    MAIL_PORT=587
    MAIL_USERNAME=your-smtp-username
    MAIL_PASSWORD=your-smtp-password
    MAIL_SMTP_AUTH=true
    MAIL_SMTP_STARTTLS=true

Set these as environment variables; do not commit SMTP credentials. `APP_FRONTEND_BASE_URL` is used to create verification links. Existing photographer accounts are marked verified by the V3 migration so they can continue to sign in.

New registrations are assigned the `FREE_TRIAL` subscription, and `trial_ends_at` is set seven days after registration. Existing users without an assigned plan are also assigned the trial by the V4 migration. Trial access is not yet automatically expired or converted to a paid plan.

The V4 migration seeds the requested INR prices: Bronze ₹599, Silver ₹799, Gold ₹1,299 and Platinum ₹1,999 per month. Yearly prices are calculated as 12 monthly payments less 10%; the payment preview adds 18% GST to the selected monthly or discounted yearly amount. These displayed totals are previews only. Selecting a paid plan does not change the photographer's `subscription_id` until a payment provider is integrated.

Bronze has `data_module_enabled = FALSE`; the dashboard disables its Data module tab when the photographer's assigned plan does not allow it. Silver, Gold and Platinum allow the Data module and set their RAW storage limits to 50 GB, 100 GB and 500 GB respectively.

## Importing the projects

- IntelliJ IDEA: open the backend folder or its pom.xml.
- Visual Studio Code: open the frontend folder for the React project, or the workspace root to see both projects.

## Local development security notes

The session cookie is HTTP-only and SameSite=Lax. The local Vite proxy keeps frontend API calls on the same browser origin. For a deployed HTTPS site, set the session cookie Secure flag and replace the local database credentials with secret-managed values.

Customer and Data workflows, file processing, customer tracking, notifications, WhatsApp delivery and actual subscription payments need further implementation before production use.
