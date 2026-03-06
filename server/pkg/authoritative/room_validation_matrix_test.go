package authoritative

import (
	"testing"

	"github.com/starkraft/server/pkg/protocol"
)

func TestRoomValidationMatrix(t *testing.T) {
	xIn, yIn := 10.0, 10.0
	xOut, yOut := 99.0, 99.0
	cases := []struct {
		name         string
		prepare      func(r *room)
		batch        protocol.CommandBatchMessage
		wantAccepted bool
		wantReason   string
	}{
		{
			name: "reject not owner move",
			batch: protocol.CommandBatchMessage{
				Type: "commandBatch",
				Tick: 1,
				Commands: []protocol.WireCommand{
					{CommandType: "move", UnitIDs: []int{2}, X: &xIn, Y: &yIn},
				},
			},
			wantAccepted: false,
			wantReason:   "notOwner",
		},
		{
			name: "reject out of bounds move",
			batch: protocol.CommandBatchMessage{
				Type: "commandBatch",
				Tick: 1,
				Commands: []protocol.WireCommand{
					{CommandType: "move", UnitIDs: []int{1}, X: &xOut, Y: &yOut},
				},
			},
			wantAccepted: false,
			wantReason:   "outOfBounds",
		},
		{
			name: "reject rate limited command",
			prepare: func(r *room) {
				r.maxCommandsPerTickPerClient = 0
			},
			batch: protocol.CommandBatchMessage{
				Type: "commandBatch",
				Tick: 1,
				Commands: []protocol.WireCommand{
					{CommandType: "move", UnitIDs: []int{1}, X: &xIn, Y: &yIn},
				},
			},
			wantAccepted: false,
			wantReason:   "rateLimit",
		},
		{
			name: "reject invalid future tick",
			prepare: func(r *room) {
				r.tick = 1
				r.maxFutureTickSkew = 0
			},
			batch: protocol.CommandBatchMessage{
				Type: "commandBatch",
				Tick: 2,
				Commands: []protocol.WireCommand{
					{CommandType: "move", UnitIDs: []int{1}, X: &xIn, Y: &yIn},
				},
			},
			wantAccepted: false,
			wantReason:   "invalidTick",
		},
		{
			name: "accept valid move",
			batch: protocol.CommandBatchMessage{
				Type: "commandBatch",
				Tick: 1,
				Commands: []protocol.WireCommand{
					{CommandType: "move", UnitIDs: []int{1}, X: &xIn, Y: &yIn},
				},
			},
			wantAccepted: true,
			wantReason:   "",
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			r := newRoom("validation")
			if tc.prepare != nil {
				tc.prepare(r)
			}
			r.pendingBatches = append(r.pendingBatches, queuedBatch{
				clientID: "player-1",
				batch:    tc.batch,
			})
			acks := r.applyPendingLocked()
			if len(acks) != 1 {
				t.Fatalf("expected one ack, got %d", len(acks))
			}
			ack := acks[0].ack
			if ack.Accepted != tc.wantAccepted || ack.Reason != tc.wantReason {
				t.Fatalf("unexpected ack: got accepted=%v reason=%s", ack.Accepted, ack.Reason)
			}
		})
	}
}
