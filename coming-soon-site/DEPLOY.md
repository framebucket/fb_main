# FrameBucket public landing page

This small Node service serves a public, static landing page for `https://framebucket.in/`.
It is separate from the FrameBucket application and does not need the database or API.

## Deploy as a new Railway service

1. Push this folder to the Git repository connected to Railway.
2. In Railway, create a **new service** from that same repository. Do not change the root directory of the existing backend or Python services.
3. Set the new service's **Root Directory** to `coming-soon-site` and deploy. Railway detects `package.json` and runs `npm start`.
4. Open the new service's Railway-provided public URL. Confirm it shows the FrameBucket page and that `/health` responds with `ok`.
5. In that service's **Settings → Networking**, add the custom domain `framebucket.in`. Add `www.framebucket.in` too if Railway offers a separate domain entry for it.
6. Copy the exact DNS record type, name, and target Railway displays for each domain.
7. In GoDaddy, open **My Products → Domains → framebucket.in → DNS → Add New Record** and enter those exact records. Do not guess the target. Avoid changing MX or email-related TXT records; those may control email delivery.
8. Wait for Railway to show the custom domain as active and HTTPS as ready. Open `https://framebucket.in` in a private browser window. Confirm that it loads without signing in.
9. In Meta Business Settings → **Business info**, set the website to `https://framebucket.in/`, save, then retry the WhatsApp display-name submission.

DNS changes can take time to propagate. Keep the current GoDaddy DNS records available until the new domain is working. If the root domain already has an A record or `www` already has a CNAME, compare it with Railway's instructions before changing that specific web record. Leave MX records and unrelated TXT records untouched.
