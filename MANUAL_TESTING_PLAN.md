# FrameBucket Manual Testing Plan and Execution Report

**Purpose:** Manual QA handoff for the teammate who will execute these scenarios and use the case IDs as a basis for automation.

**Prepared:** 9 November 2026  
**Application:** FrameBucket React frontend and Spring Boot API, deployed as one Railway web service with PostgreSQL  
**Document status:** Test plan prepared; execution results have not been entered.

> This document is based on the current application code and README. It is not a report of tests already run. Record Pass, Fail, Blocked, or Not Run for each case and attach evidence.

## 1. Application scope

The current implementation includes:

- Public home page, pricing plans, monthly/yearly display, and checkout preview.
- Photographer registration, email and mobile verification, login, session handling, and logout.
- Photographer dashboard overview.
- Customer registration and photographer-scoped customer list.
- Enquiries, secure public quotation links, quotation calculation, PDF generation, finalized quotation, and event creation from an enquiry.
- Event registration, calendar date blocking, event types, team/freelancer assignment, and customer event details.
- Team member/freelancer creation, invitation links, invitation completion, and resend.
- Customer OTP sign-in, booked-events portal, and customer-submitted event location/time details.
- Email and WhatsApp notification integrations controlled by environment variables.

### Explicitly incomplete or outside this test pass

- **Data module:** The tab is plan-gated, but photo upload, RAW/JPEG processing, album selection, and delivery are not implemented. Test only whether the tab is enabled/disabled and displays its coming-soon placeholder. Do not treat an upload workflow as expected functionality.
- **Payments:** Checkout is a price/GST preview. It does not collect money or change the subscription. Test that payment is unavailable; do not attempt a real payment.
- **Trial lifecycle:** Registration assigns a seven-day trial, but automatic trial expiry or conversion to a paid plan is not implemented.
- **Revenue:** Dashboard and customer revenue are calculated from event prices. They do not represent payments received.
- **Inbound WhatsApp/webhook processing:** Not implemented. This plan covers outbound notifications only.

## 2. Test environment and safety

### Recommended environment

Use a dedicated Railway staging environment and a non-production PostgreSQL database if available. Record the deployed URL, deployment/build identifier, and environment below. If only production is available, use controlled QA accounts and records, avoid deleting or modifying real customer records, and coordinate the test window with the app owner.

The React site and API share the same deployed origin. Test using the Railway public application URL in a current desktop browser. Also check a narrow mobile viewport for the responsive cases.

### Notification modes

For registration and customer sign-in OTPs, set **APP_SMS_MODE=console** in the test environment when Meta has not approved the relevant template. After saving the variable, wait for Railway to redeploy. Trigger the OTP request and retrieve the six-digit code from backend Deploy Logs by searching for **Local mobile verification code**. Enter the code normally; this changes delivery, it does not bypass verification.

Codes expire after 10 minutes. The server allows up to five failed attempts for a code. A resend cooldown of 60 seconds applies. Do not paste OTPs, access tokens, SMTP credentials, or full customer personal data into this report.

Email and general WhatsApp notifications have separate modes from mobile OTP delivery:

- For email-link testing, use configured console mode or a controlled SMTP mailbox.
- For WhatsApp Cloud API tests, use a dedicated test WABA/phone number, approved templates belonging to the same WABA as the sender, and opted-in test recipients. The current FrameBucket mobile-verification template is in Meta review, so WhatsApp delivery cases depending on it must be marked **Blocked** until Meta approves it.
- A successful UI/API response does not by itself prove that an external email or WhatsApp message was delivered. Record provider-side delivery evidence separately.

### Suggested test accounts and records

Create two photographer accounts, **Photographer A** and **Photographer B**, with unique email addresses and unique Indian-format mobile numbers controlled by the tester. Use 10 digits or the exact +91 plus 10-digit format. Give A and B different studio names so generated identifiers can be distinguished. Verify both accounts fully before testing protected modules.

For Photographer A, create:

- One customer with a past date of birth, unique email/mobile, and a clearly synthetic address.
- A second customer with different contact data.
- One active team member and one active freelancer.
- One invited pending team profile for invite-link tests.
- One custom event type.
- Several events on distinct dates, including a future date to test date blocking.
- One enquiry using a separate synthetic customer identity.

Use future event dates that are not already booked by the same photographer.

### Execution conventions

- **P0:** Core access, account creation, identity verification, critical workflow, or data isolation.
- **P1:** Important functional path or business rule.
- **P2:** Presentation, boundary, or lower-risk behavior.
- **Pass:** Expected result observed and evidence recorded.
- **Fail:** Actual behavior differs from expected; create a defect with reproduction steps.
- **Blocked:** A dependency prevents execution, such as a pending Meta template or unavailable SMTP provider. State the dependency.
- **Not Run:** Not attempted.

