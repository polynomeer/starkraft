package main

import (
	"context"
	"log"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"syscall"
	"time"

	"github.com/starkraft/server/pkg/authoritative"
)

func main() {
	addr := getenv("STARKRAFT_SERVER_ADDR", ":8080")
	simVersion := getenv("STARKRAFT_SIM_VERSION", "dev")
	buildHash := getenv("STARKRAFT_BUILD_HASH", "")
	replayPath := os.Getenv("STARKRAFT_REPLAY_PATH")
	emptyRoomTTL := getenvDuration("STARKRAFT_EMPTY_ROOM_TTL", 30*time.Second)
	resumeWindow := getenvDuration("STARKRAFT_RESUME_WINDOW", 15*time.Second)
	keyframeEvery := getenvInt("STARKRAFT_KEYFRAME_EVERY", 50)

	srv := authoritative.NewServer(authoritative.Config{
		Addr:          addr,
		SimVersion:    simVersion,
		BuildHash:     buildHash,
		TickInterval:  20 * time.Millisecond,
		ReplayPath:    replayPath,
		KeyframeEvery: keyframeEvery,
		EmptyRoomTTL:  emptyRoomTTL,
		ResumeWindow:  resumeWindow,
	})

	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	log.Printf("starkraft-server listening on %s", addr)
	if err := srv.Run(ctx); err != nil {
		log.Fatalf("server error: %v", err)
	}
}

func getenv(key, fallback string) string {
	if v := strings.TrimSpace(os.Getenv(key)); v != "" {
		return v
	}
	return fallback
}

func getenvInt(key string, fallback int) int {
	raw := strings.TrimSpace(os.Getenv(key))
	if raw == "" {
		return fallback
	}
	value, err := strconv.Atoi(raw)
	if err != nil {
		log.Printf("invalid %s=%q (using default %d): %v", key, raw, fallback, err)
		return fallback
	}
	return value
}

func getenvDuration(key string, fallback time.Duration) time.Duration {
	raw := strings.TrimSpace(os.Getenv(key))
	if raw == "" {
		return fallback
	}
	value, err := time.ParseDuration(raw)
	if err != nil {
		log.Printf("invalid %s=%q (using default %s): %v", key, raw, fallback, err)
		return fallback
	}
	return value
}
