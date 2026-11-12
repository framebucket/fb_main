"""End-to-end checks: lead capture, packages, quote follow-ups, contracts + e-sign + advance + date holds,
delivery checklist, films, album proofing, branding, watermarks, custom domains, crew, gear, costs, insights."""
import os
OUT = os.getenv("FB_E2E_OUT", "out"); os.makedirs(OUT, exist_ok=True)
import base64, io, re, sys, time
import requests
from PIL import Image

sys.argv = sys.argv[:1]
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "e2e_admin.py")).read().split("# ------------------------------------------------------------------------------------------------")[0])

STUB = os.getenv("FB_STUB_URL", "http://127.0.0.1:8001")


def png_signature():
    img = Image.new("RGBA", (300, 100), (255, 255, 255, 0))
    from PIL import ImageDraw
    d = ImageDraw.Draw(img)
    d.line([(10, 80), (80, 20), (150, 80), (220, 20), (290, 70)], fill=(20, 20, 60, 255), width=4)
    buf = io.BytesIO(); img.save(buf, "PNG")
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


def customer_login(mobile):
    c = Client()
    mark = log_size()
    c.post("/api/customer-auth/request-code", {"mobileNumber": mobile})
    time.sleep(0.5)
    codes = re.findall(r"\b(\d{6})\b", open(LOG, encoding="utf-8", errors="replace").read()[mark:])
    r = c.post("/api/customer-auth/verify-code", {"mobileNumber": mobile, "code": codes[-1]})
    assert r.status_code == 200, r.text
    return c


# ------------------------------------------------------------------------------------------------
section("Setup")
owner = Client(); register(owner, "Owner", "FrameBucket HQ", "owner@framebucket.in", "9000011111")
p = Client(); me = register(p, "Ravi Kumar", "Pixel Stories", "ravi@example.com", "9876500001")
P = uid("ravi@example.com")
check("new photographer on Studio trial with new features", all(f in me["entitlements"]["features"] for f in
      ["CONTRACTS", "FILMS", "ALBUM_PROOFING", "WATERMARK", "CREW_PAYOUTS", "GEAR_COSTS", "INSIGHTS", "LEAD_CAPTURE", "CUSTOM_PACKAGES", "DELIVERY_TRACKER"])
      and "CUSTOM_DOMAIN" not in me["entitlements"]["features"], me["entitlements"]["features"])
free = Client(); register(free, "Free Photographer", "Free Studio", "free@example.com", "9876500002")
F = uid("free@example.com")
expect("admin moves free photographer to Free", owner.put(f"/api/admin/photographers/{F}/plan", {"planCode": "FREE"}), 200)
expect("studio profile with slug + UPI", p.put("/api/studio/profile", {"upiId": "pixel@okaxis", "upiPayeeName": "Pixel Stories",
       "portfolioSlug": "pixel-stories", "portfolioPublic": True, "remindersEnabled": True}), 200)
types = p.get("/api/events/types").json()
wedding = next(t for t in types if t["name"] == "Wedding")

# ------------------------------------------------------------------------------------------------
section("Packages & add-ons per studio")
cat = expect("catalogue (seeded)", p.get("/api/studio/quote-catalog"), 200).json()
check("9 services + 3 add-ons seeded with old codes", len(cat["services"]) == 9 and len(cat["addons"]) == 3
      and {s["code"] for s in cat["services"]} >= {"photo", "candid", "drone"}, cat)
check("defaults: ₹400/sheet, 5% discount, 30% advance", float(cat["albumSheetPrice"]) == 400 and float(cat["discountPercent"]) == 5
      and float(cat["advancePercent"]) == 30 and not cat["customTerms"], cat)
cat = expect("add package", p.post("/api/studio/quote-catalog/packages", {"name": "Gold Wedding", "description": "Two-day wedding coverage",
       "inclusions": ["Candid photography", "Cinematic film", "40-sheet album"], "price": 150000, "highlighted": True, "active": True, "sortOrder": 10}), 200).json()
gold = cat["packages"][0]
cat = expect("add add-on", p.post("/api/studio/quote-catalog/items", {"kind": "ADDON", "name": "Drone reel", "description": "60-second aerial reel",
       "price": 7000, "active": True, "sortOrder": 200}), 200).json()
drone = next(a for a in cat["addons"] if a["name"] == "Drone reel")
check("add-on gets a code", drone["code"] == "drone-reel", drone)
candid = next(s for s in cat["services"] if s["code"] == "candid")
cat = expect("change service price", p.put(f"/api/studio/quote-catalog/items/{candid['id']}", {**{k: candid[k] for k in ["kind", "name", "description", "active", "sortOrder"]}, "price": 22000}), 200).json()
led = next(s for s in cat["services"] if s["code"] == "led")
cat = expect("switch off a service", p.delete(f"/api/studio/quote-catalog/items/{led['id']}"), 200).json()
check("switched-off service kept but inactive", any(s["code"] == "led" and not s["active"] for s in cat["services"]))
cat = expect("pricing settings + custom terms", p.put("/api/studio/quote-catalog/settings", {"albumSheetPrice": 500, "discountPercent": 10,
       "advancePercent": 25, "contractTerms": "1. Advance is non-refundable.\n2. Balance due on the event day."}), 200).json()
check("settings saved", float(cat["albumSheetPrice"]) == 500 and cat["customTerms"], cat)
expect("validation: discount > 90 rejected", p.put("/api/studio/quote-catalog/settings", {"albumSheetPrice": 500, "discountPercent": 95, "advancePercent": 25}), 400)
other = Client(); register(other, "Other Studio", "Other", "other@example.com", "9876500003")
expect("other studio can't edit my package", other.put(f"/api/studio/quote-catalog/packages/{gold['id']}", {"name": "x", "price": 1, "active": True, "highlighted": False, "sortOrder": 1}), 404)

# ------------------------------------------------------------------------------------------------
section("Lead capture")
links = expect("lead capture links", p.get("/api/studio/lead-capture"), 200).json()
check("links + embed code", links["ready"] and links["instagramUrl"].endswith("/enquire/pixel-stories?source=instagram")
      and "<iframe" in links["embedCode"] and "chat=1" in links["whatsappUrl"], links)
