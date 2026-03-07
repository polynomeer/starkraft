package authoritative

import (
	"context"
	"encoding/json"
	"net"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/gorilla/websocket"
	"github.com/starkraft/server/pkg/protocol"
)

func TestWebSocketHandshakeAndSnapshot(t *testing.T) {
	srv := NewServer(Config{SimVersion: "test", TickInterval: 20 * time.Millisecond})
	defer srv.Close()
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
	defer srv.Close()
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
	defer srv.Close()
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
	waitForPendingBatches(t, srv, "default", 1)

	ackCmdEnv := stepUntilMessageType(t, srv, conn, "commandAck", 5)
	var cmdAck protocol.CommandAckMessage
	if err := json.Unmarshal(ackCmdEnv.Message, &cmdAck); err != nil {
		t.Fatalf("decode command ack: %v", err)
	}
	if !cmdAck.Accepted || cmdAck.Reason != "" {
		t.Fatalf("expected accepted ack, got %+v", cmdAck)
	}
	snapEnv := readUntilMessageType(t, conn, "snapshot")
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
	waitForPendingBatches(t, srv, "default", 1)
	badAckEnv := stepUntilMessageType(t, srv, conn, "commandAck", 5)
	var badAck protocol.CommandAckMessage
	if err := json.Unmarshal(badAckEnv.Message, &badAck); err != nil {
		t.Fatalf("decode bad ack: %v", err)
	}
	if badAck.Accepted || badAck.Reason == "" {
		t.Fatalf("expected rejected ack, got %+v", badAck)
	}
}

func TestSurrenderCommandEndsMatch(t *testing.T) {
	srv := NewServer(Config{SimVersion: "test", TickInterval: 20 * time.Millisecond})
	defer srv.Close()
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
	_ = readEnvelope(t, conn) // initial snapshot

	req := "s-1"
	batchRaw, _ := json.Marshal(protocol.CommandBatchMessage{
		Type: "commandBatch",
		Tick: 1,
		Commands: []protocol.WireCommand{
			{CommandType: "surrender", RequestID: &req},
		},
	})
	if err := conn.WriteJSON(protocol.ProtocolEnvelope{ProtocolVersion: protocol.CurrentProtocolVersion, SimVersion: "test", Message: batchRaw}); err != nil {
		t.Fatalf("write surrender batch: %v", err)
	}
	waitForPendingBatches(t, srv, "default", 1)

	ackEnv := stepUntilMessageType(t, srv, conn, "commandAck", 5)
	var ack protocol.CommandAckMessage
	if err := json.Unmarshal(ackEnv.Message, &ack); err != nil {
		t.Fatalf("decode surrender ack: %v", err)
	}
	if !ack.Accepted || ack.Reason != "" {
		t.Fatalf("expected accepted surrender ack, got %+v", ack)
	}

	snapEnv := readUntilMessageType(t, conn, "snapshot")
	var snap protocol.SnapshotMessage
	if err := json.Unmarshal(snapEnv.Message, &snap); err != nil {
		t.Fatalf("decode snapshot: %v", err)
	}
	if !snap.MatchEnded {
		t.Fatalf("expected snapshot matchEnded=true after surrender")
	}
	if snap.WinnerID == nil || *snap.WinnerID != "player-2" {
		t.Fatalf("expected winner player-2, got %v", snap.WinnerID)
	}

	endEnv := readUntilMessageType(t, conn, "matchEnd")
	var end protocol.MatchEndMessage
	if err := json.Unmarshal(endEnv.Message, &end); err != nil {
		t.Fatalf("decode matchEnd: %v", err)
	}
	if end.WinnerID == nil || *end.WinnerID != "player-2" {
		t.Fatalf("expected matchEnd winner player-2, got %v", end.WinnerID)
	}
}

