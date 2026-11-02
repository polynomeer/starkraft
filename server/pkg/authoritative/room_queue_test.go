package authoritative

import (
	"testing"

	"github.com/starkraft/server/pkg/protocol"
)

func TestQueueAnchorsOnLowestOwnedUnitID(t *testing.T) {
	for run := 0; run < 50; run++ {
		r := newRoom("queue-room")
		// Extra owned units so map iteration order could pick a different anchor.
		for id := 10; id < 20; id++ {
			r.units[id] = unitState{id: id, ownerID: "player-1", typeID: "Worker", x: float64(id), y: float64(id), hp: 40}
		}
		r.nextUnitID = 20

		accepted, reason := r.applyQueueLocked("player-1", protocol.WireCommand{CommandType: "queue"})
		if !accepted {
			t.Fatalf("run %d: expected queue accepted, got %s", run, reason)
		}
		spawned, ok := r.units[20]
		if !ok {
			t.Fatalf("run %d: expected spawned unit id 20", run)
		}
		// Unit 1 at (2,2) is the lowest-id unit owned by player-1.
		if spawned.x != 2.5 || spawned.y != 2.5 {
			t.Fatalf("run %d: expected spawn at (2.5,2.5), got (%v,%v)", run, spawned.x, spawned.y)
		}
	}
}
