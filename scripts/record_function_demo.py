import argparse
import base64
import bz2
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request
import websocket

from io import BytesIO
from pathlib import Path

from PIL import Image


WIDTH = 1920
HEIGHT = 1080
FPS = 30
CAPTURE_FPS = 5
FRAME_REPEAT = FPS // CAPTURE_FPS
cv2 = None
np = None


def load_video_libs(repo):
    global cv2, np
    dll = Path(repo) / "openh264-1.8.0-win64.dll"
    if not dll.exists():
        try:
            url = "https://github.com/cisco/openh264/releases/download/v1.8.0/openh264-1.8.0-win64.dll.bz2"
            packed = urllib.request.urlopen(url, timeout=60).read()
            dll.write_bytes(bz2.decompress(packed))
        except Exception as exc:
            print(f"Warning: failed to prepare OpenH264 DLL, falling back if needed: {exc}", file=sys.stderr)
    os.environ["PATH"] = str(repo) + os.pathsep + os.environ.get("PATH", "")
    import cv2 as cv2_module
    import numpy as np_module
    cv2 = cv2_module
    np = np_module


class CdpClient:
    def __init__(self, ws_url):
        self.ws = websocket.create_connection(ws_url, timeout=30)
        self.next_id = 1

    def send(self, method, params=None, timeout=30):
        message_id = self.next_id
        self.next_id += 1
        self.ws.send(json.dumps({
            "id": message_id,
            "method": method,
            "params": params or {},
        }))
        deadline = time.time() + timeout
        while time.time() < deadline:
            raw = self.ws.recv()
            data = json.loads(raw)
            if data.get("id") == message_id:
                if "error" in data:
                    raise RuntimeError(f"CDP {method} failed: {data['error']}")
                return data.get("result", {})
        raise TimeoutError(f"Timed out waiting for {method}")

    def eval(self, expression, timeout=30):
        result = self.send("Runtime.evaluate", {
            "expression": expression,
            "awaitPromise": True,
            "returnByValue": True,
        }, timeout=timeout)
        remote = result.get("result", {})
        if "value" in remote:
            return remote["value"]
        return None

    def close(self):
        try:
            self.ws.close()
        except Exception:
            pass


class Recorder:
    def __init__(self, cdp, output):
        self.cdp = cdp
        self.output = Path(output)
        self.writer = None
        self.frames = 0

    def __enter__(self):
        self.output.parent.mkdir(parents=True, exist_ok=True)
        fourcc = cv2.VideoWriter_fourcc(*"avc1")
        self.writer = cv2.VideoWriter(str(self.output), fourcc, FPS, (WIDTH, HEIGHT))
        if not self.writer.isOpened():
            fourcc = cv2.VideoWriter_fourcc(*"mp4v")
            self.writer = cv2.VideoWriter(str(self.output), fourcc, FPS, (WIDTH, HEIGHT))
        if not self.writer.isOpened():
            raise RuntimeError("Could not open MP4 VideoWriter")
        return self

    def __exit__(self, exc_type, exc, tb):
        if self.writer:
            self.writer.release()

    def frame(self):
        result = self.cdp.send("Page.captureScreenshot", {
            "format": "jpeg",
            "quality": 82,
            "captureBeyondViewport": False,
        }, timeout=20)
        image = Image.open(BytesIO(base64.b64decode(result["data"]))).convert("RGB")
        if image.size != (WIDTH, HEIGHT):
            image = image.resize((WIDTH, HEIGHT), Image.LANCZOS)
        frame = cv2.cvtColor(np.asarray(image), cv2.COLOR_RGB2BGR)
        for _ in range(FRAME_REPEAT):
            self.writer.write(frame)
            self.frames += 1

    def seconds(self, duration):
        for _ in range(max(1, int(duration * CAPTURE_FPS))):
            self.frame()

    def until(self, predicate_js, timeout, settle_seconds=2):
        deadline = time.time() + timeout
        ready = False
        while time.time() < deadline:
            self.frame()
            try:
                ready = bool(self.cdp.eval(predicate_js, timeout=5))
            except Exception:
                ready = False
            if ready:
                self.seconds(settle_seconds)
                return True
        return False


def wait_http(url, timeout=120):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(url, timeout=3) as response:
                if response.status == 200:
                    return
        except Exception:
            time.sleep(1)
    raise TimeoutError(f"Application is not reachable: {url}")


def find_chrome():
    candidates = [
        r"C:\Program Files\Google\Chrome\Application\chrome.exe",
        r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
    ]
    for candidate in candidates:
        if Path(candidate).exists():
            return candidate
    found = shutil.which("chrome") or shutil.which("chrome.exe")
    if found:
        return found
    raise FileNotFoundError("Chrome executable was not found")


def wait_for_page_ws(profile, port, timeout=30):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            actual_port = port
            if actual_port == 0:
                active_port = Path(profile) / "DevToolsActivePort"
                if not active_port.exists():
                    time.sleep(0.5)
                    continue
                actual_port = int(active_port.read_text(encoding="utf-8").splitlines()[0])
            list_url = f"http://127.0.0.1:{actual_port}/json/list"
            with urllib.request.urlopen(list_url, timeout=2) as response:
                pages = json.loads(response.read().decode("utf-8"))
                for page in pages:
                    if page.get("type") == "page" and page.get("webSocketDebuggerUrl"):
                        return page["webSocketDebuggerUrl"]
        except Exception:
            time.sleep(0.5)
    raise TimeoutError("Chrome remote debugging endpoint did not start")


def js_click(selector):
    return f"""
(() => {{
  const el = document.querySelector({json.dumps(selector)});
  if (!el) throw new Error('Missing selector: {selector}');
  el.click();
  return true;
}})()
"""


