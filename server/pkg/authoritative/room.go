package authoritative

import (
	"sort"
	"sync"

	"github.com/starkraft/server/pkg/protocol"
)

type unitState struct {
	id      int
	ownerID string
	typeID  string
	x       float64
	y       float64
}

type room struct {
	id             string
	tick           int
	worldHash      int64
	units          map[int]unitState
	clients        map[*clientConn]struct{}
	pendingBatches []queuedBatch
	mu             sync.Mutex
}

type queuedBatch struct {
	clientID string
	batch    protocol.CommandBatchMessage
}

func newRoom(id string) *room {
	r := &room{
		id:      id,
		units:   make(map[int]unitState),
		clients: make(map[*clientConn]struct{}),
	}
	// Deterministic seed state for MVP networking.
	r.units[1] = unitState{id: 1, ownerID: "player-1", typeID: "Worker", x: 2, y: 2}
	r.units[2] = unitState{id: 2, ownerID: "player-2", typeID: "Worker", x: 28, y: 28}
	r.worldHash = r.computeWorldHash()
	return r
}

func (r *room) addClient(c *clientConn) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.clients[c] = struct{}{}
}

func (r *room) removeClient(c *clientConn) {
	r.mu.Lock()
	defer r.mu.Unlock()
	delete(r.clients, c)
}

func (r *room) enqueue(clientID string, batch protocol.CommandBatchMessage) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.pendingBatches = append(r.pendingBatches, queuedBatch{clientID: clientID, batch: batch})
}

func (r *room) step() snapshotRecord {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.tick++
	r.worldHash = r.computeWorldHash()
	r.pendingBatches = r.pendingBatches[:0]
	return r.snapshotLocked()
}

func (r *room) snapshot() snapshotRecord {
	r.mu.Lock()
	defer r.mu.Unlock()
	return r.snapshotLocked()
}

func (r *room) snapshotLocked() snapshotRecord {
	units := make([]protocol.SnapshotUnit, 0, len(r.units))
	keys := make([]int, 0, len(r.units))
	for id := range r.units {
		keys = append(keys, id)
	}
	sort.Ints(keys)
	for _, id := range keys {
		u := r.units[id]
		units = append(units, protocol.SnapshotUnit{ID: u.id, OwnerID: u.ownerID, TypeID: u.typeID, X: u.x, Y: u.y})
	}
	return snapshotRecord{Tick: r.tick, WorldHash: r.worldHash, Units: units}
}

func (r *room) computeWorldHash() int64 {
	var h int64 = int64(r.tick)*1469598103934665603 + 31
	keys := make([]int, 0, len(r.units))
	for id := range r.units {
		keys = append(keys, id)
	}
	sort.Ints(keys)
	for _, id := range keys {
		u := r.units[id]
		h = h*1099511628211 + int64(u.id)
		h = h*1099511628211 + int64(u.x*100)
		h = h*1099511628211 + int64(u.y*100)
	}
	return h
}

type snapshotRecord struct {
	Tick      int
	WorldHash int64
	Units     []protocol.SnapshotUnit
}
