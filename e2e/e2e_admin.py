"""End-to-end checks for subscription plans v2, plan enforcement and the admin portal API."""
import os
OUT = os.getenv("FB_E2E_OUT", "out"); os.makedirs(OUT, exist_ok=True)
import datetime as dt, re, subprocess, sys, time
import requests

B = os.getenv("FB_BASE_URL", "http://localhost:18080")
LOG = os.getenv("FB_APP_LOG", "backend.log")
RESULTS = []
SECTION = ["setup"]
PASSWORD = "Sup3rSecret!!xyz"
today = dt.date.today()
D = lambda n: (today + dt.timedelta(days=n)).isoformat()


def section(name):
    SECTION[0] = name
    print(f"\n== {name}")


def check(name, ok, detail=""):
    RESULTS.append((SECTION[0], name, bool(ok), detail))
    print(("  PASS " if ok else "  FAIL ") + name + ("" if ok else f"  -> {str(detail)[:400]}"))
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


def plan_blocked(name, r, feature=None, limit=None):
    try:
        body = r.json()
    except Exception:
        body = {}
    ok = r.status_code == 403 and body.get("code") == "PLAN_UPGRADE_REQUIRED" and bool(body.get("detail"))
    if feature: ok = ok and body.get("feature") == feature
    if limit: ok = ok and body.get("limit") == limit
    check(f"{name} blocked by plan [{r.status_code}] {body.get('feature') or body.get('limit') or ''}", ok, r.text)


def log_size():
    return len(open(LOG, encoding="utf-8", errors="replace").read())


def log_find(pattern, after=0):
    found = re.findall(pattern, open(LOG, encoding="utf-8", errors="replace").read()[after:])
    return found[-1] if found else None


def psql(sql):
    return subprocess.run(["psql", "-Atc", sql],
                          capture_output=True, text=True).stdout.strip()


def register(c, name, studio, email, mobile):
    mark = log_size()
    c.post("/api/auth/register", {"fullName": name, "studioName": studio, "email": email, "mobileNumber": mobile,
                                  "password": PASSWORD, "confirmPassword": PASSWORD})
    time.sleep(0.4)
    c.post("/api/auth/verify-email", {"token": log_find(r"verify-email\?token=(\S+)", mark)})
    c.post("/api/auth/verify-mobile", {"identifier": email, "code": log_find(rf"code for {mobile}: (\d+)", mark)})
    r = c.post("/api/auth/login", {"username": email, "password": PASSWORD})
    assert r.status_code == 200, r.text
    return r.json()


def uid(email):
    return psql(f"select id from fb_user where email='{email}'")


# ------------------------------------------------------------------------------------------------
section("Public plans & sign-up trial")
anon = Client()
plans = expect("public plans", anon.get("/api/subscriptions"), 200).json()
check("4 public plans in order", [p["code"] for p in plans] == ["FREE", "SOLO", "STUDIO", "BUSINESS"], [p["code"] for p in plans])
studio_plan = next(p for p in plans if p["code"] == "STUDIO")
check("Studio highlighted, ₹1,699 / ₹16,990", studio_plan["highlighted"] and float(studio_plan["monthlyPrice"]) == 1699 and float(studio_plan["yearlyPrice"]) == 16990, studio_plan)
check("yearly saving = 2 months", float(studio_plan["yearlySavings"]) == 1699 * 12 - 16990, studio_plan["yearlySavings"])
check("public plan list hides subscriber counts", studio_plan.get("subscriberCount") is None)

owner = Client(); me_owner = register(owner, "Owner", "FrameBucket HQ", "owner@framebucket.in", "9000011111")
a = Client(); me_a = register(a, "Asha Rao", "Lens & Light", "asha@example.com", "9876543210")
b = Client(); register(b, "Ravi Iyer", "Ravi Clicks", "ravi@example.com", "9876500000")
A, Bid = uid("asha@example.com"), uid("ravi@example.com")
check("owner is admin", me_owner["admin"] is True, me_owner)
check("photographer is not admin", me_a["admin"] is False)
ent = a.get("/api/auth/me").json()["entitlements"]
check("new sign-up on 14-day Studio trial", ent["status"] == "TRIAL" and ent["planCode"] == "FREE_TRIAL" and "FACE_RECOGNITION" in ent["features"], ent)
trial_days = (dt.datetime.fromisoformat(ent["trialEndsAt"].replace("Z", "+00:00")) - dt.datetime.now(dt.timezone.utc)).days
check("trial is 14 days", 13 <= trial_days <= 14, trial_days)

