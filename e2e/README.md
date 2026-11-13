# End-to-end suites

API-level checks that drive the real Spring Boot backend over HTTP with a fresh PostgreSQL database per
suite. `stub_data_service.py` stands in for the Python data service; for the editing-style, album-design
and invitation suites it runs the real fb_data code in-process when `FB_DATA_SRC` points at an fb_data
checkout (otherwise those suites print `SKIPPED`).

| Suite | Covers |
| --- | --- |
| `e2e_suite.py` | auth, customers, events, quotes, payments & receipts, galleries, downloads, portal |
| `e2e_admin.py` | subscription plans, plan limits and feature gates, admin portal |
| `e2e_growth.py` | packages, lead capture, contracts, delivery, films, proofs, branding, crew, gear, insights |
| `e2e_style.py` | AI editing style & studio logo (needs fb_data) |
| `e2e_album.py` | AI album auto-design, PSD/JPEG downloads, send to proofing (needs fb_data) |
| `e2e_invite.py` | client wedding invitations, designs, regenerate, MP4 (needs fb_data + ffmpeg) |
| `e2e_otp.py` | WhatsApp-code sign-in for photographers and customers, remembered app sessions until an app update |
| `e2e_presets.py` | editing presets: plan limits 3/5/10/25, upload, rename, delete, AI pick preview (needs fb_data) |
| `e2e_refs.py` | reference album designs: Studio+ only (50/100), layout analysis, limits, thumbnails (needs fb_data) |

Run locally:

```bash
mvn -f backend/pom.xml -DskipTests package
pip install -r e2e/requirements.txt
PGPASSWORD=postgres e2e/run_e2e.sh                     # all suites
FB_DATA_SRC=../fb_data e2e/run_e2e.sh e2e_album         # with the real data-service code
```

Logs, responses and generated files go to `e2e/out/`. In GitHub Actions the workflow
`.github/workflows/tests.yml` runs everything on each push and pull request.