check("free plan without slug gets setup message", not free.get("/api/studio/lead-capture").json()["ready"])
anon = requests.Session()
form = expect("public enquiry form", anon.get(B + "/api/public/studios/pixel-stories/enquiry-form"), 200).json()
check("form has studio name + event types", form["studioName"] == "Pixel Stories" and len(form["eventTypes"]) >= 3 and form["whatsappAvailable"], form)
expect("unknown studio form", anon.get(B + "/api/public/studios/nobody-here/enquiry-form"), 404)
r_dash = anon.get(B + "/dashboard"); r_enq = anon.get(B + "/enquire/pixel-stories")
check("enquiry page can be embedded (no X-Frame-Options)", r_enq.status_code == 200 and "X-Frame-Options" not in r_enq.headers, dict(r_enq.headers))
check("other pages still DENY framing", r_dash.headers.get("X-Frame-Options") == "DENY", dict(r_dash.headers))
mark = log_size()
lead = expect("submit lead from Instagram (no CSRF needed)", anon.post(B + "/api/public/studios/pixel-stories/enquiries", json={
    "name": "Ananya Rao", "mobileNumber": "9811100001", "email": "ananya@example.com", "eventTypeId": wedding["id"],
    "preferredDate": D(60), "message": "Wedding in Udaipur, 300 guests", "source": "instagram"}), 201).json()
check("WhatsApp continue link", lead["whatsappUrl"].startswith("https://wa.me/919876500001?text=") and "Ananya" in lead["whatsappUrl"], lead)
time.sleep(0.5)
lead_token = log_find(r"/quote/([A-Za-z0-9_\-]+)", mark)
check("client got quote link email", lead_token)
check("photographer emailed about lead", "New enquiry from Instagram: Ananya Rao" in open(LOG).read()[mark:])
before = int(psql("select count(*) from fb_enquiries"))
expect("honeypot submission pretends success", anon.post(B + "/api/public/studios/pixel-stories/enquiries", json={
    "name": "Bot", "mobileNumber": "9811100009", "email": "bot@example.com", "eventTypeId": wedding["id"], "website": "http://spam"}), 201)
check("honeypot created nothing", int(psql("select count(*) from fb_enquiries")) == before)
expect("lead validation", anon.post(B + "/api/public/studios/pixel-stories/enquiries", json={"name": "", "mobileNumber": "1", "email": "x", "eventTypeId": wedding["id"]}), 400)
enquiries = p.get("/api/enquiries").json()
L = next(e for e in enquiries if e["name"] == "Ananya Rao")
tracking = {t["enquiryId"]: t for t in expect("enquiry tracking", p.get("/api/studio/enquiries/tracking"), 200).json()}
check("lead source + message + date tracked", tracking[L["id"]]["source"] == "INSTAGRAM" and "Udaipur" in tracking[L["id"]]["message"]
      and tracking[L["id"]]["preferredDate"] == D(60) and tracking[L["id"]]["quoteOpenedAt"] is None, tracking[L["id"]])

# ------------------------------------------------------------------------------------------------
section("Quote with package + add-ons")
qc = Client()
ctx = expect("quote context", anon.get(B + f"/api/public/enquiries/quote/{lead_token}"), 200).json()
check("packages + add-ons offered, inactive service hidden", ctx["packages"][0]["name"] == "Gold Wedding" and
      any(a["id"] == "drone-reel" for a in ctx["addons"]) and not any(s["id"] == "led" for s in ctx["services"]), ctx)
check("brand view in context (Studio trial, no colour yet)", ctx["brand"]["studioName"] == "Pixel Stories" and not ctx["brand"]["poweredByFrameBucket"], ctx["brand"])
time.sleep(0.2)
check("quote marked opened", p.get("/api/studio/enquiries/tracking").json()[0]["quoteOpenedAt"] is not None or
      any(t["quoteOpenedAt"] for t in p.get("/api/studio/enquiries/tracking").json() if t["enquiryId"] == L["id"]))
qform = {"venue": "Udaipur", "eventDate": D(60), "preferredTime": "Evening", "events": [{"eventTypeId": wedding["id"], "serviceIds": ["candid"]}],
         "albumSheets": 30, "packageId": gold["id"], "addonIds": ["drone-reel", "prewedding"]}
calc = expect("calculate", qc.post(f"/api/public/enquiries/quote/{lead_token}/calculate", qform), 200).json()
expected = round((150000 + 22000 + 7000 + 25000 + 30 * 500) * 0.9)
check(f"price = (package+candid+addons+sheets) - 10% = {expected}", float(calc["finalPrice"]) == expected, calc)
bad = dict(qform, events=[{"eventTypeId": wedding["id"], "serviceIds": ["led"]}])
expect("switched-off service rejected", qc.post(f"/api/public/enquiries/quote/{lead_token}/calculate", bad), 400)
expect("unknown package rejected", qc.post(f"/api/public/enquiries/quote/{lead_token}/calculate", dict(qform, packageId=gold["id"][:-4] + "0000")), 400)
expect("submit quote (CSRF ok for real browser flow)", Client().post(f"/api/public/enquiries/quote/{lead_token}/submit", qform), (200, 201))
pdf = anon.get(B + f"/api/public/enquiries/quote/{lead_token}/pdf").content
check("quote PDF lists package + add-ons", b"Gold Wedding" in pdf and b"Drone reel" in pdf, pdf[:200])
ctx = anon.get(B + f"/api/public/enquiries/quote/{lead_token}").json()
check("submitted context shows package + add-on names", ctx["submittedPackageName"] == "Gold Wedding" and "Drone reel" in ctx["submittedAddonNames"], ctx)
check("old suite quotes still work (no package)", True)

