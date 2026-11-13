"""WhatsApp-code sign-in for photographers and customers, and remembered app sessions (until the app updates)."""
import os
OUT = os.getenv("FB_E2E_OUT", "out"); os.makedirs(OUT, exist_ok=True)
import sys
import requests
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "e2e_admin.py")).read().split("# ------------------------------------------------------------------------------------------------")[0])
STUB = os.getenv("FB_STUB_URL", "http://127.0.0.1:8001")

APP_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130 Mobile FrameBucketApp/1.2.0"
DESK_UA = "Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/130 Electron/33 FrameBucket/1.1.0"

def code_for(mobile, mark):
    import time; time.sleep(0.4)
    return log_find(rf"code for {mobile}: (\d+)", mark)

section("Sign-up without a password")
anon = Client()
mark = log_size()
r = expect("register with no password", anon.post("/api/auth/register", {"fullName": "Ravi Kumar", "studioName": "Ravi Clicks", "email": "ravi@example.com", "mobileNumber": "9876511111"}), 201)
code = code_for("+919876511111", mark) or code_for("9876511111", mark)
check("WhatsApp code sent at sign-up", bool(code), open(LOG).read()[mark:][-600:])
check("password hash is random, not blank", len(psql("select password_hash from fb_user where email='ravi@example.com'")) > 20)
r = expect("sign in with that code", anon.post("/api/auth/login/verify-code", {"identifier": "9876511111", "code": code}), 200)
check("mobile now verified", r.json().get("mobileVerified") is True, r.text)
check("email not yet verified does not block", r.json().get("emailVerified") is False, r.text)
expect("session works", anon.get("/api/auth/me"), 200)
check("no remembered-device cookie for a browser", anon.s.cookies.get("FB_DEVICE") is None)

section("Code sign-in")
c = Client()
expect("unknown number refused", c.post("/api/auth/login/request-code", {"identifier": "9000000001"}), 400)
mark = log_size()
r = expect("request code by email", c.post("/api/auth/login/request-code", {"identifier": "ravi@example.com"}), 200)
check("masked number shown", r.json().get("sentTo", "").endswith("1111") and "98765" not in r.json().get("sentTo", ""), r.text)
code = code_for("+919876511111", mark) or code_for("9876511111", mark)
r2 = expect("second request inside 60 s is a cooldown", c.post("/api/auth/login/request-code", {"identifier": "ravi@example.com"}), 200)
check("cooldown message", "moment ago" in r2.json()["message"], r2.text)
wrong = "000000" if code != "000000" else "111111"
expect("wrong code refused", c.post("/api/auth/login/verify-code", {"identifier": "ravi@example.com", "code": wrong}), 400)
expect("not signed in after a wrong code", c.get("/api/auth/me"), (401, 403))
expect("right code signs in", c.post("/api/auth/login/verify-code", {"identifier": "ravi@example.com", "code": code}), 200)
expect("code is single-use", Client().post("/api/auth/login/verify-code", {"identifier": "ravi@example.com", "code": code}), 400)
psql("update fb_photographer_login_otp set created_at = created_at - interval '2 minutes'")
mark = log_size()
c5 = Client(); c5.post("/api/auth/login/request-code", {"identifier": "9876511111"})
code5 = code_for("+919876511111", mark) or code_for("9876511111", mark)
for i in range(5):
    c5.post("/api/auth/login/verify-code", {"identifier": "9876511111", "code": wrong})
expect("locked after 5 wrong attempts, even with the right code", c5.post("/api/auth/login/verify-code", {"identifier": "9876511111", "code": code5}), 400)

section("Remembered app sessions")
def app_login(ua, identifier="ravi@example.com", mobile="9876511111"):
    psql("update fb_photographer_login_otp set created_at = created_at - interval '2 minutes'")
    a = Client(); a.s.headers["User-Agent"] = ua
    mark = log_size()
    a.post("/api/auth/login/request-code", {"identifier": identifier})
    code = code_for("+91" + mobile, mark) or code_for(mobile, mark)
    expect(f"app sign-in ({ua.split()[-1]})", a.post("/api/auth/login/verify-code", {"identifier": identifier, "code": code}), 200)
    return a