func TestReplayFileContainsHeaderCommandAndKeyframe(t *testing.T) {
	replayPath := filepath.Join(t.TempDir(), "room.replay.jsonl")
	srv := NewServer(Config{
		SimVersion:    "test",
		BuildHash:     "build-1",
		TickInterval:  20 * time.Millisecond,
		ReplayPath:    replayPath,
		KeyframeEvery: 1,
	})
	defer srv.Close()
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
	_ = readEnvelope(t, conn) // initial snapshot

	x, y := 6.0, 7.0
	req := "r-10"
	batchRaw, _ := json.Marshal(protocol.CommandBatchMessage{
		Type: "commandBatch", Tick: 1,
		Commands: []protocol.WireCommand{{CommandType: "move", RequestID: &req, UnitIDs: []int{1}, X: &x, Y: &y}},
	})
	if err := conn.WriteJSON(protocol.ProtocolEnvelope{ProtocolVersion: protocol.CurrentProtocolVersion, SimVersion: "test", Message: batchRaw}); err != nil {
		t.Fatalf("write batch: %v", err)
	}
	waitForPendingBatches(t, srv, "default", 1)
	_ = stepUntilMessageType(t, srv, conn, "commandAck", 5)
	_ = readUntilMessageType(t, conn, "snapshot")

	if err := srv.Close(); err != nil {
		t.Fatalf("close replay: %v", err)
	}
	content, err := os.ReadFile(replayPath)
	if err != nil {
		t.Fatalf("read replay: %v", err)
	}
	text := string(content)
	if !strings.Contains(text, "\"recordType\":\"header\"") {
		t.Fatalf("replay missing header record: %s", text)
	}
	if !strings.Contains(text, "\"recordType\":\"command\"") {
		t.Fatalf("replay missing command record: %s", text)
	}
	if !strings.Contains(text, "\"recordType\":\"keyframe\"") {
		t.Fatalf("replay missing keyframe record: %s", text)
	}
}

func TestServerRunAdvancesTicksOverTime(t *testing.T) {
	srv := NewServer(Config{SimVersion: "test", TickInterval: 10 * time.Millisecond})
	defer srv.Close()
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen: %v", err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go func() {
		_ = srv.RunOnListener(ctx, ln)
	}()

	wsURL := "ws://" + ln.Addr().String() + "/ws"
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
	_ = readEnvelope(t, conn) // snapshot tick 0

	var last protocol.SnapshotMessage
	for i := 0; i < 4; i++ {
		env := readEnvelope(t, conn)
		if err := json.Unmarshal(env.Message, &last); err != nil {
			t.Fatalf("decode snapshot: %v", err)
		}
	}
	if last.Tick < 3 {
		t.Fatalf("expected tick progression, got tick=%d", last.Tick)
	}
}

func TestBatchTooLargeIsRejectedWithAck(t *testing.T) {
	srv := NewServer(Config{
		SimVersion:       "test",
		TickInterval:     20 * time.Millisecond,
		MaxBatchCommands: 1,
	})
	defer srv.Close()
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
	_ = readEnvelope(t, conn) // initial snapshot

	x1, y1 := 4.0, 5.0
	x2, y2 := 6.0, 7.0
	req1 := "r-1"
	req2 := "r-2"
	batchRaw, _ := json.Marshal(protocol.CommandBatchMessage{
		Type: "commandBatch",
		Tick: 1,
		Commands: []protocol.WireCommand{
			{CommandType: "move", RequestID: &req1, UnitIDs: []int{1}, X: &x1, Y: &y1},
			{CommandType: "move", RequestID: &req2, UnitIDs: []int{1}, X: &x2, Y: &y2},
		},
	})
	if err := conn.WriteJSON(protocol.ProtocolEnvelope{ProtocolVersion: protocol.CurrentProtocolVersion, SimVersion: "test", Message: batchRaw}); err != nil {
		t.Fatalf("write batch: %v", err)
	}

	ackEnv := readUntilMessageType(t, conn, "commandAck")
	var ack protocol.CommandAckMessage
	if err := json.Unmarshal(ackEnv.Message, &ack); err != nil {
		t.Fatalf("decode ack: %v", err)
	}
	if ack.Accepted || ack.Reason != "batchTooLarge" {
		t.Fatalf("expected batchTooLarge rejection, got %+v", ack)
	}
}

func TestHandshakeRejectsInvalidClientName(t *testing.T) {
	srv := NewServer(Config{
		SimVersion:          "test",
		TickInterval:        20 * time.Millisecond,
		MaxClientNameLength: 8,
	})
	defer srv.Close()
	ts := httptest.NewServer(srv.Handler())
	defer ts.Close()

	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"
	conn, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
	if err != nil {
		t.Fatalf("dial websocket: %v", err)
	}
	defer conn.Close()

	hsRaw, _ := json.Marshal(protocol.HandshakeMessage{Type: "handshake", ClientName: "invalid name with spaces"})
	if err := conn.WriteJSON(protocol.ProtocolEnvelope{ProtocolVersion: protocol.CurrentProtocolVersion, SimVersion: "test", Message: hsRaw}); err != nil {
		t.Fatalf("write handshake: %v", err)
	}
	conn.SetReadDeadline(time.Now().Add(500 * time.Millisecond))
	var env protocol.ProtocolEnvelope
	err = conn.ReadJSON(&env)
	if err == nil {
		t.Fatalf("expected handshake to be rejected and connection closed")
	}
	if !strings.Contains(err.Error(), "invalid client name") {
		t.Fatalf("expected invalid client name close reason, got: %v", err)
	}
}

