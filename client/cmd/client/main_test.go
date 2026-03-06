package main

import (
	"testing"

	"github.com/starkraft/client/pkg/protocol"
)

func TestControlGroupsSaveRecallIsIsolated(t *testing.T) {
	groups := newControlGroups()
	selected := []int{3, 3, 1}
	groups.save(1, selected)
	selected[0] = 99

	restored := groups.recall(1)
	if len(restored) != 2 || restored[0] != 3 || restored[1] != 1 {
		t.Fatalf("unexpected restored group: %v", restored)
	}
	restored[0] = 55
	restoredAgain := groups.recall(1)
	if restoredAgain[0] != 3 {
		t.Fatalf("group recall should return copy, got %v", restoredAgain)
	}
}

func TestMergeUniqueIDsPreservesOrder(t *testing.T) {
	merged := mergeUniqueIDs([]int{4, 2, 4}, []int{2, 9, 4, 7})
	want := []int{4, 2, 9, 7}
	if len(merged) != len(want) {
		t.Fatalf("unexpected length: got %v want %v", merged, want)
	}
	for i := range want {
		if merged[i] != want[i] {
			t.Fatalf("unexpected merged[%d]=%d want=%d merged=%v", i, merged[i], want[i], merged)
		}
	}
}

func TestParseGroupSlot(t *testing.T) {
	slot, ok := parseGroupSlot([]string{"groupSave", "5"})
	if !ok || slot != 5 {
		t.Fatalf("expected slot 5 parse success, got slot=%d ok=%v", slot, ok)
	}
	if _, ok := parseGroupSlot([]string{"groupSave", "12"}); ok {
		t.Fatalf("expected slot parse failure for out-of-range slot")
	}
	if _, ok := parseGroupSlot([]string{"groupSave", "abc"}); ok {
		t.Fatalf("expected slot parse failure for non-number")
	}
}

func TestHudStateUpdateSummarizesUnits(t *testing.T) {
	hud := newHudState("player-1")
	winner := "player-1"
	hud.update(protocol.SnapshotMessage{
		Tick:       7,
		WorldHash:  1234,
		MatchEnded: true,
		WinnerID:   &winner,
		Units: []protocol.SnapshotUnit{
			{ID: 1, OwnerID: "player-1", TypeID: "Worker"},
			{ID: 2, OwnerID: "player-1", TypeID: "Soldier"},
			{ID: 3, OwnerID: "player-2", TypeID: "Worker"},
		},
	})

	if hud.tick != 7 || hud.worldHash != 1234 {
		t.Fatalf("unexpected hud tick/hash: %+v", hud)
	}
	if hud.totalUnits != 3 || hud.myUnits != 2 || hud.enemyUnits != 1 {
		t.Fatalf("unexpected unit counters: %+v", hud)
	}
	if !hud.matchEnded || hud.winnerID != "player-1" {
		t.Fatalf("unexpected match state: %+v", hud)
	}
	if hud.myTypeCounts["Worker"] != 1 || hud.myTypeCounts["Soldier"] != 1 {
		t.Fatalf("unexpected type counts: %+v", hud.myTypeCounts)
	}
	if hud.enemyByOwner["player-2"] != 1 {
		t.Fatalf("unexpected enemy owner counts: %+v", hud.enemyByOwner)
	}
}

func TestRequestStatusBookCountByState(t *testing.T) {
	book := newRequestStatusBook()
	book.onSubmit("a", "move", 1)
	book.onSubmit("b", "attack", 1)
	book.onSubmit("c", "queue", 1)
	book.onSubmit("d", "build", 1)
	book.onAck(protocol.CommandAckMessage{RequestID: ptr("a"), Accepted: true, CommandType: "move"})
	book.onAck(protocol.CommandAckMessage{RequestID: ptr("b"), Accepted: false, Reason: "notOwner", CommandType: "attack"})
	book.onSendError("c", "network")

	pending, accepted, rejected, sendFailed := book.countByState()
	if pending != 1 || accepted != 1 || rejected != 1 || sendFailed != 1 {
		t.Fatalf(
			"unexpected counts pending=%d accepted=%d rejected=%d sendFailed=%d",
			pending,
			accepted,
			rejected,
			sendFailed,
		)
	}
}

func ptr(value string) *string {
	return &value
}