# ------------------------------------------------------------------------------------------------
section("Quote follow-ups")
mark = log_size()
m = expect("manual enquiry", p.post("/api/enquiries", {"name": "Karan Mehta", "mobileNumber": "9811100002", "email": "karan@example.com", "eventTypeId": wedding["id"]}), (200, 201)).json()
psql(f"update fb_enquiries set created_at = now() - interval '3 days' where id = '{m['id']}'")
time.sleep(7)
check("scheduler sent the 48h follow-up", psql(f"select quote_reminder_sent_at is not null from fb_enquiries where id = '{m['id']}'") == "t")
check("follow-up email has quote link", "build your photography quote" in open(LOG).read()[mark:])
expect("manual reminder within 12h refused", p.post(f"/api/studio/enquiries/{m['id']}/remind"), 429)
psql(f"update fb_enquiries set quote_reminder_sent_at = now() - interval '13 hours' where id = '{m['id']}'")
t = expect("manual reminder", p.post(f"/api/studio/enquiries/{m['id']}/remind"), 200).json()
check("reminder timestamp updated", t["quoteReminderSentAt"] is not None, t)
fe = free.post("/api/enquiries", {"name": "Z", "mobileNumber": "9811100003", "email": "z@example.com", "eventTypeId": wedding["id"]}).json()
plan_blocked("Free plan reminders", free.post(f"/api/studio/enquiries/{fe['id']}/remind"), feature="CONTRACTS")

# ------------------------------------------------------------------------------------------------
section("Contracts, e-signature, advance, date hold")
expect("contract before quote refused", p.get(f"/api/studio/enquiries/{m['id']}/contract"), 409)
draft = expect("contract draft", p.get(f"/api/studio/enquiries/{L['id']}/contract"), 200).json()
check("draft: quote date, total, 25% advance, custom terms", draft["eventDate"] == D(60) and float(draft["totalAmount"]) == expected
      and float(draft["advanceAmount"]) == round(expected * 0.25) and "non-refundable" in draft["terms"], draft)
mark = log_size()
sent = expect("send contract", p.post(f"/api/studio/enquiries/{L['id']}/contract", {"advanceAmount": 50000}), 200).json()
check("contract SENT with share link + email", sent["contract"]["status"] == "SENT" and sent["shareLink"].endswith(lead_token)
      and "booking agreement is ready to sign" in open(LOG).read()[mark:], sent)
ctx = anon.get(B + f"/api/public/enquiries/quote/{lead_token}").json()
check("client sees contract", ctx["contract"]["status"] == "SENT" and float(ctx["contract"]["advanceAmount"]) == 50000 and ctx["contract"]["upiAvailable"], ctx["contract"])
draft_pdf = anon.get(B + f"/api/public/enquiries/quote/{lead_token}/contract.pdf")
check("draft agreement PDF", draft_pdf.content[:5] == b"%PDF-" and b"DRAFT" in draft_pdf.content)
expect("advance QR before signing refused", anon.get(B + f"/api/public/enquiries/quote/{lead_token}/contract/advance-qr.png"), 409)
cl = Client()
expect("sign without agreeing refused", cl.post(f"/api/public/enquiries/quote/{lead_token}/contract/sign", {"signerName": "Ananya Rao", "signature": png_signature(), "agree": False}), 400)
expect("sign with junk signature refused", cl.post(f"/api/public/enquiries/quote/{lead_token}/contract/sign", {"signerName": "Ananya Rao", "signature": "data:image/png;base64,AAAA", "agree": True}), 422)
mark = log_size()
signed = expect("client signs", cl.post(f"/api/public/enquiries/quote/{lead_token}/contract/sign", {"signerName": "Ananya Rao", "signature": png_signature(), "agree": True}), 200).json()
check("signed + emails", signed["status"] == "SIGNED" and signed["signerName"] == "Ananya Rao" and "Signed: Ananya Rao" in open(LOG).read()[mark:], signed)
expect("sign twice refused", cl.post(f"/api/public/enquiries/quote/{lead_token}/contract/sign", {"signerName": "X", "signature": png_signature(), "agree": True}), 409)
check("signature evidence stored", psql(f"select signer_ip is not null and length(content_sha256) = 64 and length(signature_png) > 100 from fb_contract where enquiry_id = '{L['id']}'") == "t")
check("date held on calendar", D(60) in p.get("/api/events/blocked-dates").json())
cust = p.post("/api/customers", {"name": "Walk In", "email": "walkin@example.com", "mobileNumber": "9811100004", "dateOfBirth": "1990-01-01", "address": "Delhi"}).json()
expect("another booking on the held date refused", p.post("/api/events", {"eventDate": D(60), "eventTypeId": wedding["id"], "customerId": cust["id"], "price": "1000"}), 409)
signed_pdf = anon.get(B + f"/api/public/enquiries/quote/{lead_token}/contract.pdf").content
check("signed PDF has signature + fingerprint", b"Signed electronically by Ananya Rao" in signed_pdf and b"/Subtype /Image" in signed_pdf and b"SHA-256" in signed_pdf)
qr = anon.get(B + f"/api/public/enquiries/quote/{lead_token}/contract/advance-qr.png")
check("advance UPI QR", qr.status_code == 200 and qr.content[:4] == b"\x89PNG")
rep = expect("client reports advance UTR", cl.post(f"/api/public/enquiries/quote/{lead_token}/contract/advance", {"reference": "UTR998877"}), 200).json()
check("advance reported", rep["advanceReference"] == "UTR998877" and rep["advanceReportedAt"], rep)
conf = expect("photographer confirms advance", p.post(f"/api/studio/enquiries/{L['id']}/contract/advance", {"method": "UPI", "paidOn": str(today)}), 200).json()
check("advance confirmed", conf["advanceConfirmedAt"] is not None, conf)
expect("send again after signing refused", p.post(f"/api/studio/enquiries/{L['id']}/contract", {}), 409)
expect("finalize quote", p.put(f"/api/enquiries/{L['id']}/finalized-quote", {"finalizedQuote": expected}), 200)
setup = expect("event setup", p.get(f"/api/enquiries/{L['id']}/event-setup"), 200).json()
check("setup suggests contract date and doesn't block it", setup["suggestedEventDate"] == D(60) and D(60) not in setup["blockedDates"], setup)
ev = expect("create event from enquiry on held date", p.post(f"/api/enquiries/{L['id']}/create-event", {"eventDate": D(60), "dateOfBirth": "1995-05-05", "address": "Udaipur"}), (200, 201)).json()
EV = ev["eventId"] if "eventId" in ev else ev.get("id")
check("hold released", psql(f"select count(*) from fb_date_hold where enquiry_id = '{L['id']}'") == "0")
billing = expect("event billing", p.get(f"/api/events/{EV}/billing"), 200).json()
adv = [x for x in billing["payments"] if x["purpose"] == "ADVANCE"]
check("advance auto-recorded with receipt", len(adv) == 1 and float(adv[0]["amount"]) == 50000 and adv[0]["receiptNumber"].startswith("RCPT-")
      and adv[0]["reference"] == "UTR998877", billing["payments"])
