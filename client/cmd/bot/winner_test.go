package main

import "testing"

func TestWinnerLabelPrintsTheIDNotThePointer(t *testing.T) {
	winner := "player-1"
	if got := winnerLabel(&winner); got != "player-1" {
		t.Fatalf("winnerLabel = %q, want player-1", got)
	}
	if got := winnerLabel(nil); got != "none" {
		t.Fatalf("winnerLabel(nil) = %q, want none", got)
	}
}
