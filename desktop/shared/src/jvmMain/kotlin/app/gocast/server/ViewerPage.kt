package app.gocast.server

object ViewerPage {
    /** @param iceServersJson RTCIceServer list from [app.gocast.protocol.RemoteSignaling.encodeIceServers]. */
    fun html(token: String, iceServersJson: String = "[]"): String = """
<!DOCTYPE html>
<html lang="en" style="background:#12141A;color-scheme:dark">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <meta name="color-scheme" content="dark" />
  <title>gocast viewer</title>
  <style>
    * { box-sizing: border-box; }
    html, body {
      margin: 0;
      width: 100%;
      height: 100%;
      overflow: hidden;
      background: #12141A;
      color: #eee;
      font-family: Segoe UI, system-ui, sans-serif;
    }
    #wrap {
      position: absolute;
      inset: 0;
      background: #12141A;
    }
    /*
     * Hardware-decoded <video> can composite above HTML overlays in embeds.
     * Keep <video> as a tiny offscreen decoder and blit frames to <canvas>.
     */
    #video {
      position: absolute;
      width: 1px;
      height: 1px;
      opacity: 0;
      pointer-events: none;
      left: -100px;
      top: -100px;
    }
    #stage {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      background: #12141A;
      z-index: 0;
    }
    #status {
      position: absolute;
      left: 12px;
      bottom: 12px;
      z-index: 20;
      opacity: 0.7;
      font-size: 12px;
      background: rgba(0,0,0,0.45);
      padding: 4px 8px;
      border-radius: 4px;
      pointer-events: none;
    }
    #paused {
      display: none;
      position: absolute;
      inset: 0;
      z-index: 15;
      align-items: center;
      justify-content: center;
      flex-direction: column;
      gap: 8px;
      background: rgba(0, 0, 0, 0.42);
      pointer-events: none;
      text-align: center;
      padding: 24px;
    }
    #paused.visible { display: flex; }
    #paused .title {
      font-size: 22px;
      font-weight: 600;
      color: #f2f4f8;
      letter-spacing: 0.01em;
      text-shadow: 0 1px 8px rgba(0,0,0,0.55);
    }
    #paused .hint {
      font-size: 14px;
      color: rgba(232, 236, 245, 0.82);
      max-width: 28em;
      line-height: 1.45;
      text-shadow: 0 1px 6px rgba(0,0,0,0.5);
    }

    /* Collapsed = perfect circle; hover expands into a pill with a fat slider */
    #vol {
      position: absolute;
      top: 14px;
      left: 14px;
      z-index: 30;
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
      z-index: 30;
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
      z-index: 30;
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
      z-index: 31;
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
    <canvas id="stage" aria-hidden="true"></canvas>
    <video id="video" autoplay playsinline muted></video>
    <audio id="cast-audio" autoplay></audio>
    <div id="paused" aria-live="polite">
      <div class="title">Stream paused</div>
      <div class="hint">Resume from the casting notification on your phone.</div>
    </div>
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
      <button type="button" id="btn-fullscreen">Full screen</button>
      <button type="button" id="btn-pairing">Show pairing again</button>
      <button type="button" id="btn-disconnect">Disconnect</button>
    </div>
  </div>
  <script>
    const TOKEN = ${token.toJsonString()};
    // STUN/TURN from the signaling Worker; refreshed before each offer (credentials expire).
    const INITIAL_ICE_SERVERS = $iceServersJson;
    const statusEl = document.getElementById('status');
    const pausedEl = document.getElementById('paused');
    const video = document.getElementById('video');
    const stage = document.getElementById('stage');
    const stageCtx = stage.getContext('2d');
    // Picture and sound on separate elements — A/V on one <video> can freeze on frame 1.
    const audio = document.getElementById('cast-audio');
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

    let volume = 1;
    let muted = false;
    let preMuteVolume = 1;
    let videoStream = null;
    let audioStream = null;
    let streamPaused = false;
    let paintRunning = false;

    function setStatus(text) { statusEl.textContent = text; }

    function resizeStage() {
      const dpr = window.devicePixelRatio || 1;
      const w = stage.clientWidth;
      const h = stage.clientHeight;
      if (w < 1 || h < 1) return;
      stage.width = Math.round(w * dpr);
      stage.height = Math.round(h * dpr);
      stageCtx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }

    function paintFrame() {
      if (!paintRunning) return;
      requestAnimationFrame(paintFrame);
      const vw = video.videoWidth;
      const vh = video.videoHeight;
      if (!vw || !vh) return;
      const cw = stage.clientWidth;
      const ch = stage.clientHeight;
      if (cw < 1 || ch < 1) return;
      const scale = Math.min(cw / vw, ch / vh);
      const dw = vw * scale;
      const dh = vh * scale;
      const dx = (cw - dw) / 2;
      const dy = (ch - dh) / 2;
      stageCtx.fillStyle = '#000';
      stageCtx.fillRect(0, 0, cw, ch);
      stageCtx.drawImage(video, dx, dy, dw, dh);
    }

    function startPaint() {
      if (paintRunning) return;
      paintRunning = true;
      resizeStage();
      requestAnimationFrame(paintFrame);
    }

    window.addEventListener('resize', resizeStage);
    resizeStage();

    function setStreamPaused(paused) {
      streamPaused = paused;
      pausedEl.classList.toggle('visible', paused);
      if (paused) setStatus('Paused');
    }

    function applyAudioUi() {
      video.muted = true;
      video.volume = 0;
      audio.volume = muted ? 0 : volume;
      audio.muted = muted || volume === 0;
      btnVol.innerHTML = (muted || volume === 0) ? ICON_OFF : ICON_ON;
      btnVol.setAttribute('aria-label', (muted || volume === 0) ? 'Unmute' : 'Mute');
      btnVol.title = (muted || volume === 0) ? 'Unmute' : 'Mute';
      volSlider.value = String(muted ? 0 : volume);
    }

    async function playEl(el) {
      try { await el.play(); } catch (_) {}
    }

    async function ensurePlaying() {
      applyAudioUi();
      await playEl(video);
      if (audio.srcObject) await playEl(audio);
    }

    applyAudioUi();

    document.getElementById('wrap').addEventListener('click', () => ensurePlaying());

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
      if (audio.srcObject) playEl(audio);
    });
    volSlider.addEventListener('click', (e) => e.stopPropagation());
    volRoot.addEventListener('click', (e) => e.stopPropagation());

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
      } catch (_) {}
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

    let fullscreen = false;
    const btnFullscreen = document.getElementById('btn-fullscreen');
    function setFullscreenUi(on) {
      fullscreen = on;
      btnFullscreen.textContent = on ? 'Exit full screen' : 'Full screen';
    }
    btnFullscreen.addEventListener('click', () => {
      menu.classList.remove('open');
      setFullscreenUi(!fullscreen);
      postAction('/__gocast/toggle-fullscreen');
    });
    window.addEventListener('keydown', (e) => {
      if (e.key === 'F11') {
        e.preventDefault();
        setFullscreenUi(!fullscreen);
        postAction('/__gocast/toggle-fullscreen');
      } else if (e.key === 'Escape' && fullscreen) {
        e.preventDefault();
        setFullscreenUi(false);
        postAction('/__gocast/exit-fullscreen');
      }
    });

    let pc = null;
    let linkType = null;
    let linkTimer = null;

    // Mirrors LinkClassifier in protocol/: private peer address = LAN, relay = relayed.
    function isLocalAddress(address) {
      if (!address) return false;
      const value = String(address).trim().replace(/^\[|\]$/g, '').toLowerCase();
      if (value.endsWith('.local')) return true;
      const v4 = value.split('.');
      if (v4.length === 4 && v4.every((p) => /^\d{1,3}$/.test(p) && Number(p) <= 255)) {
        const a = Number(v4[0]);
        const b = Number(v4[1]);
        return a === 10 || a === 127 || (a === 172 && b >= 16 && b <= 31) ||
          (a === 192 && b === 168) || (a === 169 && b === 254);
      }
      if (!value.includes(':')) return false;
      const v6 = value.split('%')[0];
      return v6 === '::1' || /^f[cd]/.test(v6) || /^fe[89ab]/.test(v6);
    }

    function classifyLink(local, remote) {
      if (!local && !remote) return null;
      const localType = local && local.candidateType;
      const remoteType = remote && remote.candidateType;
      if (localType === 'relay' || remoteType === 'relay') return 'Relayed';
      const remoteAddress = remote && (remote.address || remote.ip);
      if (remoteAddress) return isLocalAddress(remoteAddress) ? 'Direct (LAN)' : 'Direct (internet)';
      const localAddress = local && (local.address || local.ip);
      return remoteType === 'host' && isLocalAddress(localAddress) ? 'Direct (LAN)' : 'Direct (internet)';
    }

    // The link type is logged (console + PC log), not shown: users only need "Casting".
    function castingStatus() {
      return 'Casting';
    }

    async function refreshLinkType() {
      if (!pc || pc.connectionState !== 'connected') return;
      let stats;
      try { stats = await pc.getStats(); } catch (_) { return; }
      let pair = null;
      stats.forEach((report) => {
        if (report.type === 'transport' && report.selectedCandidatePairId) {
          pair = stats.get(report.selectedCandidatePairId) || pair;
        }
      });
      if (!pair) {
        stats.forEach((report) => {
          if (report.type === 'candidate-pair' && report.nominated && report.state === 'succeeded') pair = report;
        });
      }
      if (!pair) return;
      const next = classifyLink(stats.get(pair.localCandidateId), stats.get(pair.remoteCandidateId));
      if (!next || next === linkType) return;
      linkType = next;
      console.log('gocast link: ' + next);
      fetch('/__gocast/link-type', { method: 'POST', body: next, cache: 'no-store' }).catch(() => {});
      if (!streamPaused && (videoStream || audioStream)) setStatus(castingStatus());
    }

    function stopLinkWatch() {
      clearInterval(linkTimer);
      linkTimer = null;
      linkType = null;
    }

    async function currentIceServers() {
      try {
        const response = await fetch('/__gocast/ice-servers', { cache: 'no-store' });
        const servers = await response.json();
        if (Array.isArray(servers) && servers.length > 0) return servers;
      } catch (_) {}
      return INITIAL_ICE_SERVERS;
    }

    // A dead video link (the phone switched networks, a relay hiccup) is rebuilt by
    // asking the phone for a fresh offer, so nobody has to press Retry. Repeats
    // until a new connection is on its way.
    let recoverTimer = null;
    function scheduleRecovery(delayMs) {
      if (recoverTimer) return;
      recoverTimer = setTimeout(() => {
        recoverTimer = null;
        if (streamPaused || !pc) return;
        const state = pc.connectionState;
        if (state === 'new' || state === 'connecting' || state === 'connected') return;
        if (ws.readyState === WebSocket.OPEN) {
          console.log('gocast: video link ' + state + ', asking the phone for a fresh offer');
          ws.send(JSON.stringify({ type: 'viewer-ready' }));
        }
        scheduleRecovery(RECOVER_REPEAT_MS);
      }, delayMs);
    }
    function cancelRecovery() {
      clearTimeout(recoverTimer);
      recoverTimer = null;
    }
    const RECOVER_REPEAT_MS = 6000;
    // "disconnected" often heals by itself within a few seconds; "failed" never does.
    const RECOVER_AFTER_DISCONNECT_MS = 4000;

    function wirePeerConnection(iceServers) {
      cancelRecovery();
      if (pc) {
        try { pc.close(); } catch (_) {}
      }
      videoStream = null;
      audioStream = null;
      video.srcObject = null;
      audio.srcObject = null;
      stopLinkWatch();
      pc = new RTCPeerConnection({ iceServers });
      pc.ontrack = (event) => {
        const kind = event.track.kind;
        if (kind === 'video') {
          if (!videoStream) videoStream = new MediaStream();
          if (!videoStream.getTracks().includes(event.track)) {
            videoStream.addTrack(event.track);
          }
          video.srcObject = videoStream;
          video.muted = true;
          video.volume = 0;
          playEl(video);
          startPaint();
        } else if (kind === 'audio') {
          if (!audioStream) audioStream = new MediaStream();
          if (!audioStream.getTracks().includes(event.track)) {
            audioStream.addTrack(event.track);
          }
          audio.srcObject = audioStream;
          applyAudioUi();
          playEl(audio);
        }
        const v = videoStream ? videoStream.getTracks().length : 0;
        const a = audioStream ? audioStream.getTracks().length : 0;
        if (v > 0 || a > 0) {
          setStreamPaused(false);
          setStatus(castingStatus());
        }
      };
      pc.onconnectionstatechange = () => {
        if (streamPaused) return;
        const state = pc.connectionState;
        if (state === 'connected') {
          cancelRecovery();
          setStatus('Connected');
          // The pair can change (relay first, direct later), so keep looking.
          refreshLinkType();
          clearInterval(linkTimer);
          linkTimer = setInterval(refreshLinkType, 5000);
        } else if (state === 'connecting') {
          setStatus('Connecting…');
        } else if (state === 'failed' || state === 'closed' || state === 'disconnected') {
          setStatus('Connection lost. Waiting for phone…');
          if (state === 'failed') scheduleRecovery(0);
          if (state === 'disconnected') scheduleRecovery(RECOVER_AFTER_DISCONNECT_MS);
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

      if (msg.type === 'cast-paused') {
        setStreamPaused(true);
        return;
      }
      if (msg.type === 'cast-resumed') {
        setStreamPaused(false);
        setStatus('Resuming…');
        return;
      }
      if (msg.type === 'offer' && msg.sdp) {
        setStreamPaused(false);
        wirePeerConnection(await currentIceServers());
        await pc.setRemoteDescription({ type: 'offer', sdp: msg.sdp });
        const answer = await pc.createAnswer();
        await pc.setLocalDescription(answer);
        ws.send(JSON.stringify({ type: 'answer', sdp: answer.sdp }));
        setStatus('Connecting…');
      } else if (msg.type === 'ice' && msg.candidate) {
        if (!pc) return;
        try {
          await pc.addIceCandidate({
            candidate: msg.candidate,
            sdpMid: msg.sdpMid ?? undefined,
            sdpMLineIndex: msg.sdpMLineIndex ?? undefined,
          });
        } catch (_) {}
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
