package headless

import (
	"testing"

	"github.com/starkraft/client/pkg/protocol"
)

func TestSnapshotInterpolation(t *testing.T) {
	buf := &SnapshotBuffer{}
	buf.Push(protocol.SnapshotMessage{Tick: 1, Units: []protocol.SnapshotUnit{{ID: 1, OwnerID: "p1", X: 0, Y: 0, HP: 40}}})
	buf.Push(protocol.SnapshotMessage{Tick: 2, Units: []protocol.SnapshotUnit{{ID: 1, OwnerID: "p1", X: 10, Y: 10, HP: 40}}})
	out := buf.Interpolate(0.5)
	if len(out) != 1 {
		t.Fatalf("expected one unit, got %d", len(out))
	}
	if out[0].X != 5 || out[0].Y != 5 {
		t.Fatalf("expected interpolated 5,5 got %.2f,%.2f", out[0].X, out[0].Y)
	}
}
