# WhatsApp Cloud API setup

The backend sends these WhatsApp notifications through the same Cloud API phone number and access token. Create each template in the WhatsApp Business Account (WABA) attached to the phone number in `WHATSAPP_MESSAGES_URL`. Template name and language must exactly match the values in Meta.

Text notification templates are sent as **body-only positional templates**. Put their listed placeholders in the body, in the exact order shown. The mobile OTP sender separately supplies the code in the body and URL button index 0. Matched-photo delivery is the exception: it uses the approved image header and three body variables described below.

## Templates and parameters

| Flow | Meta template name (default) | Category | Body parameters in order |
| --- | --- | --- | --- |
| Customer receives a link to build a quotation when an enquiry is created | `framebucket_enquiry_quote_link` | Utility | `{{1}}` customer name; `{{2}}` studio name; `{{3}}` quotation builder URL |
| Photographer receives the generated PDF after the customer submits a quotation | `framebucket_quote_submitted` | Utility | `{{1}}` photographer name; `{{2}}` customer name; `{{3}}` quotation PDF URL |
| Team member receives an invitation | `framebucket_team_member_invite` | Utility | `{{1}}` member name; `{{2}}` studio name; `{{3}}` member type; `{{4}}` invitation URL |
| Customer is told which photographer was assigned to the event | `framebucket_event_assignment_customer` | Utility | `{{1}}` photographer name; `{{2}}` photographer phone; `{{3}}` studio name |
| Assigned team member receives event details | `framebucket_event_assignment_assignee` | Utility | `{{1}}` event type; `{{2}}` event location; `{{3}}` landmark; `{{4}}` event time; `{{5}}` studio name |
| Customer changes event location or time in the portal (sent to the assigned photographer, event crew and studio owner, on every change) | `framebucket_event_details_updated` (set `WHATSAPP_EVENT_UPDATE_TEMPLATE`) | Utility | `{{1}}` studio name; `{{2}}` customer name; `{{3}}` event type; `{{4}}` event date; `{{5}}` event location; `{{6}}` landmark; `{{7}}` event time |
| Customer receives a mobile verification code | `framebucket_mobile_verification` | Authentication | `{{1}}` OTP code in the body; the current sender also sends it to URL button index `0` |
| Photographer receives the private photo ZIP after album selection is finalized | `framebucket_album_selection_finalized` | Utility | `{{1}}` customer name; `{{2}}` private raw-photo ZIP URL |
| Guest receives a matched event photo | `framebucket_event_photo_match` | Utility | Image header; body: `{{1}}` guest name; `{{2}}` event type; `{{3}}` photo number |
| Day-before shoot reminder (sent to the customer and the assigned photographer; only when `WHATSAPP_EVENT_REMINDER_TEMPLATE` is set) | `framebucket_event_reminder` | Utility | `{{1}}` event type; `{{2}}` studio name; `{{3}}` date and time; `{{4}}` location |

## Suggested template bodies

Use these bodies when creating the templates in WhatsApp Manager. Meta may adjust the category during review.

### `framebucket_enquiry_quote_link`

```text
Hi {{1}}, {{2}} received your photography enquiry. Use this secure link to build your quotation:

{{3}}

We look forward to helping with your celebration.
```

### `framebucket_quote_submitted`

```text
Hello {{1}}, {{2}} has submitted a quotation request. The generated quotation PDF is available here:

{{3}}
```

### `framebucket_team_member_invite`

```text
Hi {{1}}, {{2}} invited you to join the studio team as {{3}}. Accept the invitation here:

{{4}}
```

### `framebucket_event_assignment_customer`

```text
Hello, {{1}} ({{2}}) has been assigned as the photographer for your event with {{3}}.
```

### `framebucket_event_assignment_assignee`

```text
You have been assigned to a {{1}} event at {{2}} ({{3}}). Event time: {{4}}. Studio: {{5}}.
```

### `framebucket_event_details_updated`

```text
{{1}}: {{2}} has updated the event details.

Event: {{3}} on {{4}}
Location: {{5}}
Landmark: {{6}}
Time: {{7}}

Please plan with the latest details.
```

Until this template is approved and `WHATSAPP_EVENT_UPDATE_TEMPLATE` is set, each change re-sends `framebucket_event_assignment_assignee` (with the date added to the time) to the assigned photographer and event crew. The studio owner gets the alert only with the dedicated template. No alert is sent when the customer saves without changing anything.

### `framebucket_album_selection_finalized`

```text
Hello,

Your customer ({{1}}) has finalised the photo selection for album designing. Please find the raw photos link:

{{2}}

Regards,
framebucket.in
```

### `framebucket_event_reminder`

```text
Reminder: your {{1}} with {{2}} is tomorrow, {{3}}.

Location: {{4}}

See you there!
framebucket.in
```

### Mobile verification template

