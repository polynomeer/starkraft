package main

import (
	"flag"
	"fmt"
	"os"

	"github.com/starkraft/server/pkg/authoritative"
)

func main() {
	replayPath := flag.String("replay", "", "path to server replay jsonl")
	flag.Parse()
	if *replayPath == "" {
		fmt.Fprintln(os.Stderr, "usage: replaycheck --replay <path>")
		os.Exit(2)
	}
	summary, err := authoritative.VerifyReplayFile(*replayPath)
	if err != nil {
		fmt.Fprintf(os.Stderr, "replay verify failed: %v\n", err)
		os.Exit(1)
	}
	fmt.Printf(
		"ok records=%d header=%d commands=%d keyframes=%d matchEnd=%d lastTick=%d lastWorldHash=%d\n",
		summary.Records,
		summary.HeaderCount,
		summary.CommandCount,
		summary.KeyframeCount,
		summary.MatchEndCount,
		summary.LastTick,
		summary.LastWorldHash,
	)
}