func TestHandshakeRejectsProtocolMismatch(t *testing.T) {
	srv := NewServer(Config{
		SimVersion:   "test",
		TickInterval: 20 * time.Millisecond,
	})
	defer srv.Close()
	ts := httptest.NewServer(srv.Handler())
	defer ts.Close()

	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"
	conn, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
	if err != nil {
		t.Fatalf("dial websocket: %v", err)
	}
	defer conn.Close()

	hsRaw, _ := json.Marshal(protocol.HandshakeMessage{Type: "handshake", ClientName: "bot-a"})
	if err := conn.WriteJSON(protocol.ProtocolEnvelope{
		ProtocolVersion: protocol.CurrentProtocolVersion + 1,
		SimVersion:      "test",
		Message:         hsRaw,
	}); err != nil {
		t.Fatalf("write handshake: %v", err)
	}
	conn.SetReadDeadline(time.Now().Add(500 * time.Millisecond))
	var env protocol.ProtocolEnvelope
	err = conn.ReadJSON(&env)
	if err == nil {
		t.Fatalf("expected protocol-mismatch handshake rejection")
	}
	if !strings.Contains(err.Error(), "protocol mismatch") {
		t.Fatalf("expected protocol mismatch close reason, got: %v", err)
	}
}

func TestBatchTickOutsideWindowIsRejected(t *testing.T) {
	srv := NewServer(Config{SimVersion: "test", TickInterval: 20 * time.Millisecond})
	defer srv.Close()
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
	_ = readEnvelope(t, conn) // initial snapshot

	x, y := 10.0, 10.0
	req := "r-future"
	batchRaw, _ := json.Marshal(protocol.CommandBatchMessage{
		Type: "commandBatch",
		Tick: 99,
		Commands: []protocol.WireCommand{
			{CommandType: "move", RequestID: &req, UnitIDs: []int{1}, X: &x, Y: &y},
		},
	})
	if err := conn.WriteJSON(protocol.ProtocolEnvelope{ProtocolVersion: protocol.CurrentProtocolVersion, SimVersion: "test", Message: batchRaw}); err != nil {
		t.Fatalf("write batch: %v", err)
	}
	waitForPendingBatches(t, srv, "default", 1)

	ackEnv := stepUntilMessageType(t, srv, conn, "commandAck", 5)
	var ack protocol.CommandAckMessage
	if err := json.Unmarshal(ackEnv.Message, &ack); err != nil {
		t.Fatalf("decode ack: %v", err)
	}
	if ack.Accepted || ack.Reason != "invalidTick" {
		t.Fatalf("expected invalidTick rejection, got %+v", ack)
	}
}

func TestPendingQueueLimitRejectsOverflow(t *testing.T) {
	srv := NewServer(Config{
		SimVersion:                 "test",
		TickInterval:               20 * time.Millisecond,
		MaxPendingBatchesPerClient: 1,
	})
	defer srv.Close()
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
	_ = readEnvelope(t, conn) // initial snapshot

	x1, y1 := 4.0, 5.0
	req1 := "r-1"
	batch1Raw, _ := json.Marshal(protocol.CommandBatchMessage{
		Type: "commandBatch", Tick: 1,
		Commands: []protocol.WireCommand{{CommandType: "move", RequestID: &req1, UnitIDs: []int{1}, X: &x1, Y: &y1}},
	})
	if err := conn.WriteJSON(protocol.ProtocolEnvelope{ProtocolVersion: protocol.CurrentProtocolVersion, SimVersion: "test", Message: batch1Raw}); err != nil {
		t.Fatalf("write batch1: %v", err)
	}

	x2, y2 := 6.0, 7.0
	req2 := "r-2"
	batch2Raw, _ := json.Marshal(protocol.CommandBatchMessage{
		Type: "commandBatch", Tick: 1,
		Commands: []protocol.WireCommand{{CommandType: "move", RequestID: &req2, UnitIDs: []int{1}, X: &x2, Y: &y2}},
	})
	if err := conn.WriteJSON(protocol.ProtocolEnvelope{ProtocolVersion: protocol.CurrentProtocolVersion, SimVersion: "test", Message: batch2Raw}); err != nil {
		t.Fatalf("write batch2: %v", err)
	}

	ackEnv := readUntilMessageType(t, conn, "commandAck")
	var ack protocol.CommandAckMessage
	if err := json.Unmarshal(ackEnv.Message, &ack); err != nil {
		t.Fatalf("decode ack: %v", err)
	}
	if ack.Accepted || ack.Reason != "queueFull" {
		t.Fatalf("expected queueFull rejection, got %+v", ack)
	}
}

