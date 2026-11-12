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
import samples

probe = requests.get(STUB + "/api/v1/events/probe/invitation", params={"photographer_id": "p", "customer_id": "c"})
if probe.status_code == 501:
    print("SKIPPED: the real fb_data code is not available to the stub (set FB_DATA_SRC).")
    sys.exit(0)
section("Editing style & logo")
ph = Client(); register_photographer(ph, "Asha Rao", "Lens & Light", "asha@example.com", "9876543210")
other = Client(); register_photographer(other, "Ravi Iyer", "Ravi Clicks", "ravi@example.com", "9876500000")
st = expect("get style (empty)", ph.get("/api/studio/editing-style"), 200).json()
check("no look yet, trial plan allows it, no logo", not st["has_style"] and st["planAllows"] and not st["has_logo"], st)
expect("upload studio logo", ph.post("/api/studio/brand/logo", files={"file": ("logo.png", samples.logo_png(), "image/png")}), 200)
check("logo now visible to data service", ph.get("/api/studio/editing-style").json()["has_logo"])
few=[("files", (f"r{i}.jpg", samples.graded(i, 800), "image/jpeg")) for i in (0,2,4)]
r = expect("too few references rejected", ph.post("/api/studio/editing-style/references", files=few), 422)
check("clear message", "at least 5" in r.text, r.text)
refs=[("files", (f"r{i}.jpg", samples.graded(i, 1600), "image/jpeg")) for i in (0,2,4,5,7,8)]
st = expect("learn look from 6 references", ph.post("/api/studio/editing-style/references", files=refs), 200).json()
check("look learned", st["has_style"] and st["reference_count"]==6 and st["summary"]["words"], st)
print("   ", st["summary"]["words"])
st = expect("save settings", ph.put("/api/studio/editing-style", {"strength": 65, "stampLogo": True, "applyStyle": True}), 200).json()
check("settings stored", st["strength"]==65 and st["stamp_logo"] and st["apply_style"], st)
expect("invalid strength rejected", ph.put("/api/studio/editing-style", {"strength": 140}), 400)
r = expect("preview on a sample photo", ph.post("/api/studio/editing-style/preview?strength=90", files={"file": ("IMG_9.jpg", samples.jpeg(samples.photo(9)), "image/jpeg")}), 200)
check("preview is a JPEG", r.headers.get("Content-Type","").startswith("image/jpeg") and r.content[:3]==b"\xff\xd8\xff", r.headers)
open(os.path.join(OUT, "style-preview.jpg"), "wb").write(r.content)
ost = expect("other studio sees its own (empty) look", other.get("/api/studio/editing-style"), 200).json()
check("studios are isolated", not ost["has_style"], ost)
expect("other studio preview refused without a look", other.post("/api/studio/editing-style/preview", files={"file": ("a.jpg", samples.jpeg(samples.photo(1, (400, 300))), "image/jpeg")}), 422)
expect("needs login", Client().get("/api/studio/editing-style"), 401)
st = expect("remove look", ph.delete("/api/studio/editing-style"), 200).json()
check("look removed, settings kept", not st["has_style"] and st["strength"]==65, st)
failed = [r for r in RESULTS if not r[2]]
print(f"\nRESULT: {len(RESULTS) - len(failed)} passed, {len(failed)} failed")
for r in failed:
    print("  FAILED:", r[0], r[1], str(r[3])[:300])
sys.exit(1 if failed else 0)
