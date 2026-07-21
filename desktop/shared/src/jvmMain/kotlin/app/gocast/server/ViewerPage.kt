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

    /* Collapsed = perfect circle; hover expands into a pill with a fat slider */
    #vol {
      position: absolute;
      top: 14px;
      left: 14px;
      z-index: 12;
      display: flex;
      align-items: center;
      justify-content: center;
      box-sizing: border-box;
      width: 48px;
      height: 48px;
      padding: 0;
      border-radius: 50%;
      overflow: hidden;
      background: rgba(27, 32, 48, 0.88);
      box-shadow: 0 2px 10px rgba(0,0,0,0.45);
      backdrop-filter: blur(8px);
      transition: width 0.2s ease, border-radius 0.2s ease, background 0.15s ease,
                  padding 0.2s ease, justify-content 0.2s ease;
    }
    #vol:hover, #vol:focus-within, #vol.expanded {
      width: 220px;
      border-radius: 24px;
      justify-content: flex-start;
      padding: 0 14px 0 2px;
      overflow: visible;
      background: rgba(42, 49, 66, 0.96);
    }
    #btn-vol {
      -webkit-appearance: none;
      appearance: none;
      flex: 0 0 auto;
      box-sizing: border-box;
      width: 48px;
      height: 48px;
      min-width: 48px;
      min-height: 48px;
      border: none;
      outline: none;
      border-radius: 50%;
      background: transparent;
      color: #e8ecf5;
      cursor: pointer;
      display: grid;
      place-items: center;
      padding: 0;
      margin: 0;
      font: inherit;
    }
    #btn-vol:hover { background: rgba(255,255,255,0.08); }
    #btn-vol svg { width: 22px; height: 22px; display: block; }
    #vol-slider-wrap {
      width: 0;
      overflow: hidden;
      opacity: 0;
      transition: width 0.2s ease, opacity 0.15s ease;
      margin-left: 0;
      display: flex;
      align-items: center;
      height: 48px;
    }
    #vol:hover #vol-slider-wrap,
    #vol:focus-within #vol-slider-wrap,
    #vol.expanded #vol-slider-wrap {
      width: 160px;
      opacity: 1;
    }
    #vol-slider {
      -webkit-appearance: none;
      appearance: none;
      width: 160px;
      height: 48px;
      margin: 0;
      background: transparent;
      cursor: pointer;
    }
    #vol-slider:focus { outline: none; }
    #vol-slider::-webkit-slider-runnable-track {
      height: 12px;
      border-radius: 6px;
      background: rgba(255,255,255,0.22);
    }
    #vol-slider::-webkit-slider-thumb {
      -webkit-appearance: none;
      appearance: none;
      width: 22px;
      height: 22px;
      border-radius: 50%;
      background: #73acfe;
      border: 2px solid #e8ecf5;
      margin-top: -5px;
      box-shadow: 0 1px 4px rgba(0,0,0,0.45);
    }
    #vol-slider::-moz-range-track {
      height: 12px;
      border-radius: 6px;
      background: rgba(255,255,255,0.22);
    }
    #vol-slider::-moz-range-thumb {
      width: 22px;
      height: 22px;
      border-radius: 50%;
      background: #73acfe;
      border: 2px solid #e8ecf5;
      box-shadow: 0 1px 4px rgba(0,0,0,0.45);
    }

    #fab {
      -webkit-appearance: none;
      appearance: none;
      position: absolute;
      top: 14px;
      right: 14px;
      z-index: 10;
      box-sizing: border-box;
      width: 40px;
      height: 40px;
      min-width: 40px;
      min-height: 40px;
      margin: 0;
      border: none;
      outline: none;
      border-radius: 50%;
      background: rgba(27, 32, 48, 0.88);
      color: #e8ecf5;
      font-size: 18px;
      font-family: inherit;
      line-height: 1;
      cursor: pointer;
      box-shadow: 0 2px 10px rgba(0,0,0,0.45);
    }
    #fab:hover { background: rgba(42, 49, 66, 0.95); }
    #btn-retry {
      -webkit-appearance: none;
      appearance: none;
      position: absolute;
      top: 14px;
      right: 62px;
      z-index: 10;
      box-sizing: border-box;
      width: 40px;
      height: 40px;
      min-width: 40px;
      min-height: 40px;
      margin: 0;
      padding: 0;
      border: none;
      outline: none;
      border-radius: 50%;
      background: rgba(27, 32, 48, 0.88);
      color: #e8ecf5;
      cursor: pointer;
      box-shadow: 0 2px 10px rgba(0,0,0,0.45);
      display: grid;
      place-items: center;
    }
    #btn-retry:hover { background: rgba(42, 49, 66, 0.95); }
    #btn-retry svg { width: 20px; height: 20px; display: block; }
    #menu {
      display: none;
      position: absolute;
      top: 60px;
      right: 14px;
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
    <video id="video" autoplay playsinline></video>
    <div id="status">Connecting…</div>

    <div id="vol" title="Volume">
      <button id="btn-vol" type="button" aria-label="Mute" title="Mute"></button>
      <div id="vol-slider-wrap">
        <input id="vol-slider" type="range" min="0" max="1" step="0.01" value="1"
               aria-label="Volume" />
      </div>
    </div>

    <button id="btn-retry" type="button" aria-label="Screen stuck? Try again"
            title="Screen stuck? Try again"></button>
    <button id="fab" type="button" aria-label="Menu" title="Menu">☰</button>
    <div id="menu" role="menu">
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
    const btnRetry = document.getElementById('btn-retry');
    const volRoot = document.getElementById('vol');
    const btnVol = document.getElementById('btn-vol');
    const volSlider = document.getElementById('vol-slider');

    const ICON_ON = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M11 5L6 9H3v6h3l5 4V5z"/><path d="M15.5 8.5a5 5 0 0 1 0 7"/><path d="M18.5 5.5a9 9 0 0 1 0 13"/></svg>';
    const ICON_OFF = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M11 5L6 9H3v6h3l5 4V5z"/><path d="M22 9l-6 6"/><path d="M16 9l6 6"/></svg>';
    const ICON_RETRY = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 12a9 9 0 1 1-2.6-6.4"/><path d="M21 3v6h-6"/></svg>';
    btnRetry.innerHTML = ICON_RETRY;

    // Desktop cast audio: 100% unmuted by default. Phone speakers are muted separately.
    let volume = 1;
    let muted = false;
    let preMuteVolume = 1;

    function setStatus(text) { statusEl.textContent = text; }

    function applyAudioUi() {
      video.volume = muted ? 0 : volume;
      video.muted = muted || volume === 0;
      btnVol.innerHTML = (muted || volume === 0) ? ICON_OFF : ICON_ON;
      btnVol.setAttribute('aria-label', (muted || volume === 0) ? 'Unmute' : 'Mute');
      btnVol.title = (muted || volume === 0) ? 'Unmute' : 'Mute';
      volSlider.value = String(muted ? 0 : volume);
    }

    async function ensurePlaying() {
      applyAudioUi();
      try {
        await video.play();
        return;
      } catch (e) {
        console.warn('unmuted play blocked, retrying', e);
      }
      // Autoplay policies sometimes require a muted play() first — do that
      // temporarily, then restore 100% unmuted. Never flip the UI to muted.
      video.muted = true;
      try {
        await video.play();
      } catch (_) {
        return;
      }
      applyAudioUi();
      try {
        await video.play();
      } catch (e) {
        console.warn('still blocked after unmute — click speaker to enable sound', e);
      }
    }

    applyAudioUi();

    // If Chromium blocked unmuted autoplay, first click restores intended 100% sound.
    document.getElementById('wrap').addEventListener('click', () => {
      if (!muted && volume > 0 && (video.muted || video.volume === 0)) {
        applyAudioUi();
        ensurePlaying();
      }
    });

    btnVol.addEventListener('click', (e) => {
      e.stopPropagation();
      if (muted || volume === 0) {
        muted = false;
        volume = preMuteVolume > 0 ? preMuteVolume : 1;
      } else {
        preMuteVolume = volume > 0 ? volume : 1;
        muted = true;
      }
      applyAudioUi();
      ensurePlaying();
    });

    volSlider.addEventListener('input', () => {
      volume = Number(volSlider.value);
      muted = volume === 0;
      if (volume > 0) preMuteVolume = volume;
      applyAudioUi();
    });
    volSlider.addEventListener('click', (e) => e.stopPropagation());
    volRoot.addEventListener('click', (e) => e.stopPropagation());

    // Keep slider open briefly after interacting so it doesn't collapse mid-drag.
    let expandTimer = null;
    function bumpExpand() {
      volRoot.classList.add('expanded');
      clearTimeout(expandTimer);
      expandTimer = setTimeout(() => volRoot.classList.remove('expanded'), 1200);
    }
    volSlider.addEventListener('pointerdown', bumpExpand);
    btnVol.addEventListener('pointerdown', bumpExpand);

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

    btnRetry.addEventListener('click', (e) => {
      e.stopPropagation();
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
        if (event.streams && event.streams[0]) {
          video.srcObject = event.streams[0];
        } else {
          let stream = video.srcObject;
          if (!(stream instanceof MediaStream)) {
            stream = new MediaStream();
            video.srcObject = stream;
          }
          stream.addTrack(event.track);
        }
        applyAudioUi();
        ensurePlaying();
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
