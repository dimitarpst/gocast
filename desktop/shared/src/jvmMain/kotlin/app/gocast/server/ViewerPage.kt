package app.gocast.server

object ViewerPage {
    fun html(token: String): String = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>gocast viewer</title>
  <style>
    * { box-sizing: border-box; }
    html, body {
      margin: 0;
      width: 100%;
      height: 100%;
      overflow: hidden;
      background: #000;
      color: #eee;
      font-family: Segoe UI, system-ui, sans-serif;
    }
    #wrap {
      position: absolute;
      inset: 0;
      background: #000;
    }
    video {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      object-fit: contain;
      background: #000;
    }
    #status {
      position: absolute;
      left: 12px;
      bottom: 12px;
      z-index: 2;
      opacity: 0.7;
      font-size: 12px;
      background: rgba(0,0,0,0.45);
      padding: 4px 8px;
      border-radius: 4px;
      pointer-events: none;
    }
    #fab {
      position: absolute;
      top: 14px;
      left: 14px;
      z-index: 10;
      width: 40px;
      height: 40px;
      border: none;
      border-radius: 50%;
      background: rgba(27, 32, 48, 0.88);
      color: #e8ecf5;
      font-size: 18px;
      line-height: 1;
      cursor: pointer;
      box-shadow: 0 2px 10px rgba(0,0,0,0.45);
    }
    #fab:hover { background: rgba(42, 49, 66, 0.95); }
    #menu {
      display: none;
      position: absolute;
      top: 60px;
      left: 14px;
      z-index: 11;
      min-width: 200px;
      padding: 6px;
      border-radius: 12px;
      background: rgba(27, 32, 48, 0.96);
      box-shadow: 0 8px 24px rgba(0,0,0,0.5);
    }
    #menu.open { display: block; }
    #menu button {
      display: block;
      width: 100%;
      border: none;
      background: transparent;
      color: #e8ecf5;
      text-align: left;
      padding: 10px 12px;
      border-radius: 8px;
      font-size: 14px;
      cursor: pointer;
    }
    #menu button:hover { background: rgba(255,255,255,0.08); }
  </style>
</head>
<body>
  <div id="wrap">
    <video id="video" autoplay playsinline muted></video>
    <div id="status">Connecting…</div>
    <button id="fab" type="button" aria-label="Menu" title="Menu">☰</button>
    <div id="menu" role="menu">
      <button type="button" id="btn-retry">Screen stuck? Retry</button>
      <button type="button" id="btn-pairing">Show pairing again</button>
      <button type="button" id="btn-disconnect">Disconnect</button>
    </div>
  </div>
  <script>
    const TOKEN = ${token.toJsonString()};
    const statusEl = document.getElementById('status');
    const video = document.getElementById('video');
    const fab = document.getElementById('fab');
    const menu = document.getElementById('menu');

    function setStatus(text) { statusEl.textContent = text; }

    async function postAction(path) {
      try {
        await fetch(path, { method: 'POST', cache: 'no-store' });
      } catch (e) {
        console.warn('action failed', path, e);
      }
    }

    fab.addEventListener('click', (e) => {
      e.stopPropagation();
      menu.classList.toggle('open');
    });
    document.addEventListener('click', () => menu.classList.remove('open'));
    menu.addEventListener('click', (e) => e.stopPropagation());

    document.getElementById('btn-retry').addEventListener('click', () => {
      menu.classList.remove('open');
      location.reload();
    });
    document.getElementById('btn-pairing').addEventListener('click', () => {
      menu.classList.remove('open');
      postAction('/__gocast/show-pairing');
    });
    document.getElementById('btn-disconnect').addEventListener('click', () => {
      menu.classList.remove('open');
      postAction('/__gocast/disconnect');
    });

    let pc = null;

    function wirePeerConnection() {
      if (pc) {
        try { pc.close(); } catch (_) {}
      }
      pc = new RTCPeerConnection({ iceServers: [] });
      pc.ontrack = (event) => {
        video.srcObject = event.streams[0] || new MediaStream([event.track]);
        setStatus('Receiving');
      };
      pc.onconnectionstatechange = () => {
        const state = pc.connectionState;
        setStatus('Peer: ' + state);
        if (state === 'failed' || state === 'closed') {
          setStatus('Peer: ' + state + ' — waiting for phone…');
        }
      };
      pc.onicecandidate = (event) => {
        if (!event.candidate || ws.readyState !== WebSocket.OPEN) return;
        ws.send(JSON.stringify({
          type: 'ice',
          candidate: event.candidate.candidate,
          sdpMid: event.candidate.sdpMid,
          sdpMLineIndex: event.candidate.sdpMLineIndex,
        }));
      };
    }

    const wsUrl = (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws';
    const ws = new WebSocket(wsUrl);

    ws.onopen = () => {
      ws.send(JSON.stringify({ type: 'hello', token: TOKEN, role: 'viewer' }));
    };

    ws.onmessage = async (event) => {
      const text = event.data;
      if (text === 'ok') {
        setStatus('Waiting for phone offer…');
        return;
      }
      let msg;
      try { msg = JSON.parse(text); } catch (_) { return; }

      if (msg.type === 'offer' && msg.sdp) {
        wirePeerConnection();
        await pc.setRemoteDescription({ type: 'offer', sdp: msg.sdp });
        const answer = await pc.createAnswer();
        await pc.setLocalDescription(answer);
        ws.send(JSON.stringify({ type: 'answer', sdp: answer.sdp }));
        setStatus('Answer sent');
      } else if (msg.type === 'ice' && msg.candidate) {
        if (!pc) return;
        try {
          await pc.addIceCandidate({
            candidate: msg.candidate,
            sdpMid: msg.sdpMid ?? undefined,
            sdpMLineIndex: msg.sdpMLineIndex ?? undefined,
          });
        } catch (e) {
          console.warn('addIceCandidate', e);
        }
      }
    };

    ws.onclose = () => setStatus('Disconnected');
    ws.onerror = () => setStatus('WebSocket error');
  </script>
</body>
</html>
    """.trimIndent()

    private fun String.toJsonString(): String =
        buildString {
            append('"')
            for (ch in this@toJsonString) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    else -> append(ch)
                }
            }
            append('"')
        }
}