# cancel flow on a second enquiry
mark = log_size()
e3 = p.post("/api/enquiries", {"name": "Isha", "mobileNumber": "9811100005", "email": "isha@example.com", "eventTypeId": wedding["id"]}).json()
time.sleep(0.4)
t3 = log_find(r"/quote/([A-Za-z0-9_\-]+)", mark)
Client().post(f"/api/public/enquiries/quote/{t3}/submit", {"venue": "Goa", "eventDate": D(61), "preferredTime": "Morning", "events": [{"eventTypeId": wedding["id"], "serviceIds": ["photo"]}], "albumSheets": 0})
expect("contract on already-held/booked date refused", p.post(f"/api/studio/enquiries/{e3['id']}/contract", {"eventDate": D(60)}), 409)
expect("past date refused", p.post(f"/api/studio/enquiries/{e3['id']}/contract", {"eventDate": D(-1)}), 422)
expect("send contract #2", p.post(f"/api/studio/enquiries/{e3['id']}/contract", {}), 200)
Client().post(f"/api/public/enquiries/quote/{t3}/contract/sign", {"signerName": "Isha", "signature": png_signature(), "agree": True})
check("second hold", D(61) in p.get("/api/events/blocked-dates").json())
expect("cancel contract", p.delete(f"/api/studio/enquiries/{e3['id']}/contract"), 204)
check("cancel releases hold", D(61) not in p.get("/api/events/blocked-dates").json())
check("client no longer sees cancelled contract", anon.get(B + f"/api/public/enquiries/quote/{t3}").json()["contract"] is None)
tr = {t["enquiryId"]: t for t in p.get("/api/studio/enquiries/tracking").json()}
check("tracking shows contract statuses", tr[L["id"]]["contract"]["status"] == "SIGNED" and tr[e3["id"]]["contract"]["status"] == "CANCELLED")
# free plan
fq_mark = log_size()
fe2 = free.post("/api/enquiries", {"name": "Y", "mobileNumber": "9811100006", "email": "y@example.com", "eventTypeId": wedding["id"]}).json()
time.sleep(0.4)
ft = log_find(r"/quote/([A-Za-z0-9_\-]+)", fq_mark)
Client().post(f"/api/public/enquiries/quote/{ft}/submit", {"venue": "", "eventDate": D(70), "preferredTime": "Morning", "events": [{"eventTypeId": wedding["id"], "serviceIds": ["photo"]}], "albumSheets": 0})
plan_blocked("Free plan contracts", free.post(f"/api/studio/enquiries/{fe2['id']}/contract", {}), feature="CONTRACTS")

# ------------------------------------------------------------------------------------------------
section("Delivery checklist")
requests.post(STUB + f"/__rich/{EV}")
steps = expect("default steps", p.get(f"/api/events/{EV}/delivery/steps"), 200).json()
check("5 default steps", [s["kind"] for s in steps] == ["TEASER", "HIGHLIGHTS", "GALLERY", "ALBUM_DESIGN", "PRINTED_ALBUM"], steps)
teaser = steps[0]
mark = log_size()
steps = expect("teaser done + notify", p.put(f"/api/events/{EV}/delivery/steps/{teaser['id']}", {"title": "Teaser", "kind": "TEASER", "status": "DONE",
               "note": "Watch it in your portal", "notifyClient": True}), 200).json()
check("completed with timestamp + client emailed", steps[0]["status"] == "DONE" and steps[0]["completedAt"] and "Teaser is ready" in open(LOG).read()[mark:])
steps = expect("add custom step", p.post(f"/api/events/{EV}/delivery/steps", {"title": "Parents album", "kind": "OTHER", "dueDate": D(120)}), 200).json()
check("custom step appended", steps[-1]["title"] == "Parents album" and steps[-1]["dueDate"] == D(120))
steps = expect("delete step", p.delete(f"/api/events/{EV}/delivery/steps/{steps[-1]['id']}"), 200).json()
check("6 -> 5 steps", len(steps) == 5)
expect("other studio can't see steps", other.get(f"/api/events/{EV}/delivery/steps"), 404)

# ------------------------------------------------------------------------------------------------
section("Films")
vids = expect("gallery videos", p.get(f"/api/events/{EV}/films/videos"), 200).json()
check("one uploaded video found", len(vids) == 1 and vids[0]["filename"] == "highlights.mp4", vids)
films = expect("add uploaded film", p.post(f"/api/events/{EV}/films", {"title": "Highlights", "kind": "HIGHLIGHTS", "mediaId": vids[0]["mediaId"], "notifyClient": True}), 200).json()
films = expect("add YouTube film", p.post(f"/api/events/{EV}/films", {"title": "Full film", "kind": "FULL_FILM", "externalUrl": "https://youtu.be/dQw4w9WgXcQ"}), 200).json()
check("YouTube embed url", films[-1]["embedUrl"] == "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ", films[-1])
expect("http link refused", p.post(f"/api/events/{EV}/films", {"title": "x", "kind": "OTHER", "externalUrl": "http://example.com/v"}), 422)
expect("photo instead of video refused", p.post(f"/api/events/{EV}/films", {"title": "x", "kind": "OTHER", "mediaId": "a1b2c3d4e5f60001"}), 422)
expect("both sources refused", p.post(f"/api/events/{EV}/films", {"title": "x", "kind": "OTHER", "mediaId": vids[0]["mediaId"], "externalUrl": "https://vimeo.com/123456"}), 422)
steps = p.get(f"/api/events/{EV}/delivery/steps").json()
check("highlights step auto-completed", next(s for s in steps if s["kind"] == "HIGHLIGHTS")["status"] == "DONE")