Use a fresh browser profile or clear site cookies between roles. Do not reuse one session when validating photographer/customer role separation. Record screenshots with secrets and personal data redacted.

## 3. Test cases

### A. Deployment, public pages, and plan preview

#### DEP-01 — React site and API are reachable

**Priority:** P0 · **Automation suitability:** High

1. Open the Railway application root URL.
2. Confirm the landing page and branding render without a browser error.
3. Open pricing and wait for plan data.
4. In a new tab, request the public subscriptions endpoint at /api/subscriptions.

**Expected:** The site returns the React app; plans load; the API returns a non-empty list of active paid plans. No 502/503 response, blank page, or uncaught frontend error appears.

#### DEP-02 — Public deep links survive direct navigation and refresh

**Priority:** P1 · **Automation suitability:** High

1. Open a valid quotation URL directly in a new tab.
2. Refresh the page.
3. Repeat with a valid team invitation URL.

**Expected:** The server serves the SPA route and the page loads its API context. Refresh does not produce a server 404. Use test tokens only.

#### PUB-01 — Landing-page navigation

**Priority:** P2 · **Automation suitability:** High

1. Use Pricing and confirm the page scrolls to pricing.
2. Use Log in and confirm the login page opens.
3. Use Create account / Start your workspace and confirm registration opens.
4. Use the FrameBucket brand link to return home.

**Expected:** Each control reaches the intended page/section and browser Back/Forward navigation remains usable.

#### PLAN-01 — Monthly and yearly plans display correctly

**Priority:** P1 · **Automation suitability:** High

1. Open pricing and record each plan name, currency, monthly price, and feature list.
2. Select Yearly.
3. Compare yearly prices and savings against the monthly values.
4. Switch back to Monthly.

**Expected:** Values are displayed in INR. Yearly pricing is 12 monthly payments less 10%, with displayed savings consistent with the calculation. The selected billing period and button state update correctly. Bronze shows Data unavailable; higher plans display their configured feature/storage details.

#### PLAN-02 — Checkout is a preview only

**Priority:** P1 · **Automation suitability:** High

1. Select a plan from pricing.
2. Confirm plan name, billing period, subtotal, GST, and total.
3. Switch the checkout billing period between monthly and yearly.
4. Inspect the payment control and select Back to pricing.
5. Open a checkout URL containing an invalid plan code.

**Expected:** GST is 18% and total equals plan price plus GST. The payment control is disabled and the page says payment is not available yet. No payment is collected and no plan assignment changes. Invalid plan code shows an unavailable-plan message.

### B. Photographer registration, verification, and authentication

#### AUTH-01 — Register a valid photographer account

**Priority:** P0 · **Automation suitability:** High, with controlled email/OTP delivery

1. Open Register.
2. Enter a unique full name, optional studio name, valid email, 10-digit mobile, and matching password/confirmation of at least 12 characters.
3. Submit once.
4. Confirm the UI returns to Login and displays the account-created/verification notice.
5. Check the configured email delivery channel and backend OTP logs.

**Expected:** One account is created; it is not signed in until both email and mobile are verified. A seven-day free trial is assigned. The email verification link is available through configured delivery; in console email mode it is in backend logs. In console SMS mode a six-digit mobile code is logged.

#### AUTH-02 — Registration required fields and validation

**Priority:** P1 · **Automation suitability:** High

1. Submit with each required field blank, one at a time.
2. Try malformed email and mobile values (9 digits, 11 digits, spaces, or a country code other than the supported +91 format).
3. Try a password shorter than 12 characters and a mismatched confirmation.
4. Try full name, email, and password values at/over UI limits.

**Expected:** The browser/server rejects invalid input with an accessible validation message; no account is created. Studio name may be blank. Valid mobile input is exactly 10 digits or +91 followed by 10 digits.

#### AUTH-03 — Duplicate photographer identity is rejected

**Priority:** P1 · **Automation suitability:** High

1. Register a test account successfully.
2. Attempt a second registration using the same email and a different mobile.
3. Attempt another using a different email and the same mobile; use +91 form if the first used 10 digits.

**Expected:** Each duplicate is rejected and no duplicate account is created. Mobile storage/lookup treats 10-digit and +91 forms as the same number.

#### AUTH-04 — Email verification succeeds; invalid/reused links fail

**Priority:** P0 · **Automation suitability:** High

1. Open a newly issued verification link.
2. Select Confirm my email.
3. Enter the correct mobile code to finish activation.
4. Try a deliberately modified token and then reuse the already consumed token.

**Expected:** A valid token verifies account email and advances to mobile verification. The correct OTP activates the mobile. Invalid, expired, or reused email tokens show an error and do not verify the account. Email links expire after 24 hours; expiration testing may be marked Not Run if waiting is impractical.

#### AUTH-05 — Email verification resend

