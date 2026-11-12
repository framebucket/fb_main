"""FrameBucket end-to-end API suite: drives the real Spring Boot backend + Postgres over HTTP."""
import os
OUT = os.getenv("FB_E2E_OUT", "out"); os.makedirs(OUT, exist_ok=True)
import datetime as dt, io, json, re, subprocess, sys, time, zipfile
import requests

B = os.getenv("FB_BASE_URL", "http://localhost:18080")
STUB = os.getenv("FB_STUB_URL", "http://127.0.0.1:8001")
LOG = os.getenv("FB_APP_LOG", "backend.log")
RESULTS = []
SECTION = ["setup"]


def section(name):
    SECTION[0] = name
    print(f"\n== {name}")


def check(name, ok, detail=""):
    RESULTS.append((SECTION[0], name, bool(ok), detail))
    print(("  PASS " if ok else "  FAIL ") + name + ("" if ok else f"  -> {str(detail)[:300]}"))
    return ok


class Client:
    def __init__(self):
        self.s = requests.Session()

    def _csrf(self):
        if not self.s.cookies.get("XSRF-TOKEN"):
            self.s.get(B + "/api/auth/csrf")
        return {"X-XSRF-TOKEN": self.s.cookies.get("XSRF-TOKEN")}

    def req(self, method, path, body=None, files=None, data=None):
        headers = self._csrf() if method != "GET" else {}
        return self.s.request(method, B + path, json=body, files=files, data=data, headers=headers, allow_redirects=False)

    def get(self, p): return self.req("GET", p)
    def post(self, p, b=None, **k): return self.req("POST", p, b, **k)
    def put(self, p, b=None): return self.req("PUT", p, b)
    def delete(self, p): return self.req("DELETE", p)


def expect(name, r, status):
    statuses = status if isinstance(status, tuple) else (status,)
    check(f"{name} [{r.status_code}]", r.status_code in statuses, r.text)
    return r


def log_find(pattern, after=0):
    text = open(LOG, encoding="utf-8", errors="replace").read()[after:]
    found = re.findall(pattern, text)
    return found[-1] if found else None


def log_size():
    return len(open(LOG, encoding="utf-8", errors="replace").read())


def psql(sql):
    return subprocess.run(["psql", "-Atc", sql],
                          capture_output=True, text=True).stdout.strip()


today = dt.date.today()
TOMORROW = (today + dt.timedelta(days=1)).isoformat()
D = lambda n: (today + dt.timedelta(days=n)).isoformat()
PASSWORD = "Sup3rSecret!!xyz"


def register_photographer(c, name, studio, email, mobile):
    mark = log_size()
    r = expect(f"register {email}", c.post("/api/auth/register", {"fullName": name, "studioName": studio, "email": email,
               "mobileNumber": mobile, "password": PASSWORD, "confirmPassword": PASSWORD}), 201 if False else (200, 201))
    time.sleep(0.5)
    token = log_find(r"verify-email\?token=(\S+)", mark)
    code = log_find(rf"code for {mobile}: (\d+)", mark)
    expect("login blocked before verification", c.post("/api/auth/login", {"username": email, "password": PASSWORD}), (400, 401, 403))
    expect("verify email", c.post("/api/auth/verify-email", {"token": token}), 200)
    expect("verify mobile", c.post("/api/auth/verify-mobile", {"identifier": email, "code": code}), 200)
    expect("wrong password rejected", c.post("/api/auth/login", {"username": email, "password": "wrong-password-123"}), (400, 401))
    r = expect("login", c.post("/api/auth/login", {"username": email, "password": PASSWORD}), 200)
    return r.json()


# ---------------------------------------------------------------------------------------------
section("1. Auth & subscription plans")
anon = Client()
expect("public plans", anon.get("/api/subscriptions"), 200)
expect("public stats", anon.get("/api/public/stats"), 200)
expect("dashboard needs login", anon.get("/api/dashboard/summary"), 401)
expect("CSRF required for state change", requests.post(B + "/api/auth/login", json={"username": "x", "password": "y"}), 403)
ph = Client()
me = register_photographer(ph, "Asha Rao", "Lens & Light", "asha@example.com", "9876543210")
expect("me", ph.get("/api/auth/me"), 200)
check("data module enabled on trial", me.get("dataModuleEnabled") is True, me)
other = Client()
register_photographer(other, "Ravi Iyer", "Ravi Clicks", "ravi@example.com", "9876500000")

