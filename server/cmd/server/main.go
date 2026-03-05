package main

import (
	"context"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/starkraft/server/pkg/authoritative"
)

func main() {
	addr := getenv("STARKRAFT_SERVER_ADDR", ":8080")
	simVersion := getenv("STARKRAFT_SIM_VERSION", "dev")
	buildHash := getenv("STARKRAFT_BUILD_HASH", "")
	replayPath := os.Getenv("STARKRAFT_REPLAY_PATH")

	srv := authoritative.NewServer(authoritative.Config{
		Addr:         addr,
		SimVersion:   simVersion,
		BuildHash:    buildHash,
		TickInterval: 20 * time.Millisecond,
		ReplayPath:   replayPath,
		KeyframeEvery: 50,
	})

	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	log.Printf("starkraft-server listening on %s", addr)
	if err := srv.Run(ctx); err != nil {
		log.Fatalf("server error: %v", err)
	}
}

func getenv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
