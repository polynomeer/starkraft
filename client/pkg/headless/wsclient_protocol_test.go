package headless

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/gorilla/websocket"
	"github.com/starkraft/client/pkg/protocol"
)

func TestReadLoopRejectsMismatchedEnvelopeProtocol(t *testing.T) {
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

		snapRaw, _ := json.Marshal(protocol.SnapshotMessage{
			Type:      "snapshot",
			Tick:      1,
			WorldHash: 7,
		})
		if err := conn.WriteJSON(protocol.ProtocolEnvelope{
			ProtocolVersion: protocol.CurrentProtocolVersion + 1,
			SimVersion:      "test",
			Message:         snapRaw,
		}); err != nil {
			t.Errorf("write mismatched snapshot: %v", err)
			return
		}
		time.Sleep(200 * time.Millisecond)
	}))
	defer server.Close()

	wsURL := "ws" + strings.TrimPrefix(server.URL, "http")
	client, err := Dial(wsURL, "test", "bot-a", nil)
	if err != nil {
		t.Fatalf("dial: %v", err)
	}
	defer client.Close()

	select {
	case err := <-client.ErrCh:
		if !strings.Contains(err.Error(), "protocol mismatch") {
			t.Fatalf("expected protocol mismatch, got: %v", err)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("timed out waiting for protocol mismatch error")
	}
}