# ---------------------------------------------------------------------------------------------
section("4. Client management (CRM)")
r = expect("create customer", ph.post("/api/customers", {"name": "Priya Sharma", "email": "priya@example.com", "mobileNumber": "9123456780",
           "dateOfBirth": "1995-04-02", "address": "Bengaluru"}), 201)
cust = r.json()
expect("invalid customer rejected", ph.post("/api/customers", {"name": "", "email": "bad", "mobileNumber": "1", "dateOfBirth": "2999-01-01", "address": ""}), 400)
r = expect("list customers", ph.get("/api/customers"), 200)
check("customer listed", any(x["id"] == cust["id"] for x in r.json()))
expect("customer detail", ph.get(f"/api/customers/{cust['id']}"), 200)
expect("other photographer cannot see customer", other.get(f"/api/customers/{cust['id']}"), (403, 404))

# ---------------------------------------------------------------------------------------------
section("5. Team management")
r = expect("create team member", ph.post("/api/team", {"name": "Karthik", "age": 27, "mobileNumber": "9000000001", "languages": ["ENGLISH", "KANNADA"],
           "memberType": "TEAM_MEMBER", "skills": ["CANDID_PHOTOGRAPHY"]}), (200, 201))
member = r.json()
mark = log_size()
r = expect("invite freelancer", ph.post("/api/team/invitations", {"name": "Meera", "mobileNumber": "9000000002", "memberType": "FREELANCER"}), (200, 201))
expect("list team", ph.get("/api/team"), 200)
time.sleep(0.5)
invite = log_find(r"team-invite/([A-Za-z0-9_\-]+)", mark)
if check("invite link sent", invite, "no invite link in log"):
    expect("public invite lookup", anon.get(f"/api/public/team-invites/{invite}"), 200)
    expect("complete invite", anon.put(f"/api/public/team-invites/{invite}", {"name": "Meera K", "age": 30, "languages": ["HINDI"], "skills": ["VIDEOGRAPHY"]}), 200)

# ---------------------------------------------------------------------------------------------
section("6. Quotations & booking (enquiry -> quote -> event)")
types = expect("event types", ph.get("/api/events/types"), 200).json()
wedding = next(t for t in types if t["name"] == "Wedding")
expect("custom event type", ph.post("/api/events/types", {"name": "Graduation"}), (200, 201))
mark = log_size()
enq = expect("create enquiry", ph.post("/api/enquiries", {"name": "Neha Gupta", "mobileNumber": "9555500001", "email": "neha@example.com", "eventTypeId": wedding["id"]}), (200, 201)).json()
expect("list enquiries", ph.get("/api/enquiries"), 200)
time.sleep(0.5)
qtoken = log_find(r"/quote/([A-Za-z0-9_\-]+)", mark)
if check("quote link sent", qtoken, "no quote link in log"):
    ctx = expect("public quote context", anon.get(f"/api/public/enquiries/quote/{qtoken}"), 200).json()
    services = [s["id"] for s in ctx["services"]][:2]
    form = {"venue": "Palace Grounds", "eventDate": D(40), "preferredTime": "Evening", "events": [{"eventTypeId": wedding["id"], "serviceIds": services}], "albumSheets": 30}
    calc = expect("calculate quote", anon.post(f"/api/public/enquiries/quote/{qtoken}/calculate", form), 200).json()
    check("quote price positive", float(calc["finalPrice"]) > 0, calc)
    expect("submit quote", anon.post(f"/api/public/enquiries/quote/{qtoken}/submit", form), (200, 201))
    pdf = anon.get(f"/api/public/enquiries/quote/{qtoken}/pdf")
    check("quote PDF", pdf.status_code == 200 and pdf.content[:5] == b"%PDF-", pdf.status_code)
expect("finalize quote", ph.put(f"/api/enquiries/{enq['id']}/finalized-quote", {"finalizedQuote": 245000}), 200)
expect("quotation pdf (photographer)", ph.get(f"/api/enquiries/{enq['id']}/quotation.pdf"), 200)
expect("event setup", ph.get(f"/api/enquiries/{enq['id']}/event-setup"), 200)
ev2 = expect("create event from enquiry", ph.post(f"/api/enquiries/{enq['id']}/create-event", {"eventDate": D(40), "dateOfBirth": "1996-01-01", "address": "Mysuru"}), (200, 201)).json()

