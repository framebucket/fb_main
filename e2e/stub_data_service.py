"""Stand-in for the fb_data FastAPI service used by the end-to-end suites.

Gallery/outbox/download endpoints are canned fixtures, and every request is logged so the suites can
check that Java sends the right event / customer IDs and the service key.

The editing-style, preset, album-reference, album-design and invitation endpoints run the REAL fb_data code in-process when
FB_DATA_SRC points at a checkout of the fb_data repository (with its dependencies installed) and
FB_DATA_DATABASE_URL at the test database; otherwise they answer 501 and those suites are skipped.

    python stub_data_service.py 8001
"""
import io, json, os, sys, zipfile
from pathlib import Path
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs
from PIL import Image

PHOTOS = ["a1b2c3d4e5f60001", "a1b2c3d4e5f60002", "a1b2c3d4e5f60003"]
CALLS = []
ALBUM_EVENTS = set()
RICH = set()  # events that also have a film and album spreads (enabled by POST /__rich/<event_id>)
VIDEO = "f1lm000000000001"
SPREADS = {"sprd000000000010": "spread-10.jpg", "sprd000000000002": "spread-2.jpg", "sprd000000000001": "spread-1.jpg"}
MP4 = b"\x00\x00\x00\x18ftypmp42" + bytes(range(256)) * 40

def media_for(event_id, folder):
    items = [{"photo_id": pid, "photo_number": i + 1, "media_type": "image", "status": "READY", "folder_id": "root", "original_filename": f"IMG_{i+1}.CR3"} for i, pid in enumerate(PHOTOS)]
    if event_id in RICH:
        items.append({"photo_id": VIDEO, "photo_number": 4, "media_type": "video", "status": "READY", "folder_id": "films", "original_filename": "highlights.mp4", "size_bytes": len(MP4)})
        for n, (pid, name) in enumerate(SPREADS.items()):
            items.append({"photo_id": pid, "photo_number": 5 + n, "media_type": "image", "status": "READY", "folder_id": "proofs", "original_filename": name})
    return [m for m in items if folder is None or m["folder_id"] == folder]

def jpeg(colour):
    buf = io.BytesIO(); Image.new("RGB", (64, 48), colour).save(buf, "JPEG"); return buf.getvalue()

_SVC = None


class NotConfigured(Exception):
    pass


def real_service():
    global _SVC
    if _SVC is None:
        src = os.getenv("FB_DATA_SRC")
        if not src:
            raise NotConfigured("Set FB_DATA_SRC to an fb_data checkout to serve this route.")
        sys.path.insert(0, src)
        os.environ.setdefault("FB_DATABASE_URL", os.getenv("FB_DATA_DATABASE_URL", ""))
        os.environ.setdefault("FB_STORAGE_BACKEND", "local")
        os.environ.setdefault("FB_DATA_ROOT", os.path.abspath(os.getenv("FB_E2E_OUT", "out") + "/data-storage"))
        os.environ.setdefault("FB_TEMP_ROOT", os.path.abspath(os.getenv("FB_E2E_OUT", "out") + "/data-tmp"))
        os.environ.setdefault("FB_FACE_MATCHING_ENABLED", "false")
        os.environ.setdefault("FB_SERVICE_TOKEN", "t")
        from app.config import Settings
        from app.db import Database
        from app.service import PhotoService
        settings = Settings.from_env()
        service = PhotoService(settings, database=Database(settings.database_url, 4))
        service.db.migrate()
        _SVC = service
    return _SVC

def drain():
    svc = real_service(); db = svc.db; ran = 0
    while True:
        r = db.run("select json_agg(j) from (select id,kind,payload from fb_data.job where status='QUEUED' order by id limit 50) j")
        rows = json.loads(r) if r else None
        if not rows: return ran
        for j in rows:
            db.run(f"update fb_data.job set status='RUNNING', locked_at=now() where id={j['id']}", count=True)
            try: svc.run_job(j['kind'], j['payload']); st = 'DONE'
            except Exception as e:
                import traceback; traceback.print_exc(); st = 'FAILED'; svc.job_failed(j['kind'], j['payload'], str(e))
            db.run(f"update fb_data.job set status='{st}' where id={j['id']}", count=True); ran += 1

def multipart(content_type, body):
    from email.parser import BytesParser
    from email.policy import default
    msg = BytesParser(policy=default).parsebytes(b"Content-Type: " + content_type.encode() + b"\r\n\r\n" + body)
    return [(part.get_param("name", header="content-disposition"), part.get_filename(), part.get_payload(decode=True))
            for part in msg.iter_parts()]