**Priority:** P1 · **Automation suitability:** High

1. Start with an unverified test account.
2. From Login, enter its email and choose Send a new link.
3. Open the newest link and verify the email.
4. Request a resend for an already verified account.

**Expected:** A fresh link is issued for an unverified account. The response remains generic for accounts that do not need verification. Resend does not create another user.

#### AUTH-06 — Mobile OTP in console mode

**Priority:** P0 · **Automation suitability:** High

1. Set the test service’s APP_SMS_MODE to console and wait for redeploy.
2. Register an unverified test account or request a replacement code.
3. Search Deploy Logs for “Local mobile verification code”.
4. Enter the six-digit code on the email-verification page.

**Expected:** Backend logs show the code and destination mobile for test use; entering the code verifies the mobile. OTP remains mandatory; console mode changes delivery only.

#### AUTH-07 — Incorrect, expired, and exhausted OTPs

**Priority:** P1 · **Automation suitability:** Medium (expiry requires time or controlled fixture)

1. Submit a wrong six-digit code.
2. Submit a value with fewer/more than six digits or non-digits.
3. Repeat incorrect codes until the attempt limit is reached.
4. Request a replacement after the cooldown and try the old code.
5. If practical, wait beyond 10 minutes and try an expired code.

**Expected:** Invalid codes do not verify the account. The UI reports invalid/expired code without signing in. After five failed attempts or expiry, the old code cannot be used; a newly issued code can be used after the 60-second resend cooldown.

#### AUTH-08 — Photographer sign-in is blocked until both verifications complete

**Priority:** P0 · **Automation suitability:** High

1. Create an account and leave email unverified; attempt sign-in.
2. Verify email only; attempt sign-in.
3. Verify mobile; sign in again.

**Expected:** Sign-in is denied until both email and mobile are verified. Once both are verified, the user reaches the photographer dashboard.

#### AUTH-09 — Photographer identifiers, bad password, and session

**Priority:** P0 · **Automation suitability:** High

1. Sign in using verified email and password.
2. Sign out.
3. Sign in using the same account’s 10-digit mobile; repeat with +91 mobile.
4. Try an incorrect password.
5. Refresh the dashboard, then sign out and press browser Back.

**Expected:** Valid identifiers authenticate the same user. Incorrect password does not create an authenticated session. Session survives refresh; logout invalidates it and protected pages redirect/deny access. Password is never displayed or echoed.

#### AUTH-10 — Customer and photographer sign-in modes remain separate

**Priority:** P1 · **Automation suitability:** High

1. On Login, switch between Photographer and Customer.
2. Confirm the forms and instructions change.
3. Submit a photographer password while Customer is selected and a customer mobile while Photographer is selected.

**Expected:** Customer mode requests a mobile OTP; photographer mode requests identifier/password. A customer OTP session does not grant photographer dashboard access and vice versa.

### C. Dashboard and customer records

#### DASH-01 — Dashboard overview empty and populated states

**Priority:** P1 · **Automation suitability:** High

1. Sign in to a photographer with no records.
2. Confirm activity and revenue empty states.
3. Create one enquiry, one customer, and one priced event.
4. Reload Overview.

**Expected:** Empty state explains that no new records/revenue exist. Populated monthly activity counts reflect records created in the current month. Revenue chart groups positive event prices by event type; monthly and financial-year totals match event prices. Revenue is presented as event-price data, not payment collections.

#### DASH-02 — Data module plan gate and placeholder

**Priority:** P2 · **Automation suitability:** High

1. Sign in with a test account whose plan has Data disabled (for example, a test Bronze assignment).
2. Confirm Data is disabled.
3. Sign in with a test account whose plan enables Data.
4. Open Data.

**Expected:** Disabled plan cannot open Data. Enabled plan sees the coming-soon placeholder. No upload, album-selection, or delivery action is presented as available. Do not use production SQL to change a plan for this test.

#### CUST-01 — Customer list empty state and valid registration

**Priority:** P1 · **Automation suitability:** High

1. Open Customers → Customer List with an account that has no customers.
2. Open Customer Registration and enter a valid name, email, mobile, past date of birth, and synthetic address.
3. Save and return to Customer List.
4. Refresh the page.

**Expected:** Empty state and Register customer control work. Valid customer appears after save and after refresh. Email is normalized to lowercase; mobile is stored/displayed in 10-digit form. First generated code follows the photographer’s SUB_ID plus a three-digit sequence (for example, SUBID_000).

#### CUST-02 — Customer form validation and duplicate handling

**Priority:** P1 · **Automation suitability:** High

1. Try blank required fields, invalid email/mobile, a future date of birth, and an address over the limit.
2. Save with valid values.
3. Submit the same name/email/mobile combination again; repeat with mobile entered with +91.

