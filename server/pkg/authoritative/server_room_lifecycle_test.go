package authoritative

import (
	"encoding/json"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/gorilla/websocket"
	"github.com/starkraft/server/pkg/protocol"
)

func TestEmptyRoomIsEvictedAfterTTL(t *testing.T) {
	srv := NewServer(Config{
		SimVersion:    "test",
		TickInterval:  20 * time.Millisecond,
		EmptyRoomTTL:  25 * time.Millisecond,
		MaxReadBytes:  64 * 1024,
		BuildHash:     "build-test",
		KeyframeEvery: 0,
	})
	defer srv.Close()
	ts := httptest.NewServer(srv.Handler())
	defer ts.Close()

	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"
	conn, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
	if err != nil {
		t.Fatalf("dial websocket: %v", err)
	}

	roomID := "evict-me"
	hsRaw, _ := json.Marshal(protocol.HandshakeMessage{
		Type:          "handshake",
		ClientName:    "bot-a",
		RequestedRoom: &roomID,
	})
	if err := conn.WriteJSON(protocol.ProtocolEnvelope{
		ProtocolVersion: protocol.CurrentProtocolVersion,
		SimVersion:      "test",
		Message:         hsRaw,
	}); err != nil {
		t.Fatalf("write handshake: %v", err)
	}
	conn.SetReadDeadline(time.Now().Add(2 * time.Second))
	_ = readUntilMessageType(t, conn, "handshakeAck")
	_ = readUntilMessageType(t, conn, "snapshot")
	_ = conn.Close()

	srv.stepRooms()
	time.Sleep(40 * time.Millisecond)
	srv.stepRooms()

	srv.mu.Lock()
	_, exists := srv.rooms[roomID]
	srv.mu.Unlock()
	if exists {
		t.Fatalf("expected room %s to be evicted after ttl", roomID)
	}
}