# ------------------------------------------------------------------------------------------------
section("Admin access control")
expect("photographer cannot open admin API", a.get("/api/admin/overview"), 403)
expect("anonymous cannot open admin API", anon.get("/api/admin/overview"), 401)
expect("admin overview", owner.get("/api/admin/overview"), 200)
expect("SPA route /admin", anon.get("/admin"), 200)

# ------------------------------------------------------------------------------------------------
section("Free plan limits")
expect("admin moves A to Free", owner.put(f"/api/admin/photographers/{A}/plan", {"planCode": "FREE", "note": "test"}), 200)
ent = a.get("/api/auth/me").json()["entitlements"]
check("A now FREE with 7 features (no re-login needed)", ent["status"] == "FREE" and sorted(ent["features"]) == ["CRM", "CUSTOM_PACKAGES", "DELIVERY_TRACKER", "GALLERY", "LEAD_CAPTURE", "QUOTATIONS", "TEAM"], ent)
cust_ids = []
for i in range(25):
    r = a.post("/api/customers", {"name": f"Client {i}", "email": f"c{i}@example.com", "mobileNumber": f"91234{i:05d}", "dateOfBirth": "1990-01-01", "address": "Pune"})
    if r.status_code == 201: cust_ids.append(r.json()["id"])
check("25 customers allowed on Free", len(cust_ids) == 25, len(cust_ids))
plan_blocked("26th customer", a.post("/api/customers", {"name": "Client X", "email": "cx@example.com", "mobileNumber": "9123499999", "dateOfBirth": "1990-01-01", "address": "Pune"}), limit="customers")
wedding = next(t for t in a.get("/api/events/types").json() if t["name"] == "Wedding")
evs = [a.post("/api/events", {"eventDate": D(i + 1), "eventTypeId": wedding["id"], "customerId": cust_ids[i], "price": "50000"}) for i in range(3)]
check("3 upcoming events allowed", all(r.status_code == 201 for r in evs), [r.status_code for r in evs])
E = evs[0].json()["id"]
plan_blocked("4th upcoming event", a.post("/api/events", {"eventDate": D(10), "eventTypeId": wedding["id"], "customerId": cust_ids[3], "price": "1"}), limit="upcomingEvents")
expect("past-dated event still allowed (doesn't count)", a.post("/api/events", {"eventDate": D(-5), "eventTypeId": wedding["id"], "customerId": cust_ids[4], "price": "1"}), 201)
expect("1 team member allowed", a.post("/api/team", {"name": "Karthik", "age": 27, "mobileNumber": "9000000001", "languages": ["ENGLISH"], "memberType": "TEAM_MEMBER", "skills": ["PHOTOGRAPHY"]}), (200, 201))
plan_blocked("2nd team member", a.post("/api/team/invitations", {"name": "Meera", "mobileNumber": "9000000002", "memberType": "FREELANCER"}), limit="teamMembers")
plan_blocked("record payment", a.post(f"/api/events/{E}/payments", {"amount": 100, "paidOn": today.isoformat(), "method": "UPI", "purpose": "ADVANCE"}), feature="BILLING")
r = a.get(f"/api/events/{E}/payment-qr.png")
plan_blocked("UPI QR (PNG endpoint returns JSON error)", r, feature="BILLING")
plan_blocked("add expense", a.post("/api/studio/expenses", {"category": "Travel", "amount": 10, "spentOn": today.isoformat()}), feature="BILLING")
plan_blocked("add portfolio photo", a.post("/api/studio/portfolio", {"eventId": E, "mediaIds": ["a1b2c3d4e5f60001"]}), feature="PORTFOLIO")
plan_blocked("create e-album", a.post(f"/api/events/{E}/flipbooks", {"title": "x", "mediaIds": ["a1b2c3d4e5f60001"]}), feature="E_ALBUM")
expect("gallery still available on Free", a.get(f"/api/events/{E}/gallery/media"), 200)
r = a.post(f"/api/events/{E}/gallery/uploads", {"folderId": "root", "filename": "big.CR3", "sizeBytes": 6 * 1024 ** 3, "contentType": "image/x-canon-cr3"})
plan_blocked("6 GB upload over 5 GB storage", r, limit="storageGb")
gtoken = psql(f"select guest_access_token from fb_event_booking where id='{E}'")
ctx = expect("guest page", anon.get(f"/api/public/event-galleries/{gtoken}"), 200).json()
check("guest page says face matching unavailable", ctx.get("faceRecognitionAvailable") is False, ctx)
r = anon.post(f"/api/public/event-galleries/{gtoken}/guests", files={"selfie": ("s.jpg", b"x", "image/jpeg")},
              data={"name": "Guest", "mobileNumber": "9888800001", "faceConsent": "true", "whatsappConsent": "true"})
