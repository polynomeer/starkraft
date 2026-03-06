package protocol

import (
	"bytes"
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
)

func TestHandshakeEnvelopeRoundTripGolden(t *testing.T) {
	room := "room-1"
	build := "abc123"
	msg := HandshakeMessage{Type: "handshake", ClientName: "bot-a", RequestedRoom: &room}
	msgRaw, err := json.Marshal(msg)
	if err != nil {
		t.Fatalf("marshal handshake: %v", err)
	}
	env := ProtocolEnvelope{ProtocolVersion: CurrentProtocolVersion, SimVersion: "1.0.0", BuildHash: &build, Message: msgRaw}
	raw, err := json.Marshal(env)
	if err != nil {
		t.Fatalf("marshal envelope: %v", err)
	}
	golden := loadGolden(t, "v1-handshake-envelope.json")
	if string(raw) != golden {
		t.Fatalf("golden mismatch\nwant=%s\n got=%s", golden, string(raw))
	}
}

func TestHandshakeAckEnvelopeRoundTripGolden(t *testing.T) {
	token := "resume-1"
	msg := HandshakeAckMessage{
		Type:            "handshakeAck",
		RoomID:          "room-1",
		ClientID:        "player-1",
		ServerTickMs:    20,
		ProtocolVersion: CurrentProtocolVersion,
		ResumeToken:     &token,
	}
	msgRaw, err := json.Marshal(msg)
	if err != nil {
		t.Fatalf("marshal handshake ack: %v", err)
	}
	env := ProtocolEnvelope{ProtocolVersion: CurrentProtocolVersion, SimVersion: "1.0.0", Message: msgRaw}
	raw, err := json.Marshal(env)
	if err != nil {
		t.Fatalf("marshal envelope: %v", err)
	}
	golden := loadGolden(t, "v1-handshake-ack-envelope.json")
	if string(raw) != golden {
		t.Fatalf("golden mismatch\nwant=%s\n got=%s", golden, string(raw))
	}
}

func TestCommandBatchEnvelopeRoundTripGolden(t *testing.T) {
	req1 := "req-1"
	req2 := "req-2"
	msg := CommandBatchMessage{Type: "commandBatch", Tick: 120, Commands: []WireCommand{{CommandType: "move", RequestID: &req1}, {CommandType: "attack", RequestID: &req2}}}
	msgRaw, err := json.Marshal(msg)
	if err != nil {
		t.Fatalf("marshal batch: %v", err)
	}
	env := ProtocolEnvelope{ProtocolVersion: CurrentProtocolVersion, SimVersion: "1.0.0", Message: msgRaw}
	raw, err := json.Marshal(env)
	if err != nil {
		t.Fatalf("marshal envelope: %v", err)
	}
	golden := loadGolden(t, "v1-command-batch-envelope.json")
	if string(raw) != golden {
		t.Fatalf("golden mismatch\nwant=%s\n got=%s", golden, string(raw))
	}
}

func TestSnapshotEnvelopeRoundTripGolden(t *testing.T) {
	msg := SnapshotMessage{Type: "snapshot", Tick: 480, WorldHash: 123456789}
	msgRaw, err := json.Marshal(msg)
	if err != nil {
		t.Fatalf("marshal snapshot: %v", err)
	}
	env := ProtocolEnvelope{ProtocolVersion: CurrentProtocolVersion, SimVersion: "1.0.0", Message: msgRaw}
	raw, err := json.Marshal(env)
	if err != nil {
		t.Fatalf("marshal envelope: %v", err)
	}
	golden := loadGolden(t, "v1-snapshot-envelope.json")
	if string(raw) != golden {
		t.Fatalf("golden mismatch\nwant=%s\n got=%s", golden, string(raw))
	}
}

func TestCommandAckEnvelopeRoundTripGolden(t *testing.T) {
	req := "req-7"
	msg := CommandAckMessage{
		Type:        "commandAck",
		Tick:        121,
		RequestID:   &req,
		CommandType: "move",
		Accepted:    false,
		Reason:      "outOfBounds",
	}
	msgRaw, err := json.Marshal(msg)
	if err != nil {
		t.Fatalf("marshal commandAck: %v", err)
	}
	env := ProtocolEnvelope{ProtocolVersion: CurrentProtocolVersion, SimVersion: "1.0.0", Message: msgRaw}
	raw, err := json.Marshal(env)
	if err != nil {
		t.Fatalf("marshal envelope: %v", err)
	}
	golden := loadGolden(t, "v1-command-ack-envelope.json")
	if string(raw) != golden {
		t.Fatalf("golden mismatch\nwant=%s\n got=%s", golden, string(raw))
	}
}

func TestMatchEndEnvelopeRoundTripGolden(t *testing.T) {
	winner := "player-1"
	msg := MatchEndMessage{Type: "matchEnd", Tick: 600, WinnerID: &winner}
	msgRaw, err := json.Marshal(msg)
	if err != nil {
		t.Fatalf("marshal matchEnd: %v", err)
	}
	env := ProtocolEnvelope{ProtocolVersion: CurrentProtocolVersion, SimVersion: "1.0.0", Message: msgRaw}
	raw, err := json.Marshal(env)
	if err != nil {
		t.Fatalf("marshal envelope: %v", err)
	}
	golden := loadGolden(t, "v1-match-end-envelope.json")
	if string(raw) != golden {
		t.Fatalf("golden mismatch\nwant=%s\n got=%s", golden, string(raw))
	}
}

func loadGolden(t *testing.T, file string) string {
	t.Helper()
	candidates := []string{
		filepath.Join("..", "..", "..", "shared-protocol", "golden", file),
		filepath.Join("..", "..", "..", "..", "shared-protocol", "golden", file),
	}
	for _, c := range candidates {
		b, err := os.ReadFile(c)
		if err == nil {
			return string(bytes.TrimSpace(b))
		}
	}
	t.Fatalf("missing golden file %s", file)
	return ""
}
