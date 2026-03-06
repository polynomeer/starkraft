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
	hp      int
}

type room struct {
	id                          string
	tick                        int
	worldHash                   int64
	units                       map[int]unitState
	clients                     map[*clientConn]struct{}
	pendingBatches              []queuedBatch
	mu                          sync.Mutex
	mapWidth                    float64
	mapHeight                   float64
	maxCommandsPerTickPerClient int
	maxUnitsPerClient           int
	nextUnitID                  int
	matchEnded                  bool
	winnerID                    string
	maxTicks                    int
	maxPastTickSkew             int
	maxFutureTickSkew           int
	maxPendingBatchesPerClient  int
}

type queuedBatch struct {
	clientID string
	batch    protocol.CommandBatchMessage
}

func newRoom(id string) *room {
	r := &room{
		id:                          id,
		units:                       make(map[int]unitState),
		clients:                     make(map[*clientConn]struct{}),
		mapWidth:                    32,
		mapHeight:                   32,
		maxCommandsPerTickPerClient: 16,
		maxUnitsPerClient:           64,
		nextUnitID:                  3,
		maxTicks:                    600,
		maxPastTickSkew:             2,
		maxFutureTickSkew:           2,
		maxPendingBatchesPerClient:  8,
	}
	// Deterministic seed state for MVP networking.
	r.units[1] = unitState{id: 1, ownerID: "player-1", typeID: "Worker", x: 2, y: 2, hp: 40}
	r.units[2] = unitState{id: 2, ownerID: "player-2", typeID: "Worker", x: 28, y: 28, hp: 40}
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

func (r *room) enqueue(clientID string, batch protocol.CommandBatchMessage) bool {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.maxPendingBatchesPerClient > 0 {
		count := 0
		for i := 0; i < len(r.pendingBatches); i++ {
			if r.pendingBatches[i].clientID == clientID {
				count++
			}
		}
		if count >= r.maxPendingBatchesPerClient {
			return false
		}
	}
	r.pendingBatches = append(r.pendingBatches, queuedBatch{clientID: clientID, batch: batch})
	return true
}

func (r *room) pendingBatchCount() int {
	r.mu.Lock()
	defer r.mu.Unlock()
	return len(r.pendingBatches)
}

func (r *room) step() tickResult {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.tick++
	acks := r.applyPendingLocked()
	r.worldHash = r.computeWorldHash()
	r.pendingBatches = r.pendingBatches[:0]
	return tickResult{snapshot: r.snapshotLocked(), acks: acks, matchEnded: r.matchEnded}
}

type commandAckRecord struct {
	clientID string
	ack      protocol.CommandAckMessage
	command  protocol.WireCommand
}

type tickResult struct {
	snapshot   snapshotRecord
	acks       []commandAckRecord
	matchEnded bool
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
		units = append(units, protocol.SnapshotUnit{ID: u.id, OwnerID: u.ownerID, TypeID: u.typeID, X: u.x, Y: u.y, HP: u.hp})
	}
	var winner *string
	if r.winnerID != "" {
		w := r.winnerID
		winner = &w
	}
	return snapshotRecord{Tick: r.tick, WorldHash: r.worldHash, Units: units, MatchEnded: r.matchEnded, WinnerID: winner}
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
		h = h*1099511628211 + int64(u.hp)
	}
	return h
}

func (r *room) applyPendingLocked() []commandAckRecord {
	if len(r.pendingBatches) == 0 {
		return nil
	}
	acks := make([]commandAckRecord, 0, len(r.pendingBatches)*2)
	perClientCount := make(map[string]int, len(r.pendingBatches))
	for _, qb := range r.pendingBatches {
		if !r.batchTickAcceptedLocked(qb.batch.Tick) {
			for _, cmd := range qb.batch.Commands {
				acks = append(acks, commandAckRecord{
					clientID: qb.clientID,
					command:  cmd,
					ack: protocol.CommandAckMessage{
						Type: "commandAck", Tick: r.tick, RequestID: cmd.RequestID,
						CommandType: cmd.CommandType, Accepted: false, Reason: "invalidTick",
					},
				})
			}
			continue
		}
		for _, cmd := range qb.batch.Commands {
			perClientCount[qb.clientID]++
			if r.matchEnded {
				acks = append(acks, commandAckRecord{
					clientID: qb.clientID,
					command:  cmd,
					ack: protocol.CommandAckMessage{
						Type: "commandAck", Tick: r.tick, RequestID: cmd.RequestID,
						CommandType: cmd.CommandType, Accepted: false, Reason: "matchEnded",
					},
				})
				continue
			}
			if perClientCount[qb.clientID] > r.maxCommandsPerTickPerClient {
				acks = append(acks, commandAckRecord{
					clientID: qb.clientID,
					command:  cmd,
					ack: protocol.CommandAckMessage{
						Type: "commandAck", Tick: r.tick, RequestID: cmd.RequestID,
						CommandType: cmd.CommandType, Accepted: false, Reason: "rateLimit",
					},
				})
				continue
			}
			accepted, reason := r.applyCommandLocked(qb.clientID, cmd)
			acks = append(acks, commandAckRecord{
				clientID: qb.clientID,
				command:  cmd,
				ack: protocol.CommandAckMessage{
					Type: "commandAck", Tick: r.tick, RequestID: cmd.RequestID,
					CommandType: cmd.CommandType, Accepted: accepted, Reason: reason,
				},
			})
		}
	}
	r.updateWinnerLocked()
	return acks
}