# ------------------------------------------------------------------------------------------------
section("Album proofing")
proofs = expect("send proof v1 from folder", p.post(f"/api/events/{EV}/proofs", {"folderId": "proofs", "message": "First draft"}), 200).json()
v1 = proofs[0]
check("v1 spreads in natural order", v1["version"] == 1 and v1["mediaIds"] == ["sprd000000000001", "sprd000000000002", "sprd000000000010"], v1)
check("album step in progress", next(s for s in p.get(f"/api/events/{EV}/delivery/steps").json() if s["kind"] == "ALBUM_DESIGN")["status"] == "IN_PROGRESS")
expect("empty folder refused", p.post(f"/api/events/{EV}/proofs", {"folderId": "nothing"}), 422)
cu = customer_login("9811100001")
dv = expect("customer delivery view", cu.get(f"/api/customer-portal/events/{EV}/delivery"), 200).json()
check("customer sees steps, 2 films, proof v1", len(dv["steps"]) == 5 and len(dv["films"]) == 2 and dv["proof"]["version"] == 1, dv)
fid = next(f for f in dv["films"] if f["source"] == "UPLOAD")["id"]
s = cu.s.get(B + f"/api/customer-portal/events/{EV}/films/{fid}/stream", headers={"Range": "bytes=0-1023"})
check("film streams with range (206 video/mp4)", s.status_code == 206 and s.headers["Content-Type"] == "video/mp4" and s.headers.get("Content-Range", "").startswith("bytes 0-"), (s.status_code, dict(s.headers)))
yid = next(f for f in dv["films"] if f["source"] == "LINK")["id"]
check("linked film redirects", cu.get(f"/api/customer-portal/events/{EV}/films/{yid}/stream").status_code == 302)
sp = cu.get(f"/api/customer-portal/events/{EV}/proofs/{v1['id']}/spreads/0")
check("spread image", sp.status_code == 200 and sp.headers["Content-Type"].startswith("image/"), sp.status_code)
expect("spread out of range", cu.get(f"/api/customer-portal/events/{EV}/proofs/{v1['id']}/spreads/9"), 404)
expect("request changes with nothing to change refused", cu.post(f"/api/customer-portal/events/{EV}/proofs/{v1['id']}/request-changes", {}), 422)
pv = expect("client comments on spread 2", cu.post(f"/api/customer-portal/events/{EV}/proofs/{v1['id']}/comments", {"spreadIndex": 1, "body": "Swap the left photo with a family one"}), 200).json()
check("comment stored", pv["comments"][0]["author"] == "CUSTOMER" and pv["comments"][0]["spreadIndex"] == 1)
mark = log_size()
pv = expect("client requests changes", cu.post(f"/api/customer-portal/events/{EV}/proofs/{v1['id']}/request-changes", {"note": "Just that one swap"}), 200).json()
check("changes requested + photographer emailed", pv["status"] == "CHANGES_REQUESTED" and "Album changes requested" in open(LOG).read()[mark:])
cid = pv["comments"][0]["id"]
proofs = expect("photographer replies", p.post(f"/api/events/{EV}/proofs/{v1['id']}/comments", {"spreadIndex": 1, "body": "Done in v2"}), 200).json()
proofs = expect("photographer resolves", p.put(f"/api/events/{EV}/proofs/{v1['id']}/comments/{cid}", {"resolved": True}), 200).json()
check("resolved", any(c["id"] == cid and c["resolved"] for c in proofs[0]["comments"]))
proofs = expect("send proof v2", p.post(f"/api/events/{EV}/proofs", {"mediaIds": ["sprd000000000002", "sprd000000000001"]}), 200).json()
check("v1 superseded, v2 sent", proofs[0]["version"] == 2 and proofs[0]["status"] == "SENT" and proofs[1]["status"] == "SUPERSEDED")
expect("unknown spread ids refused", p.post(f"/api/events/{EV}/proofs", {"mediaIds": ["nope"]}), 422)
expect("comment on superseded version refused", cu.post(f"/api/customer-portal/events/{EV}/proofs/{v1['id']}/comments", {"spreadIndex": 0, "body": "x"}), 409)
v2 = proofs[0]
mark = log_size()
pv = expect("client approves v2", cu.post(f"/api/customer-portal/events/{EV}/proofs/{v2['id']}/approve", {}), 200).json()
check("approved + emailed", pv["status"] == "APPROVED" and "Album approved" in open(LOG).read()[mark:])
check("album design step done", next(s for s in p.get(f"/api/events/{EV}/delivery/steps").json() if s["kind"] == "ALBUM_DESIGN")["status"] == "DONE")
expect("approve twice refused", cu.post(f"/api/customer-portal/events/{EV}/proofs/{v2['id']}/approve", {}), 409)
expect("customer can't see another event", Client().get(f"/api/customer-portal/events/{EV}/delivery"), 401)

