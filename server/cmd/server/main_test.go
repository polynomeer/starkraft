package main

import (
	"testing"
	"time"
)

func TestGetenvInt(t *testing.T) {
	t.Setenv("STARKRAFT_TEST_INT", "12")
	got := getenvInt("STARKRAFT_TEST_INT", 3)
	if got != 12 {
		t.Fatalf("expected parsed int 12, got %d", got)
	}

	t.Setenv("STARKRAFT_TEST_INT", "bad")
	got = getenvInt("STARKRAFT_TEST_INT", 3)
	if got != 3 {
		t.Fatalf("expected fallback 3 for invalid int, got %d", got)
	}

	t.Setenv("STARKRAFT_TEST_INT", "   ")
	got = getenvInt("STARKRAFT_TEST_INT", 7)
	if got != 7 {
		t.Fatalf("expected fallback 7 for whitespace int, got %d", got)
	}
}

func TestGetenvDuration(t *testing.T) {
	t.Setenv("STARKRAFT_TEST_DURATION", "45s")
	got := getenvDuration("STARKRAFT_TEST_DURATION", 10*time.Second)
	if got != 45*time.Second {
		t.Fatalf("expected parsed duration 45s, got %s", got)
	}

	t.Setenv("STARKRAFT_TEST_DURATION", "not-a-duration")
	got = getenvDuration("STARKRAFT_TEST_DURATION", 10*time.Second)
	if got != 10*time.Second {
		t.Fatalf("expected fallback 10s for invalid duration, got %s", got)
	}

	t.Setenv("STARKRAFT_TEST_DURATION", "   ")
	got = getenvDuration("STARKRAFT_TEST_DURATION", 12*time.Second)
	if got != 12*time.Second {
		t.Fatalf("expected fallback 12s for whitespace duration, got %s", got)
	}
}

func TestGetenvTrimsWhitespace(t *testing.T) {
	t.Setenv("STARKRAFT_TEST_STRING", "  dev  ")
	got := getenv("STARKRAFT_TEST_STRING", "fallback")
	if got != "dev" {
		t.Fatalf("expected trimmed string dev, got %q", got)
	}

	t.Setenv("STARKRAFT_TEST_STRING", "   ")
	got = getenv("STARKRAFT_TEST_STRING", "fallback")
	if got != "fallback" {
		t.Fatalf("expected fallback for whitespace string, got %q", got)
	}
}