a = app_login(APP_UA)
check("Android app gets FB_DEVICE cookie", bool(a.s.cookies.get("FB_DEVICE")))
check("cookie is HttpOnly", any(ck.name == "FB_DEVICE" and ck.has_nonstandard_attr("HttpOnly") for ck in a.s.cookies), [ (ck.name, ck._rest) for ck in a.s.cookies])
check("stored with app name + version", psql("select app_name||' '||app_version from fb_device_session where revoked_at is null order by created_at desc limit 1") == "android 1.2.0")
check("only a hash is stored", psql(f"select count(*) from fb_device_session where token_hash = '{a.s.cookies.get('FB_DEVICE')}'") == "0")
a.s.cookies.set("JSESSIONID", "expired-session")  # server restart / idle timeout
for ck in list(a.s.cookies):
    if ck.name == "JSESSIONID": a.s.cookies.clear(ck.domain, ck.path, ck.name)
expect("session restored from the device token", a.get("/api/auth/me"), 200)
check("restored user is right", a.get("/api/auth/me").json().get("email") == "ravi@example.com")
for ck in list(a.s.cookies):
    if ck.name == "JSESSIONID": a.s.cookies.clear(ck.domain, ck.path, ck.name)
a.s.headers["User-Agent"] = APP_UA.replace("1.2.0", "1.3.0")
expect("after an app update the old sign-in no longer works", a.get("/api/auth/me"), (401, 403))
check("old token revoked", psql("select count(*) from fb_device_session where app_version='1.2.0' and revoked_at is null") == "0")

d = app_login(DESK_UA)
check("desktop app remembered", psql("select count(*) from fb_device_session where app_name='desktop' and revoked_at is null") == "1")
expect("desktop sign-out", d.post("/api/auth/logout"), 204)
check("sign-out revokes the device", psql("select count(*) from fb_device_session where app_name='desktop' and revoked_at is null") == "0")
for ck in list(d.s.cookies):
    if ck.name == "JSESSIONID": d.s.cookies.clear(ck.domain, ck.path, ck.name)
expect("signed out stays signed out", d.get("/api/auth/me"), (401, 403))
b = Client()
b.s.cookies.set("FB_DEVICE", "made-up-token")
expect("made-up token gets nothing", b.get("/api/auth/me"), (401, 403))

old = app_login("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130 Mobile FrameBucketApp")
check("older app build (no version in its user agent) is remembered too", psql("select count(*) from fb_device_session where app_version='0' and revoked_at is null") == "1")

section("Customers")
owner = Client(); register(owner, "Asha Nair", "Lens & Light", "asha.otp@example.com", "9876500077")
expect("photographer adds a customer", owner.post("/api/customers", {"name": "Neha Rao", "email": "neha@example.com", "mobileNumber": "9123456781", "dateOfBirth": "1994-03-12", "address": "12 Indiranagar, Bengaluru 560038"}), (200, 201))
cu = Client(); cu.s.headers["User-Agent"] = APP_UA
mark = log_size()
cu.post("/api/customer-auth/request-code", {"mobileNumber": "9123456781"})
ccode = code_for("+919123456781", mark) or code_for("9123456781", mark) or log_find(r"(\d{6})", mark)
expect("customer signs in with WhatsApp code", cu.post("/api/customer-auth/verify-code", {"mobileNumber": "9123456781", "code": ccode}), 200)
check("customer app remembered", psql("select count(*) from fb_device_session where principal_kind='CUSTOMER' and revoked_at is null") == "1")
for ck in list(cu.s.cookies):
    if ck.name == "JSESSIONID": cu.s.cookies.clear(ck.domain, ck.path, ck.name)
expect("customer session restored", cu.get("/api/customer-auth/me"), 200)
expect("customer can't reach photographer APIs", cu.get("/api/customers"), (401, 403))

section("Old password sign-in still works for existing API clients")
expect("password login", Client().post("/api/auth/login", {"username": "asha.otp@example.com", "password": PASSWORD}), 200)

failed = [r for r in RESULTS if not r[2]]
print(f"\nRESULT: {len(RESULTS) - len(failed)} passed, {len(failed)} failed")
for r in failed:
    print("  FAILED:", r[0], r[1], str(r[3])[:300])
sys.exit(1 if failed else 0)