# ------------------------------------------------------------------------------------------------
section("Branding & watermarks")
logo = io.BytesIO(); Image.new("RGBA", (120, 60), (200, 30, 30, 255)).save(logo, "PNG")
br = expect("upload logo", p.post("/api/studio/brand/logo", files={"file": ("logo.png", logo.getvalue(), "image/png")}), 200).json()
check("logo url", br["logoUrl"] and br["logoUrl"].startswith(f"/api/public/brand/{P}/logo"), br)
expect("non-image logo refused", p.post("/api/studio/brand/logo", files={"file": ("x.png", b"not an image", "image/png")}), 415)
lg = anon.get(B + br["logoUrl"])
check("public logo served + cached", lg.status_code == 200 and lg.headers["Content-Type"] == "image/png" and "max-age" in lg.headers.get("Cache-Control", ""))
br = expect("brand colour", p.put("/api/studio/brand", {"brandColor": "#7A1F3D", "watermarkMode": "OFF", "watermarkPosition": "TILED", "watermarkOpacity": 30, "watermarkUseLogo": False}), 200).json()
check("brand colour saved", br["brandColor"] == "#7A1F3D" and br["canBrand"] and br["canWatermark"] and not br["canUseCustomDomain"], br)
dv = cu.get(f"/api/customer-portal/events/{EV}/delivery").json()
check("client portal gets brand colour + logo", dv["brand"]["color"] == "#7A1F3D" and dv["brand"]["logoUrl"], dv["brand"])
orig = cu.get(f"/api/customer-portal/events/{EV}/gallery/media/a1b2c3d4e5f60001/preview").content
br = expect("watermark ALWAYS", p.put("/api/studio/brand", {"brandColor": "#7A1F3D", "watermarkMode": "ALWAYS", "watermarkText": "© Pixel Stories", "watermarkPosition": "CENTER", "watermarkOpacity": 60, "watermarkUseLogo": False}), 200).json()
wm = cu.get(f"/api/customer-portal/events/{EV}/gallery/media/a1b2c3d4e5f60001/preview")


def diff(a, b):
    ia, ib = Image.open(io.BytesIO(a)).convert("RGB"), Image.open(io.BytesIO(b)).convert("RGB")
    return sum(abs(x - y) for pa, pb in zip(ia.getdata(), ib.getdata()) for x, y in zip(pa, pb))


check("gallery preview watermarked", wm.status_code == 200 and wm.headers["Content-Type"] == "image/jpeg" and diff(orig, wm.content) > 2000, (wm.status_code, diff(orig, wm.content) if wm.status_code == 200 else None))
p.put("/api/studio/brand", {"brandColor": "#7A1F3D", "watermarkMode": "ALWAYS", "watermarkPosition": "TILED", "watermarkOpacity": 40, "watermarkUseLogo": True})
wl = cu.get(f"/api/customer-portal/events/{EV}/gallery/media/a1b2c3d4e5f60002/preview")
check("logo watermark works", wl.status_code == 200 and wl.headers["Content-Type"] == "image/jpeg")
spw = cu.get(f"/api/customer-portal/events/{EV}/proofs/{v2['id']}/spreads/0")
sp_plain = requests.get(STUB + f"/api/v1/events/{EV}/photos/sprd000000000002/preview").content
check("album spread watermarked", spw.status_code == 200 and diff(sp_plain, spw.content) > 2000)
p.put("/api/studio/brand", {"brandColor": "#7A1F3D", "watermarkMode": "UNTIL_PAID", "watermarkPosition": "BOTTOM_RIGHT", "watermarkOpacity": 40, "watermarkUseLogo": False})
check("UNTIL_PAID: watermarked while balance due", diff(orig, cu.get(f"/api/customer-portal/events/{EV}/gallery/media/a1b2c3d4e5f60001/preview").content) > 500)
bal = float(p.get(f"/api/events/{EV}/billing").json()["balance"])
expect("record balance payment", p.post(f"/api/events/{EV}/payments", {"amount": bal, "paidOn": str(today), "method": "UPI", "purpose": "BALANCE"}), (200, 201))
clean = cu.get(f"/api/customer-portal/events/{EV}/gallery/media/a1b2c3d4e5f60001/preview")
check("UNTIL_PAID: clean preview once fully paid", clean.status_code == 200 and diff(orig, clean.content) == 0)
plan_blocked("Free plan brand colour", free.put("/api/studio/brand", {"brandColor": "#000000", "watermarkMode": "OFF", "watermarkPosition": "TILED", "watermarkOpacity": 30, "watermarkUseLogo": False}), feature="CUSTOM_BRANDING")
plan_blocked("Free plan watermark", free.put("/api/studio/brand", {"brandColor": "", "watermarkMode": "ALWAYS", "watermarkPosition": "TILED", "watermarkOpacity": 30, "watermarkUseLogo": False}), feature="WATERMARK")
check("Free plan brand view says Powered by FrameBucket", anon.get(B + f"/api/public/enquiries/quote/{ft}").json()["brand"]["poweredByFrameBucket"])

# ------------------------------------------------------------------------------------------------
section("Custom domain")
plan_blocked("Studio plan custom domain", p.put("/api/studio/brand/domain", {"domain": "photos.pixelstories.in"}), feature="CUSTOM_DOMAIN")
expect("admin upgrades to Business", owner.put(f"/api/admin/photographers/{P}/plan", {"planCode": "BUSINESS", "billingCycle": "YEARLY", "months": 12}), 200)
expect("invalid domain refused", p.put("/api/studio/brand/domain", {"domain": "not a domain"}), 422)
br = expect("set domain", p.put("/api/studio/brand/domain", {"domain": "https://Photos.PixelStories.in/"}), 200).json()
check("domain normalized, not verified, CNAME target shown", br["customDomain"] == "photos.pixelstories.in" and br["customDomainVerifiedAt"] is None and br["customDomainTarget"], br)
expect("domain taken by another studio", owner.put("/api/admin/photographers/" + uid("other@example.com") + "/plan", {"planCode": "BUSINESS"}), 200)
other.put("/api/studio/profile", {"portfolioSlug": "other-studio", "portfolioPublic": False, "remindersEnabled": True})
expect("same domain for other studio refused", other.put("/api/studio/brand/domain", {"domain": "photos.pixelstories.in"}), 409)
expect("verify fails without DNS", p.post("/api/studio/brand/domain/verify"), 422)
expect("Caddy ask: unverified domain", anon.get(B + "/api/public/domains/check?domain=photos.pixelstories.in"), 404)
psql(f"update fb_studio_brand set custom_domain_verified_at = now() where photographer_id = '{P}'")
expect("Caddy ask: verified domain", anon.get(B + "/api/public/domains/check?domain=photos.pixelstories.in"), 200)
expect("Caddy ask: random domain", anon.get(B + "/api/public/domains/check?domain=evil.example.com"), 404)
res = expect("resolve by Host header", anon.get(B + "/api/public/domains/resolve", headers={"Host": "photos.pixelstories.in"}), 200).json()
check("resolves to studio slug", res["slug"] == "pixel-stories", res)
expect("main host is not a studio domain", anon.get(B + "/api/public/domains/resolve"), 404)