def js_set_value(selector, value):
    return f"""
(() => {{
  const el = document.querySelector({json.dumps(selector)});
  if (!el) throw new Error('Missing selector: {selector}');
  el.focus();
  el.value = {json.dumps(value, ensure_ascii=False)};
  el.dispatchEvent(new Event('input', {{ bubbles: true }}));
  el.dispatchEvent(new Event('change', {{ bubbles: true }}));
  return true;
}})()
"""


def type_value(cdp, recorder, selector, value, seconds=8):
    cdp.eval(js_set_value(selector, ""))
    steps = max(1, len(value))
    delay_frames = max(1, int((seconds * CAPTURE_FPS) / steps))
    current = ""
    for char in value:
        current += char
        cdp.eval(js_set_value(selector, current))
        for _ in range(delay_frames):
            recorder.frame()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default="http://127.0.0.1:9910/")
    parser.add_argument("--token", default=os.environ.get("CONTEST_API_TOKEN", "demo-recording-token"))
    parser.add_argument("--output", default="contest-deliverables/OnCallAgent_Function_Demo.mp4")
    parser.add_argument("--chrome-port", type=int, default=0)
    args = parser.parse_args()

    repo = Path(__file__).resolve().parents[1]
    os.chdir(repo)
    load_video_libs(repo)
    wait_http(args.url)

    upload_path = repo / "uploads" / "payment_order_runbook.md"
    upload_content = upload_path.read_text(encoding="utf-8")

    chrome = find_chrome()
    profile = Path(tempfile.mkdtemp(prefix="oncall-recording-chrome-"))
    chrome_proc = subprocess.Popen([
        chrome,
        f"--remote-debugging-port={args.chrome_port}",
        "--remote-allow-origins=*",
        f"--user-data-dir={profile}",
        "--headless=new",
        "--disable-gpu",
        "--hide-scrollbars",
        "--mute-audio",
        "--no-first-run",
        "--no-default-browser-check",
        f"--window-size={WIDTH},{HEIGHT}",
        args.url,
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    cdp = None
    try:
        ws_url = wait_for_page_ws(profile, args.chrome_port)
        cdp = CdpClient(ws_url)
        cdp.send("Page.enable")
        cdp.send("Runtime.enable")
        cdp.send("Emulation.setDeviceMetricsOverride", {
            "width": WIDTH,
            "height": HEIGHT,
            "deviceScaleFactor": 1,
            "mobile": False,
        })
        cdp.send("Page.navigate", {"url": args.url})
        cdp.eval("new Promise(resolve => window.addEventListener('load', resolve, {once:true}))", timeout=40)
        cdp.eval("document.body.style.cursor = 'default'; true")

        with Recorder(cdp, args.output) as recorder:
            recorder.seconds(8)

            # 1. Home and product structure.
            cdp.eval(js_click("#knowledgeBaseBtn"))
            recorder.seconds(8)
            cdp.eval(js_click("#chatNavBtn"))
            recorder.seconds(6)
            cdp.eval(js_click("#authDebugBtn"))
            recorder.seconds(7)
            cdp.eval(js_click("#chatNavBtn"))
            recorder.seconds(7)

            # 2. Knowledge base management and upload.
            cdp.eval(js_click("#knowledgeBaseBtn"))
            recorder.until("document.querySelectorAll('.kb-file-item').length >= 7", 25, 12)
            cdp.eval(f"""
(async () => {{
  const app = window.superBizApp;
  if (!app) throw new Error('App instance missing');
  const file = new File([{json.dumps(upload_content, ensure_ascii=False)}], 'payment_order_runbook.md', {{ type: 'text/markdown' }});
  await app.uploadFile(file);
  return true;
}})()
""", timeout=90)
            recorder.until("document.querySelectorAll('.kb-file-item').length >= 7 && !document.querySelector('.upload-overlay.show')", 40, 16)

            # 3. RAG question, answer, and TopK=3 references.
            cdp.eval(js_click("#chatNavBtn"))
            recorder.seconds(5)
            type_value(cdp, recorder, "#messageInput", "支付订单超时如何排查？", seconds=9)
            cdp.eval(js_click("#sendButton"))
            recorder.until("document.querySelectorAll('.source-card').length >= 3", 150, 22)
            cdp.eval("document.querySelector('#chatMessages')?.scrollTo({top: document.querySelector('#chatMessages').scrollHeight, behavior: 'smooth'}); true")
            recorder.seconds(14)

            # 4. Token auth debug with real /chat responses.
            cdp.eval(js_click("#authDebugBtn"))
            recorder.seconds(6)
            cdp.eval(js_set_value("#authTokenInput", args.token))
            cdp.eval(js_click("#authRunWithoutTokenBtn"))
            recorder.until("document.querySelector('#authNoTokenStatus')?.textContent.includes('401')", 30, 7)
            cdp.eval(js_click("#authRunWithTokenBtn"))
            recorder.until("document.querySelector('#authWithTokenStatus')?.textContent.includes('200')", 160, 16)

        duration = Path(args.output).stat().st_size
        print(json.dumps({
            "output": str(Path(args.output).resolve()),
            "frames": recorder.frames,
            "seconds": round(recorder.frames / FPS, 2),
            "bytes": duration,
        }, ensure_ascii=False, indent=2))
    finally:
        if cdp:
            cdp.close()
        chrome_proc.terminate()
        try:
            chrome_proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            chrome_proc.kill()
        shutil.rmtree(profile, ignore_errors=True)


if __name__ == "__main__":
    main()