func TestTwoClientsMatchEndsAndBroadcastsToBoth(t *testing.T) {
	srv := NewServer(Config{SimVersion: "test", TickInterval: 20 * time.Millisecond})
	defer srv.Close()
	ts := httptest.NewServer(srv.Handler())
	defer ts.Close()

	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"
	roomID := "duel"
	aConn := dialAndHandshake(t, wsURL, "bot-a", roomID)
	defer aConn.Close()
	bConn := dialAndHandshake(t, wsURL, "bot-b", roomID)
	defer bConn.Close()

	moveX, moveY := 28.0, 28.0
	moveReq := "move-close"
	moveBatchRaw, _ := json.Marshal(protocol.CommandBatchMessage{
		Type: "commandBatch", Tick: 1,
		Commands: []protocol.WireCommand{{CommandType: "move", RequestID: &moveReq, UnitIDs: []int{1}, X: &moveX, Y: &moveY}},
	})
	if err := aConn.WriteJSON(protocol.ProtocolEnvelope{ProtocolVersion: protocol.CurrentProtocolVersion, SimVersion: "test", Message: moveBatchRaw}); err != nil {
		t.Fatalf("write move batch: %v", err)
	}
	waitForPendingBatches(t, srv, roomID, 1)
	moveAck := stepUntilMessageType(t, srv, aConn, "commandAck", 5)
	var moveCmdAck protocol.CommandAckMessage
	if err := json.Unmarshal(moveAck.Message, &moveCmdAck); err != nil {
		t.Fatalf("decode move ack: %v", err)
	}
	if !moveCmdAck.Accepted {
		t.Fatalf("expected move accepted ack, got %+v", moveCmdAck)
	}
	_ = readUntilMessageType(t, aConn, "snapshot")
	_ = readUntilMessageType(t, bConn, "snapshot")

	var endA protocol.SnapshotMessage
	var endB protocol.SnapshotMessage
	for i := 0; i < 4; i++ {
		attackReq := "atk-" + string(rune('a'+i))
		targetID := 2
		attackBatchRaw, _ := json.Marshal(protocol.CommandBatchMessage{
			Type: "commandBatch", Tick: i + 2,
			Commands: []protocol.WireCommand{{CommandType: "attack", RequestID: &attackReq, UnitIDs: []int{1}, TargetUnitID: &targetID}},
		})
		if err := aConn.WriteJSON(protocol.ProtocolEnvelope{ProtocolVersion: protocol.CurrentProtocolVersion, SimVersion: "test", Message: attackBatchRaw}); err != nil {
			t.Fatalf("write attack batch: %v", err)
		}
		waitForPendingBatches(t, srv, roomID, 1)
		attackAck := stepUntilMessageType(t, srv, aConn, "commandAck", 5)
		var attackCmdAck protocol.CommandAckMessage
		if err := json.Unmarshal(attackAck.Message, &attackCmdAck); err != nil {
			t.Fatalf("decode attack ack: %v", err)
		}
		if !attackCmdAck.Accepted {
			t.Fatalf("expected attack accepted ack, got %+v", attackCmdAck)
		}

		endASnapEnv := readUntilMessageType(t, aConn, "snapshot")
		if err := json.Unmarshal(endASnapEnv.Message, &endA); err != nil {
			t.Fatalf("decode snapshot A: %v", err)
		}
		endBSnapEnv := readUntilMessageType(t, bConn, "snapshot")
		if err := json.Unmarshal(endBSnapEnv.Message, &endB); err != nil {
			t.Fatalf("decode snapshot B: %v", err)
		}
	}

	if !endA.MatchEnded || endA.WinnerID == nil || *endA.WinnerID != "player-1" {
		t.Fatalf("expected match end winner player-1 for client A, got %+v", endA)
	}
	if !endB.MatchEnded || endB.WinnerID == nil || *endB.WinnerID != "player-1" {
		t.Fatalf("expected match end winner player-1 for client B, got %+v", endB)
	}
	if endA.WorldHash != endB.WorldHash || endA.Tick != endB.Tick {
		t.Fatalf("clients observed different terminal snapshots: A=%+v B=%+v", endA, endB)
	}
}