# ---------------------------------------------------------------------------------------------
section("13. Event booking & calendar")
expect("next event id", ph.get("/api/events/next-id"), 200)
ev = expect("create event (tomorrow)", ph.post("/api/events", {"eventDate": TOMORROW, "eventTypeId": wedding["id"], "customerId": cust["id"], "price": "₹1,50,000"}), (200, 201)).json()
E = ev["id"]
expect("double booking same date rejected", ph.post("/api/events", {"eventDate": TOMORROW, "eventTypeId": wedding["id"], "customerId": cust["id"], "price": "1000"}), (400, 409, 422))
blocked = expect("blocked dates", ph.get("/api/events/blocked-dates"), 200).json()
check("tomorrow blocked", TOMORROW in blocked, blocked)
expect("assign team member", ph.put(f"/api/events/{E}/assignee", {"assigneeType": "TEAM_MEMBER", "assigneeId": member["id"]}), 200)
events = expect("list events", ph.get("/api/events"), 200).json()
check("events listed with assignee", any(x["id"] == E and x["assigneeName"] == "Karthik" for x in events))
expect("other photographer cannot touch event", other.get(f"/api/events/{E}/billing"), 404)

# ---------------------------------------------------------------------------------------------
section("3. Event QR & guest registration")
qr = expect("event QR png", ph.get(f"/api/events/{E}/qr-code.png"), 200)
check("QR is PNG", qr.content[:8] == b"\x89PNG\r\n\x1a\n")
gtoken = psql(f"select guest_access_token from fb_event_booking where id='{E}'")
expect("public event gallery page", anon.get(f"/api/public/event-galleries/{gtoken}"), 200)
expect("unknown guest token", anon.get("/api/public/event-galleries/nope-nope-nope"), 404)
expect("guest registration without consent rejected",
       anon.post(f"/api/public/event-galleries/{gtoken}/guests", files={"selfie": ("s.jpg", b"notajpeg", "image/jpeg")},
                 data={"name": "Guest", "mobileNumber": "9888800001", "faceConsent": "false", "whatsappConsent": "false"}), (400, 422))

# ---------------------------------------------------------------------------------------------
section("Customer portal login")
cu = Client()
mark = log_size()
expect("request customer OTP", cu.post("/api/customer-auth/request-code", {"mobileNumber": "9123456780"}), 200)
time.sleep(0.5)
text = open(LOG, encoding="utf-8", errors="replace").read()[mark:]
codes = re.findall(r"\b(\d{6})\b", text)
check("OTP delivered", codes, "no code")
expect("wrong OTP rejected", cu.post("/api/customer-auth/verify-code", {"mobileNumber": "9123456780", "code": "000000"}), (400, 401))
expect("verify customer OTP", cu.post("/api/customer-auth/verify-code", {"mobileNumber": "9123456780", "code": codes[-1]}), 200)
cevents = expect("customer events", cu.get("/api/customer-portal/events"), 200).json()
check("customer sees booked event", any(x["eventId"] == E for x in cevents), cevents)
expect("customer shares location", cu.put(f"/api/customer-portal/events/{E}/location", {"locationName": "Taj West End", "locationMapUrl": "", "landmark": "Race Course Rd", "notes": "Gate 2", "eventTime": "17:30:00"}), 200)
expect("customer blocked from photographer API", cu.get("/api/events"), 403)

# ---------------------------------------------------------------------------------------------
section("7 & 1. Online galleries & photo selection (via data-service stand-in)")
folders = expect("photographer folders", ph.get(f"/api/events/{E}/gallery/folders"), 200).json()
media = expect("photographer media", ph.get(f"/api/events/{E}/gallery/media"), 200).json()
PHOTOS = [p["photo_id"] for p in media["photos"]]
check("3 photos listed", len(PHOTOS) == 3, media)
prev = expect("photographer preview", ph.get(f"/api/events/{E}/gallery/media/{PHOTOS[0]}/preview"), 200)
check("preview is JPEG", prev.content[:2] == b"\xff\xd8")
expect("customer media", cu.get(f"/api/customer-portal/events/{E}/gallery/media"), 200)
expect("customer selection get", cu.get(f"/api/customer-portal/events/{E}/gallery/selection"), 200)
expect("customer selection save", cu.put(f"/api/customer-portal/events/{E}/gallery/selection", {"photoIds": PHOTOS[:2]}), 200)
big = [f"{i:016x}" for i in range(6000)]
expect("selection > 5,000 photos accepted by Java (cap now 50,000)", cu.put(f"/api/customer-portal/events/{E}/gallery/selection", {"photoIds": big}), 200)
expect("selection > 50,000 rejected", cu.put(f"/api/customer-portal/events/{E}/gallery/selection", {"photoIds": [f"{i:x}" for i in range(50001)]}), 400)
dl = expect("free download (default policy)", cu.post(f"/api/customer-portal/events/{E}/gallery/download", {"photoIds": []}), 200)
check("download is a ZIP", zipfile.is_zipfile(io.BytesIO(dl.content)))