check("guest face registration refused on Free [403]", r.status_code == 403 and "Face matching" in r.json().get("detail", ""), r.text)
expect("download policy FREE allowed", a.put(f"/api/events/{E}/download-policy", {"mode": "FREE", "unlocked": False}), 200)
r = a.post("/api/auth/login", {"username": "asha@example.com", "password": "nope-nope-nope"})
check("error reasons now reach the browser", r.status_code == 401 and "password" in r.json().get("detail", "").lower(), r.text)

# ------------------------------------------------------------------------------------------------
section("Plan request → admin approval")
expect("free plan can't be requested", a.post("/api/subscriptions/requests", {"planCode": "FREE", "billingCycle": "MONTHLY"}), 422)
req = expect("A requests Solo yearly", a.post("/api/subscriptions/requests", {"planCode": "SOLO", "billingCycle": "YEARLY", "message": "Paid via UPI"}), 201).json()
check("request priced at yearly amount", float(req["amount"]) == 7990, req)
expect("second pending request refused", a.post("/api/subscriptions/requests", {"planCode": "STUDIO", "billingCycle": "MONTHLY"}), 409)
mine = expect("my subscription", a.get("/api/subscriptions/me"), 200).json()
check("pending request + usage shown", mine["pendingRequest"]["planCode"] == "SOLO" and mine["usage"]["customers"] == 25 and mine["usage"]["upcomingEvents"] == 3, mine)
time.sleep(0.3)
check("admin emailed about the request", "FrameBucket plan request: Solo" in open(LOG).read())
pending = expect("admin lists pending requests", owner.get("/api/admin/plan-requests"), 200).json()
check("request listed", any(p["id"] == req["id"] for p in pending), pending)
appr = expect("admin approves with UPI reference", owner.post(f"/api/admin/plan-requests/{req['id']}/approve", {"reference": "UTR998877", "note": "UPI received"}), 200).json()
check("request approved", appr["status"] == "APPROVED", appr)
expect("approving twice refused", owner.post(f"/api/admin/plan-requests/{req['id']}/approve", {}), 409)
ent = a.get("/api/auth/me").json()["entitlements"]
expires = dt.datetime.fromisoformat(ent["planExpiresAt"].replace("Z", "+00:00"))
check("A on SOLO, ACTIVE, yearly, ~12 months", ent["planCode"] == "SOLO" and ent["status"] == "ACTIVE" and ent["billingCycle"] == "YEARLY" and 360 <= (expires - dt.datetime.now(dt.timezone.utc)).days <= 366, ent)
expect("billing works on Solo", a.post(f"/api/events/{E}/payments", {"amount": 100, "paidOn": today.isoformat(), "method": "UPI", "purpose": "ADVANCE"}), 201)
expect("4th upcoming event now allowed", a.post("/api/events", {"eventDate": D(10), "eventTypeId": wedding["id"], "customerId": cust_ids[3], "price": "1"}), 201)
plan_blocked("paid downloads still need Studio", a.put(f"/api/events/{E}/download-policy", {"mode": "PAID", "price": 99, "unlocked": False}), feature="PAID_DOWNLOADS")
hist = a.get("/api/subscriptions/me").json()["history"]
check("history records approval", hist and hist[0]["action"] == "PLAN_REQUEST_APPROVED" and hist[0]["reference"] == "UTR998877", hist[:1])
r = a.post("/api/subscriptions/requests", {"planCode": "STUDIO", "billingCycle": "MONTHLY"})
expect("A requests Studio", r, 201)
expect("A cancels it", a.delete("/api/subscriptions/requests/pending"), 204)

# ------------------------------------------------------------------------------------------------
section("Admin edits a plan (applies immediately)")
solo = next(p for p in owner.get("/api/admin/plans").json() if p["code"] == "SOLO")
check("admin plan list includes subscriber count", solo["subscriberCount"] == 1, solo)
body = {k: solo[k] for k in ["name", "tagline", "description", "monthlyPrice", "yearlyPrice", "highlighted", "publicPlan", "active", "sortOrder", "trialDays", "features", "limits"]}
expect("unknown feature rejected", owner.put("/api/admin/plans/SOLO", {**body, "features": body["features"] + ["TELEPORT"]}), 422)
expect("add FACE_RECOGNITION to Solo", owner.put("/api/admin/plans/SOLO", {**body, "features": body["features"] + ["FACE_RECOGNITION"]}), 200)
r = anon.post(f"/api/public/event-galleries/{gtoken}/guests", files={"selfie": ("s.jpg", b"x", "image/jpeg")},
              data={"name": "Guest", "mobileNumber": "9888800001", "faceConsent": "false", "whatsappConsent": "false"})
