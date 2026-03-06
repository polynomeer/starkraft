package main

import "testing"

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
