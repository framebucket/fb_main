"""E2E: AI album auto-design through the real Spring Boot backend; album-design routes are served by the real
fb_data service code (via the stub) against Postgres."""
import os
OUT = os.getenv("FB_E2E_OUT", "out"); os.makedirs(OUT, exist_ok=True)
import io, sys, time, zipfile, subprocess
sys.argv = sys.argv[:1]
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "e2e_admin.py")).read().split("# ------------------------------------------------------------------------------------------------")[0])
import requests
from PIL import Image
STUB = os.getenv("FB_STUB_URL", "http://127.0.0.1:8001")

probe = requests.get(STUB + "/api/v1/events/probe/invitation", params={"photographer_id": "p", "customer_id": "c"})
if probe.status_code == 501:
    print("SKIPPED: the real fb_data code is not available to the stub (set FB_DATA_SRC).")
    sys.exit(0)
    
section("Setup")
owner = Client(); register(owner, "Owner", "FrameBucket HQ", "owner@framebucket.in", "9000011111")
p = Client(); me = register(p, "Ravi Kumar", "Pixel Stories", "ravi@example.com", "9876500001")
check("trial includes AI album design + invitations", {"AI_ALBUM_DESIGN", "AI_INVITATIONS"} <= set(me["entitlements"]["features"]), me["entitlements"]["features"])
free = Client(); register(free, "Free Photographer", "Free Studio", "free@example.com", "9876500002")
expect("admin moves to Free", owner.put(f"/api/admin/photographers/{uid('free@example.com')}/plan", {"planCode": "FREE"}), 200)
other = Client(); register(other, "Other", "Other", "other@example.com", "9876500003")
wedding = next(t for t in p.get("/api/events/types").json() if t["name"] == "Wedding")
cust = p.post("/api/customers", {"name": "Anu", "email": "anu@example.com", "mobileNumber": "9811100001", "dateOfBirth": "1995-01-01", "address": "Bengaluru"}).json()
ev = expect("event", p.post("/api/events", {"eventDate": D(30), "eventTypeId": wedding["id"], "customerId": cust["id"], "price": "90000"}), (200, 201)).json()
EV = ev.get("eventId") or ev.get("id")
P = uid("ravi@example.com")

section("Album design")
L = expect("list before selection", p.get(f"/api/events/{EV}/album-designs"), 200).json()
check("no submissions yet, sizes offered, plan allows", L.get("submissions") == [] and "12x36" in L["sizes"] and L["planAllows"], L)
expect("other studio can't list", other.get(f"/api/events/{EV}/album-designs"), (403, 404))
r = requests.post(f"{STUB}/__album_setup/{EV}", params={"photographer_id": P, "customer_id": cust["id"]})
check("client submitted selection (real service)", r.status_code == 200, r.text)
L = p.get(f"/api/events/{EV}/album-designs").json()
sub = L["submissions"][0]; check("submission listed", sub["photo_count"] >= 20, L)
expect("bad size refused", p.post(f"/api/events/{EV}/album-designs", {"submissionId": sub["submission_id"], "size": "huge", "sheets": 10}), 400)
expect("too many sheets refused", p.post(f"/api/events/{EV}/album-designs", {"submissionId": sub["submission_id"], "size": "12x30", "sheets": 500}), 400)
expect("bad dpi refused", p.post(f"/api/events/{EV}/album-designs", {"submissionId": sub["submission_id"], "size": "12x30", "sheets": 8, "dpi": 72}), 422)
expect("free plan refused", free.post(f"/api/events/{EV}/album-designs", {"submissionId": sub["submission_id"], "size": "12x30", "sheets": 8}), (403, 402))
d = expect("design album 12×36, 8 sheets", p.post(f"/api/events/{EV}/album-designs", {"submissionId": sub["submission_id"], "size": "12 × 36", "sheets": 8, "dpi": 150}), 200).json()
check("queued v1 with normalised label", d["status"] == "QUEUED" and d["size_label"] == "12x36" and d["version"] == 1, d)
expect("second design while busy refused", p.post(f"/api/events/{EV}/album-designs", {"submissionId": sub["submission_id"], "size": "12x30", "sheets": 8}), 422)
expect("download before ready → 409", p.get(f"/api/events/{EV}/album-designs/{d['design_id']}/download/psd"), 409)
t = time.time(); requests.post(f"{STUB}/__drain", timeout=900); print("  design job %.1fs" % (time.time() - t))
s = expect("status", p.get(f"/api/events/{EV}/album-designs/{d['design_id']}"), 200).json()
check("ready with 8 spreads", s["status"] == "READY" and len(s["spreads"]) == 8 and s["progress"] == 100, s)
img = p.get(f"/api/events/{EV}/album-designs/{d['design_id']}/spreads/0")
check("spread preview is a 12×36 JPEG", img.status_code == 200 and img.headers["Content-Type"].startswith("image/jpeg")
      and abs(Image.open(io.BytesIO(img.content)).size[0] / Image.open(io.BytesIO(img.content)).size[1] - 3) < 0.01, img.status_code)
open(os.path.join(OUT, "album-spread0.jpg"), "wb").write(img.content)
expect("spread out of range", p.get(f"/api/events/{EV}/album-designs/{d['design_id']}/spreads/8"), 404)
expect("other studio can't view", other.get(f"/api/events/{EV}/album-designs/{d['design_id']}/spreads/0"), (403, 404))
expect("bad design id", p.get(f"/api/events/{EV}/album-designs/..%2Fx"), (400, 404))
for kind, ext in [("jpeg", ".jpg"), ("psd", ".psd")]:
    z = p.get(f"/api/events/{EV}/album-designs/{d['design_id']}/download/{kind}")
    ok = z.status_code == 200 and "attachment" in z.headers.get("Content-Disposition", "")
    names = zipfile.ZipFile(io.BytesIO(z.content)).namelist() if ok else []
    check(f"{kind} ZIP has 8 spreads + README ({len(z.content)//1_000_000} MB)", ok and len([n for n in names if n.endswith(ext)]) == 8 and "README.txt" in names, (z.status_code, z.text[:200]))
expect("unknown archive", p.get(f"/api/events/{EV}/album-designs/{d['design_id']}/download/raw"), 404)
proofs = expect("send to client as proof", p.post(f"/api/events/{EV}/album-designs/{d['design_id']}/send", {"message": "First draft by AI"}), 200).json()
check("proof v1 with 8 spreads", proofs[0]["version"] == 1 and proofs[0]["spreadCount"] == 8 and proofs[0]["status"] == "SENT", proofs[0])
proofs = expect("sending again re-uses the folder", p.post(f"/api/events/{EV}/album-designs/{d['design_id']}/send", {}), 200).json()
check("proof v2 replaces v1", proofs[0]["version"] == 2 and proofs[1]["status"] == "SUPERSEDED", [x["status"] for x in proofs])
d2 = expect("design v2 12x30", p.post(f"/api/events/{EV}/album-designs", {"submissionId": sub["submission_id"], "size": "12x30", "sheets": 6}), 200).json()
check("v2", d2["version"] == 2)

failed = [r for r in RESULTS if not r[2]]
print(f"\nRESULT: {len(RESULTS) - len(failed)} passed, {len(failed)} failed")
for r in failed:
    print("  FAILED:", r[0], r[1], str(r[3])[:300])
sys.exit(1 if failed else 0)