# ---------------------------------------------------------------------------------------------
section("Studio settings")
expect("invalid UPI rejected", ph.put("/api/studio/profile", {"upiId": "not a upi", "portfolioPublic": False, "remindersEnabled": True}), 400)
expect("publish without slug rejected", ph.put("/api/studio/profile", {"portfolioPublic": True, "remindersEnabled": True}), 422)
prof = expect("save profile", ph.put("/api/studio/profile", {"upiId": "lenslight@okhdfcbank", "upiPayeeName": "Lens & Light", "businessAddress": "12 MG Road, Bengaluru",
              "gstin": "29abcde1234f1z5", "receiptFooter": "Thank you!", "portfolioSlug": "lens-light", "portfolioHeadline": "Wedding stories",
              "portfolioBio": "Candid weddings.", "portfolioPublic": True, "remindersEnabled": True}), 200).json()
check("GSTIN upper-cased", prof["gstin"] == "29ABCDE1234F1Z5", prof)
expect("slug taken by another studio", other.put("/api/studio/profile", {"portfolioSlug": "lens-light", "portfolioPublic": False, "remindersEnabled": True}), 409)

# ---------------------------------------------------------------------------------------------
section("12. Payment receipts & billing")
b = expect("event billing", ph.get(f"/api/events/{E}/billing"), 200).json()
check("total parsed from '₹1,50,000'", float(b["total"]) == 150000, b)
p1 = expect("record advance", ph.post(f"/api/events/{E}/payments", {"amount": 50000, "paidOn": today.isoformat(), "method": "UPI", "purpose": "ADVANCE", "reference": "UTR123"}), 201).json()
p2 = expect("record cash", ph.post(f"/api/events/{E}/payments", {"amount": 25000, "paidOn": today.isoformat(), "method": "CASH", "purpose": "BALANCE"}), 201).json()
check("receipt numbers sequential", (p1["receiptNumber"], p2["receiptNumber"]) == ("RCPT-00001", "RCPT-00002"), (p1, p2))
expect("zero amount rejected", ph.post(f"/api/events/{E}/payments", {"amount": 0, "paidOn": today.isoformat(), "method": "UPI", "purpose": "ADVANCE"}), 400)
b = ph.get(f"/api/events/{E}/billing").json()
check("balance = 75,000", float(b["balance"]) == 75000, b)
rc = expect("receipt PDF", ph.get(f"/api/events/{E}/payments/{p1['id']}/receipt.pdf"), 200)
check("receipt is PDF", rc.content[:5] == b"%PDF-")
open(os.path.join(OUT, "suite-receipt.pdf"), "wb").write(rc.content)
tmp = expect("record + delete payment", ph.post(f"/api/events/{E}/payments", {"amount": 10, "paidOn": today.isoformat(), "method": "OTHER", "purpose": "OTHER"}), 201).json()
expect("delete payment", ph.delete(f"/api/events/{E}/payments/{tmp['id']}"), 204)
expect("other studio cannot read receipt", other.get(f"/api/events/{E}/payments/{p1['id']}/receipt.pdf"), 404)

# ---------------------------------------------------------------------------------------------
section("9. Payment QR codes (UPI)")
q = expect("photographer UPI QR (balance)", ph.get(f"/api/events/{E}/payment-qr.png"), 200)
check("UPI QR is PNG", q.content[:4] == b"\x89PNG")
open(os.path.join(OUT, "suite-upi.png"), "wb").write(q.content)
expect("photographer UPI QR custom amount", ph.get(f"/api/events/{E}/payment-qr.png?amount=5000"), 200)
expect("customer UPI QR for balance", cu.get(f"/api/customer-portal/events/{E}/payment-qr.png?purpose=BALANCE"), 200)
expect("customer download QR when downloads free -> 409", cu.get(f"/api/customer-portal/events/{E}/payment-qr.png?purpose=DOWNLOAD"), 409)
oc = other.post("/api/customers", {"name": "X Y", "email": "xy@example.com", "mobileNumber": "9111100001", "dateOfBirth": "1990-01-01", "address": "Pune"}).json()
oev = other.post("/api/events", {"eventDate": D(10), "eventTypeId": wedding["id"], "customerId": oc["id"], "price": "20000"}).json()
expect("QR when studio has no UPI ID -> 409", other.get(f"/api/events/{oev['id']}/payment-qr.png"), 409)

