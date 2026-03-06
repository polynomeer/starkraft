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

func TestClientRoutesMatchEndMessages(t *testing.T) {
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
			Type: "handshakeAck", RoomID: "test-room", ClientID: "player-1", ServerTickMs: 20, ProtocolVersion: protocol.CurrentProtocolVersion,
		})
		if err := conn.WriteJSON(protocol.ProtocolEnvelope{
			ProtocolVersion: protocol.CurrentProtocolVersion,
			SimVersion:      "test",
			Message:         ackRaw,
		}); err != nil {
			t.Errorf("write handshake ack: %v", err)
			return
		}

		endRaw, _ := json.Marshal(protocol.MatchEndMessage{
			Type: "matchEnd", Tick: 4, WinnerID: ptrString("player-2"),
		})
		if err := conn.WriteJSON(protocol.ProtocolEnvelope{
			ProtocolVersion: protocol.CurrentProtocolVersion,
			SimVersion:      "test",
			Message:         endRaw,
		}); err != nil {
			t.Errorf("write match end: %v", err)
			return
		}
	}))
	defer server.Close()

	wsURL := "ws" + strings.TrimPrefix(server.URL, "http")
	c, err := Dial(wsURL, "test", "bot-a", nil)
	if err != nil {
		t.Fatalf("dial: %v", err)
	}
	defer c.Close()

	select {
	case msg := <-c.MatchEndCh:
		if msg.Tick != 4 {
			t.Fatalf("expected matchEnd tick 4, got %d", msg.Tick)
		}
		if msg.WinnerID == nil || *msg.WinnerID != "player-2" {
			t.Fatalf("expected winner player-2, got %v", msg.WinnerID)
		}
	case <-time.After(2 * time.Second):
		t.Fatalf("timed out waiting for matchEnd message")
	}
}

func ptrString(v string) *string {
	return &v
}