**Expected:** Invalid records are rejected. Exact duplicate customer identity for the same photographer is rejected. +91 and 10-digit forms normalize to the same mobile. No extra row or ID is created on rejection.

#### CUST-03 — Customer metrics and detail view

**Priority:** P1 · **Automation suitability:** High

1. Open a customer linked to at least one event.
2. Inspect the Customer List fields for event count and total revenue.
3. Open the customer from an Event row.

**Expected:** Name, email, normalized mobile, date of birth, address, count, revenue, and registration date are consistent. Event count increments after event creation; revenue is the sum of positive prices on that customer’s bookings, not a payment ledger.

#### CUST-04 — Photographer customer-list isolation

**Priority:** P0 · **Automation suitability:** High

1. Create a distinct customer under Photographer A.
2. Sign out and sign in as Photographer B.
3. Inspect B’s Customer List and, if API checks are part of the pass, request A’s customer UUID while authenticated as B.

**Expected:** B cannot list or retrieve A’s customer record. Direct access to another photographer’s customer UUID is denied/not found.

### D. Enquiries and public quotation workflow

#### ENQ-01 — Create and list an enquiry

**Priority:** P0 · **Automation suitability:** High

1. Open Enquiries and confirm the empty state for a new photographer.
2. Select New enquiry and enter valid name, mobile, email, and event type.
3. Submit and record the displayed enquiry ID.
4. Refresh Enquiries.

**Expected:** Enquiry appears at the top of the list and persists. ID begins with the photographer’s generated SUB_ID and includes a timestamp. Event type and contact fields are correct.

#### ENQ-02 — Enquiry validation and custom event type

**Priority:** P1 · **Automation suitability:** High

1. Attempt blank name/email/mobile or invalid email/mobile.
2. In the enquiry form, create a new event type and select it.
3. Save, then create another enquiry and confirm the type is available.
4. Try to create the same event type again with case variation.

**Expected:** Invalid enquiry is not saved. New type is stored and reusable. Duplicate type is rejected and no duplicate option is created.

#### ENQ-03 — Enquiry notification and secure quotation link

**Priority:** P1 · **Automation suitability:** Medium (external delivery needs a test provider)

1. Create an enquiry in the configured notification mode.
2. Check console logs or controlled mailbox and WhatsApp test recipient, as applicable.
3. Open the quotation link.

**Expected:** Notification contains the right customer/studio context and a link for that enquiry. Link opens only that enquiry’s quote builder. In console mode, expected notification details appear in backend logs. In Cloud API mode, verify provider delivery status; do not treat a queued request as delivered.

#### QUO-01 — Public quote link displays the correct context

**Priority:** P0 · **Automation suitability:** High

1. Open the quote link for a new enquiry in a signed-out/private browser.
2. Confirm customer name, mobile, email, main occasion, and studio are displayed.
3. Inspect whether contact identity fields are editable.

**Expected:** Public quote page loads without a photographer session; identity details belong to the correct enquiry and are read-only. Event types and service options are available. Component pricing is not exposed; only the estimate is shown.

#### QUO-02 — Invalid quote token

**Priority:** P1 · **Automation suitability:** High

1. Change one character in a valid quote token.
2. Open the changed URL.

**Expected:** Page reports that the link is unavailable; no customer or quotation details are displayed.

#### QUO-03 — Quote selection and server-calculated totals

**Priority:** P0 · **Automation suitability:** High

1. Select Conventional Photography for one event and wait for the estimate to finish updating.
2. Confirm the estimate is INR 6,650 (₹7,000 less the standard 5% discount).
3. Add a second event/service and observe recalculation.
4. Set album sheets to 1 and observe recalculation; set to 0 again.
5. Select up to 8 events and confirm each event type/service selection is independent.

**Expected:** Estimate changes when selections change and submit stays disabled while pricing is updating. One album sheet adds ₹400 before the standard discount (for the one-sheet-only example, expected final is ₹380). The quote page shows the final estimate, not a per-service price list. Values remain within configured event/sheet limits.

#### QUO-04 — Quote builder has no coupon field

**Priority:** P2 · **Automation suitability:** High

1. Open a valid quote link and review the date and booking section.

**Expected:** No coupon input or Apply code button is shown. The estimate applies only the standard discount.

#### QUO-05 — Submit quote once and open the PDF

**Priority:** P0 · **Automation suitability:** High

1. Complete a quote with an event, service, and valid estimate.
2. Submit once and record quote number and final total.
3. Open the quotation PDF from the confirmation page.
4. Reload the same public quote URL.
5. Try to submit again if the UI allows a replay.

**Expected:** Confirmation shows final estimate and PDF link. PDF opens and contains customer/studio/event selection and final amount. Reload displays already-submitted state. One enquiry has one submitted quotation; repeat submission is rejected/no duplicate is created. The estimate is not described as a confirmed booking.

