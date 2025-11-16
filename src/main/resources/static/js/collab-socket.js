// Minimal STOMP-over-WebSocket client for CollaboDraw
(function(){
  let stompClient = null;
  let heartbeatTimer = null;

  function connect(callback){
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    // Optional: disable debug logs
    stompClient.debug = null;
    stompClient.connect({}, function(){
      try { console.log('[RT] STOMP connected'); } catch {}
      if (callback) callback();
    }, function(error){
      try { console.warn('[RT] STOMP connection error:', error); } catch {}
    });
  }

  function disconnect(){
    if (heartbeatTimer) { clearInterval(heartbeatTimer); heartbeatTimer = null; }
    if (stompClient) { stompClient.disconnect(()=>{}); stompClient = null; }
  }

  function joinBoard(boardId){
    if (!stompClient) return;
    stompClient.send(`/app/board/${boardId}/join`, { 'content-type': 'application/json' }, JSON.stringify({}));
  }

  function leaveBoard(boardId){
    if (!stompClient) return;
    stompClient.send(`/app/board/${boardId}/leave`, { 'content-type': 'application/json' }, JSON.stringify({}));
  }

  function heartbeat(boardId){
    if (!stompClient) return;
    stompClient.send(`/app/board/${boardId}/heartbeat`, { 'content-type': 'application/json' }, JSON.stringify({}));
  }

  function updateCursor(boardId, x, y){
    if (!stompClient) return;
    stompClient.send(`/app/board/${boardId}/cursor`, { 'content-type': 'application/json' }, JSON.stringify({ x: Number(x)||0, y: Number(y)||0 }));
  }

  function subscribeParticipants(boardId, handler){
    if (!stompClient) return { unsubscribe: ()=>{} };
    return stompClient.subscribe(`/topic/board.${boardId}.participants`, (message)=>{
      try {
        const payload = JSON.parse(message.body);
        if (payload && payload.type === 'participants') {
          handler(payload.items || []);
        }
      } catch {}
    });
  }

  function subscribeCursors(boardId, handler){
    if (!stompClient) return { unsubscribe: ()=>{} };
    return stompClient.subscribe(`/topic/board.${boardId}.cursors`, (message)=>{
      try {
        const payload = JSON.parse(message.body);
        if (payload && payload.type === 'cursor') {
          handler(payload);
        }
      } catch {}
    });
  }

  function subscribeVersions(boardId, handler){
    if (!stompClient) return { unsubscribe: ()=>{} };
    return stompClient.subscribe(`/topic/board.${boardId}.versions`, (message)=>{
      try {
        const payload = JSON.parse(message.body);
        if (payload && payload.type === 'version') {
          handler(payload);
        }
      } catch {}
    });
  }

  function subscribeElements(boardId, handler){
    if (!stompClient) return { unsubscribe: ()=>{} };
    return stompClient.subscribe(`/topic/board.${boardId}.elements`, (message)=>{
      try {
        const payload = JSON.parse(message.body);
        if (payload && payload.type === 'element') {
          handler(payload.payload || {}, payload.meta || {});
        }
      } catch {}
    });
  }

  function publishVersion(boardId, version){
    if (!stompClient) return;
    stompClient.send(`/app/board/${boardId}/version`, { 'content-type': 'application/json' }, JSON.stringify(version || {}));
  }

  function publishElement(boardId, elementEvent){
    if (!stompClient) return;
    stompClient.send(`/app/board/${boardId}/element`, { 'content-type': 'application/json' }, JSON.stringify(elementEvent || {}));
  }

  window.CollaboSocket = {
    connect, disconnect, joinBoard, leaveBoard, heartbeat, updateCursor,
    subscribeParticipants, subscribeCursors, subscribeVersions, subscribeElements,
    publishVersion, publishElement,
    startHeartbeat(boardId, intervalMs=15000){
      if (heartbeatTimer) clearInterval(heartbeatTimer);
      heartbeatTimer = setInterval(()=> heartbeat(boardId), intervalMs);
    }
  };
})();