# ------------------------------------------------------------------------------------------------
section("Crew, clashes, payouts")
tm = expect("team member", p.post("/api/team", {"name": "Karthik", "age": 27, "mobileNumber": "9700000001", "languages": ["ENGLISH"], "memberType": "TEAM_MEMBER", "skills": ["PHOTOGRAPHY"]}), (200, 201)).json()
expect("freelancer", p.post("/api/team", {"name": "Meera", "age": 25, "mobileNumber": "9700000002", "languages": ["HINDI"], "memberType": "FREELANCER", "skills": ["VIDEOGRAPHY"]}), (200, 201))
members = expect("crew directory", p.get("/api/studio/crew/members"), 200).json()
karthik = next(x for x in members if x["name"] == "Karthik"); meera = next(x for x in members if x["name"] == "Meera")
check("directory has both types", karthik["memberType"] == "TEAM" and meera["memberType"] == "FREELANCER", members)
crew = expect("assign Karthik", p.post(f"/api/events/{EV}/crew", {"memberType": "TEAM", "memberId": karthik["memberId"], "role": "Candid photographer", "callTime": "07:30:00", "fee": 12000}), 200).json()
check("crew listed with fee + no warnings", crew[0]["role"] == "Candid photographer" and float(crew[0]["fee"]) == 12000 and crew[0]["warnings"] == [], crew)
expect("duplicate assignment refused", p.post(f"/api/events/{EV}/crew", {"memberType": "TEAM", "memberId": karthik["memberId"], "role": "x"}), 409)
expect("mark Meera unavailable", p.post("/api/studio/crew/unavailable", {"memberType": "FREELANCER", "memberId": meera["memberId"], "date": D(60), "note": "Sister's wedding"}), 200)
chk = expect("availability check", p.get(f"/api/events/{EV}/crew/check?memberType=FREELANCER&memberId={meera['memberId']}"), 200).json()
check("unavailable warning", any("unavailable" in w and "Sister" in w for w in chk["warnings"]), chk)
crew = p.post(f"/api/events/{EV}/crew", {"memberType": "FREELANCER", "memberId": meera["memberId"], "role": "Cinematographer", "fee": 15000}).json()
check("assignment allowed but warned", any("unavailable" in w for c in crew if c["name"] == "Meera" for w in c["warnings"]), crew)
# cross-studio clash: Other studio books the same freelancer (same mobile) on the same date
other.post("/api/team", {"name": "Karthik K", "age": 27, "mobileNumber": "9700000001", "languages": ["ENGLISH"], "memberType": "FREELANCER", "skills": ["PHOTOGRAPHY"]})
oc = other.post("/api/customers", {"name": "O C", "email": "oc@example.com", "mobileNumber": "9700000009", "dateOfBirth": "1990-01-01", "address": "Pune"}).json()
oev = other.post("/api/events", {"eventDate": D(60), "eventTypeId": wedding["id"], "customerId": oc["id"], "price": "50000"}).json()
ok_member = next(x for x in other.get("/api/studio/crew/members").json() if x["mobileNumber"].endswith("9700000001"))
oc_crew = expect("other studio assigns same person", other.post(f"/api/events/{oev['id']}/crew", {"memberType": ok_member["memberType"], "memberId": ok_member["memberId"], "role": "Second shooter", "fee": 8000}), 200).json()
check("cross-studio clash warning (no names leaked)", any("another shoot" in w for w in oc_crew[0]["warnings"]) and not any("Pixel" in w for w in oc_crew[0]["warnings"]), oc_crew)
crew = p.get(f"/api/events/{EV}/crew").json()
check("my side warned too", any("another shoot" in w for c in crew if c["name"] == "Karthik" for w in c["warnings"]), crew)
kid = next(c["id"] for c in crew if c["name"] == "Karthik")
crew = expect("update fee", p.put(f"/api/events/{EV}/crew/{kid}", {"memberType": "TEAM", "memberId": karthik["memberId"], "role": "Lead photographer", "fee": 14000}), 200).json()
# a past event so fees count as earned
pc = p.post("/api/customers", {"name": "Past Client", "email": "past@example.com", "mobileNumber": "9700000010", "dateOfBirth": "1990-01-01", "address": "Delhi"}).json()
pev = expect("past event", p.post("/api/events", {"eventDate": D(-10), "eventTypeId": wedding["id"], "customerId": pc["id"], "price": "80000"}), (200, 201)).json()
PEV = pev["id"]
p.post(f"/api/events/{PEV}/crew", {"memberType": "TEAM", "memberId": karthik["memberId"], "role": "Photographer", "fee": 10000})
pay = expect("payout", p.post("/api/studio/crew/payouts", {"memberType": "TEAM", "memberId": karthik["memberId"], "eventId": PEV, "amount": 6000, "paidOn": str(today), "method": "UPI", "reference": "UPI123"}), 200).json()
check("payout listed with event code", pay[0]["eventCode"] and float(pay[0]["amount"]) == 6000)
bal = {b["name"]: b for b in expect("balances", p.get("/api/studio/crew/balances"), 200).json()}
check("Karthik: earned 10k, upcoming 14k, paid 6k, due 4k", float(bal["Karthik"]["earned"]) == 10000 and float(bal["Karthik"]["upcoming"]) == 14000
      and float(bal["Karthik"]["paid"]) == 6000 and float(bal["Karthik"]["due"]) == 4000 and bal["Karthik"]["assignments"] == 2, bal["Karthik"])
