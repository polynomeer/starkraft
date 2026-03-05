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

func TestWebSocketHandshakeAndSnapshot(t *testing.T) {
	srv := NewServer(Config{SimVersion: "test", TickInterval: 20 * time.Millisecond})
	ts := httptest.NewServer(srv.Handler())
	defer ts.Close()

	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"
	conn, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
	if err != nil {
		t.Fatalf("dial websocket: %v", err)
	}
	defer conn.Close()

	hsRaw, _ := json.Marshal(protocol.HandshakeMessage{Type: "handshake", ClientName: "bot-a"})
	if err := conn.WriteJSON(protocol.ProtocolEnvelope{ProtocolVersion: protocol.CurrentProtocolVersion, SimVersion: "test", Message: hsRaw}); err != nil {
		t.Fatalf("write handshake: %v", err)
	}

	conn.SetReadDeadline(time.Now().Add(2 * time.Second))
	ack := readEnvelope(t, conn)
	if mt, _ := protocol.DecodeMessageType(ack.Message); mt != "handshakeAck" {
		t.Fatalf("expected handshakeAck, got %s", mt)
	}
	firstSnap := readEnvelope(t, conn)
	if mt, _ := protocol.DecodeMessageType(firstSnap.Message); mt != "snapshot" {
		t.Fatalf("expected snapshot, got %s", mt)
	}
}

func TestRoomStepBroadcastsIncreasingTicks(t *testing.T) {
	srv := NewServer(Config{SimVersion: "test", TickInterval: 20 * time.Millisecond})
	ts := httptest.NewServer(srv.Handler())
	defer ts.Close()

	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"
	conn, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
	if err != nil {
		t.Fatalf("dial websocket: %v", err)
	}
	defer conn.Close()

	hsRaw, _ := json.Marshal(protocol.HandshakeMessage{Type: "handshake", ClientName: "bot-a"})
	if err := conn.WriteJSON(protocol.ProtocolEnvelope{ProtocolVersion: protocol.CurrentProtocolVersion, SimVersion: "test", Message: hsRaw}); err != nil {
		t.Fatalf("write handshake: %v", err)
	}

	conn.SetReadDeadline(time.Now().Add(2 * time.Second))
	_ = readEnvelope(t, conn) // handshake ack
	_ = readEnvelope(t, conn) // initial snapshot tick 0

	srv.stepRooms()
	env := readEnvelope(t, conn)
	var snap protocol.SnapshotMessage
	if err := json.Unmarshal(env.Message, &snap); err != nil {
		t.Fatalf("decode snapshot: %v", err)
	}
	if snap.Tick <= 0 {
		t.Fatalf("expected tick > 0, got %d", snap.Tick)
	}
}

func readEnvelope(t *testing.T, conn *websocket.Conn) protocol.ProtocolEnvelope {
	t.Helper()
	var env protocol.ProtocolEnvelope
	if err := conn.ReadJSON(&env); err != nil {
		t.Fatalf("read envelope: %v", err)
	}
	return env
}