check("face registration passes plan check (fails later on consent) [400]", r.status_code == 400, r.text)
expect("restore Solo", owner.put("/api/admin/plans/SOLO", body), 200)
fp = next(p for p in owner.get("/api/admin/plans").json() if p["code"] == "FREE")
fb = {k: fp[k] for k in ["name", "tagline", "description", "monthlyPrice", "yearlyPrice", "highlighted", "publicPlan", "active", "sortOrder", "trialDays", "features", "limits"]}
expect("Free plan can't be deactivated", owner.put("/api/admin/plans/FREE", {**fb, "active": False}), 422)

# ------------------------------------------------------------------------------------------------
section("Expiry, grace and trial rules")
psql(f"update fb_user set plan_expires_at = now() - interval '1 day' where id = '{A}'")
ent = a.get("/api/auth/me").json()["entitlements"]
check("1 day after expiry: GRACE, keeps Solo", ent["status"] == "GRACE" and ent["planCode"] == "SOLO", ent)
psql(f"update fb_user set plan_expires_at = now() - interval '4 days' where id = '{A}'")
ent = a.get("/api/auth/me").json()["entitlements"]
check("4 days after expiry: LAPSED, back to Free", ent["status"] == "LAPSED" and ent["planCode"] == "FREE" and ent["subscribedPlanCode"] == "SOLO", ent)
plan_blocked("billing after lapse", a.post(f"/api/events/{E}/payments", {"amount": 1, "paidOn": today.isoformat(), "method": "UPI", "purpose": "ADVANCE"}), feature="BILLING")
expect("existing data still readable after lapse", a.get(f"/api/events/{E}/billing"), 200)
expect("admin renews Solo for 1 month", owner.put(f"/api/admin/photographers/{A}/plan", {"planCode": "SOLO", "billingCycle": "MONTHLY", "months": 1, "amount": 799, "reference": "UTR1"}), 200)
check("renewed → ACTIVE", a.get("/api/auth/me").json()["entitlements"]["status"] == "ACTIVE")
expect("past paid-until date refused", owner.put(f"/api/admin/photographers/{A}/plan", {"planCode": "SOLO", "periodEnd": D(-1)}), 422)
psql(f"update fb_user set trial_ends_at = now() - interval '1 hour' where id = '{Bid}'")
ent = b.get("/api/auth/me").json()["entitlements"]
check("trial over: TRIAL_ENDED on Free", ent["status"] == "TRIAL_ENDED" and ent["planCode"] == "FREE", ent)
expect("admin extends B's trial 7 days", owner.post(f"/api/admin/photographers/{Bid}/trial", {"days": 7, "note": "Asked for more time"}), 200)
ent = b.get("/api/auth/me").json()["entitlements"]
check("B back on trial for ~7 days", ent["status"] == "TRIAL" and 6 <= (dt.datetime.fromisoformat(ent["trialEndsAt"].replace("Z", "+00:00")) - dt.datetime.now(dt.timezone.utc)).days <= 7, ent)

# ------------------------------------------------------------------------------------------------
section("Suspend / reactivate")
O = uid("owner@framebucket.in")
expect("admin can't suspend an admin", owner.post(f"/api/admin/photographers/{O}/suspend", {"reason": "x"}), 409)
expect("suspend B", owner.post(f"/api/admin/photographers/{Bid}/suspend", {"reason": "Payment dispute"}), 200)
r = b.get("/api/customers")
check("B kicked out immediately [401 ACCOUNT_SUSPENDED]", r.status_code == 401 and r.json().get("code") == "ACCOUNT_SUSPENDED", r.text)
expect("B can't log in while suspended", Client().post("/api/auth/login", {"username": "ravi@example.com", "password": PASSWORD}), (401, 403))
expect("reactivate B", owner.post(f"/api/admin/photographers/{Bid}/reactivate"), 200)
expect("B can log in again", Client().post("/api/auth/login", {"username": "ravi@example.com", "password": PASSWORD}), 200)

