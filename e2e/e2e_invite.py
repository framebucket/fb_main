"""E2E: client-portal wedding invitations through Spring Boot; invitation routes served by the real fb_data code."""
import os
OUT = os.getenv("FB_E2E_OUT", "out"); os.makedirs(OUT, exist_ok=True)
import io, re, sys, time, subprocess
sys.argv = sys.argv[:1]
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "e2e_admin.py")).read().split("# ------------------------------------------------------------------------------------------------")[0])
import requests
from PIL import Image
STUB = os.getenv("FB_STUB_URL", "http://127.0.0.1:8001")

probe = requests.get(STUB + "/api/v1/events/probe/invitation", params={"photographer_id": "p", "customer_id": "c"})
if probe.status_code == 501:
    print("SKIPPED: the real fb_data code is not available to the stub (set FB_DATA_SRC).")
    sys.exit(0)
import samples
    
def customer_login(mobile):
    c = Client(); mark = log_size()
    c.post("/api/customer-auth/request-code", {"mobileNumber": mobile}); time.sleep(0.5)
    codes = re.findall(r"\b(\d{6})\b", open(LOG, encoding="utf-8", errors="replace").read()[mark:])
    r = c.post("/api/customer-auth/verify-code", {"mobileNumber": mobile, "code": codes[-1]}); assert r.status_code == 200, r.text
    return c

section("Setup")
owner = Client(); register(owner, "Owner", "FrameBucket HQ", "owner@framebucket.in", "9000011111")
p = Client(); register(p, "Ravi Kumar", "Pixel Stories", "ravi@example.com", "9876500001")
free = Client(); register(free, "Free Photographer", "Free Studio", "free@example.com", "9876500002")
expect("admin moves to Free", owner.put(f"/api/admin/photographers/{uid('free@example.com')}/plan", {"planCode": "FREE"}), 200)
wedding = next(t for t in p.get("/api/events/types").json() if t["name"] == "Wedding")
cust = p.post("/api/customers", {"name": "Varsha", "email": "varsha@example.com", "mobileNumber": "9811100001", "dateOfBirth": "1996-01-01", "address": "Bengaluru"}).json()
ev = p.post("/api/events", {"eventDate": D(40), "eventTime": "10:30:00", "eventTypeId": wedding["id"], "customerId": cust["id"], "price": "90000"}).json()
EV = ev.get("eventId") or ev.get("id")
fc = free.post("/api/customers", {"name": "Anu", "email": "anu@example.com", "mobileNumber": "9811100002", "dateOfBirth": "1996-01-01", "address": "Pune"}).json()
fev = free.post("/api/events", {"eventDate": D(40), "eventTypeId": wedding["id"], "customerId": fc["id"], "price": "1000"}).json()
FEV = fev.get("eventId") or fev.get("id")
cu = customer_login("9811100001"); fcu = customer_login("9811100002")

section("Invitation")
IB = f"/api/customer-portal/events/{EV}/invitation"
st = expect("open (empty)", cu.get(IB), 200).json()
check("allowed, suggested from booking", st["planAllows"] and not st["exists"] and "December" in st["suggested"]["events"][0]["date"] or "20" in st["suggested"]["events"][0]["date"]
      and st["suggested"]["events"][0]["date"], st)
f = expect("free studio's client", fcu.get(f"/api/customer-portal/events/{FEV}/invitation"), 200).json()
check("free plan → not available", f == {"planAllows": False}, f)
expect("free plan save refused", fcu.put(f"/api/customer-portal/events/{FEV}/invitation", {"bride": "A", "groom": "B"}), 403)
expect("other client can't open", fcu.get(IB), (403, 404))
expect("anonymous refused", Client().get(IB), 401)
expect("designs before details refused", cu.post(IB + "/designs"), 422)
expect("missing names refused", cu.put(IB, {"bride": "Varsha", "events": [{"date": "x", "venue": "y"}]}), 422)
details = {**st["suggested"], "bride": "Varsha", "groom": "Nitish", "invocation": "|| Shri Ganeshaya Namah ||",
           "bride_family": "D/o Smt. Lakshmi & Sri. Ramesh", "groom_family": "S/o Smt. Shobha & Sri. Prakash",
           "events": [{**st["suggested"]["events"][0], "title": "Muhurtham", "venue": "Sri Krishna Kalyana Mantapa", "address": "Jayanagar, Bengaluru"},
                      {"title": "Reception", "date": "The evening before", "time": "7:00 PM", "venue": "Same venue", "address": ""}]}
st = expect("save details", cu.put(IB, details), 200).json()
check("saved with 2 functions", st["exists"] and len(st["details"]["events"]) == 2, st)
photo = samples.jpeg(samples.photo(3, (1400, 1800)))
expect("non-image refused", cu.post(IB + "/photo", files={"file": ("x.txt", b"hello", "text/plain")}), 400)
st = expect("upload couple photo", cu.post(IB + "/photo", files={"file": ("couple.jpg", photo, "image/jpeg")}), 200).json()
check("photo stored", st["has_photo"], st)
t = time.time(); st = expect("5 designs", cu.post(IB + "/designs"), 200).json(); print("  designs %.1fs" % (time.time() - t))
check("5 different themes", len({d["theme"] for d in st["designs"]}) == 5 and st["regenerations_left"] == 39, st["designs"])
img = cu.get(IB + "/designs/0")
check("design preview is a 9:16 JPEG", img.status_code == 200 and abs(Image.open(io.BytesIO(img.content)).size[1] / Image.open(io.BytesIO(img.content)).size[0] - 16 / 9) < 0.01)
open(os.path.join(OUT, "invitation-design0.jpg"), "wb").write(img.content)
expect("design out of range", cu.get(IB + "/designs/7"), 404)
first = {d["theme"] for d in st["designs"]}
st = expect("regenerate", cu.post(IB + "/designs"), 200).json()
check("new batch uses other themes", len({d["theme"] for d in st["designs"]} - first) >= 3, st["designs"])
expect("video before finalize → 404", cu.get(IB + "/video"), 404)
st = expect("finalize design 2", cu.post(IB + "/finalize", {"index": 2}), 200).json()
check("rendering", st["status"] == "RENDERING" and st["chosen"]["theme"] == st["designs"][2]["theme"], st)
expect("regenerate while rendering refused", cu.post(IB + "/designs"), 422)
t = time.time(); requests.post(f"{STUB}/__drain", timeout=600); print("  video %.1fs" % (time.time() - t))
st = cu.get(IB).json(); check("ready", st["status"] == "READY" and st["video_ready"], st)
v = cu.get(IB + "/video"); check(f"MP4 download ({len(v.content)//1000} KB)", v.status_code == 200 and v.headers["Content-Type"] == "video/mp4" and v.content[4:8] == b"ftyp", v.status_code)
open(os.path.join(OUT, "invitation.mp4"), "wb").write(v.content)
pz = cu.get(IB + "/poster"); check("poster download", pz.status_code == 200 and pz.headers["Content-Type"] == "image/jpeg")
st = expect("remove photo", cu.delete(IB + "/photo"), 200).json(); check("photo removed", not st["has_photo"])

failed = [r for r in RESULTS if not r[2]]
print(f"\nRESULT: {len(RESULTS) - len(failed)} passed, {len(failed)} failed")
for r in failed:
    print("  FAILED:", r[0], r[1], str(r[3])[:300])
sys.exit(1 if failed else 0)
