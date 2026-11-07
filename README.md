# FrameBucket starter projects

This workspace contains a Spring Boot backend and a React frontend for the photographer user and customer modules.

## What is included

- Public home page, photographer registration and login.
- Photographer email verification with a local console-link mode and configurable SMTP delivery.
- Indian mobile numbers accept 10 digits or an optional `+91` prefix and are stored as 10 digits.
- FrameBucket logo and navy/coral brand styling from `frontend/public/framebucket-logo.png`.
- PostgreSQL-backed Bronze, Silver, Gold and Platinum pricing with a seven-day free-trial assignment.
- Pricing and checkout-preview pages with annual discount and GST breakdown.
- Session-based authentication with an HTTP-only session cookie and CSRF protection.
- Photographer workspace with Enquiries, Customers, Events and Data modules.
- Customer registration and photographer-scoped customer list with contact, date-of-birth, delivery-address, event-count and revenue fields.
- PostgreSQL schema migrations and example SQL queries. Photographer account, subscription and verification Java code is grouped under `com.framebucket.user`; shared security configuration stays under `com.framebucket.config`.
- The photographer and subscription schema uses `fb_user`, `fb_user_subscription`, `fb_user_email` and `fb_user_mobile` tables.
- Customer module backend code is grouped under `com.framebucket.customer`; its `fb_customer` rows reference their photographer in `fb_user` and receive a photographer-scoped ID.
- Event module backend code is grouped under `com.framebucket.event`; event bookings and event types are stored in `fb_event_booking` and `fb_event_list`.
- Enquiries use `fb_enquiries` and send customers a secure quotation builder link. Submitted quotations are stored in `fb_quotaion`, including the generated PDF in `quote_pdf`; the customer sees the final estimate while component prices stay server-side.
- Photographers can save a negotiated finalized quote, create an event from an enquiry, reuse or create its customer, and store an updated PDF showing the finalized amount.
- Local Vite proxy from the React development server to Spring Boot.

The Enquiries module supports adding customer requests, assigning event types, and converting quoted enquiries into customer records. Each enquiry ID combines the photographer's decrypted studio sub ID with its creation timestamp. New enquiries produce an email and WhatsApp quotation link. Local email and WhatsApp modes log delivery to the backend console; configure `APP_EMAIL_MODE=smtp` with `MAIL_*`, and `APP_WHATSAPP_MODE=cloud` with `WHATSAPP_MESSAGES_URL`, `WHATSAPP_ACCESS_TOKEN`, and approved templates for delivery. After the customer submits the quote, the photographer receives a WhatsApp notice and can open the stored PDF from the enquiry list. The Customer module supports photographer-scoped registration and listing. Each customer receives an ID in the form `<SUB_ID>_000`, and event IDs use `<SUB_ID>_00000`. `SUB_ID` is generated from the studio name by removing spaces and special characters and converting letters to uppercase. The application stores the sub ID encrypted in `fb_user.sub_id` and decrypts it when creating IDs. Set `APP_SUB_ID_ENCRYPTION_KEY` to a stable secret of at least 32 UTF-8 bytes in deployed environments; changing it without re-encrypting stored values prevents the application from reading existing sub IDs. If no studio name is set, the photographer name is used to generate the sub ID. The Events page creates bookings with customer links, event types, and a calendar that marks and disables dates already booked by that photographer. Wedding, Engagement, and Naming Ceremony are available by default; photographers can add their own event types. The customer portal lists booked events. Customers can choose **Customer** on the sign-in page, enter their registered mobile number, and verify the same one-time code sent to their registered email address(es) and mobile. The local email and mobile senders write the code to the backend console; a real SMS provider adapter must be connected before deployment. Photographer sign-in accepts either email or mobile number with the existing password. Mobile numbers accept 10 digits or an optional `+91` prefix, and are stored as 10 digits. Event bookings update the customer event count; revenue remains unchanged because bookings do not yet collect an amount. The Data tab is a starting point for photo workflows, which are not implemented yet. The payment page is a preview and does not collect payment or activate a paid plan.

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

The backend runs at http://localhost:8080. Flyway applies the SQL migrations automatically on startup, including the customer, event, enquiry and quotation tables, generated customer and event IDs, and normalization of existing `+91` mobile numbers to 10 digits.

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
- GET /api/customers — lists customers registered by the signed-in photographer.
- GET /api/customers/{customerId} — fetches one of the photographer's customer records.
- POST /api/customers — registers a customer for the signed-in photographer.
- GET /api/events — lists the photographer's bookings.
- GET /api/events/next-id — previews the next event ID.
- GET /api/events/blocked-dates — lists dates already booked by the photographer.
- POST /api/events — creates an event booking.
- GET /api/events/types — lists default and photographer-created event types.
- POST /api/events/types — creates a photographer event type.
- POST /api/customer-auth/request-code — requests a customer sign-in code using a registered mobile number.
- POST /api/customer-auth/verify-code — verifies the code and starts a customer session.
- GET /api/customer-auth/me — returns the current customer portal profile.
- GET /api/customer-portal/events — returns the signed-in customer's booked events.

Registration fields: fullName, studioName (optional), email, mobileNumber as 10 digits or with an optional `+91` prefix, password and confirmPassword (both minimum 12 characters). The API stores the normalized 10-digit Indian number, normalizes email addresses to lowercase, compares the password confirmation, and stores only a BCrypt password hash.

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