Keep the existing approved authentication template because the current OTP sender sends the OTP as body parameter `{{1}}` and as a URL-button parameter (`sub_type: url`, index `0`). If you change the template's button type or remove the URL button, update `WhatsAppMobileOtpSender` to match before deploying.

## Matched event photo delivery

Create and get Meta approval for a Utility template named `framebucket_event_photo_match` (or set `WHATSAPP_PHOTO_MATCH_TEMPLATE` to the exact approved name). Give it an **Image** header and this body with three text placeholders:

~~~text
Hello {{1}}, we found photo {{3}} of you from the {{2}} event. Your photo is attached.

Regards,
FrameBucket
~~~

The sender uploads each matched gallery image to the WhatsApp media endpoint and uses its media ID in the image header. The guest form asks for explicit WhatsApp consent before matching or delivery. Only the event's processed image is sent; video matches are skipped.

## Example values for Meta's template approval form

Use non-customer sample values when Meta asks for examples:

| Variable | Sample |
| --- | --- |
| Customer or member name | `Ananya` |
| Photographer name | `Rohan` |
| Studio name | `FrameBucket Studio` |
| Member type | `photographer` |
| Event type | `Engagement` |
| Photographer phone | `919876543210` |
| Location / landmark | `Lakeview Venue` / `Near City Park` |
| Event time | `12 Dec 2026, 10:00 AM` |
| OTP | `123456` |
| Quotation, invitation, album or PDF link | `https://example.com/example-link` |

## Railway / deployment variables

Set these on the **Spring Boot backend service**. The Python data service does not send these WhatsApp notifications. Keep the access token private and use a rotated token if an earlier one was exposed.

```dotenv
APP_SMS_MODE=whatsapp
APP_WHATSAPP_MODE=cloud
WHATSAPP_MESSAGES_URL=https://graph.facebook.com/v25.0/REPLACE_WITH_PRODUCTION_PHONE_NUMBER_ID/messages
WHATSAPP_ACCESS_TOKEN=REPLACE_WITH_ROTATED_SYSTEM_USER_TOKEN
WHATSAPP_TEMPLATE_LANGUAGE=en_US

WHATSAPP_ENQUIRY_TEMPLATE=framebucket_enquiry_quote_link
WHATSAPP_ENQUIRY_TEMPLATE_LANGUAGE=en_US
WHATSAPP_QUOTE_SUBMITTED_TEMPLATE=framebucket_quote_submitted
WHATSAPP_QUOTE_SUBMITTED_TEMPLATE_LANGUAGE=en_US
WHATSAPP_TEAM_INVITE_TEMPLATE=framebucket_team_member_invite
WHATSAPP_TEAM_INVITE_TEMPLATE_LANGUAGE=en_US
WHATSAPP_ASSIGNMENT_CUSTOMER_TEMPLATE=framebucket_event_assignment_customer
WHATSAPP_ASSIGNMENT_CUSTOMER_TEMPLATE_LANGUAGE=en_US
WHATSAPP_ASSIGNMENT_ASSIGNEE_TEMPLATE=framebucket_event_assignment_assignee
WHATSAPP_ASSIGNMENT_ASSIGNEE_TEMPLATE_LANGUAGE=en_US
WHATSAPP_MOBILE_VERIFICATION_TEMPLATE=framebucket_mobile_verification
WHATSAPP_MOBILE_VERIFICATION_TEMPLATE_LANGUAGE=en_US
WHATSAPP_ALBUM_DESIGN_TEMPLATE=framebucket_album_selection_finalized
WHATSAPP_ALBUM_DESIGN_TEMPLATE_LANGUAGE=en_US
WHATSAPP_PHOTO_MATCH_TEMPLATE=framebucket_event_photo_match
WHATSAPP_PHOTO_MATCH_TEMPLATE_LANGUAGE=en_US
WHATSAPP_EVENT_UPDATE_TEMPLATE=framebucket_event_details_updated
WHATSAPP_EVENT_UPDATE_TEMPLATE_LANGUAGE=en_US
WHATSAPP_EVENT_REMINDER_TEMPLATE=framebucket_event_reminder
WHATSAPP_EVENT_REMINDER_TEMPLATE_LANGUAGE=en_US
GALLERY_PHOTO_DELIVERY_POLL_MS=5000
```

Replace the phone-number ID with the production sender's ID, and set each language to the exact locale shown for that approved template in Meta. `en_US` is only an example. The per-template language values are optional overrides; if omitted, each falls back to `WHATSAPP_TEMPLATE_LANGUAGE`.

`APP_SMS_MODE=whatsapp` controls mobile OTP only. `APP_WHATSAPP_MODE=cloud` enables enquiry, quotation, team, event-assignment, album-design, and matched-photo notifications. The matched-photo template must have an image header and exactly three body variables as documented above. Its locale values must match Meta's approved template exactly.