class H(BaseHTTPRequestHandler):
    def log_message(self, *a): pass

    def _send(self, code, body, ctype="application/json"):
        if isinstance(body, (dict, list)): body = json.dumps(body).encode()
        self.send_response(code); self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body)

    def _route(self, method):
        u = urlparse(self.path); q = {k: v[0] for k, v in parse_qs(u.query).items()}
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length) if length else b""
        if not length and "chunked" in (self.headers.get("Transfer-Encoding") or "").lower():
            chunks = []
            while True:
                size = int(self.rfile.readline().strip().split(b";")[0], 16)
                if size == 0:
                    self.rfile.readline(); break
                chunks.append(self.rfile.read(size)); self.rfile.readline()
            body = b"".join(chunks)
        CALLS.append({"m": method, "path": u.path, "q": q, "key": self.headers.get("X-FrameBucket-Service-Key"), "body": body[:300].decode("utf-8", "replace")})
        p = u.path.split("/")
        if u.path == "/__calls": return self._send(200, CALLS)
        if u.path == "/health": return self._send(200, {"status": "ok"})
        if u.path.startswith("/__rich/"):
            RICH.add(u.path.split("/")[2]); return self._send(200, {"rich": sorted(RICH)})
        if u.path == "/api/v1/outbox/claim": return self._send(200, {"items": []})
        if u.path == "/api/v1/events" and method == "POST": return self._send(200, {"status": "ok"})
        try:
            return self._dispatch(method, u, p, q, body)
        except NotConfigured as exc:
            return self._send(501, {"detail": str(exc)})

    def _dispatch(self, method, u, p, q, body):
        if u.path.startswith("/__album_setup/"):
            return self._album_setup(p[2], q)
        if u.path == "/__drain":
            return self._send(200, {"ran": drain()})
        if len(p) >= 6 and p[1:4] == ["api", "v1", "events"] and p[5] == "invitation":
            return self._invitation(method, p[4], p[6:], q, body)
        if len(p) >= 6 and p[1:4] == ["api", "v1", "events"] and p[5] == "album-designs":
            return self._album(method, p[4], p[6:], q, body)
        if len(p) >= 6 and p[1:4] == ["api", "v1", "events"]:
            rest = p[5:]
            if rest == ["folders"]: return self._send(200, {"folders": [{"folder_id": "root", "name": "Gallery", "photo_count": 3}]})
            if rest == ["album-selection"]:
                return self._send(200, {"photo_ids": [PHOTOS[2], PHOTOS[0]]})
            if rest == ["photos"] and p[4] in ALBUM_EVENTS:
                return self._send(200, {"photos": real_service().list_gallery(p[4], q["customer_id"], q.get("folder_id"))})
            if rest == ["photos"]:
                return self._send(200, {"photos": media_for(p[4], q.get("folder_id"))})
            if len(rest) == 3 and rest[0] == "photos" and rest[2] == "preview-url": return self._send(200, {"url": None})
            if len(rest) == 3 and rest[0] == "photos" and rest[2] == "preview" and rest[1] == VIDEO and p[4] in RICH:
                rng = self.headers.get("Range")
                if rng and rng.startswith("bytes="):
                    start = int(rng[6:].split("-")[0] or 0); chunk = MP4[start:start + 1024]
                    self.send_response(206); self.send_header("Content-Type", "video/mp4"); self.send_header("Accept-Ranges", "bytes")
                    self.send_header("Content-Range", f"bytes {start}-{start + len(chunk) - 1}/{len(MP4)}")
                    self.send_header("Content-Length", str(len(chunk))); self.end_headers(); self.wfile.write(chunk); return
                return self._send(200, MP4, "video/mp4")
            if len(rest) == 3 and rest[0] == "photos" and rest[2] == "preview" and rest[1] in SPREADS and p[4] in RICH:
                buf = io.BytesIO(); Image.new("RGB", (400, 200), (90, 120, 200)).save(buf, "JPEG"); return self._send(200, buf.getvalue(), "image/jpeg")
            if len(rest) == 3 and rest[0] == "photos" and rest[2] == "preview":
                if rest[1] not in PHOTOS: return self._send(404, {"detail": "Photo not found"})
                return self._send(200, jpeg((200, 150, 80)), "image/jpeg")
            if rest == ["downloads"]:
                buf = io.BytesIO()
                with zipfile.ZipFile(buf, "w") as z: z.writestr("00001_IMG_1.jpg", jpeg((10, 10, 10)))
                return self._send(200, buf.getvalue(), "application/zip")
        if len(p) >= 6 and p[1:4] == ["api", "v1", "studios"] and p[5] == "presets":
            return self._presets(method, p[4], p[6:], q, body)
        if len(p) >= 6 and p[1:4] == ["api", "v1", "studios"] and p[5] == "album-references":
            return self._references(method, p[4], p[6:], q, body)
        if len(p) >= 6 and p[1:4] == ["api", "v1", "studios"] and p[5] == "style":
            return self._style(method, p[4], p[6:], q, body)
        return self._send(404, {"detail": f"stub has no route for {method} {u.path}"})

    def _style(self, method, pid, rest, q, body):
        """Studio look endpoints are served by the REAL data-service code (app/service.py), not stubbed."""
        svc = real_service()
        try:
            if rest == [] and method == "GET": return self._send(200, svc.studio_style(pid))
            if rest == [] and method == "PUT":
                d = json.loads(body or b"{}"); return self._send(200, svc.update_style_settings(pid, d.get("strength"), d.get("apply_style"), d.get("stamp_logo")))
            if rest == [] and method == "DELETE": return self._send(200, svc.clear_style(pid))
            parts = multipart(self.headers.get("Content-Type", ""), body)
            if rest == ["references"] and method == "POST":
                files = [data for name, fn, data in parts if name == "files"]
                return self._send(200, svc.save_style_references(pid, files))
            if rest == ["preview"] and method == "POST":
                name, fn, data = [x for x in parts if x[0] == "file"][0]
                st = int(q["strength"]) if "strength" in q else None
                return self._send(200, svc.style_preview(pid, fn, data, st), "image/jpeg")
        except ValueError as e:
            return self._send(422, {"detail": str(e)})
        return self._send(404, {"detail": "no style route"})

    def _presets(self, method, pid, rest, q, body):
        """Editing presets, served by the REAL data-service code (mirrors app/api.py)."""
        svc = real_service()
        from app.service import PresetLimitReached
        from app.presets import PresetError
        try:
            if rest == [] and method == "GET": return self._send(200, svc.studio_presets(pid))
            if rest == ["settings"] and method == "PUT":
                return self._send(200, svc.set_use_presets(pid, json.loads(body)["use_presets"]))
            if len(rest) == 1 and rest[0] not in ("preview", "settings") and method == "PATCH":
                d = json.loads(body or b"{}"); return self._send(200, svc.update_preset(pid, rest[0], d.get("name"), d.get("scene")))
            if len(rest) == 1 and method == "DELETE": return self._send(200, svc.delete_preset(pid, rest[0]))
            parts = multipart(self.headers.get("Content-Type", ""), body)
            fields = {name: (fn, data) for name, fn, data in parts}
            if rest == [] and method == "POST":
                fn, data = fields["file"]
                name = fields.get("name", (None, b""))[1].decode() or None
                scene = fields.get("scene", (None, b"any"))[1].decode()
                return self._send(200, svc.add_preset(pid, fn, data, name, scene, int(q["limit"]) if "limit" in q else None))
            if rest == ["preview"] and method == "POST":
                fn, data = fields["file"]
                jpeg_bytes, info = svc.preset_preview(pid, fn, data, q.get("preset_id"))
                self.send_response(200); self.send_header("Content-Type", "image/jpeg")
                self.send_header("X-Preset-Name", info.get("preset") or ""); self.send_header("X-Scene", info.get("scene") or "")
                self.send_header("Content-Length", str(len(jpeg_bytes))); self.end_headers(); self.wfile.write(jpeg_bytes); return
        except PresetLimitReached as e:
            return self._send(409, {"detail": str(e)})
        except LookupError as e:
            return self._send(404, {"detail": str(e)})
        except (PresetError, ValueError) as e:
            return self._send(422, {"detail": str(e)})
        return self._send(404, {"detail": "no preset route"})

    def _references(self, method, pid, rest, q, body):
        """Reference album designs, served by the REAL data-service code (mirrors app/api.py)."""
        svc = real_service()
        from app.service import ReferenceLimitReached
        try:
            if rest == [] and method == "GET": return self._send(200, svc.album_references(pid))
            if rest == [] and method == "POST":
                files = [(fn, data) for name, fn, data in multipart(self.headers.get("Content-Type", ""), body) if name == "files"]
                return self._send(200, svc.add_album_references(pid, files, int(q["limit"]) if "limit" in q else None))
            if len(rest) == 1 and method == "DELETE": return self._send(200, svc.delete_album_reference(pid, rest[0]))
            if len(rest) == 2 and rest[1] == "thumb": return self._send(200, svc.album_reference_thumb(pid, rest[0]), "image/jpeg")
        except ReferenceLimitReached as e:
            return self._send(409, {"detail": str(e)})
        except LookupError as e:
            return self._send(404, {"detail": str(e)})
        except ValueError as e:
            return self._send(422, {"detail": str(e)})
        return self._send(404, {"detail": "no reference route"})

    def _album_setup(self, event_id, q):
        """Create the fb_data event with real photos and a submitted album selection (as the real flow would)."""
        import samples
        svc = real_service(); pid, cid = q["photographer_id"], q["customer_id"]
        svc.create_event(event_id, pid, cid, "EVT"); ALBUM_EVENTS.add(event_id)
        ids = [svc.ingest_file(f, f"IMG_{i:04d}.jpg", event_id, pid, cid)["photo_id"]
               for i, f in enumerate(samples.write_photos(Path(os.getenv("FB_E2E_OUT", "out")) / f"photos-{event_id[:8]}", 30))]
        drain()
        svc.submit_album(event_id, pid, cid, ids)
        drain()
        return self._send(200, {"photos": len(ids)})

    def _album(self, method, event_id, rest, q, body):
        """Album auto-design endpoints are served by the REAL data-service code."""
        svc = real_service()
        from app.service import DesignNotReady, DirectDownload
        d = json.loads(body or b"{}") if method == "POST" else {}
        pid, cid = q.get("photographer_id") or d.get("photographer_id"), q.get("customer_id") or d.get("customer_id")
        try:
            if rest == [] and method == "GET": return self._send(200, {"submissions": svc.album_submissions(event_id, pid, cid)})
            if rest == [] and method == "POST":
                return self._send(200, svc.create_album_design(event_id, pid, cid, d["submission_id"], d["height_in"], d["width_in"], d["sheets"], d.get("dpi", 300), d.get("size_label")))
            if len(rest) == 1 and method == "GET": return self._send(200, svc.album_design_status(event_id, rest[0], pid, cid))
            if len(rest) == 3 and rest[1] == "spreads":
                path, _ = svc.album_design_preview(event_id, rest[0], int(rest[2]), pid, cid)
                return self._send(200, path.read_bytes(), "image/jpeg")
            if len(rest) == 3 and rest[1] == "download":
                path, filename, _ = svc.album_design_download(event_id, rest[0], rest[2], pid, cid)
                data = path.read_bytes()
                self.send_response(200); self.send_header("Content-Type", "application/zip")
                self.send_header("Content-Disposition", f'attachment; filename="{filename}"')
                self.send_header("Content-Length", str(len(data))); self.end_headers(); self.wfile.write(data); return
            if len(rest) == 2 and rest[1] == "publish": return self._send(200, svc.publish_album_design(event_id, rest[0], pid, cid))
        except DesignNotReady as e:
            return self._send(409, {"detail": str(e)})
        except FileNotFoundError as e:
            return self._send(404, {"detail": str(e)})
        except ValueError as e:
            return self._send(422, {"detail": str(e)})
        return self._send(404, {"detail": "no album-design route"})

    def _invitation(self, method, event_id, rest, q, body):
        svc = real_service()
        from app.invitation import InvitationError
        d = json.loads(body or b"{}") if method in ("PUT", "POST") and rest != ["photo"] else {}
        pid, cid = q.get("photographer_id") or d.get("photographer_id"), q.get("customer_id") or d.get("customer_id")
        try:
            if rest == [] and method == "GET": return self._send(200, svc.invitation(event_id, pid, cid))
            if rest == [] and method == "PUT": return self._send(200, svc.save_invitation_details(event_id, pid, cid, d["details"]))
            if rest == ["photo"] and method == "POST":
                data = [x for x in multipart(self.headers.get("Content-Type", ""), body) if x[0] == "file"][0][2]
                return self._send(200, svc.save_invitation_photo(event_id, pid, cid, data))
            if rest == ["photo"] and method == "DELETE": return self._send(200, svc.delete_invitation_photo(event_id, pid, cid))
            if rest == ["designs"] and method == "POST": return self._send(200, svc.generate_invitation_designs(event_id, pid, cid))
            if len(rest) == 2 and rest[0] == "designs": return self._send(200, svc.invitation_preview(event_id, cid, int(rest[1])), "image/jpeg")
            if rest == ["finalize"]: return self._send(200, svc.finalize_invitation(event_id, pid, cid, d["index"]))
            if rest in (["video"], ["poster"]):
                path, _, ctype = svc.invitation_file(event_id, cid, rest[0])
                return self._send(200, path.read_bytes(), ctype)
        except InvitationError as e:
            return self._send(422, {"detail": str(e)})
        except FileNotFoundError as e:
            return self._send(404, {"detail": str(e)})
        except ValueError as e:
            return self._send(404, {"detail": str(e)})
        return self._send(404, {"detail": "no invitation route"})

    def do_GET(self): self._route("GET")
    def do_POST(self): self._route("POST")
    def do_PUT(self): self._route("PUT")
    def do_DELETE(self): self._route("DELETE")
    def do_PATCH(self): self._route("PATCH")

ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1]) if len(sys.argv) > 1 else 8001), H).serve_forever()