#### ENQ-04 — Photographer reviews original quote and saves a finalized amount

**Priority:** P1 · **Automation suitability:** High

1. Return to the photographer Enquiries list after quote submission.
2. Open the original quotation PDF.
3. Enter a negotiated final amount and Save.
4. Try a negative, blank, or malformed amount.
5. Try saving before the customer has submitted a quotation.

**Expected:** Original quote opens and retains the customer estimate. A valid non-negative finalized amount saves and persists after refresh. Invalid amount is rejected. Finalized amount cannot be saved before a submitted quote and becomes locked after event creation.

#### ENQ-05 — Create an event from a submitted enquiry

**Priority:** P0 · **Automation suitability:** High

1. Use an enquiry with a submitted quote and saved finalized amount.
2. Choose Create Event.
3. If no matching customer exists, enter required date of birth and address; choose an available date.
4. Complete creation and inspect Enquiries, Events, Customers, and both quotation PDFs.
5. Repeat with an enquiry whose exact name/email/mobile already matches a customer.

**Expected:** Event uses the enquiry event type and finalized amount. A new customer is created only when no matching customer exists; otherwise the matching record is reused. Enquiry shows converted customer/event codes. Event appears once. Finalized PDF reflects the negotiated amount, and customer/event counts update. A retry must not create a duplicate event.

#### ENQ-06 — Enquiry-to-event prerequisites and unavailable dates

**Priority:** P1 · **Automation suitability:** High

1. Attempt to create an event before quote submission.
2. Submit a quote but attempt before saving the finalized amount.
3. Choose an already-booked date for this photographer.

**Expected:** Quote submission and finalized amount are required in order. An already-booked date is disabled or rejected with an explanatory message; no duplicate event/customer is created.

### E. Events and assignment

#### EVT-01 — Event creation requires an existing customer

**Priority:** P1 · **Automation suitability:** High

1. Sign in as a photographer with no customers.
2. Open Events.

**Expected:** Create new event is disabled and the page explains that a customer must be registered first. No empty event can be created.

#### EVT-02 — Create event and reserve its date

**Priority:** P0 · **Automation suitability:** High

1. With a customer available, open Event Registration.
2. Choose an unbooked date, event type, customer, and optional price.
3. Save and record the event code.
4. Refresh Events and reopen the calendar.

**Expected:** Event appears once and persists. Event code follows SUB_ID plus a five-digit sequence, beginning with _00000 for a new account. Customer event count increments. That date is shown as booked/disabled for the same photographer.

#### EVT-03 — Same photographer cannot book the same date twice

**Priority:** P1 · **Automation suitability:** High

1. Create an event for a date.
2. Try to select the same date for a second event under another customer/type.
3. If testing concurrent users, attempt two saves for the same photographer/date from separate sessions.

**Expected:** Calendar prevents selecting an occupied date; server rejects a duplicate if a request bypasses the UI or races. Only one event exists for that photographer/date.

#### EVT-04 — Event type defaults, custom type, and duplicate rejection

**Priority:** P1 · **Automation suitability:** High

1. Confirm Wedding, Engagement, and Naming Ceremony are available.
2. Create a custom event type from the event modal.
3. Save an event using it and refresh.
4. Try to create the same name again with case variation.

**Expected:** Default and custom options appear; custom type persists and can be used. Duplicate names are rejected without creating another entry.

#### EVT-05 — Assign and clear an active team member/freelancer

**Priority:** P1 · **Automation suitability:** High for data; external notification manual

1. Assign an active team member to an event and Save.
2. Refresh; confirm the assignee remains.
3. Reassign to an active freelancer.
4. Clear the assignment by selecting Unassigned and Save.
5. Inspect configured notification logs/provider for initial assignments and changes.

**Expected:** Assignment shows correct name/type and persists. Unassigned state can be saved. Active assignee changes generate the configured customer/assignee notification request. Saving an unchanged assignee does not create duplicate notifications. Pending profiles are not offered as active choices.

#### EVT-06 — Event row links and overview/customer metrics

**Priority:** P2 · **Automation suitability:** High

1. Click an event’s customer name.
2. Open any enquiry-linked finalized quotation link.
3. Compare event date, time, type, customer, assignee, price, and location with the customer portal and overview.

**Expected:** Customer detail and finalized PDF open the correct records. Displayed event data is consistent. Overview revenue uses event price, not payment status.

#### EVT-07 — A photographer cannot use another photographer’s customer/event/type/assignee

**Priority:** P0 · **Automation suitability:** High

1. Create distinct customers, event type, team profile, and event under Photographer A.
2. Sign in as Photographer B.
3. Attempt to list/access A’s records, and, if API testing is part of the pass, submit A’s UUIDs to B’s event/customer/assignee endpoints.