# ------------------------------------------------------------------------------------------------
section("Admin lists, detail, overview, audit")
page = expect("search photographers", owner.get("/api/admin/photographers?query=lens"), 200).json()
check("search finds Lens & Light", page["total"] == 1 and page["items"][0]["email"] == "asha@example.com", page)
page = owner.get("/api/admin/photographers?status=ACTIVE").json()
check("filter ACTIVE", [p["email"] for p in page["items"]] == ["asha@example.com"], page)
page = owner.get("/api/admin/photographers?plan=FREE_TRIAL&sort=trialEnds").json()
check("filter by plan", {p["email"] for p in page["items"]} == {"owner@framebucket.in", "ravi@example.com"}, [p["email"] for p in page["items"]])
expect("bad status filter rejected", owner.get("/api/admin/photographers?status=NOPE"), 400)
check("last login recorded", page["items"][0]["lastLoginAt"] is not None)
d = expect("photographer detail", owner.get(f"/api/admin/photographers/{A}"), 200).json()
check("detail counts", d["counts"]["customers"] == 25 and d["counts"]["events"] == 5 and float(d["counts"]["clientPaymentsRecorded"]) == 100, d["counts"])
check("detail history + requests", len(d["history"]) >= 3 and len(d["planRequests"]) == 2, (len(d["history"]), len(d["planRequests"])))
check("storage read from data-service tables (0 here)", d["usage"]["storageBytes"] == 0)
expect("save admin notes", owner.put(f"/api/admin/photographers/{A}/notes", {"notes": "Wedding studio, Bengaluru. Paid by UPI."}), 200)
check("notes saved", owner.get(f"/api/admin/photographers/{A}").json()["adminNotes"].startswith("Wedding studio"))
ov = owner.get("/api/admin/overview").json()
check("overview counts", ov["photographers"] == 3 and ov["byStatus"]["ACTIVE"] == 1 and ov["byStatus"]["TRIAL"] == 2 and ov["payingByPlan"] == {"SOLO": 1}, ov)
check("MRR = Solo monthly ₹799", float(ov["monthlyRecurringRevenue"]) == 799, ov["monthlyRecurringRevenue"])
check("subscription revenue last 30 days = 7990 + 799", float(ov["subscriptionRevenueLast30Days"]) == 8789, ov["subscriptionRevenueLast30Days"])
check("signups chart has 30 days and 3 sign-ups today", len(ov["signupsLast30Days"]) == 30 and ov["signupsLast30Days"][-1]["count"] == 3, ov["signupsLast30Days"][-2:])
audit = expect("audit log", owner.get("/api/admin/audit"), 200).json()
actions = [x["action"] for x in audit]
for act in ["CHANGE_PLAN", "APPROVE_PLAN_REQUEST", "UPDATE_PLAN", "EXTEND_TRIAL", "SUSPEND", "REACTIVATE", "UPDATE_NOTES"]:
    check(f"audit has {act}", act in actions, actions)
check("audit shows studio name", any(x["targetName"] == "Lens & Light" for x in audit))

# ------------------------------------------------------------------------------------------------
section("Public pages respect the plan")
expect("A (Solo) sets up portfolio", a.put("/api/studio/profile", {"portfolioSlug": "lens-light", "portfolioPublic": True, "remindersEnabled": True}), 200)
expect("A adds portfolio photo", a.post("/api/studio/portfolio", {"eventId": E, "mediaIds": ["a1b2c3d4e5f60001"]}), 200)
pub = expect("public portfolio", anon.get("/api/public/portfolio/lens-light"), 200).json()
check("Solo shows 'Powered by FrameBucket'", pub["poweredByFrameBucket"] is True, pub)
expect("admin moves A to Studio", owner.put(f"/api/admin/photographers/{A}/plan", {"planCode": "STUDIO", "billingCycle": "MONTHLY", "months": 1}), 200)
check("Studio removes the badge", anon.get("/api/public/portfolio/lens-light").json()["poweredByFrameBucket"] is False)
expect("admin moves A to Free", owner.put(f"/api/admin/photographers/{A}/plan", {"planCode": "FREE"}), 200)
expect("portfolio hidden on Free", anon.get("/api/public/portfolio/lens-light"), 404)

passed = sum(1 for r in RESULTS if r[2]); failed = [r for r in RESULTS if not r[2]]
print(f"\nRESULT: {passed} passed, {len(failed)} failed")
for s, n, _, d in failed:
    print(f"  FAILED [{s}] {n}: {str(d)[:400]}")
sys.exit(1 if failed else 0)