# ---------------------------------------------------------------------------------------------
section("8. Free & paid downloads")
expect("paid without price rejected", ph.put(f"/api/events/{E}/download-policy", {"mode": "PAID", "unlocked": False}), 422)
expect("set paid downloads ₹999", ph.put(f"/api/events/{E}/download-policy", {"mode": "PAID", "price": 999, "unlocked": False}), 200)
expect("customer download blocked (402)", cu.post(f"/api/customer-portal/events/{E}/gallery/download", {"photoIds": []}), 402)
expect("customer download-fee QR", cu.get(f"/api/customer-portal/events/{E}/payment-qr.png?purpose=DOWNLOAD"), 200)
cb = expect("customer billing view", cu.get(f"/api/customer-portal/events/{E}/billing"), 200).json()
check("customer sees locked downloads + balance", cb["downloadPolicy"]["unlocked"] is False and float(cb["balance"]) == 75000, cb)
expect("record download payment", ph.post(f"/api/events/{E}/payments", {"amount": 999, "paidOn": today.isoformat(), "method": "UPI", "purpose": "DOWNLOAD"}), 201)
b = ph.get(f"/api/events/{E}/billing").json()
check("download payment auto-unlocks", b["downloadPolicy"]["unlocked"] is True, b["downloadPolicy"])
check("download fee doesn't reduce event balance", float(b["balance"]) == 75000, b)
expect("customer download works after unlock", cu.post(f"/api/customer-portal/events/{E}/gallery/download", {"photoIds": PHOTOS[:1]}), 200)
expect("turn downloads off", ph.put(f"/api/events/{E}/download-policy", {"mode": "DISABLED", "unlocked": False}), 200)
expect("customer download refused when off (403)", cu.post(f"/api/customer-portal/events/{E}/gallery/download", {"photoIds": []}), 403)
expect("back to free", ph.put(f"/api/events/{E}/download-policy", {"mode": "FREE", "unlocked": False}), 200)
expect("customer receipt download", cu.get(f"/api/customer-portal/events/{E}/payments/{p1['id']}/receipt.pdf"), 200)

# ---------------------------------------------------------------------------------------------
section("10. Expense management")
x1 = expect("add event expense", ph.post("/api/studio/expenses", {"eventId": E, "category": "Travel", "amount": 1200.5, "spentOn": today.isoformat(), "vendor": "Uber"}), 201).json()
expect("add general expense", ph.post("/api/studio/expenses", {"category": "Software", "amount": 499, "spentOn": today.isoformat()}), 201)
expect("edit expense", ph.put(f"/api/studio/expenses/{x1['id']}", {"eventId": E, "category": "Travel", "amount": 1500, "spentOn": today.isoformat(), "vendor": "Ola"}), 200)
expect("expense on another studio's event rejected", other.post("/api/studio/expenses", {"eventId": E, "category": "Food", "amount": 1, "spentOn": today.isoformat()}), 404)
expect("other studio cannot delete expense", other.delete(f"/api/studio/expenses/{x1['id']}"), 404)
expect("list expenses", ph.get("/api/studio/expenses"), 200)
fs = expect("finance summary", ph.get("/api/studio/finance/summary"), 200).json()
check("summary totals", float(fs["received"]) == 75999 and float(fs["expenses"]) == 1999 and float(fs["net"]) == 74000, fs)