**Expected:** B cannot read or mutate A’s records. B cannot create an event for A’s customer or assign A’s team member. API returns unauthorized/not found/bad request and data remains unchanged.

### F. Team and freelancer management

#### TEAM-01 — Directly create an active team member

**Priority:** P1 · **Automation suitability:** High

1. Open Team → Create team member.
2. Enter name, age 16–100, mobile, member type, at least one supported language, and at least one supported skill.
3. Save and refresh the list.

**Expected:** Profile appears under Team members or Freelancers with Active status and selected details. Data persists.

#### TEAM-02 — Direct-registration validation

**Priority:** P1 · **Automation suitability:** High

1. Try Save team member with missing age, language, skill, or member type.
2. Try age 15, 101, malformed mobile, or unsupported choices.
3. Try a duplicate mobile for an existing team member, first as 10 digits and then with +91.

**Expected:** Invalid profile is rejected. Minimum age 16 and maximum age 100 are accepted; out-of-range values fail. At least one language/skill is required for direct registration. Contact mobile is unique across team members/freelancers for the same photographer after normalization.

#### TEAM-03 — Issue a team/freelancer invitation

**Priority:** P1 · **Automation suitability:** High for app; delivery manual

1. Enter name/mobile/member type and optionally age/languages/skills.
2. Choose Send invite link.
3. Record the displayed link and expiry; use Copy link.
4. Check console/approved WhatsApp delivery as configured.

**Expected:** Profile appears with Invitation pending status. A secure invitation URL is displayed and copyable. It expires after 14 days. Member type and mobile are fixed in the public invitation context. External delivery is verified separately.

#### TEAM-04 — Complete invitation

**Priority:** P0 · **Automation suitability:** High

1. Open the invitation URL signed out.
2. Confirm studio, member type, and mobile are shown as context/read-only values.
3. Enter valid name, age, at least one language, and at least one skill; complete profile.
4. Return to Team list.

**Expected:** Profile transitions from pending to Active. Inviter’s Team/Freelancers list reflects the submitted profile. The invite link cannot be completed again.

#### TEAM-05 — Invalid, consumed, expired, or wrong invitation token

**Priority:** P1 · **Automation suitability:** High for invalid/consumed; medium for expiry

1. Change one character in a token.
2. Reopen a token after completion.
3. If practical in a controlled environment, test after its 14-day expiry.

**Expected:** Invalid, consumed, and expired links show an unavailable/expired message and cannot activate a profile.

#### TEAM-06 — Resend a pending invitation

**Priority:** P1 · **Automation suitability:** High

1. Resend an invite for a pending profile.
2. Open the new link, then try the previous link.
3. Complete the new link and try resend on the now-active profile.

**Expected:** A new link and expiry are issued for pending profiles; prior token becomes unusable. Active profiles cannot be invited again through the pending resend action.

### G. Customer sign-in and portal

#### CUSTAUTH-01 — Request customer sign-in code

**Priority:** P0 · **Automation suitability:** High

1. Select Customer on Login.
2. Enter a registered customer mobile in 10-digit form and request code.
3. Repeat with +91 format.
4. Request code for an unregistered mobile.

**Expected:** Registered mobile receives the same sign-in code at registered email address(es) and configured mobile channel. Unknown number gets a clear not-registered error and no session. +91 and 10-digit forms resolve to the same mobile.

#### CUSTAUTH-02 — Verify customer OTP, resend, and role session

**Priority:** P0 · **Automation suitability:** High

1. Enter a valid customer code.
2. Refresh Customer Portal.
3. Try a wrong/expired code and resend after the 60-second cooldown.
4. Sign out and open Customer Portal directly.

**Expected:** Valid code creates a customer session and opens the portal. Invalid/expired code does not create a session. Resend cooldown is enforced. Sign-out ends the session and direct access returns to Login.

#### PORT-01 — Customer sees booked events and empty state

**Priority:** P1 · **Automation suitability:** High

1. Sign in as a customer with no events.
2. Confirm empty state.
3. Sign in with a customer mobile that has one or more linked booked events.
4. Inspect event, photographer/studio, date, time, status, assignee, and location.

**Expected:** Empty state is clear. Booked-event list shows events connected to the authenticated customer identity; fields match the photographer’s event record. Missing assignee/location display as not assigned/not provided.

#### PORT-02 — Customer adds or updates event location and time

**Priority:** P1 · **Automation suitability:** High

1. Open an event and choose Add location details or Update location.
2. Enter venue, valid HTTPS Google Maps link, landmark, notes, and required event time; save.
3. Reopen the portal and the photographer’s Events list.
4. Repeat with the map URL blank, then with a non-Google or HTTP URL.

**Expected:** Valid details save and persist. Blank map URL is replaced with a Google Maps search link based on venue. Valid Google Maps share links are accepted. Non-Google/HTTP links are rejected with an error. Venue and event time are required; landmark/notes may be blank. Photographer sees saved details.