func TestMalformedMoveCommandIsRejected(t *testing.T) {
	srv := NewServer(Config{SimVersion: "test", TickInterval: 20 * time.Millisecond})
	defer srv.Close()
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
	_ = readEnvelope(t, conn) // initial snapshot

	// Missing x coordinate for move => protocol payload invalid.
	y := 7.0
	req := "bad-move"
	batchRaw, _ := json.Marshal(protocol.CommandBatchMessage{
		Type: "commandBatch",
		Tick: 1,
		Commands: []protocol.WireCommand{
			{CommandType: "move", RequestID: &req, UnitIDs: []int{1}, Y: &y},
		},
	})
	if err := conn.WriteJSON(protocol.ProtocolEnvelope{ProtocolVersion: protocol.CurrentProtocolVersion, SimVersion: "test", Message: batchRaw}); err != nil {
		t.Fatalf("write batch: %v", err)
	}

	ackEnv := readEnvelope(t, conn)
	var ack protocol.CommandAckMessage
	if err := json.Unmarshal(ackEnv.Message, &ack); err != nil {
		t.Fatalf("decode ack: %v", err)
	}
	if ack.Accepted || ack.Reason != "invalidPayload" {
		t.Fatalf("expected invalidPayload rejection, got %+v", ack)
	}
}

func TestMalformedSurrenderCommandIsRejected(t *testing.T) {
	srv := NewServer(Config{SimVersion: "test", TickInterval: 20 * time.Millisecond})
	defer srv.Close()
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
	_ = readEnvelope(t, conn) // initial snapshot

	req := "bad-surrender"
	x := 10.0
	batchRaw, _ := json.Marshal(protocol.CommandBatchMessage{
		Type: "commandBatch",
		Tick: 1,
		Commands: []protocol.WireCommand{
			{CommandType: "surrender", RequestID: &req, X: &x},
		},
	})
	if err := conn.WriteJSON(protocol.ProtocolEnvelope{ProtocolVersion: protocol.CurrentProtocolVersion, SimVersion: "test", Message: batchRaw}); err != nil {
		t.Fatalf("write batch: %v", err)
	}

	ackEnv := readEnvelope(t, conn)
	var ack protocol.CommandAckMessage
	if err := json.Unmarshal(ackEnv.Message, &ack); err != nil {
		t.Fatalf("decode ack: %v", err)
	}
	if ack.Accepted || ack.Reason != "invalidPayload" {
		t.Fatalf("expected invalidPayload rejection, got %+v", ack)
	}
}

