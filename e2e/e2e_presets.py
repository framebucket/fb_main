"""Editing presets: plan limits (Free 3 ... Business 25), upload/rename/delete, AI preview (needs fb_data)."""
import os
OUT = os.getenv("FB_E2E_OUT", "out"); os.makedirs(OUT, exist_ok=True)
import sys
import requests
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "e2e_admin.py")).read().split("# ------------------------------------------------------------------------------------------------")[0])
STUB = os.getenv("FB_STUB_URL", "http://127.0.0.1:8001")
import samples

probe = requests.get(STUB + "/api/v1/studios/probe/presets")
if probe.status_code == 501:
    print("SKIPPED: the real fb_data code is not available to the stub (set FB_DATA_SRC).")
    sys.exit(0)
FILES = {"warm.xmp": samples.XMP_PRESET, "moody.lrtemplate": samples.LRTEMPLATE_PRESET, "teal.cube": samples.cube_preset()}
PHOTO = samples.jpeg(samples.photo(7, (1600, 1067)))

owner = Client(); register(owner, "Owner", "FB", "owner@framebucket.in", "9876500001")
def set_plan(email, code):
    expect(f"admin sets {code}", owner.put(f"/api/admin/photographers/{uid(email)}/plan", {"planCode": code, "billingCycle": "MONTHLY", "months": 1}), 200)

section("Plan limits")
plans = {p["code"]: p for p in Client().get("/api/subscriptions").json()}
check("preset limits 3/5/10/25", [plans[c]["limits"].get("editingPresets") for c in ["FREE", "SOLO", "STUDIO", "BUSINESS"]] == [3, 5, 10, 25], {c: plans[c]["limits"] for c in plans})
check("reference limits 0/0/50/100", [plans[c]["limits"].get("albumReferences") for c in ["FREE", "SOLO", "STUDIO", "BUSINESS"]] == [0, 0, 50, 100])

c = Client(); register(c, "Preset Pro", "Pixels", "preset@example.com", "9876522222")
set_plan("preset@example.com", "FREE")
r = expect("list presets", c.get("/api/studio/editing-presets"), 200).json()
check("Free: 0 of 3", r["count"] == 0 and r["limit"] == 3 and r["use_presets"] is True, r)

section("Upload, limit, delete, rename")
up = lambda name, scene="any": c.post("/api/studio/editing-presets", files={"file": (name, FILES[name])}, data={"scene": scene})
expect("upload .xmp", up("warm.xmp", "portrait"), 200)
expect("upload .lrtemplate", up("moody.lrtemplate", "indoor"), 200)
r = expect("upload .cube", up("teal.cube", "outdoor"), 200).json()
check("3 presets with names and scenes", [(p["name"], p["scene"]) for p in r["presets"]] == [("Golden Hour Bride", "portrait"), ("Moody Film", "indoor"), ("Teal Orange", "outdoor")], r["presets"])
plan_blocked("4th preset on Free", up("warm.xmp"), limit="editingPresets")
expect("wrong file type refused", c.post("/api/studio/editing-presets", files={"file": ("photo.jpg", PHOTO)}), 400)
r = expect("delete one", c.delete(f"/api/studio/editing-presets/{r['presets'][1]['id']}"), 200).json()
check("2 left", r["count"] == 2)
r = expect("replace it", up("moody.lrtemplate", "night"), 200).json()
moody = [p for p in r["presets"] if p["kind"] == "lrtemplate"][0]
r = expect("rename", c.req("PATCH", f"/api/studio/editing-presets/{moody['id']}", {"name": "Sangeet night"}), 200).json()
check("renamed", any(p["name"] == "Sangeet night" and p["scene"] == "night" for p in r["presets"]), r["presets"])
expect("bad scene refused", c.req("PATCH", f"/api/studio/editing-presets/{moody['id']}", {"scene": "beach"}), 400)
other = Client(); register(other, "Other", "Other", "other.preset@example.com", "9876533333")
expect("another studio can't delete it", other.delete(f"/api/studio/editing-presets/{moody['id']}"), 404)
expect("another studio sees none", other.get("/api/studio/editing-presets"), 200)
check("isolation", other.get("/api/studio/editing-presets").json()["count"] == 0)

section("AI preview and switch")
r = expect("AI preview", c.post("/api/studio/editing-presets/preview", files={"file": ("both.jpg", PHOTO)}), 200).json()
check("preview names the chosen preset", r["preset"] in {"Golden Hour Bride", "Sangeet night", "Teal Orange"} and r["image"].startswith("data:image/jpeg;base64,"), {k: str(v)[:60] for k, v in r.items()})
r = expect("forced preview", c.post(f"/api/studio/editing-presets/preview?presetId={moody['id']}", files={"file": ("both.jpg", PHOTO)}), 200).json()
check("forced preset used", r["preset"] == "Sangeet night", r.get("preset"))
r = expect("pause AI picking", c.put("/api/studio/editing-presets/settings", {"usePresets": False}), 200).json()
check("paused", r["use_presets"] is False)

section("Bigger plans")
set_plan("preset@example.com", "SOLO")
check("Solo limit 5", c.get("/api/studio/editing-presets").json()["limit"] == 5)
expect("4th on Solo", up("warm.xmp"), 200); expect("5th on Solo", up("teal.cube"), 200)
plan_blocked("6th on Solo", up("warm.xmp"), limit="editingPresets")
set_plan("preset@example.com", "BUSINESS")
check("Business limit 25", c.get("/api/studio/editing-presets").json()["limit"] == 25)
expect("6th on Business", up("warm.xmp"), 200)

failed = [r for r in RESULTS if not r[2]]
print(f"\nRESULT: {len(RESULTS) - len(failed)} passed, {len(failed)} failed")
for r in failed:
    print("  FAILED:", r[0], r[1], str(r[3])[:300])
sys.exit(1 if failed else 0)