#### PORT-03 — Customer cannot enter photographer workspace

**Priority:** P0 · **Automation suitability:** High

1. Sign in as a customer.
2. Navigate directly to /dashboard and try photographer-only modules/API.
3. Navigate directly to /customer-portal after signing out.

**Expected:** Customer role is denied photographer-only data. Signed-out user must authenticate before viewing customer events. A customer session is not treated as a photographer session.

### H. Security, tenancy, and session handling

#### SEC-01 — Photographer tenant isolation across modules

**Priority:** P0 · **Automation suitability:** High

1. As A, create an enquiry, customer, event, and team profile.
2. Sign out and sign in as B.
3. Compare B’s lists; if API checks are part of the pass, attempt to access A’s record IDs while authenticated as B.

**Expected:** B’s lists contain only B’s data. A’s IDs cannot be used to read/update records through B’s session. Record response codes and evidence without exposing tokens.

#### SEC-02 — CSRF and protected endpoints

**Priority:** P0 · **Automation suitability:** Medium/high

1. Use the app normally to register or mutate a test record; confirm the SPA obtains CSRF cookie/token and the request succeeds.
2. In an API client, omit the CSRF header/cookie for a state-changing request.
3. Call a photographer-protected endpoint with no session, then with a customer session.

**Expected:** Normal app request succeeds. Missing CSRF token is rejected. Protected API returns unauthorized for no session and forbidden/unauthorized for the wrong role. GET endpoints remain read-only.

#### SEC-03 — Public quote and invitation tokens are narrowly scoped

**Priority:** P1 · **Automation suitability:** High

1. Use a valid quote token and inspect its customer context.
2. Modify the token and retry.
3. Use a valid invitation token and complete it; replay it.

**Expected:** Quote token exposes only its associated quote workflow; modified token returns unavailable. Invitation token allows one completion only and cannot be repurposed to another profile. Do not publish tokens in defect screenshots.

#### SEC-04 — Same customer mobile under multiple studios (privacy decision)

**Priority:** P0 release review · **Automation suitability:** High

1. In the test environment, register the same controlled customer mobile under Photographer A and Photographer B with different event data.
2. Request customer sign-in using that mobile.
3. Inspect which profiles/events appear in the customer portal.

**Expected for execution:** Record observed behavior and raise a product/security decision with the owner. Current implementation authenticates by mobile and builds the customer session from all customer rows matching that mobile, so it may show events from multiple photographers. Do not mark the privacy requirement as passed until the intended shared-mobile behavior is explicitly confirmed or corrected.

### I. Notifications and runtime behavior

#### INT-01 — Email delivery mode

**Priority:** P1 · **Automation suitability:** Provider integration requires a test mailbox

1. In console mode, register a test user and locate the verification link in backend logs.
2. In controlled staging SMTP mode, repeat and inspect mailbox, link target, sender, and link validity.
3. Resend a link and try the previous link.

**Expected:** Console mode logs a usable link. SMTP mode delivers to the controlled mailbox with the correct front-end base URL. Resend issues a fresh active token. Do not use personal customer inboxes.

#### INT-02 — Mobile OTP console-mode logging

**Priority:** P0 · **Automation suitability:** High

1. Use APP_SMS_MODE=console.
2. Trigger photographer mobile verification and customer sign-in OTP.
3. Search backend logs for the corresponding destination and code.
4. Verify the code in the UI and then restore the intended mode.

**Expected:** Test codes can be obtained from private backend logs, and normal verification remains required. Treat OTP log lines as sensitive; do not retain them in screenshots or the report.

#### INT-03 — WhatsApp mobile-verification template

**Priority:** P1 · **Automation suitability:** External Meta approval required

1. Mark Blocked until the FrameBucket WABA’s mobile-verification template is Approved/Active.
2. After approval, confirm Railway uses the FrameBucket sender’s phone-number ID, exact template name, and exact approved language code.
3. Register a test user whose number opted in to receive the OTP.
4. Confirm delivery and verify the received code in the app.

**Expected:** The intended FrameBucket sender sends an authentication template with the OTP parameter and copy-code button. The code matches the one verified by the backend. Test WABA templates/test-number assets are not a substitute for the production WABA template.

#### INT-04 — Enquiry/team/quotation/event WhatsApp notices

**Priority:** P1 · **Automation suitability:** External Meta approval and test recipient required

1. Verify each relevant FrameBucket template is Approved/Active and its language/placeholders match application configuration.
2. Trigger separately: enquiry quote-link notice; quotation submitted notice; team invite; event assignment to customer; event assignment to assignee.
3. Inspect destination, placeholder order, URL/PDF link, and delivery status.

**Expected:** Each stage uses the configured template for that notice and contains correct recipient and variable values. If any template is pending/rejected, mark only that delivery case Blocked and include the Meta status; continue the rest of the app tests.

