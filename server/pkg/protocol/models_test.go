package protocol

import (
	"encoding/json"
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
	golden := `{"protocolVersion":1,"simVersion":"1.0.0","buildHash":"abc123","message":{"type":"handshake","clientName":"bot-a","requestedRoom":"room-1"}}`
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
	golden := `{"protocolVersion":1,"simVersion":"1.0.0","message":{"type":"commandBatch","tick":120,"commands":[{"commandType":"move","requestId":"req-1"},{"commandType":"attack","requestId":"req-2"}]}}`
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
