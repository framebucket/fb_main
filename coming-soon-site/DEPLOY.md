# FrameBucket public landing page

This small Node service serves a public, static landing page for `https://www.framebucket.in/`.
It is separate from the FrameBucket application and does not need the database or API.

## Deploy as a new Railway service

1. Push this folder to the Git repository connected to Railway.
2. In Railway, create a **new service** from that same repository. Do not change the root directory of the existing backend or Python services.
3. Set the new service's **Root Directory** to `coming-soon-site` and deploy. Railway detects `package.json` and runs `npm start`.
4. Open the new service's Railway-provided public URL. Confirm it shows the FrameBucket page and that `/health` responds with `ok`.
5. In that service's **Settings → Networking**, add `www.framebucket.in` as the custom domain. If the apex `framebucket.in` is already listed and Railway's plan limit prevents adding another, remove that pending apex entry first; GoDaddy cannot use Railway's root CNAME directly.
6. Copy both DNS records Railway displays for `www.framebucket.in`: the CNAME routing record and the TXT verification record. Railway needs both.
7. In GoDaddy, open **My Products → Domains → framebucket.in → DNS**. Add or edit a CNAME with **Name** `www` and the exact CNAME **Value** Railway displays. Add the TXT record with the exact **Name** and **Value** Railway displays. Leave MX and unrelated TXT records untouched. If GoDaddy already has a `www` CNAME, edit it instead of creating a duplicate.
8. In GoDaddy's DNS **Forwarding** section, forward the root domain `framebucket.in` to `https://www.framebucket.in/` using forwarding only (no masking). GoDaddy applies HTTPS forwarding automatically. Do not create a CNAME for `@`.
9. Wait for Railway to show the `www` custom domain as active and HTTPS as ready. Open `https://www.framebucket.in` in a private browser window and confirm the page loads without signing in. Also confirm `https://framebucket.in` redirects to it.
10. In Meta Business Settings → **Business info**, set the website to `https://www.framebucket.in/`, save, then retry the WhatsApp display-name submission.

DNS changes can take time to propagate. Keep the current GoDaddy DNS records available until the new domain is working. The root domain cannot be CNAME'd to Railway directly through GoDaddy; use the `www` hostname and GoDaddy's root-to-`www` forwarding, or move DNS hosting to a provider that supports CNAME flattening. Changing nameservers requires copying all existing DNS records first, especially mail records.
