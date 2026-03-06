package authoritative

import (
	"strings"
	"testing"

	"github.com/starkraft/server/pkg/protocol"
)

func TestClientConnSendEnvelopeRejectsWhenQueueFull(t *testing.T) {
	c := &clientConn{
		simVersion: "test",
		writeQueue: make(chan protocol.ProtocolEnvelope, 1),
	}

	if err := c.sendEnvelope(protocol.SnapshotMessage{Type: "snapshot", Tick: 1}); err != nil {
		t.Fatalf("first enqueue failed: %v", err)
	}
	err := c.sendEnvelope(protocol.SnapshotMessage{Type: "snapshot", Tick: 2})
	if err == nil {
		t.Fatalf("expected outbound queue full error")
	}
	if !strings.Contains(err.Error(), "outbound queue full") {
		t.Fatalf("unexpected error: %v", err)
	}
}
