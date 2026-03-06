package authoritative

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestVerifyReplayFileAcceptsBasicTimeline(t *testing.T) {
	path := filepath.Join(t.TempDir(), "replay.jsonl")
	content := strings.Join([]string{
		`{"recordType":"header","timestampUtc":"2026-01-01T00:00:00Z","protocolVersion":1,"simVersion":"test","tickMs":20}`,
		`{"recordType":"command","clientId":"player-1","command":{"commandType":"move"},"ack":{"type":"commandAck","tick":1,"commandType":"move","accepted":true}}`,
		`{"recordType":"keyframe","tick":1,"worldHash":123,"units":[]}`,
		`{"recordType":"keyframe","tick":2,"worldHash":456,"units":[]}`,
		`{"recordType":"matchEnd","tick":2,"winnerId":"player-1"}`,
	}, "\n") + "\n"
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatalf("write replay: %v", err)
	}

	summary, err := VerifyReplayFile(path)
	if err != nil {
		t.Fatalf("verify replay: %v", err)
	}
	if summary.HeaderCount != 1 || summary.KeyframeCount != 2 || summary.MatchEndCount != 1 {
		t.Fatalf("unexpected summary: %+v", summary)
	}
}

func TestVerifyReplayFileRejectsRegressedKeyframeTick(t *testing.T) {
	path := filepath.Join(t.TempDir(), "replay.jsonl")
	content := strings.Join([]string{
		`{"recordType":"header","timestampUtc":"2026-01-01T00:00:00Z","protocolVersion":1,"simVersion":"test","tickMs":20}`,
		`{"recordType":"keyframe","tick":2,"worldHash":123,"units":[]}`,
		`{"recordType":"keyframe","tick":1,"worldHash":456,"units":[]}`,
	}, "\n") + "\n"
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatalf("write replay: %v", err)
	}

	if _, err := VerifyReplayFile(path); err == nil {
		t.Fatalf("expected keyframe regression verification failure")
	}
}