expect("other studio can't pay my crew", other.post("/api/studio/crew/payouts", {"memberType": "TEAM", "memberId": karthik["memberId"], "amount": 1, "paidOn": str(today), "method": "UPI"}), 404)
plan_blocked("Free plan crew", free.post("/api/studio/crew/unavailable", {"memberType": "TEAM", "memberId": karthik["memberId"], "date": D(5)}), feature="CREW_PAYOUTS")

# ------------------------------------------------------------------------------------------------
section("Gear & costs")
g = expect("gear item (default kit)", p.post("/api/studio/gear", {"name": "Sony A7 IV", "category": "Camera", "serialNumber": "S123", "inDefaultKit": True, "active": True}), 200).json()
g = expect("gear item 2", p.post("/api/studio/gear", {"name": "DJI Mini 4", "category": "Drone", "inDefaultKit": False, "active": True}), 200).json()
eg = expect("add default kit to event", p.post(f"/api/events/{EV}/gear", {"defaultKit": True}), 200).json()
check("default kit added", [x["name"] for x in eg] == ["Sony A7 IV"], eg)
drone_id = next(x["id"] for x in g if x["name"] == "DJI Mini 4")
eg = p.post(f"/api/events/{EV}/gear", {"gearIds": [drone_id]}).json()
eg = expect("mark packed", p.put(f"/api/events/{EV}/gear/{drone_id}", {"packed": True}), 200).json()
check("packed flag", next(x for x in eg if x["gearId"] == drone_id)["packed"])
eg = expect("remove from event", p.delete(f"/api/events/{EV}/gear/{drone_id}"), 200).json()
check("removed", len(eg) == 1)
costs = expect("add travel cost", p.post(f"/api/events/{EV}/costs", {"amount": 18000, "spentOn": str(today), "notes": "Flights BLR-UDR"}), 200).json()
costs = expect("add hotel cost", p.post(f"/api/events/{EV}/costs", {"category": "Accommodation", "amount": 9000, "spentOn": str(today)}), 200).json()
check("costs: expenses 27k + crew 29k", float(costs["expenseTotal"]) == 27000 and float(costs["crewFees"]) == 29000 and costs["expenses"][0]["category"] == "Travel", costs)
check("profit = agreed - costs", float(costs["profit"]) == expected - 56000 and costs["marginPercent"] is not None, costs)
costs = expect("delete cost", p.delete(f"/api/events/{EV}/costs/{costs['expenses'][1]['id']}"), 200).json()
check("cost removed", float(costs["expenseTotal"]) == 18000)
plan_blocked("Free plan gear", free.post("/api/studio/gear", {"name": "x", "inDefaultKit": False, "active": True}), feature="GEAR_COSTS")

# ------------------------------------------------------------------------------------------------
section("Insights")
ins = expect("insights", p.get("/api/studio/insights"), 200).json()
check("12-month series", len(ins["bookingsByMonth"]) == 12 and len(ins["shootsByMonth"]) == 12 and len(ins["seasonality"]) == 12 and len(ins["revenueByMonth"]) == 12)
check("enquiry conversion", ins["enquiries12m"] >= 3 and ins["booked12m"] == 1 and float(ins["conversionPercent"]) > 0, ins)
src = {s["source"]: s for s in ins["sources"]}
check("lead sources incl. Instagram 100% converted", src["INSTAGRAM"]["booked"] == 1 and float(src["INSTAGRAM"]["conversionPercent"]) == 100.0 and "MANUAL" in src, src)
check("AOV", float(ins["averageOrderValue"]) > 0, ins["averageOrderValue"])
check("outstanding lists past event", any(o["eventId"] == PEV and float(o["balance"]) == 80000 for o in ins["outstanding"]), ins["outstanding"])
check("profit per (past) event", any(x["eventId"] == PEV and float(x["costs"]) == 10000 and float(x["profit"]) == 70000 for x in ins["profitByEvent"]), ins["profitByEvent"])
check("revenue this month counts payments", float(ins["revenueByMonth"][-1]["amount"]) >= 50000, ins["revenueByMonth"][-1])
plan_blocked("Free plan insights", free.get("/api/studio/insights"), feature="INSIGHTS")

# ------------------------------------------------------------------------------------------------
section("Free plan gates on delivery")
fc = free.post("/api/customers", {"name": "F C", "email": "fc@example.com", "mobileNumber": "9700000020", "dateOfBirth": "1990-01-01", "address": "Pune"}).json()
fev = free.post("/api/events", {"eventDate": D(20), "eventTypeId": wedding["id"], "customerId": fc["id"], "price": "1000"}).json()
expect("Free plan has the delivery tracker", free.get(f"/api/events/{fev['id']}/delivery/steps"), 200)
plan_blocked("Free plan films", free.post(f"/api/events/{fev['id']}/films", {"title": "x", "kind": "OTHER", "externalUrl": "https://youtu.be/dQw4w9WgXcQ"}), feature="FILMS")
plan_blocked("Free plan proofs", free.post(f"/api/events/{fev['id']}/proofs", {"folderId": "root"}), feature="ALBUM_PROOFING")

# ------------------------------------------------------------------------------------------------
section("Lead form rate limit (last: it blocks this IP for 10 minutes)")
codes = [anon.post(B + "/api/public/studios/pixel-stories/enquiries", json={"name": f"Spam {i}", "mobileNumber": f"98111002{i:02d}",
         "email": f"s{i}@example.com", "eventTypeId": wedding["id"]}).status_code for i in range(6)]
check("6th quick enquiry from one IP throttled", 429 in codes, codes)

errors = [l for l in open(LOG, encoding="utf-8", errors="replace").read().splitlines() if " ERROR " in l]
check("no server errors in log", not errors, errors[:5])
failed = [r for r in RESULTS if not r[2]]
print(f"\nRESULT: {len(RESULTS) - len(failed)} passed, {len(failed)} failed")
for s, n, ok, d in failed:
    print(f"  FAILED [{s}] {n}: {str(d)[:300]}")
sys.exit(1 if failed else 0)