#### OPS-01 — PostgreSQL persistence across service restart

**Priority:** P1 · **Automation suitability:** High

1. Create a controlled customer/event/enquiry and note generated codes.
2. Restart/redeploy the staging web service without replacing the database/volume.
3. Sign in and reload the records.

**Expected:** PostgreSQL-backed records persist. No duplicate seed plans/event types or failed Flyway migration appears on restart. Do not test by deleting or replacing the production database.

#### UX-01 — Responsive layout, errors, and browser behavior

**Priority:** P2 · **Automation suitability:** Partial

1. Exercise Home, Login, Dashboard, a long table, Quote Builder, and Customer Portal at desktop and 375px-wide viewport.
2. Navigate forms with keyboard only; inspect focus visibility, required validation, and error/status announcements.
3. Simulate a temporarily unavailable API in staging or browser network tools; refresh after service recovers.

**Expected:** No essential control is clipped or unreachable. Tables can be viewed/scroll horizontally where needed. Labels and buttons remain usable; errors are visible and associated with the failed action. Loading/error states do not leave forms permanently stuck after recovery.

## 4. Known risks and expected limitations to track

1. **Customer identity across studios:** Customer login is keyed by mobile number. Current code gathers all customer records with that mobile into the session. The owner must decide whether one customer should see bookings across multiple studios sharing a phone number. Track SEC-04 as a release decision.
2. **External service approvals:** WhatsApp delivery depends on template approval for the exact sending WABA, not merely a Meta test number. SMTP reachability and sender authentication are external dependencies.
3. **Data module:** UI placeholder only; no photo upload workflow to accept.
4. **Payments/trial:** Checkout does not charge or assign paid plans; trial expiry is not enforced.
5. **Revenue terminology:** Dashboard revenue derives from entered event prices and is not reconciled with payments.
6. **Enquiry quote:** Customer quote submission is an estimate, not a confirmed booking. Event creation is a separate photographer action.

## 5. Manual execution summary worksheet

Fill this section after execution. Do not mark this document as passed until the tester has run the cases.

| Run field | Value |
|---|---|
| Environment / Railway URL | |
| Deployment or commit identifier | |
| Test date and timezone | |
| Tester | |
| Browser and version | |
| Database/environment reset or seed notes | |
| APP_EMAIL_MODE | |
| APP_SMS_MODE | |
| APP_WHATSAPP_MODE | |
| WhatsApp WABA/template approval state | |
| Total cases executed | |
| Passed / failed / blocked / not run | |
| Overall recommendation | |

| Case ID | Status | Actual result / notes | Evidence filename or link | Defect ID |
|---|---|---|---|---|
| | | | | |
| | | | | |
| | | | | |
| | | | | |

### Defect report template

- **Defect ID / title:**
- **Severity:** Critical / High / Medium / Low
- **Related test case ID:**
- **Environment/build/browser:**
- **Preconditions/test account role:**
- **Steps to reproduce:**
- **Expected result:**
- **Actual result:**
- **Reproducibility:** Always / Intermittent / Once
- **Evidence:** Redacted screenshot, request ID, timestamp, and relevant log excerpt
- **Data cleanup required:**

## 6. Automation handoff notes

- Keep case IDs stable when converting scenarios into automation.
- Prefer role/label/text-based locators over styling classes. The React app exposes labels, button names, roles, and accessible names for primary controls.
- Use separate photographer A/B and customer sessions; clear cookies between roles.
- Generate unique emails/mobile numbers per run. Add teardown or use a disposable test database so duplicate checks do not make reruns flaky.
- For OTP automation, use a test-only console mode or controlled test adapter/log collector. Do not automate by reading live WhatsApp customer messages or storing real OTPs in test artifacts.
- For external SMTP/Meta cases, keep app logic tests deterministic and make provider delivery a separate integration suite. Use approved templates and allowlisted, opted-in recipients only.
- Avoid automating pending/approval workflows against production Meta assets. Record provider status and mark blocked until dependencies are ready.
- Assert generated IDs by pattern and ownership, not by hard-coding sequence values except in a freshly reset disposable database.
- Include a test proving Photographer B cannot access Photographer A records and a separate customer shared-mobile test for the SEC-04 product decision.

## 7. Suggested exit criteria

- All P0 cases pass, or each exception has an explicitly accepted owner decision.
- No unresolved critical/high defect remains in registration/login, customer/event workflows, or photographer data isolation.
- SEC-04 has a documented intended behavior and owner sign-off.
- P1 failures are fixed or accepted with a clear workaround and issue reference.
- External notification cases are either verified with approved staging assets or explicitly reported as blocked; they are not silently counted as passed.
- Data module and payment preview limitations remain clear to the tester and are not treated as failed implemented functionality.
