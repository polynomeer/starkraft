package authoritative

import (
	"strings"
	"testing"

	"github.com/starkraft/server/pkg/protocol"
)

func TestClientConnSendEnvelopeRejectsWhenBusy(t *testing.T) {
	c := &clientConn{
		simVersion: "test",
	}

	c.writeMu.Lock()
	defer c.writeMu.Unlock()

	err := c.sendEnvelope(protocol.SnapshotMessage{Type: "snapshot", Tick: 2})
	if err == nil {
		t.Fatalf("expected connection busy error")
	}
	if !strings.Contains(err.Error(), "connection busy") {
		t.Fatalf("unexpected error: %v", err)
	}
}
