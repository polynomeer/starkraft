package headless

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/gorilla/websocket"
	"github.com/starkraft/client/pkg/protocol"
)

func TestDialFailsOnProtocolVersionMismatch(t *testing.T) {
	upgrader := websocket.Upgrader{}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			t.Errorf("upgrade websocket: %v", err)
			return
		}
		defer conn.Close()

		var hs protocol.ProtocolEnvelope
		if err := conn.ReadJSON(&hs); err != nil {
			t.Errorf("read handshake: %v", err)
			return
		}

		ackRaw, _ := json.Marshal(protocol.HandshakeAckMessage{
			Type:            "handshakeAck",
			RoomID:          "test-room",
			ClientID:        "player-1",
			ServerTickMs:    20,
			ProtocolVersion: protocol.CurrentProtocolVersion + 1,
		})
		if err := conn.WriteJSON(protocol.ProtocolEnvelope{
			ProtocolVersion: protocol.CurrentProtocolVersion + 1,
			SimVersion:      "test",
			Message:         ackRaw,
		}); err != nil {
			t.Errorf("write handshake ack: %v", err)
			return
		}
	}))
	defer server.Close()

	wsURL := "ws" + strings.TrimPrefix(server.URL, "http")
	client, err := Dial(wsURL, "test", "bot-a", nil)
	if err == nil {
		_ = client.Close()
		t.Fatal("expected protocol mismatch error")
	}
	if !strings.Contains(err.Error(), "protocol mismatch") {
		t.Fatalf("expected protocol mismatch error, got %v", err)
	}
}

func TestDialFailsOnHandshakeAckVersionMismatch(t *testing.T) {
	upgrader := websocket.Upgrader{}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			t.Errorf("upgrade websocket: %v", err)
			return
		}
		defer conn.Close()

		var hs protocol.ProtocolEnvelope
		if err := conn.ReadJSON(&hs); err != nil {
			t.Errorf("read handshake: %v", err)
			return
		}

		ackRaw, _ := json.Marshal(protocol.HandshakeAckMessage{
			Type:            "handshakeAck",
			RoomID:          "test-room",
			ClientID:        "player-1",
			ServerTickMs:    20,
			ProtocolVersion: protocol.CurrentProtocolVersion + 1,
		})
		if err := conn.WriteJSON(protocol.ProtocolEnvelope{
			ProtocolVersion: protocol.CurrentProtocolVersion,
			SimVersion:      "test",
			Message:         ackRaw,
		}); err != nil {
			t.Errorf("write handshake ack: %v", err)
			return
		}
	}))
	defer server.Close()

	wsURL := "ws" + strings.TrimPrefix(server.URL, "http")
	client, err := Dial(wsURL, "test", "bot-a", nil)
	if err == nil {
		_ = client.Close()
		t.Fatal("expected protocol mismatch error")
	}
	if !strings.Contains(err.Error(), "protocol mismatch") {
		t.Fatalf("expected protocol mismatch error, got %v", err)
	}
}

func TestDialWithResumeSendsResumeToken(t *testing.T) {
	upgrader := websocket.Upgrader{}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			t.Errorf("upgrade websocket: %v", err)
			return
		}
		defer conn.Close()

		var hsEnv protocol.ProtocolEnvelope
		if err := conn.ReadJSON(&hsEnv); err != nil {
			t.Errorf("read handshake: %v", err)
			return
		}
		var hs protocol.HandshakeMessage
		if err := json.Unmarshal(hsEnv.Message, &hs); err != nil {
			t.Errorf("decode handshake: %v", err)
			return
		}
		if hs.ResumeToken == nil || *hs.ResumeToken != "resume-1" {
			t.Errorf("expected resume token resume-1, got %v", hs.ResumeToken)
			return
		}

		ackRaw, _ := json.Marshal(protocol.HandshakeAckMessage{
			Type:            "handshakeAck",
			RoomID:          "test-room",
			ClientID:        "player-1",
			ServerTickMs:    20,
			ProtocolVersion: protocol.CurrentProtocolVersion,
			ResumeToken:     ptrString("resume-2"),
		})
		if err := conn.WriteJSON(protocol.ProtocolEnvelope{
			ProtocolVersion: protocol.CurrentProtocolVersion,
			SimVersion:      "test",
			Message:         ackRaw,
		}); err != nil {
			t.Errorf("write handshake ack: %v", err)
			return
		}
	}))
	defer server.Close()

	wsURL := "ws" + strings.TrimPrefix(server.URL, "http")
	client, err := DialWithResume(wsURL, "test", "bot-a", nil, ptrString("resume-1"))
	if err != nil {
		t.Fatalf("dial: %v", err)
	}
	defer client.Close()
}

func TestDialWithResumeTrimsSimVersion(t *testing.T) {
	upgrader := websocket.Upgrader{}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			t.Errorf("upgrade websocket: %v", err)
			return
		}
		defer conn.Close()

		var hsEnv protocol.ProtocolEnvelope
		if err := conn.ReadJSON(&hsEnv); err != nil {
			t.Errorf("read handshake: %v", err)
			return
		}
		if hsEnv.SimVersion != "dev" {
			t.Errorf("expected trimmed sim version dev, got %q", hsEnv.SimVersion)
			return
		}

		ackRaw, _ := json.Marshal(protocol.HandshakeAckMessage{
			Type:            "handshakeAck",
			RoomID:          "test-room",
			ClientID:        "player-1",
			ServerTickMs:    20,
			ProtocolVersion: protocol.CurrentProtocolVersion,
		})
		if err := conn.WriteJSON(protocol.ProtocolEnvelope{
			ProtocolVersion: protocol.CurrentProtocolVersion,
			SimVersion:      "test",
			Message:         ackRaw,
		}); err != nil {
			t.Errorf("write handshake ack: %v", err)
			return
		}
	}))
	defer server.Close()

	wsURL := "ws" + strings.TrimPrefix(server.URL, "http")
	client, err := DialWithResume(wsURL, "  dev  ", "bot-a", nil, nil)
	if err != nil {
		t.Fatalf("dial: %v", err)
	}
	defer client.Close()
}
