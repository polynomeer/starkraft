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
