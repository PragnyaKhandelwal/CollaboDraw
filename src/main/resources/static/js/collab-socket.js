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
      if (callback) callback();
    });
  }

  function disconnect(){
    if (heartbeatTimer) { clearInterval(heartbeatTimer); heartbeatTimer = null; }
    if (stompClient) { stompClient.disconnect(()=>{}); stompClient = null; }
  }

  function joinBoard(boardId){
    if (!stompClient) return;
    stompClient.send(`/app/board/${boardId}/join`, {}, JSON.stringify({}));
  }

  function leaveBoard(boardId){
    if (!stompClient) return;
    stompClient.send(`/app/board/${boardId}/leave`, {}, JSON.stringify({}));
  }

  function heartbeat(boardId){
    if (!stompClient) return;
    stompClient.send(`/app/board/${boardId}/heartbeat`, {}, JSON.stringify({}));
  }

  function updateCursor(boardId, x, y){
    if (!stompClient) return;
    stompClient.send(`/app/board/${boardId}/cursor`, {}, JSON.stringify({ x: Number(x)||0, y: Number(y)||0 }));
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

  window.CollaboSocket = {
    connect, disconnect, joinBoard, leaveBoard, heartbeat, updateCursor,
    subscribeParticipants, subscribeCursors,
    startHeartbeat(boardId, intervalMs=15000){
      if (heartbeatTimer) clearInterval(heartbeatTimer);
      heartbeatTimer = setInterval(()=> heartbeat(boardId), intervalMs);
    }
  };
})();
