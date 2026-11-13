"""Reference album designs: Studio+ only (50 / 100), upload, layout analysis, limit, delete, thumbnails (needs fb_data)."""
import os
OUT = os.getenv("FB_E2E_OUT", "out"); os.makedirs(OUT, exist_ok=True)
import sys
import requests
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "e2e_admin.py")).read().split("# ------------------------------------------------------------------------------------------------")[0])
STUB = os.getenv("FB_STUB_URL", "http://127.0.0.1:8001")
import samples

probe = requests.get(STUB + "/api/v1/studios/probe/album-references")
if probe.status_code == 501:
    print("SKIPPED: the real fb_data code is not available to the stub (set FB_DATA_SRC).")
    sys.exit(0)
SPREADS = [samples.spread(layout, seed) for seed, layout in enumerate(samples.SPREAD_LAYOUTS)]
owner = Client(); register(owner, "Owner", "FB", "owner@framebucket.in", "9876500001")
def set_plan(email, code):
    expect(f"admin sets {code}", owner.put(f"/api/admin/photographers/{uid(email)}/plan", {"planCode": code, "billingCycle": "MONTHLY", "months": 1}), 200)
def files(items): return [("files", (f"spread-{i}.jpg", data, "image/jpeg")) for i, data in items]

c = Client(); register(c, "Ref Studio", "Refs", "refs@example.com", "9876544444")
section("Free and Solo: upgrade prompt")
for plan in ["FREE", "SOLO"]:
    set_plan("refs@example.com", plan)
    r = expect(f"{plan} list", c.get("/api/studio/album-references"), 200).json()
    check(f"{plan}: not included", r["planAllows"] is False and r["limit"] == 0, r)
    plan_blocked(f"{plan} upload", c.post("/api/studio/album-references", files=files([(1, SPREADS[0])])), limit="albumReferences")

section("Studio: upload, analyse, delete")
set_plan("refs@example.com", "STUDIO")
r = expect("Studio list", c.get("/api/studio/album-references"), 200).json()
check("Studio: 0 of 50", r["planAllows"] and r["limit"] == 50 and r["count"] == 0, r)
r = expect("upload 4 spreads", c.post("/api/studio/album-references", files=files(enumerate(SPREADS[:4]))), 200).json()
check("4 stored with layouts", r["count"] == 4 and all(x["count"] >= 1 and x["boxes"] for x in r["references"]), r)
check("names from filenames", [x["name"] for x in r["references"]] == [f"spread-{i}" for i in range(4)], [x["name"] for x in r["references"]])
thumb = expect("thumbnail", c.get(f"/api/studio/album-references/{r['references'][0]['id']}/thumb"), 200)
check("thumbnail is a JPEG", thumb.content[:3] == b"\xff\xd8\xff" and thumb.headers.get("Content-Type", "").startswith("image/jpeg"))
bad = expect("portrait image reported, others kept", c.post("/api/studio/album-references", files=[("files", ("tall.jpg", b"not an image", "image/jpeg")), ("files", ("ok.jpg", SPREADS[4], "image/jpeg"))]), 200).json()
check("problem listed, good one added", bad["count"] == 5 and len(bad["problems"]) == 1, bad.get("problems"))
other = Client(); register(other, "Other", "Other", "other.refs@example.com", "9876555555")
expect("other studio can't see the thumb", other.get(f"/api/studio/album-references/{r['references'][0]['id']}/thumb"), (403, 404))
r = expect("delete one", c.delete(f"/api/studio/album-references/{r['references'][0]['id']}"), 200).json()
check("4 left", r["count"] == 4)

section("Limit")
psql("update fb_user_subscription set entitlements = jsonb_set(entitlements, '{limits,albumReferences}', '6') where plan_code='STUDIO'")
plan_blocked("3 more would pass 6", c.post("/api/studio/album-references", files=files(enumerate(SPREADS[5:8]))), limit="albumReferences")
expect("2 more fit exactly", c.post("/api/studio/album-references", files=files(enumerate(SPREADS[5:7]))), 200)
plan_blocked("7th blocked", c.post("/api/studio/album-references", files=files([(9, SPREADS[7])])), limit="albumReferences")
psql("update fb_user_subscription set entitlements = jsonb_set(entitlements, '{limits,albumReferences}', '50') where plan_code='STUDIO'")
set_plan("refs@example.com", "FREE")
expect("after a downgrade, unknown ids still answer 404 (delete stays available)", c.delete("/api/studio/album-references/0000"), (404,))
set_plan("refs@example.com", "BUSINESS")
check("Business limit 100", c.get("/api/studio/album-references").json()["limit"] == 100)

failed = [r for r in RESULTS if not r[2]]
print(f"\nRESULT: {len(RESULTS) - len(failed)} passed, {len(failed)} failed")
for r in failed:
    print("  FAILED:", r[0], r[1], str(r[3])[:300])
sys.exit(1 if failed else 0)
