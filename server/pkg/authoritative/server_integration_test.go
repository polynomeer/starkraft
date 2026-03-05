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

func TestCommandValidationAndAck(t *testing.T) {
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
	ackEnv := readEnvelope(t, conn)
	var hsAck protocol.HandshakeAckMessage
	if err := json.Unmarshal(ackEnv.Message, &hsAck); err != nil {
		t.Fatalf("decode handshake ack: %v", err)
	}
	_ = readEnvelope(t, conn) // initial snapshot

	x, y := 10.0, 10.0
	req := "r-1"
	batchRaw, _ := json.Marshal(protocol.CommandBatchMessage{
		Type: "commandBatch",
		Tick: 1,
		Commands: []protocol.WireCommand{
			{CommandType: "move", RequestID: &req, UnitIDs: []int{1}, X: &x, Y: &y},
		},
	})
	if err := conn.WriteJSON(protocol.ProtocolEnvelope{ProtocolVersion: protocol.CurrentProtocolVersion, SimVersion: "test", Message: batchRaw}); err != nil {
		t.Fatalf("write batch: %v", err)
	}

	srv.stepRooms()
	ackCmdEnv := readEnvelope(t, conn)
	var cmdAck protocol.CommandAckMessage
	if err := json.Unmarshal(ackCmdEnv.Message, &cmdAck); err != nil {
		t.Fatalf("decode command ack: %v", err)
	}
	if !cmdAck.Accepted || cmdAck.Reason != "" {
		t.Fatalf("expected accepted ack, got %+v", cmdAck)
	}
	snapEnv := readEnvelope(t, conn)
	var snap protocol.SnapshotMessage
	if err := json.Unmarshal(snapEnv.Message, &snap); err != nil {
		t.Fatalf("decode snapshot: %v", err)
	}
	if len(snap.Units) == 0 {
		t.Fatalf("expected units in snapshot")
	}
	if hsAck.ClientID == "player-1" {
		// deterministic first player owns unit 1; command should move it.
		found := false
		for _, u := range snap.Units {
			if u.ID == 1 {
				found = true
				if u.X != 10.0 || u.Y != 10.0 {
					t.Fatalf("expected unit moved to 10,10 got %+v", u)
				}
			}
		}
		if !found {
			t.Fatalf("unit 1 missing from snapshot")
		}
	}

	// Now send an invalid ownership command (unit 2 belongs to player-2)
	req2 := "r-2"
	badRaw, _ := json.Marshal(protocol.CommandBatchMessage{
		Type: "commandBatch",
		Tick: 2,
		Commands: []protocol.WireCommand{
			{CommandType: "move", RequestID: &req2, UnitIDs: []int{2}, X: &x, Y: &y},
		},
	})
	if err := conn.WriteJSON(protocol.ProtocolEnvelope{ProtocolVersion: protocol.CurrentProtocolVersion, SimVersion: "test", Message: badRaw}); err != nil {
		t.Fatalf("write bad batch: %v", err)
	}
	srv.stepRooms()
	badAckEnv := readEnvelope(t, conn)
	var badAck protocol.CommandAckMessage
	if err := json.Unmarshal(badAckEnv.Message, &badAck); err != nil {
		t.Fatalf("decode bad ack: %v", err)
	}
	if badAck.Accepted || badAck.Reason == "" {
		t.Fatalf("expected rejected ack, got %+v", badAck)
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