# ---------------------------------------------------------------------------------------------
section("11. Photography portfolio")
items = expect("add portfolio photos", ph.post("/api/studio/portfolio", {"eventId": E, "mediaIds": PHOTOS[:2], "caption": "Haldi"}), 200).json()
expect("adding same photos again is idempotent", ph.post("/api/studio/portfolio", {"eventId": E, "mediaIds": PHOTOS[:2]}), 200)
check("2 portfolio items", len(ph.get("/api/studio/portfolio").json()) == 2)
expect("update caption/order", ph.put(f"/api/studio/portfolio/{items[0]['id']}", {"caption": "Haldi morning", "sortOrder": 5}), 200)
pub = expect("public portfolio page", anon.get("/api/public/portfolio/lens-light"), 200).json()
check("public portfolio has 2 photos", len(pub["items"]) == 2, pub)
img = expect("public portfolio image", anon.get(pub["items"][0]["imageUrl"]), 200)
check("portfolio image is JPEG", img.content[:2] == b"\xff\xd8")
expect("unknown portfolio 404", anon.get("/api/public/portfolio/no-such-studio"), 404)
expect("remove portfolio item", ph.delete(f"/api/studio/portfolio/{items[1]['id']}"), 204)
ph.put("/api/studio/profile", {**{k: prof[k] for k in prof if k != "studioName"}, "portfolioPublic": False})
expect("unpublished portfolio hidden", anon.get("/api/public/portfolio/lens-light"), 404)
ph.put("/api/studio/profile", {**{k: prof[k] for k in prof if k != "studioName"}, "portfolioPublic": True})

# ---------------------------------------------------------------------------------------------
section("14. E-album flipbook")
sel = expect("customer's album selection as source", ph.get(f"/api/events/{E}/flipbooks/customer-selection"), 200).json()
check("selection returned from data service", len(sel) == 2, sel)
fb = expect("create flipbook", ph.post(f"/api/events/{E}/flipbooks", {"title": "Priya weds", "mediaIds": [PHOTOS[2], PHOTOS[0], PHOTOS[1]]}), 201).json()
expect("invalid photo id rejected", ph.post(f"/api/events/{E}/flipbooks", {"title": "x", "mediaIds": ["bad id!"]}), 422)
tok = fb["shareUrl"].rsplit("/", 1)[1]
pf = expect("public flipbook", anon.get(f"/api/public/flipbooks/{tok}"), 200).json()
check("3 pages", pf["pageCount"] == 3, pf)
page = expect("flipbook page image", anon.get(f"/api/public/flipbooks/{tok}/pages/0"), 200)
check("page is JPEG", page.content[:2] == b"\xff\xd8")
expect("page out of range 404", anon.get(f"/api/public/flipbooks/{tok}/pages/3"), 404)
cb = cu.get(f"/api/customer-portal/events/{E}/billing").json()
check("customer portal lists e-album", len(cb["flipbooks"]) == 1, cb)
expect("other studio cannot list flipbooks", other.get(f"/api/events/{E}/flipbooks"), 404)
expect("delete flipbook", ph.delete(f"/api/events/{E}/flipbooks/{fb['id']}"), 204)
expect("deleted link stops working", anon.get(f"/api/public/flipbooks/{tok}"), 404)

# ---------------------------------------------------------------------------------------------
section("Dashboard & SPA routes")
expect("dashboard summary", ph.get("/api/dashboard/summary"), 200)
for path in ["/", "/login", "/dashboard", "/customer-portal", "/portfolio/lens-light", "/flipbook/abc", f"/event-checkin/{gtoken}"]:
    expect(f"SPA route {path}", anon.get(path), 200)
expect("logout", ph.post("/api/auth/logout"), 204)
expect("after logout no access", ph.get("/api/auth/me"), 401)

# ---------------------------------------------------------------------------------------------
section("Data-service contract (what Java sent to the stand-in)")
calls = requests.get(STUB + "/__calls").json()
check("service key sent on every call", all(c["key"] == "t" for c in calls if c["path"] != "/__calls"), [c for c in calls if c["key"] != "t"][:2])
pub_previews = [c for c in calls if c["path"].endswith("/preview") and c["path"].startswith(f"/api/v1/events/{E}")]
check("public pages fetch previews with the event's own customer id", pub_previews and all(c["q"].get("customer_id") == cust["id"] for c in pub_previews), pub_previews[:2])
check("public pages never fetch originals", not any("/original" in c["path"] for c in calls))

json.dump({"E": E, "tomorrow": TOMORROW}, open(os.path.join(OUT, "suite-ids.json"), "w"))
passed = sum(1 for r in RESULTS if r[2]); failed = [r for r in RESULTS if not r[2]]
print(f"\nRESULT: {passed} passed, {len(failed)} failed")
for s, n, _, d in failed:
    print(f"  FAILED [{s}] {n}: {str(d)[:300]}")
json.dump(RESULTS, open(os.path.join(OUT, "suite-results.json"), "w"))
sys.exit(1 if failed else 0)