func TestHandshakeResumeTokenRestoresIdentity(t *testing.T) {
	srv := NewServer(Config{
		SimVersion:   "test",
		TickInterval: 20 * time.Millisecond,
		ResumeWindow: 2 * time.Second,
	})
	defer srv.Close()
	ts := httptest.NewServer(srv.Handler())
	defer ts.Close()

	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"
	roomID := "resume-room"
	conn1, ack1 := dialHandshakeWithAck(t, wsURL, protocol.HandshakeMessage{
		Type:          "handshake",
		ClientName:    "bot-a",
		RequestedRoom: &roomID,
	})
	if ack1.ResumeToken == nil || *ack1.ResumeToken == "" {
		t.Fatalf("expected resume token in handshake ack")
	}
	clientID := ack1.ClientID
	firstToken := *ack1.ResumeToken
	_ = conn1.Close()
	time.Sleep(10 * time.Millisecond)

	conn2, ack2 := dialHandshakeWithAck(t, wsURL, protocol.HandshakeMessage{
		Type:        "handshake",
		ClientName:  "ignored-on-resume",
		ResumeToken: &firstToken,
	})
	defer conn2.Close()
	if ack2.ClientID != clientID {
		t.Fatalf("expected resumed client id %s, got %s", clientID, ack2.ClientID)
	}
	if ack2.RoomID != roomID {
		t.Fatalf("expected resumed room %s, got %s", roomID, ack2.RoomID)
	}
	if ack2.ResumeToken == nil || *ack2.ResumeToken == "" || *ack2.ResumeToken == firstToken {
		t.Fatalf("expected rotated resume token, got %v", ack2.ResumeToken)
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

func dialAndHandshake(t *testing.T, wsURL, name, room string) *websocket.Conn {
	t.Helper()
	conn, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
	if err != nil {
		t.Fatalf("dial websocket: %v", err)
	}
	hsRaw, _ := json.Marshal(protocol.HandshakeMessage{Type: "handshake", ClientName: name, RequestedRoom: &room})
	if err := conn.WriteJSON(protocol.ProtocolEnvelope{ProtocolVersion: protocol.CurrentProtocolVersion, SimVersion: "test", Message: hsRaw}); err != nil {
		t.Fatalf("write handshake: %v", err)
	}
	conn.SetReadDeadline(time.Now().Add(2 * time.Second))
	_ = readUntilMessageType(t, conn, "handshakeAck")
	_ = readUntilMessageType(t, conn, "snapshot")
	return conn
}

func dialHandshakeWithAck(t *testing.T, wsURL string, hs protocol.HandshakeMessage) (*websocket.Conn, protocol.HandshakeAckMessage) {
	t.Helper()
	conn, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
	if err != nil {
		t.Fatalf("dial websocket: %v", err)
	}
	hsRaw, _ := json.Marshal(hs)
	if err := conn.WriteJSON(protocol.ProtocolEnvelope{
		ProtocolVersion: protocol.CurrentProtocolVersion,
		SimVersion:      "test",
		Message:         hsRaw,
	}); err != nil {
		t.Fatalf("write handshake: %v", err)
	}
	conn.SetReadDeadline(time.Now().Add(2 * time.Second))
	ackEnv := readUntilMessageType(t, conn, "handshakeAck")
	var ack protocol.HandshakeAckMessage
	if err := json.Unmarshal(ackEnv.Message, &ack); err != nil {
		t.Fatalf("decode handshake ack: %v", err)
	}
	_ = readUntilMessageType(t, conn, "snapshot")
	return conn, ack
}

func readUntilMessageType(t *testing.T, conn *websocket.Conn, want string) protocol.ProtocolEnvelope {
	t.Helper()
	for i := 0; i < 8; i++ {
		env := readEnvelope(t, conn)
		got, err := protocol.DecodeMessageType(env.Message)
		if err != nil {
			t.Fatalf("decode message type: %v", err)
		}
		if got == want {
			return env
		}
	}
	t.Fatalf("did not receive message type %s within bounded reads", want)
	return protocol.ProtocolEnvelope{}
}

func stepUntilMessageType(t *testing.T, srv *Server, conn *websocket.Conn, want string, steps int) protocol.ProtocolEnvelope {
	t.Helper()
	for i := 0; i < steps; i++ {
		srv.stepRooms()
		conn.SetReadDeadline(time.Now().Add(200 * time.Millisecond))
		for j := 0; j < 4; j++ {
			var env protocol.ProtocolEnvelope
			if err := conn.ReadJSON(&env); err != nil {
				if nerr, ok := err.(net.Error); ok && nerr.Timeout() {
					break
				}
				t.Fatalf("read envelope: %v", err)
			}
			got, err := protocol.DecodeMessageType(env.Message)
			if err != nil {
				t.Fatalf("decode message type: %v", err)
			}
			if got == want {
				return env
			}
		}
	}
	t.Fatalf("did not receive message type %s within %d step(s)", want, steps)
	return protocol.ProtocolEnvelope{}
}

func waitForPendingBatches(t *testing.T, srv *Server, roomID string, min int) {
	t.Helper()
	deadline := time.Now().Add(500 * time.Millisecond)
	for {
		srv.mu.Lock()
		rm := srv.rooms[roomID]
		srv.mu.Unlock()
		if rm != nil && rm.pendingBatchCount() >= min {
			return
		}
		if time.Now().After(deadline) {
			t.Fatalf("timed out waiting for pending batch in room %s", roomID)
		}
		time.Sleep(5 * time.Millisecond)
	}
}
