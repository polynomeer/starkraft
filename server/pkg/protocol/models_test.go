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

	var out ProtocolEnvelope
	if err := json.Unmarshal(raw, &out); err != nil {
		t.Fatalf("unmarshal envelope: %v", err)
	}
	if out.ProtocolVersion != env.ProtocolVersion || out.SimVersion != env.SimVersion {
		t.Fatalf("header mismatch: %+v", out)
	}
	var parsed HandshakeMessage
	if err := json.Unmarshal(out.Message, &parsed); err != nil {
		t.Fatalf("unmarshal message: %v", err)
	}
	if parsed.Type != "handshake" || parsed.ClientName != "bot-a" || parsed.RequestedRoom == nil || *parsed.RequestedRoom != room {
		t.Fatalf("message mismatch: %+v", parsed)
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

func TestCompatibilityMatrix(t *testing.T) {
	if Compatibility(1, 1) != Compatible {
		t.Fatal("expected compatible")
	}
	if Compatibility(1, 2) != UpgradeClient {
		t.Fatal("expected upgrade client")
	}
	if Compatibility(2, 1) != UpgradeServer {
		t.Fatal("expected upgrade server")
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