func (r *room) batchTickAcceptedLocked(batchTick int) bool {
	if batchTick < r.tick-r.maxPastTickSkew {
		return false
	}
	if batchTick > r.tick+r.maxFutureTickSkew {
		return false
	}
	return true
}

func (r *room) applyCommandLocked(clientID string, cmd protocol.WireCommand) (bool, string) {
	switch cmd.CommandType {
	case "move":
		return r.applyMoveLocked(clientID, cmd)
	case "attack":
		return r.applyAttackLocked(clientID, cmd)
	case "build":
		return r.applyBuildLocked(clientID, cmd)
	case "queue":
		return r.applyQueueLocked(clientID, cmd)
	default:
		return false, "unsupportedCommand"
	}
}

func (r *room) applyMoveLocked(clientID string, cmd protocol.WireCommand) (bool, string) {
	if cmd.X == nil || cmd.Y == nil {
		return false, "invalidPayload"
	}
	if *cmd.X < 0 || *cmd.Y < 0 || *cmd.X >= r.mapWidth || *cmd.Y >= r.mapHeight {
		return false, "outOfBounds"
	}
	if len(cmd.UnitIDs) == 0 {
		return false, "missingUnits"
	}
	for _, unitID := range cmd.UnitIDs {
		u, ok := r.units[unitID]
		if !ok {
			return false, "missingUnit"
		}
		if u.ownerID != clientID {
			return false, "notOwner"
		}
	}
	for _, unitID := range cmd.UnitIDs {
		u := r.units[unitID]
		u.x = *cmd.X
		u.y = *cmd.Y
		r.units[unitID] = u
	}
	return true, ""
}

func (r *room) applyAttackLocked(clientID string, cmd protocol.WireCommand) (bool, string) {
	if cmd.TargetUnitID == nil {
		return false, "invalidPayload"
	}
	target, ok := r.units[*cmd.TargetUnitID]
	if !ok {
		return false, "missingUnit"
	}
	if len(cmd.UnitIDs) == 0 {
		return false, "missingUnits"
	}
	for _, attackerID := range cmd.UnitIDs {
		u, ok := r.units[attackerID]
		if !ok {
			return false, "missingUnit"
		}
		if u.ownerID != clientID {
			return false, "notOwner"
		}
	}
	if target.ownerID == clientID {
		return false, "invalidTarget"
	}
	damage := 0
	for _, attackerID := range cmd.UnitIDs {
		attacker := r.units[attackerID]
		dx := attacker.x - target.x
		dy := attacker.y - target.y
		if dx*dx+dy*dy <= 36 {
			damage += 10
		}
	}
	if damage == 0 {
		return false, "outOfRange"
	}
	target.hp -= damage
	if target.hp <= 0 {
		delete(r.units, target.id)
	} else {
		r.units[target.id] = target
	}
	return true, ""
}

func (r *room) applyBuildLocked(clientID string, cmd protocol.WireCommand) (bool, string) {
	if cmd.X == nil || cmd.Y == nil {
		return false, "invalidPayload"
	}
	if *cmd.X < 0 || *cmd.Y < 0 || *cmd.X >= r.mapWidth || *cmd.Y >= r.mapHeight {
		return false, "outOfBounds"
	}
	owned := 0
	for _, u := range r.units {
		if u.ownerID == clientID {
			owned++
		}
	}
	if owned >= r.maxUnitsPerClient {
		return false, "unitCapReached"
	}
	typeID := "Worker"
	if cmd.UnitType != nil && *cmd.UnitType != "" {
		typeID = *cmd.UnitType
	}
	id := r.nextUnitID
	r.nextUnitID++
	r.units[id] = unitState{id: id, ownerID: clientID, typeID: typeID, x: *cmd.X, y: *cmd.Y, hp: 40}
	return true, ""
}

func (r *room) applyQueueLocked(clientID string, cmd protocol.WireCommand) (bool, string) {
	var anchor *unitState
	for _, u := range r.units {
		if u.ownerID == clientID {
			uCopy := u
			anchor = &uCopy
			break
		}
	}
	if anchor == nil {
		return false, "missingBuilder"
	}
	typeID := "Soldier"
	if cmd.UnitType != nil && *cmd.UnitType != "" {
		typeID = *cmd.UnitType
	}
	x := anchor.x + 0.5
	y := anchor.y + 0.5
	if x >= r.mapWidth {
		x = anchor.x - 0.5
	}
	if y >= r.mapHeight {
		y = anchor.y - 0.5
	}
	id := r.nextUnitID
	r.nextUnitID++
	r.units[id] = unitState{id: id, ownerID: clientID, typeID: typeID, x: x, y: y, hp: 60}
	return true, ""
}

func (r *room) updateWinnerLocked() {
	if r.matchEnded {
		return
	}
	countByOwner := make(map[string]int)
	for _, u := range r.units {
		countByOwner[u.ownerID]++
	}
	aliveOwners := 0
	var winner string
	for owner, c := range countByOwner {
		if c > 0 {
			aliveOwners++
			winner = owner
		}
	}
	if aliveOwners <= 1 && winner != "" {
		r.matchEnded = true
		r.winnerID = winner
		return
	}
	if r.tick >= r.maxTicks {
		r.matchEnded = true
	}
}

type snapshotRecord struct {
	Tick       int
	WorldHash  int64
	Units      []protocol.SnapshotUnit
	MatchEnded bool
	WinnerID   *string
}
